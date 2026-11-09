package com.tovar.barbermanager.data.remote

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error de API: `code` 0 = sin conexión; `message` ya viene en español (del servidor o local). */
class ApiException(val code: Int, override val message: String) : Exception(message)

/**
 * Cliente HTTP único de la app. La URL base y el token se pueden cambiar en
 * caliente (Perfil → servidor; login/logout) sin reconstruir los ViewModels.
 */
object ApiClient {

    const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    @Volatile
    private var token: String? = null

    @Volatile
    private var service: ApiService? = null

    private val moshi: Moshi = Moshi.Builder().build()

    private val authInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        token?.let { builder.header("Authorization", "Bearer $it") }
        chain.proceed(builder.build())
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun setToken(value: String?) {
        token = value
    }

    /**
     * Normaliza una URL introducida por el usuario. Acepta `host:puerto` sin
     * esquema, exige HTTP/HTTPS y deja siempre una barra final para Retrofit.
     * Devuelve null cuando la URL no es utilizable.
     */
    fun normalizeBaseUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        if (parsed.host.isBlank()) return null
        if (parsed.query != null || parsed.fragment != null) return null
        return parsed.toString().trimEnd('/') + "/"
    }

    /** Aplica una URL ya validada; si es inválida conserva la anterior. */
    fun setBaseUrl(url: String): Boolean {
        val normalized = normalizeBaseUrl(url) ?: return false
        if (normalized != baseUrl) {
            baseUrl = normalized
            service = null
        }
        return true
    }

    @Synchronized
    fun service(): ApiService = service ?: run {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(ApiService::class.java).also { service = it }
    }

    /** Traduce las excepciones de Retrofit a [ApiException] con mensaje presentable. */
    suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        throw httpToApiException(error)
    } catch (error: IOException) {
        throw ApiException(0, "No hay conexión con el servidor. Revisa tu red.")
    }

    private fun httpToApiException(error: HttpException): ApiException {
        val code = error.code()
        val message = runCatching {
            error.response()?.errorBody()?.string()
                ?.let { moshi.adapter(ApiError::class.java).fromJson(it)?.message }
        }.getOrNull()
        return ApiException(code, message ?: "Error del servidor (HTTP $code).")
    }
}
