package com.tovar.barbermanager.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalTime

/** Error de negocio mapeado directamente a HTTP por [io.ktor.server.plugins.StatusPages]. */
class ApiException(val status: HttpStatusCode, message: String) : Exception(message)

data class AuthUser(val id: Long, val rol: String) {
    val esAdmin: Boolean get() = rol == "ADMINISTRADOR"
}

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val ESTADOS_FINALES = setOf("COMPLETADA", "CANCELADA")
private val ESTADOS_ADMIN = setOf("CONFIRMADA", "COMPLETADA", "CANCELADA")

private fun badRequest(message: String) = ApiException(HttpStatusCode.BadRequest, message)
private fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, message)
private fun conflict(message: String) = ApiException(HttpStatusCode.Conflict, message)
private fun unauthorized(message: String) = ApiException(HttpStatusCode.Unauthorized, message)

/** Lee y valida el encabezado `Authorization: Bearer <token>`. */
private fun ApplicationCall.requireUser(config: Config): AuthUser {
    val header = request.headers[HttpHeaders.Authorization]
    val token = header?.removePrefix("Bearer")?.trim()
    if (token.isNullOrBlank()) throw unauthorized("Se requiere iniciar sesión.")
    val userId = Tokens.verify(token, config.tokenSecret)
        ?: throw unauthorized("La sesión expiró o no es válida; vuelve a iniciar sesión.")
    val usuario = Repository.usuarioPorId(userId) ?: throw unauthorized("La cuenta ya no existe.")
    if (!usuario.activo) throw ApiException(HttpStatusCode.Forbidden, "La cuenta está desactivada.")
    return AuthUser(usuario.id, usuario.rol)
}

private fun parseFecha(value: String): LocalDate =
    try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw badRequest("Fecha inválida; usa el formato YYYY-MM-DD.")
    }

private fun parseHora(value: String): String =
    try {
        Availability.horaDe(Availability.minutosDe(value))
    } catch (_: IllegalArgumentException) {
        throw badRequest("Hora inválida; usa el formato HH:mm.")
    }

fun Route.apiRoutes(config: Config) {
    route("/api") {
        authRoutes(config)
        catalogRoutes(config)
        appointmentRoutes(config)
        profileRoutes(config)
    }
}

// ---------------------------------------------------------------- autenticación

private fun Route.authRoutes(config: Config) {
    post("/auth/registro") {
        val body = call.receive<RegistroRequest>()
        val correo = body.correo.trim().lowercase()
        when {
            body.nombreCompleto.isBlank() -> throw badRequest("Escribe tu nombre completo.")
            !EMAIL_REGEX.matches(correo) -> throw badRequest("Correo electrónico inválido.")
            body.password.length < 6 -> throw badRequest("La contraseña debe tener al menos 6 caracteres.")
        }
        if (Repository.usuarioPorCorreo(correo) != null) throw conflict("Ese correo ya está registrado.")

        val credentials = Passwords.create(body.password)
        val telefono = body.telefono?.trim()?.takeIf { it.isNotEmpty() }
        val id = try {
            Repository.insertarUsuario(
                nombreCompleto = body.nombreCompleto.trim(),
                correo = correo,
                telefono = telefono,
                passwordHash = credentials.encodedHash,
                passwordSalt = credentials.encodedSalt,
                rol = "CLIENTE"
            )
        } catch (error: SQLException) {
            if (Repository.esDuplicado(error)) throw conflict("Ese correo ya está registrado.")
            throw error
        }
        val usuario = Repository.usuarioPorId(id) ?: throw ApiException(HttpStatusCode.InternalServerError, "No se pudo crear la cuenta.")
        call.respond(AuthResponse(Tokens.issue(id, config.tokenSecret), usuario.toDto()))
    }

    post("/auth/login") {
        val body = call.receive<LoginRequest>()
        val correo = body.correo.trim().lowercase()
        val usuario = Repository.usuarioPorCorreo(correo)
            ?: throw unauthorized("El correo o la contraseña son incorrectos.")
        if (!usuario.activo) throw ApiException(HttpStatusCode.Forbidden, "La cuenta está desactivada. Contacta al administrador.")
        if (!Passwords.verify(body.password, usuario.passwordSalt, usuario.passwordHash)) {
            throw unauthorized("El correo o la contraseña son incorrectos.")
        }
        call.respond(AuthResponse(Tokens.issue(usuario.id, config.tokenSecret), usuario.toDto()))
    }
}

// ---------------------------------------------------------------- catálogo y disponibilidad (público)

