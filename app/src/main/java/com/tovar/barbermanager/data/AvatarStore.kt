package com.tovar.barbermanager.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Foto de perfil de cada cuenta. Se guarda ya recortada (cuadrada) en el almacenamiento
 * privado de la app, una por correo, así que no cambia la base de datos ni requiere permisos.
 */
object AvatarStore {

    private fun fileFor(context: Context, email: String): File {
        val dir = File(context.applicationContext.filesDir, "avatars").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return File(dir, "$hash.jpg")
    }

    fun load(context: Context, email: String): Bitmap? {
        val file = fileFor(context, email)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun delete(context: Context, email: String) {
        fileFor(context, email).delete()
    }

    /** Guarda la imagen ya recortada. No la recicla; eso lo decide quien la creó. */
    fun saveBitmap(context: Context, email: String, bitmap: Bitmap): Boolean = try {
        FileOutputStream(fileFor(context, email)).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
        true
    } catch (_: IOException) {
        false
    }

    /**
     * Lee la imagen elegida reducida a [maxSide] px por su lado más largo y con la
     * orientación correcta. Devuelve null si no se puede leer.
     */
    fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val resolver = context.applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val scale = maxSide.toFloat() / maxOf(width, height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1)
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            raw?.let { rotate(it, orientation) }
        }
    } catch (_: Exception) {
        null
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
