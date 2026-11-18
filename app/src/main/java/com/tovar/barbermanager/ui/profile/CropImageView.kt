package com.tovar.barbermanager.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Recortador de foto de perfil: la imagen se arrastra y se acerca (pellizco o
 * [setZoomFraction]) detrás de un círculo, y [croppedBitmap] devuelve lo que se ve dentro.
 * La imagen siempre cubre el círculo, así que nunca quedan bordes vacíos.
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Se llama con el zoom actual entre 0 (mínimo) y 1 (máximo). */
    var onZoomChange: ((Float) -> Unit)? = null

    private var bitmap: Bitmap? = null
    private val imageMatrix2 = Matrix()
    private val crop = RectF()
    private val scratch = RectF()
    private var minScale = 1f

    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint().apply { color = 0xB3000000.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val scrimPath = Path()

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }
    )

    private val moveDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                imageMatrix2.postTranslate(-distanceX, -distanceY)
                keepCovering()
                invalidate()
                return true
            }
        }
    )

    fun setBitmap(source: Bitmap) {
        bitmap = source
        if (width > 0 && height > 0) resetPosition()
        invalidate()
    }

    /** Zoom entre 0 (la imagen justo cubre el círculo) y 1 (cinco veces más cerca). */
    fun setZoomFraction(fraction: Float) {
        if (bitmap == null) return
        val target = minScale * (1f + fraction.coerceIn(0f, 1f) * (MAX_ZOOM - 1f))
        zoomBy(target / currentScale(), crop.centerX(), crop.centerY())
    }

    /** Devuelve lo que se ve dentro del círculo como imagen cuadrada de [size] px. */
    fun croppedBitmap(size: Int): Bitmap? {
        val source = bitmap ?: return null
        if (crop.isEmpty) return null
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val m = Matrix(imageMatrix2)
        m.postTranslate(-crop.left, -crop.top)
        val factor = size / crop.width()
        m.postScale(factor, factor)
        Canvas(output).drawBitmap(source, m, bitmapPaint)
        return output
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = min(w, h) - 2 * MARGIN_DP * density
        crop.set(
            w / 2f - side / 2f,
            h / 2f - side / 2f,
            w / 2f + side / 2f,
            h / 2f + side / 2f
        )
        if (bitmap != null) resetPosition()
    }

    override fun onDraw(canvas: Canvas) {
        val source = bitmap ?: return
        canvas.drawBitmap(source, imageMatrix2, bitmapPaint)

        scrimPath.reset()
        scrimPath.fillType = Path.FillType.EVEN_ODD
        scrimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        scrimPath.addCircle(crop.centerX(), crop.centerY(), crop.width() / 2f, Path.Direction.CW)
        canvas.drawPath(scrimPath, scrimPaint)
        canvas.drawCircle(crop.centerX(), crop.centerY(), crop.width() / 2f, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) moveDetector.onTouchEvent(event)
        return true
    }

    private fun resetPosition() {
        val source = bitmap ?: return
        minScale = max(crop.width() / source.width, crop.height() / source.height)
        imageMatrix2.reset()
        imageMatrix2.postScale(minScale, minScale)
        imageMatrix2.postTranslate(
            crop.centerX() - source.width * minScale / 2f,
            crop.centerY() - source.height * minScale / 2f
        )
        onZoomChange?.invoke(0f)
    }

    private fun currentScale(): Float {
        val values = FloatArray(9)
        imageMatrix2.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val current = currentScale()
        val target = (current * factor).coerceIn(minScale, minScale * MAX_ZOOM)
        val applied = target / current
        imageMatrix2.postScale(applied, applied, focusX, focusY)
        keepCovering()
        invalidate()
        onZoomChange?.invoke((currentScale() / minScale - 1f) / (MAX_ZOOM - 1f))
    }

    /** Evita que la imagen se salga del círculo dejando huecos. */
    private fun keepCovering() {
        val source = bitmap ?: return
        scratch.set(0f, 0f, source.width.toFloat(), source.height.toFloat())
        imageMatrix2.mapRect(scratch)
        var dx = 0f
        var dy = 0f
        if (scratch.left > crop.left) dx = crop.left - scratch.left
        else if (scratch.right < crop.right) dx = crop.right - scratch.right
        if (scratch.top > crop.top) dy = crop.top - scratch.top
        else if (scratch.bottom < crop.bottom) dy = crop.bottom - scratch.bottom
        imageMatrix2.postTranslate(dx, dy)
    }

    private companion object {
        const val MAX_ZOOM = 5f
        const val MARGIN_DP = 16f
    }
}
