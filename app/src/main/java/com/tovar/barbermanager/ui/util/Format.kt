package com.tovar.barbermanager.ui.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale


object Format {

    private val mxLocale: Locale =
        Locale.Builder().setLanguage("es").setRegion("MX").build()

    fun price(cents: Long): String =
        NumberFormat.getCurrencyInstance(mxLocale).format(cents / 100.0)

    fun parsePrice(text: String): Long? {
        val cleaned = text.trim().replace(",", ".")
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?.movePointRight(2)
            ?.toLong()
    }
}
