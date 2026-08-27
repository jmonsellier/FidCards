package com.monselio.fidcards.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpcETest {

    /** Les quatre cas d'expansion, re-encodés puis vérifiés par le writer UPC-E. */
    @Test
    fun `chaque forme recomprimee se reencode en UPC-E`() {
        listOf(
            "012000005671", // cas 0-2 : d1 d2 L 0000 d3 d4 d5
            "012300000451", // cas 3 : d1 d2 d3 00000 d4 d5
            "012340000053", // cas 4 : d1 d2 d3 d4 00000 d5
            "012345000072", // cas 5-9 : d1 d2 d3 d4 d5 0000 L
        ).forEach { expanded ->
            val compressed = UpcE.compress(expanded)
            checkNotNull(compressed) { "recompression impossible pour $expanded" }
            assertEquals(8, compressed.length)
            // Le writer valide clé de contrôle et structure : il lève si la forme est fausse.
            BarcodeEncoder.encode(compressed, com.monselio.fidcards.data.BarcodeFormat.UPC_E)
        }
    }

    @Test
    fun `cas canonique zxing`() {
        assertEquals("01234565", UpcE.compress("012345000065"))
    }

    @Test
    fun `entrees invalides refusees`() {
        assertNull(UpcE.compress("123"))
        assertNull(UpcE.compress("912345000065")) // système de numérotation ≠ 0/1
        assertNull(UpcE.compress("01234500006A"))
        assertNull(UpcE.compress("012345678915")) // non compressible
    }
}
