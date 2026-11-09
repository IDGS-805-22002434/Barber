package com.tovar.barbermanager.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Acceso a MySQL: pool HikariCP + helpers mínimos sobre JDBC puro.
 * Al iniciar, si falta el esquema se ejecuta el script `schema.sql` (el mismo
 * que se entrega para importar a mano con el cliente de MySQL).
 */
object Db {

    private val log = LoggerFactory.getLogger(Db::class.java)
    private var pool: HikariDataSource? = null

    fun init(config: Config) {
        if (pool != null) return
        synchronized(this) {
            if (pool != null) return
            if (!schemaExists(config)) {
                log.info("Esquema ausente; ejecutando schema.sql en {}", config.dbName)
                runSchema(config)
            }
            val cfg = HikariConfig().apply {
                jdbcUrl = config.jdbcUrl(withDatabase = true)
                username = config.dbUser
                password = config.dbPassword
                maximumPoolSize = 10
                poolName = "barber-pool"
            }
            pool = HikariDataSource(cfg)
            log.info("Conectado a MySQL en {}:{}", config.dbHost, config.dbPort)
        }
    }

    fun close() {
        pool?.close()
        pool = null
    }

    fun <T> list(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> {
        requirePool().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement, params)
                statement.executeQuery().use { rs ->
                    val result = mutableListOf<T>()
                    while (rs.next()) result += mapper(rs)
                    return result
                }
            }
        }
    }

    fun <T> one(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): T? =
        list(sql, params, mapper).firstOrNull()

    fun update(sql: String, params: List<Any?> = emptyList()): Int =
        requirePool().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement, params)
                statement.executeUpdate()
            }
        }

    fun insert(sql: String, params: List<Any?> = emptyList()): Long =
        requirePool().connection.use { connection ->
            connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS).use { statement ->
                bind(statement, params)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    if (keys.next()) keys.getLong(1) else 0L
                }
            }
        }

    /** Ejecuta varias operaciones en una misma transacción; hace rollback si algo falla. */
    fun <T> transaction(block: (Connection) -> T): T {
        val connection = requirePool().connection
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false
        return try {
            val result = block(connection)
            connection.commit()
            result
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = previousAutoCommit
            connection.close()
        }
    }

    private fun requirePool(): HikariDataSource =
        pool ?: error("Db.init(config) debe llamarse antes de usar la base de datos.")

    private fun bind(statement: PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.setObject(position, null)
                is Long -> statement.setLong(position, value)
                is Int -> statement.setInt(position, value)
                is Boolean -> statement.setInt(position, if (value) 1 else 0)
                else -> statement.setObject(position, value)
            }
        }
    }

    private fun schemaExists(config: Config): Boolean = try {
        DriverManager.getConnection(config.jdbcUrl(withDatabase = false), config.dbUser, config.dbPassword)
            .use { connection ->
                connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = ? AND table_name = 'citas'"
                ).use { statement ->
                    statement.setString(1, config.dbName)
                    statement.executeQuery().use { rs ->
                        rs.next() && rs.getInt(1) > 0
                    }
                }
            }
    } catch (_: Exception) {
        false
    }

    private fun runSchema(config: Config) {
        val database = config.databaseIdentifier()
        val script = Db::class.java.getResourceAsStream("/schema.sql")
            ?.bufferedReader()?.readText()
            ?: error("No se encontró /schema.sql en el classpath.")
        val connection = DriverManager.getConnection(
            config.jdbcUrl(withDatabase = false), config.dbUser, config.dbPassword
        )
        connection.use {
            it.createStatement().use { statement ->
                statement.execute(
                    "CREATE DATABASE IF NOT EXISTS `$database` " +
                        "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                )
                statement.execute("USE `$database`")
            }
            for (statement in splitStatements(script)) {
                try {
                    connection.createStatement().use { it.execute(statement) }
                } catch (error: Exception) {
                    // Seeds re-ejecutables (INSERT repetido) no deben tumbar el arranque.
                    log.warn("Aviso al importar el esquema: {} -> {}", error.message, statement.take(80))
                }
            }
        }
        if (!schemaExists(config)) {
            error("El esquema no quedó completo; revisa server/src/main/resources/schema.sql")
        }
    }

    /** Divide el script en sentencias respetando comillas y comentarios (`--`). */
    internal fun splitStatements(script: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inBacktick = false
        var inLineComment = false
        var i = 0
        while (i < script.length) {
            val char = script[i]
            val next = script.getOrNull(i + 1)
            when {
                inLineComment -> {
                    if (char == '\n') {
                        inLineComment = false
                        current.append('\n')
                    }
                    i++
                }
                !inSingle && !inBacktick && char == '-' && next == '-' -> {
                    inLineComment = true
                    i += 2
                }
                !inBacktick && char == '\'' -> {
                    inSingle = !inSingle
                    current.append(char)
                    i++
                }
                !inSingle && char == '`' -> {
                    inBacktick = !inBacktick
                    current.append(char)
                    i++
                }
                char == ';' && !inSingle && !inBacktick -> {
                    val statement = current.toString().trim()
                    if (statement.isNotEmpty()) statements += statement
                    current.setLength(0)
                    i++
                }
                else -> {
                    current.append(char)
                    i++
                }
            }
        }
        val pending = current.toString().trim()
        if (pending.isNotEmpty()) statements += pending
        return statements.filter { it.isNotBlank() }
    }
}
