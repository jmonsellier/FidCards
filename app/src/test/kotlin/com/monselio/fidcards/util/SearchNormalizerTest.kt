package com.monselio.fidcards.util

import com.monselio.fidcards.ui.home.colorFromName
import com.monselio.fidcards.ui.home.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {

    @Test
    fun `leclerc trouve E point Leclerc`() {
        assertTrue("E.Leclerc".searchNormalized().contains("leclerc".searchNormalized()))
    }

    @Test
    fun `insensible aux accents dans les deux sens`() {
        assertEquals("geantcasino", "Géant Casino".searchNormalized())
        assertTrue("Léon de Bruxelles".searchNormalized().contains("leon".searchNormalized()))
    }

    @Test
    fun `la ponctuation et les espaces disparaissent`() {
        assertEquals("pointp", "Point.P".searchNormalized())
    }

    @Test
    fun initiales() {
        assertEquals("EL", initialsOf("E.Leclerc"))
        assertEquals("C", initialsOf("Carrefour"))
        assertEquals("GC", initialsOf("Géant Casino"))
        assertEquals("?", initialsOf("..."))
    }

    @Test
    fun `couleur stable pour un meme nom`() {
        assertEquals(colorFromName("Fnac"), colorFromName("Fnac"))
    }
}
