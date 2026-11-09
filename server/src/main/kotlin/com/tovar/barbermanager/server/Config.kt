package com.tovar.barbermanager.server

/** Configuración del servidor; todos los valores vienen de variables de entorno. */
data class Config(
    val dbHost: String,
    val dbPort: Int,
    val dbName: String,
    val dbUser: String,
    val dbPassword: String,
    val tokenSecret: String,
    val port: Int,
    val adminEmail: String,
    val adminPassword: String
) {
    /** Nombre de base válido para interpolar en SQL (sin comillas ni espacios). */
    fun databaseIdentifier(): String {
        require(dbName.matches(Regex("[A-Za-z0-9_]{1,64}"))) {
            "DB_NAME no es válido: debe contener solo letras, números y guion bajo (máx. 64)."
        }
        return dbName
    }

    fun jdbcUrl(withDatabase: Boolean): String {
        val db = if (withDatabase) databaseIdentifier() else ""
        return "jdbc:mysql://$dbHost:$dbPort/$db" +
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Mexico_City" +
            "&characterEncoding=utf8&connectionTimeZone=America/Mexico_City"
    }

    companion object {
        fun fromEnv(): Config = Config(
            dbHost = System.getenv("DB_HOST") ?: "localhost",
            dbPort = (System.getenv("DB_PORT") ?: "3306").toInt(),
            dbName = System.getenv("DB_NAME") ?: "barbermanager",
            dbUser = System.getenv("DB_USER") ?: "root",
            dbPassword = System.getenv("DB_PASSWORD") ?: "2226",
            tokenSecret = System.getenv("TOKEN_SECRET") ?: "barbermanager-dev-secret",
            port = (System.getenv("PORT") ?: "8080").toInt(),
            adminEmail = System.getenv("ADMIN_EMAIL") ?: "admin@barbermanager.mx",
            adminPassword = System.getenv("ADMIN_PASSWORD") ?: "Admin123!"
        )
    }
}
