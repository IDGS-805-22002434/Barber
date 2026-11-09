package com.tovar.barbermanager.server

import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.LocalTime

/** Consultas SQL del dominio BarberManager. Sin ORM: JDBC puro sobre el pool de [Db]. */
object Repository {

    data class UsuarioRow(
        val id: Long,
        val nombreCompleto: String,
        val correo: String,
        val telefono: String?,
        val passwordHash: String,
        val passwordSalt: String,
        val rol: String,
        val activo: Boolean,
        val creadoEn: String?
    ) {
        fun toDto(): UsuarioDto = UsuarioDto(
            id = id,
            nombreCompleto = nombreCompleto,
            correo = correo,
            telefono = telefono,
            rol = rol,
            activo = activo,
            creadoEn = creadoEn
        )
    }

    data class ServicioRow(
        val id: Long,
        val nombre: String,
        val descripcion: String,
        val categoria: String,
        val precioCentavos: Long,
        val activo: Boolean
    )

    data class BarberoRow(
        val id: Long,
        val nombre: String,
        val aniosExperiencia: Int,
        val activo: Boolean
    )

    // ------------------------------------------------------------- usuarios

    fun usuarioPorCorreo(correo: String): UsuarioRow? = Db.one(
        """
        SELECT id, nombre_completo, correo, telefono, password_hash, password_salt,
               rol, activo, creado_en
        FROM usuarios WHERE correo = ?
        """.trimIndent(),
        listOf(correo)
    ) { it.toUsuario() }

    fun usuarioPorId(id: Long): UsuarioRow? = Db.one(
        """
        SELECT id, nombre_completo, correo, telefono, password_hash, password_salt,
               rol, activo, creado_en
        FROM usuarios WHERE id = ?
        """.trimIndent(),
        listOf(id)
    ) { it.toUsuario() }

