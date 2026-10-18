package com.tovar.barbermanager

import com.tovar.barbermanager.data.model.ServiceCategory
import com.tovar.barbermanager.ui.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogTest {

    @Test
    fun `parsePrice convierte pesos a centavos`() {
        assertEquals(12050L, Format.parsePrice("120.50"))
        assertEquals(12000L, Format.parsePrice("120"))
        assertEquals(9999L, Format.parsePrice("99,99"))
    }

    @Test
    fun `parsePrice rechaza valores invalidos`() {
        assertNull(Format.parsePrice(""))
        assertNull(Format.parsePrice("   "))
        assertNull(Format.parsePrice("abc"))
        assertNull(Format.parsePrice("-5"))
        assertNull(Format.parsePrice("0"))
        assertNull(Format.parsePrice("0.00"))
    }

    @Test
    fun `price formatea centavos como pesos`() {
        assertEquals("$120.50", Format.price(12050L))
        assertEquals("$0.99", Format.price(99L))
    }

    @Test
    fun `ServiceCategory recupera la categoria desde el nombre guardado`() {
        assertEquals(ServiceCategory.BARBA, ServiceCategory.from("BARBA"))
        assertEquals(ServiceCategory.CORTES, ServiceCategory.from("desconocida"))
        assertEquals(ServiceCategory.CORTES, ServiceCategory.from(null))
    }
}
