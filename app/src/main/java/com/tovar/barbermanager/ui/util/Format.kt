package com.tovar.barbermanager.ui.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Formato de dinero en pesos mexicanos y conversión de lo que escribe el usuario. */
object Format {

    private val mxLocale: Locale =
        Locale.Builder().setLanguage("es").setRegion("MX").build()

    /** "$120.50" a partir de 12050 centavos. */
    fun price(cents: Long): String =
        NumberFormat.getCurrencyInstance(mxLocale).format(cents / 100.0)

    /** "120.50" o "120,50" → 12050; devuelve null si no es un precio válido (> 0). */
    fun parsePrice(text: String): Long? {
        val cleaned = text.trim().replace(",", ".")
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?.movePointRight(2)
            ?.toLong()
    }
}
