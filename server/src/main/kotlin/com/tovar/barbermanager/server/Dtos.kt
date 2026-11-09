package com.tovar.barbermanager.server

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val message: String)

// ---------------------------------------------------------------- auth

@Serializable
data class RegistroRequest(
    val nombreCompleto: String = "",
    val correo: String = "",
    val telefono: String? = null,
    val password: String = ""
)

@Serializable
data class LoginRequest(
    val correo: String = "",
    val password: String = ""
)

@Serializable
data class AuthResponse(val token: String, val usuario: UsuarioDto)

// ---------------------------------------------------------------- usuarios / perfil

@Serializable
data class UsuarioDto(
    val id: Long,
    val nombreCompleto: String,
    val correo: String,
    val telefono: String? = null,
    val rol: String,
    val activo: Boolean,
    val creadoEn: String? = null
)

@Serializable
data class PerfilUpdateRequest(
    val nombreCompleto: String? = null,
    val telefono: String? = null
)

@Serializable
data class PasswordUpdateRequest(
    val actualPassword: String = "",
    val nuevaPassword: String = ""
)

@Serializable
data class MensajeResponse(val message: String)

// ---------------------------------------------------------------- catálogo

@Serializable
data class ServicioDto(
    val id: Long,
    val nombre: String,
    val descripcion: String,
    val categoria: String,
    val precioCentavos: Long,
    val activo: Boolean
)

@Serializable
data class BarberoDto(
    val id: Long,
    val nombre: String,
    val aniosExperiencia: Int,
    val activo: Boolean,
    val especialidades: List<String> = emptyList()
)

// ---------------------------------------------------------------- disponibilidad

@Serializable
data class SlotDto(
    val horaInicio: String,
    val horaFin: String
)

@Serializable
data class DisponibilidadResponse(
    val barberoId: Long,
    val fecha: String,
    val slots: List<SlotDto>
)

// ---------------------------------------------------------------- citas

@Serializable
data class CitaRequest(
    val barberoId: Long = 0L,
    val servicioId: Long = 0L,
    val fecha: String = "",
    val horaInicio: String = "",
    val notas: String? = null
)

@Serializable
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

@Serializable
data class EstadoRequest(
    val estado: String = "",
    val motivo: String? = null
)

@Serializable
data class ReprogramarRequest(
    val fecha: String = "",
    val horaInicio: String = "",
    val motivo: String? = null
)

/** Servicio recibido por el cliente (RF08): historial de citas completadas. */
@Serializable
data class HistorialItemDto(
    val fecha: String,
    val horaInicio: String,
    val nombreServicio: String,
    val precioCentavos: Long,
    val barberoNombre: String
)
