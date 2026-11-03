package com.tovar.barbermanager.data.remote

import com.squareup.moshi.JsonClass

/** DTOs espejo del servidor Ktor; los nombres deben coincidir con `server/.../Dtos.kt`. */

@JsonClass(generateAdapter = true)
data class ApiError(val message: String)

@JsonClass(generateAdapter = true)
data class RegistroRequest(
    val nombreCompleto: String,
    val correo: String,
    val telefono: String? = null,
    val password: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(val correo: String, val password: String)

@JsonClass(generateAdapter = true)
data class AuthResponse(val token: String, val usuario: UsuarioDto)

@JsonClass(generateAdapter = true)
data class UsuarioDto(
    val id: Long,
    val nombreCompleto: String,
    val correo: String,
    val telefono: String? = null,
    val rol: String,
    val activo: Boolean,
    val creadoEn: String? = null
)

@JsonClass(generateAdapter = true)
data class PerfilUpdateRequest(
    val nombreCompleto: String? = null,
    val telefono: String? = null
)

@JsonClass(generateAdapter = true)
data class PasswordUpdateRequest(val actualPassword: String, val nuevaPassword: String)

@JsonClass(generateAdapter = true)
data class MensajeResponse(val message: String)

@JsonClass(generateAdapter = true)
data class ServicioDto(
    val id: Long,
    val nombre: String,
    val descripcion: String,
    val categoria: String,
    val precioCentavos: Long,
    val activo: Boolean
)

@JsonClass(generateAdapter = true)
data class BarberoDto(
    val id: Long,
    val nombre: String,
    val aniosExperiencia: Int,
    val activo: Boolean,
    val especialidades: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SlotDto(val horaInicio: String, val horaFin: String)

@JsonClass(generateAdapter = true)
data class DisponibilidadResponse(
    val barberoId: Long,
    val fecha: String,
    val slots: List<SlotDto>
)

@JsonClass(generateAdapter = true)
data class CitaRequest(
    val barberoId: Long,
    val servicioId: Long,
    val fecha: String,
    val horaInicio: String,
    val notas: String? = null
)

@JsonClass(generateAdapter = true)
data class CitaDto(
    val id: Long,
    val clienteId: Long,
    val clienteNombre: String,
    val barberoId: Long,
    val barberoNombre: String,
    val servicioId: Long,
    val nombreServicio: String,
    val precioCentavos: Long,
    val fecha: String,
    val horaInicio: String,
    val horaFin: String,
    val estado: String,
    val telefonoCliente: String? = null,
    val notas: String? = null,
    val creadaEn: String? = null
)

@JsonClass(generateAdapter = true)
data class EstadoRequest(val estado: String, val motivo: String? = null)

@JsonClass(generateAdapter = true)
data class ReprogramarRequest(val fecha: String, val horaInicio: String, val motivo: String? = null)

@JsonClass(generateAdapter = true)
data class HistorialItemDto(
    val fecha: String,
    val horaInicio: String,
    val nombreServicio: String,
    val precioCentavos: Long,
    val barberoNombre: String
)