private fun Route.catalogRoutes(@Suppress("UNUSED_PARAMETER") config: Config) {
    get("/servicios") {
        call.respond(Repository.serviciosActivos())
    }

    get("/barberos") {
        call.respond(Repository.barberosActivos())
    }

    get("/barberos/{id}/disponibilidad") {
        val barberoId = call.parameters["id"]?.toLongOrNull() ?: throw badRequest("Barbero inválido.")
        val fechaTexto = call.request.queryParameters["fecha"] ?: throw badRequest("Falta el parámetro fecha.")
        val fecha = parseFecha(fechaTexto)
        val barbero = Repository.barbero(barberoId) ?: throw notFound("El barbero no existe.")
        if (!barbero.activo) throw notFound("El barbero ya no está disponible.")

        val slots = slotsDisponibles(barberoId, fecha, exceptoCitaId = null)
        call.respond(
            DisponibilidadResponse(
                barberoId = barberoId,
                fecha = fechaTexto,
                slots = slots.map { SlotDto(it.horaInicio, it.horaFin) }
            )
        )
    }
}

/** Slots libres de un barbero en una fecha (sin citas pasadas si es hoy). */
internal fun slotsDisponibles(
    barberoId: Long,
    fecha: LocalDate,
    exceptoCitaId: Long?
): List<Availability.Slot> {
    val horario = Repository.horario(barberoId, Availability.diaSemanaDe(fecha))
    val bloqueos = Repository.bloqueos(barberoId, fecha)
    val ocupadas = Repository.citasOcupadas(barberoId, fecha, exceptoCitaId)
    val hoy = LocalDate.now()
    val minutoActual = if (fecha == hoy) LocalTime.now().let { it.hour * 60 + it.minute } else null
    return Availability.slotsDelDia(horario, bloqueos, ocupadas, minutoActual)
}

// ---------------------------------------------------------------- citas

private fun Route.appointmentRoutes(config: Config) {
    get("/citas") {
        val user = call.requireUser(config)
        val estado = call.request.queryParameters["estado"]?.uppercase()
        val clienteId = if (user.esAdmin) null else user.id
        call.respond(Repository.citas(clienteId, estado))
    }

    post("/citas") {
        val user = call.requireUser(config)
        val body = call.receive<CitaRequest>()
        val fecha = parseFecha(body.fecha)
        val horaInicio = parseHora(body.horaInicio)

        val barbero = Repository.barbero(body.barberoId) ?: throw notFound("El barbero no existe.")
        if (!barbero.activo) throw notFound("El barbero ya no está disponible.")
        val servicio = Repository.servicio(body.servicioId) ?: throw notFound("El servicio no existe.")
        if (!servicio.activo) throw notFound("El servicio ya no está disponible.")

        val hoy = LocalDate.now()
        if (Availability.esPasada(fecha, horaInicio, hoy, LocalTime.now())) {
            throw badRequest("No puedes agendar una cita en el pasado.")
        }

        val slots = slotsDisponibles(barbero.id, fecha, exceptoCitaId = null)
        val slot = Availability.buscarSlot(slots, horaInicio)
            ?: throw conflict("Ese horario ya no está disponible. Elige otro turno.")

        val usuario = Repository.usuarioPorId(user.id) ?: throw unauthorized("La cuenta ya no existe.")
        val citaId = try {
            Repository.insertarCita(
                clienteId = user.id,
                barberoId = barbero.id,
                servicioId = servicio.id,
                fecha = fecha,
                horaInicio = LocalTime.parse(slot.horaInicio),
                horaFin = LocalTime.parse(slot.horaFin),
                nombreServicio = servicio.nombre,
                precioCentavos = servicio.precioCentavos,
                nombreCliente = usuario.nombreCompleto,
                telefonoCliente = usuario.telefono,
                notas = body.notas?.trim()?.takeIf { it.isNotEmpty() }
            )
        } catch (error: SQLException) {
            if (Repository.esDuplicado(error)) throw conflict("Ese horario acaba de ocuparse. Elige otro turno.")
            throw error
        }

        Repository.insertarHistorial(
            citaId = citaId,
            cambiadoPor = user.id,
            tipoCambio = "CREADA",
            fechaNueva = fecha,
            horaInicioNueva = LocalTime.parse(slot.horaInicio),
            horaFinNueva = LocalTime.parse(slot.horaFin)
        )
        call.respond(HttpStatusCode.Created, Repository.citaPorId(citaId)!!)
    }

    patch("/citas/{id}/estado") {
        val user = call.requireUser(config)
        val cita = call.citaEncontrada()
        val body = call.receive<EstadoRequest>()
        val estado = body.estado.uppercase()
        if (estado !in ESTADOS_ADMIN) throw badRequest("Estado no válido: $estado.")
        if (!user.esAdmin && cita.clienteId != user.id) {
            throw ApiException(HttpStatusCode.Forbidden, "Solo puedes modificar tus propias citas.")
        }
        if (!user.esAdmin && estado != "CANCELADA") {
            throw ApiException(HttpStatusCode.Forbidden, "Como cliente solo puedes cancelar tus citas.")
        }
        if (cita.estado in ESTADOS_FINALES) throw conflict("La cita ya está ${cita.estado.lowercase()}.")

        Repository.actualizarEstado(cita.id, estado)
        Repository.insertarHistorial(
            citaId = cita.id,
            cambiadoPor = user.id,
            tipoCambio = if (estado == "CANCELADA") "CANCELADA" else "ESTADO_CAMBIADO",
            fechaAnterior = parseFecha(cita.fecha),
            horaInicioAnterior = LocalTime.parse(cita.horaInicio),
            horaFinAnterior = LocalTime.parse(cita.horaFin),
            motivo = body.motivo
        )
        call.respond(Repository.citaPorId(cita.id)!!)
    }

    patch("/citas/{id}/reprogramar") {
        val user = call.requireUser(config)
        val cita = call.citaEncontrada()
        val body = call.receive<ReprogramarRequest>()
        if (!user.esAdmin && cita.clienteId != user.id) {
            throw ApiException(HttpStatusCode.Forbidden, "Solo puedes reprogramar tus propias citas.")
        }
        if (cita.estado in ESTADOS_FINALES) throw conflict("La cita ya está ${cita.estado.lowercase()}.")

        val fecha = parseFecha(body.fecha)
        val horaInicio = parseHora(body.horaInicio)
        val hoy = LocalDate.now()
        if (Availability.esPasada(fecha, horaInicio, hoy, LocalTime.now())) {
            throw badRequest("No puedes reprogramar una cita al pasado.")
        }
        val slot = slotsDisponibles(cita.barberoId, fecha, exceptoCitaId = cita.id)
            .firstOrNull { it.horaInicio == horaInicio }
            ?: throw conflict("Ese horario ya no está disponible. Elige otro turno.")

        Repository.reprogramarCita(cita.id, fecha, LocalTime.parse(slot.horaInicio), LocalTime.parse(slot.horaFin))
        Repository.insertarHistorial(
            citaId = cita.id,
            cambiadoPor = user.id,
            tipoCambio = "REPROGRAMADA",
            fechaAnterior = parseFecha(cita.fecha),
            horaInicioAnterior = LocalTime.parse(cita.horaInicio),
            horaFinAnterior = LocalTime.parse(cita.horaFin),
            fechaNueva = fecha,
            horaInicioNueva = LocalTime.parse(slot.horaInicio),
            horaFinNueva = LocalTime.parse(slot.horaFin),
            motivo = body.motivo
        )
        call.respond(Repository.citaPorId(cita.id)!!)
    }
}