    fun insertarUsuario(
        nombreCompleto: String,
        correo: String,
        telefono: String?,
        passwordHash: String,
        passwordSalt: String,
        rol: String
    ): Long = Db.insert(
        """
        INSERT INTO usuarios (nombre_completo, correo, telefono, password_hash, password_salt, rol)
        VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        listOf(nombreCompleto, correo, telefono, passwordHash, passwordSalt, rol)
    )

    fun actualizarPerfil(id: Long, nombreCompleto: String, telefono: String?): Int = Db.update(
        "UPDATE usuarios SET nombre_completo = ?, telefono = ? WHERE id = ?",
        listOf(nombreCompleto, telefono, id)
    )

    fun actualizarPassword(id: Long, passwordHash: String, passwordSalt: String): Int = Db.update(
        "UPDATE usuarios SET password_hash = ?, password_salt = ? WHERE id = ?",
        listOf(passwordHash, passwordSalt, id)
    )

    fun contarAdministradores(): Int = Db.one(
        "SELECT COUNT(*) FROM usuarios WHERE rol = 'ADMINISTRADOR'"
    ) { rs -> rs.getInt(1) } ?: 0

    // ------------------------------------------------------------- catálogo

    fun serviciosActivos(): List<ServicioDto> = Db.list(
        """
        SELECT s.id, s.nombre, s.descripcion, c.nombre AS categoria, s.precio_centavos, s.activo
        FROM servicios s
        JOIN categorias c ON c.id = s.categoria_id
        WHERE s.activo = 1
        ORDER BY s.nombre
        """.trimIndent()
    ) { rs ->
        ServicioDto(
            id = rs.getLong("id"),
            nombre = rs.getString("nombre"),
            descripcion = rs.getString("descripcion"),
            categoria = rs.getString("categoria"),
            precioCentavos = rs.getLong("precio_centavos"),
            activo = rs.getBoolean("activo")
        )
    }

    fun servicio(id: Long): ServicioRow? = Db.one(
        """
        SELECT s.id, s.nombre, s.descripcion, c.nombre AS categoria, s.precio_centavos, s.activo
        FROM servicios s
        JOIN categorias c ON c.id = s.categoria_id
        WHERE s.id = ?
        """.trimIndent(),
        listOf(id)
    ) { rs ->
        ServicioRow(
            id = rs.getLong("id"),
            nombre = rs.getString("nombre"),
            descripcion = rs.getString("descripcion"),
            categoria = rs.getString("categoria"),
            precioCentavos = rs.getLong("precio_centavos"),
            activo = rs.getBoolean("activo")
        )
    }

    fun barberosActivos(): List<BarberoDto> = Db.list(
        """
        SELECT id, nombre, anios_experiencia, activo
        FROM barberos WHERE activo = 1
        ORDER BY nombre
        """.trimIndent()
    ) { rs ->
        BarberoDto(
            id = rs.getLong("id"),
            nombre = rs.getString("nombre"),
            aniosExperiencia = rs.getInt("anios_experiencia"),
            activo = rs.getBoolean("activo"),
            especialidades = especialidadesDeBarbero(rs.getLong("id"))
        )
    }

    fun barbero(id: Long): BarberoRow? = Db.one(
        "SELECT id, nombre, anios_experiencia, activo FROM barberos WHERE id = ?",
        listOf(id)
    ) { rs ->
        BarberoRow(
            id = rs.getLong("id"),
            nombre = rs.getString("nombre"),
            aniosExperiencia = rs.getInt("anios_experiencia"),
            activo = rs.getBoolean("activo")
        )
    }

    private fun especialidadesDeBarbero(barberoId: Long): List<String> = Db.list(
        """
        SELECT e.nombre
        FROM barbero_especialidad be
        JOIN especialidades e ON e.id = be.especialidad_id
        WHERE be.barbero_id = ? AND e.activo = 1
        ORDER BY e.nombre
        """.trimIndent(),
        listOf(barberoId)
    ) { rs -> rs.getString("nombre") }

    // ------------------------------------------------------------- horarios y bloqueos

    fun horario(barberoId: Long, diaSemana: Int): Availability.Horario? = Db.one(
        """
        SELECT dia_semana, hora_inicio, hora_fin, duracion_turno_minutos
        FROM barbero_horarios
        WHERE barbero_id = ? AND dia_semana = ? AND activo = 1
        """.trimIndent(),
        listOf(barberoId, diaSemana)
    ) { rs ->
        Availability.Horario(
            diaSemana = rs.getInt("dia_semana"),
            inicio = rs.getString("hora_inicio"),
            fin = rs.getString("hora_fin"),
            duracionMinutos = rs.getInt("duracion_turno_minutos")
        )
    }

    fun bloqueos(barberoId: Long, fecha: LocalDate): List<Availability.Rango> = Db.list(
        """
        SELECT hora_inicio, hora_fin
        FROM barbero_bloqueos
        WHERE barbero_id = ? AND fecha = ?
        """.trimIndent(),
        listOf(barberoId, fecha)
    ) { rs ->
        val inicio = rs.getString("hora_inicio")
        val fin = rs.getString("hora_fin")
        if (inicio == null || fin == null) Availability.rangoDelDia()
        else Availability.Rango(inicio, fin)
    }

    /** Citas que ocupan agenda (no canceladas). [exceptoCitaId] permite reprogramar sobre sí misma. */
    fun citasOcupadas(barberoId: Long, fecha: LocalDate, exceptoCitaId: Long? = null): List<Availability.Rango> {
        val sql = buildString {
            append("SELECT hora_inicio, hora_fin FROM citas WHERE barbero_id = ? AND fecha = ? AND estado <> 'CANCELADA'")
            if (exceptoCitaId != null) append(" AND id <> ?")
        }
        val params = buildList {
            add(barberoId)
            add(fecha)
            if (exceptoCitaId != null) add(exceptoCitaId)
        }
        return Db.list(sql, params) { rs ->
            Availability.Rango(rs.getString("hora_inicio"), rs.getString("hora_fin"))
        }
    }

    // ------------------------------------------------------------- citas

    fun insertarCita(
        clienteId: Long,
        barberoId: Long,
        servicioId: Long,
        fecha: LocalDate,
        horaInicio: LocalTime,
        horaFin: LocalTime,
        nombreServicio: String,
        precioCentavos: Long,
        nombreCliente: String,
        telefonoCliente: String?,
        notas: String?
    ): Long = Db.insert(
        """
        INSERT INTO citas (
            cliente_id, barbero_id, servicio_id, fecha, hora_inicio, hora_fin, estado,
            nombre_servicio, precio_centavos, nombre_cliente, telefono_cliente, notas
        ) VALUES (?, ?, ?, ?, ?, ?, 'PENDIENTE', ?, ?, ?, ?, ?)
        """.trimIndent(),
        listOf(
            clienteId, barberoId, servicioId, fecha, horaInicio, horaFin,
            nombreServicio, precioCentavos, nombreCliente, telefonoCliente, notas
        )
    )

    fun citas(clienteId: Long?, estado: String?): List<CitaDto> {
        val where = buildString {
            if (clienteId != null) append(" WHERE c.cliente_id = ?")
            if (estado != null) append(if (clienteId != null) " AND c.estado = ?" else " WHERE c.estado = ?")
        }
        val params = buildList {
            clienteId?.let { add(it) }
            estado?.let { add(it) }
        }
        return Db.list(
            """
            SELECT c.id, c.cliente_id, u.nombre_completo AS cliente_nombre,
                   c.barbero_id, b.nombre AS barbero_nombre,
                   c.servicio_id, c.nombre_servicio, c.precio_centavos,
                   c.fecha, c.hora_inicio, c.hora_fin, c.estado,
                   c.telefono_cliente, c.notas, c.creada_en
            FROM citas c
            JOIN usuarios u ON u.id = c.cliente_id
            JOIN barberos b ON b.id = c.barbero_id
            $where
            ORDER BY c.fecha DESC, c.hora_inicio DESC
            LIMIT 200
            """.trimIndent(),
            params
        ) { it.toCita() }
    }

    fun citaPorId(id: Long): CitaDto? = Db.one(
        """
        SELECT c.id, c.cliente_id, u.nombre_completo AS cliente_nombre,
               c.barbero_id, b.nombre AS barbero_nombre,
               c.servicio_id, c.nombre_servicio, c.precio_centavos,
               c.fecha, c.hora_inicio, c.hora_fin, c.estado,
               c.telefono_cliente, c.notas, c.creada_en
        FROM citas c
        JOIN usuarios u ON u.id = c.cliente_id
        JOIN barberos b ON b.id = c.barbero_id
        WHERE c.id = ?
        """.trimIndent(),
        listOf(id)
    ) { it.toCita() }

    fun actualizarEstado(id: Long, estado: String): Int = Db.update(
        "UPDATE citas SET estado = ? WHERE id = ?",
        listOf(estado, id)
    )

    fun reprogramarCita(id: Long, fecha: LocalDate, horaInicio: LocalTime, horaFin: LocalTime): Int = Db.update(
        "UPDATE citas SET fecha = ?, hora_inicio = ?, hora_fin = ?, estado = 'REPROGRAMADA' WHERE id = ?",
        listOf(fecha, horaInicio, horaFin, id)
    )

    fun historialDeCliente(clienteId: Long): List<HistorialItemDto> = Db.list(
        """
        SELECT c.fecha, c.hora_inicio, c.nombre_servicio, c.precio_centavos,
               b.nombre AS barbero_nombre, c.estado
        FROM citas c
        JOIN barberos b ON b.id = c.barbero_id
        WHERE c.cliente_id = ? AND c.estado = 'COMPLETADA'
        ORDER BY c.fecha DESC, c.hora_inicio DESC
        LIMIT 20
        """.trimIndent(),
        listOf(clienteId)
    ) { rs ->
        HistorialItemDto(
            fecha = rs.getString("fecha"),
            horaInicio = rs.getString("hora_inicio").substring(0, 5),
            nombreServicio = rs.getString("nombre_servicio"),
            precioCentavos = rs.getLong("precio_centavos"),
            barberoNombre = rs.getString("barbero_nombre")
        )
    }

    fun insertarHistorial(
        citaId: Long,
        cambiadoPor: Long?,
        tipoCambio: String,
        fechaAnterior: LocalDate? = null,
        horaInicioAnterior: LocalTime? = null,
        horaFinAnterior: LocalTime? = null,
        fechaNueva: LocalDate? = null,
        horaInicioNueva: LocalTime? = null,
        horaFinNueva: LocalTime? = null,
        motivo: String? = null
    ): Long = Db.insert(
        """
        INSERT INTO historial_citas (
            cita_id, cambiado_por, tipo_cambio,
            fecha_anterior, hora_inicio_anterior, hora_fin_anterior,
            fecha_nueva, hora_inicio_nueva, hora_fin_nueva, motivo
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
        listOf(
            citaId, cambiadoPor, tipoCambio,
            fechaAnterior, horaInicioAnterior, horaFinAnterior,
            fechaNueva, horaInicioNueva, horaFinNueva, motivo
        )
    )

    /** Clave duplicada (MySQL 1062): se traduce a HTTP 409 en las rutas. */
    fun esDuplicado(error: SQLException): Boolean =
        error.errorCode == 1062 || "23000" == error.sqlState

    // ------------------------------------------------------------- mappers

    private fun ResultSet.toUsuario() = UsuarioRow(
        id = getLong("id"),
        nombreCompleto = getString("nombre_completo"),
        correo = getString("correo"),
        telefono = getString("telefono"),
        passwordHash = getString("password_hash"),
        passwordSalt = getString("password_salt"),
        rol = getString("rol"),
        activo = getBoolean("activo"),
        creadoEn = getString("creado_en")
    )

    private fun ResultSet.toCita() = CitaDto(
        id = getLong("id"),
        clienteId = getLong("cliente_id"),
        clienteNombre = getString("cliente_nombre"),
        barberoId = getLong("barbero_id"),
        barberoNombre = getString("barbero_nombre"),
        servicioId = getLong("servicio_id"),
        nombreServicio = getString("nombre_servicio"),
        precioCentavos = getLong("precio_centavos"),
        fecha = getString("fecha"),
        horaInicio = getString("hora_inicio").substring(0, 5),
        horaFin = getString("hora_fin").substring(0, 5),
        estado = getString("estado"),
        telefonoCliente = getString("telefono_cliente"),
        notas = getString("notas"),
        creadaEn = getString("creada_en")
    )
}
