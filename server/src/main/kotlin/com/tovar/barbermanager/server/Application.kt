package com.tovar.barbermanager.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun main() {
    val config = Config.fromEnv()
    Db.init(config)
    seedAdmin(config)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: Config) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Patch)
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.message ?: "Error"))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("Cuerpo JSON inválido."))
        }
        exception<JsonConvertException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("Cuerpo JSON inválido."))
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("Cuerpo de petición inválido."))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Error no controlado", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Error interno del servidor."))
        }
    }
    routing {
        apiRoutes(config)
    }
}

/** Crea la cuenta administradora inicial si el esquema no tiene ninguna. */
private fun seedAdmin(config: Config) {
    if (Repository.contarAdministradores() > 0) return
    val credentials = Passwords.create(config.adminPassword)
    Repository.insertarUsuario(
        nombreCompleto = "Administrador BarberManager",
        correo = config.adminEmail.trim().lowercase(),
        telefono = null,
        passwordHash = credentials.encodedHash,
        passwordSalt = credentials.encodedSalt,
        rol = "ADMINISTRADOR"
    )
    println("Cuenta administradora creada: ${config.adminEmail}")
}