private suspend fun ApplicationCall.citaEncontrada(): CitaDto {
    val id = parameters["id"]?.toLongOrNull() ?: throw badRequest("Cita inválida.")
    return Repository.citaPorId(id) ?: throw notFound("La cita no existe.")
}

// ---------------------------------------------------------------- perfil

private fun Route.profileRoutes(config: Config) {
    get("/usuarios/perfil") {
        val user = call.requireUser(config)
        val usuario = Repository.usuarioPorId(user.id) ?: throw notFound("La cuenta no existe.")
        call.respond(usuario.toDto())
    }

    patch("/usuarios/perfil") {
        val user = call.requireUser(config)
        val usuario = Repository.usuarioPorId(user.id) ?: throw notFound("La cuenta no existe.")
        val body = call.receive<PerfilUpdateRequest>()
        val nombre = body.nombreCompleto?.trim()?.takeIf { it.isNotEmpty() } ?: usuario.nombreCompleto
        val telefono = when {
            body.telefono == null -> usuario.telefono
            body.telefono.isBlank() -> null
            else -> body.telefono.trim()
        }
        Repository.actualizarPerfil(user.id, nombre, telefono)
        call.respond(Repository.usuarioPorId(user.id)!!.toDto())
    }

    patch("/usuarios/perfil/contrasena") {
        val user = call.requireUser(config)
        val usuario = Repository.usuarioPorId(user.id) ?: throw notFound("La cuenta no existe.")
        val body = call.receive<PasswordUpdateRequest>()
        if (body.nuevaPassword.length < 6) throw badRequest("La contraseña debe tener al menos 6 caracteres.")
        if (!Passwords.verify(body.actualPassword, usuario.passwordSalt, usuario.passwordHash)) {
            throw ApiException(HttpStatusCode.Forbidden, "La contraseña actual es incorrecta.")
        }
        val credentials = Passwords.create(body.nuevaPassword)
        Repository.actualizarPassword(user.id, credentials.encodedHash, credentials.encodedSalt)
        call.respond(MensajeResponse("Contraseña actualizada."))
    }

    get("/usuarios/perfil/historial") {
        val user = call.requireUser(config)
        call.respond(Repository.historialDeCliente(user.id))
    }
}
