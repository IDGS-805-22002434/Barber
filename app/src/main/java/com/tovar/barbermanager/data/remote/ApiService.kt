package com.tovar.barbermanager.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints del servidor Ktor (`server/src/main/kotlin/.../Routes.kt`). */
interface ApiService {

    @POST("api/auth/registro")
    suspend fun registro(@Body body: RegistroRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("api/servicios")
    suspend fun servicios(): List<ServicioDto>

    @GET("api/barberos")
    suspend fun barberos(): List<BarberoDto>

    @GET("api/barberos/{id}/disponibilidad")
    suspend fun disponibilidad(
        @Path("id") barberoId: Long,
        @Query("fecha") fecha: String
    ): DisponibilidadResponse

    @GET("api/citas")
    suspend fun citas(@Query("estado") estado: String? = null): List<CitaDto>

    @POST("api/citas")
    suspend fun crearCita(@Body body: CitaRequest): CitaDto

    @PATCH("api/citas/{id}/estado")
    suspend fun cambiarEstadoCita(
        @Path("id") citaId: Long,
        @Body body: EstadoRequest
    ): CitaDto

    @PATCH("api/citas/{id}/reprogramar")
    suspend fun reprogramarCita(
        @Path("id") citaId: Long,
        @Body body: ReprogramarRequest
    ): CitaDto

    @GET("api/usuarios/perfil")
    suspend fun perfil(): UsuarioDto

    @PATCH("api/usuarios/perfil")
    suspend fun actualizarPerfil(@Body body: PerfilUpdateRequest): UsuarioDto

    @PATCH("api/usuarios/perfil/contrasena")
    suspend fun cambiarContrasena(@Body body: PasswordUpdateRequest): MensajeResponse

    @GET("api/usuarios/perfil/historial")
    suspend fun historial(): List<HistorialItemDto>
}
