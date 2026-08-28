package com.monselio.fidcards.brands

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandCatalogTest {

    private val catalog = BrandCatalog(ApplicationProvider.getApplicationContext())

    @Test
    fun les_cartes_communes_sont_simplifiees_a_la_source() {
        // Une seule entrée par carte commune : Carrefour, Super U, Auchan, Casino.
        assertEquals(listOf("Carrefour"), catalog.search("carrefour").map { it.name })
        assertEquals(1, catalog.brands.count { it.name.startsWith("Casino") })
        assertTrue(catalog.brands.none { it.name == "Carrefour Market" })
    }

    @Test
    fun le_champ_null_json_ne_devient_jamais_la_chaine_null() {
        // optString d'org.json rend « null » pour un null JSON.
        assertTrue(catalog.brands.none { it.sector == "null" || it.logo == "null" })
    }

    @Test
    fun la_recherche_reste_insensible_aux_accents() {
        assertTrue(catalog.search("picard").any { it.name == "Picard" })
        assertTrue(catalog.search("leclerc").any { it.name == "E.Leclerc" })
    }

    @Test
    fun le_lot_des_cartes_de_jerome_est_au_catalogue() {
        // Enseignes ajoutées le 2026-08-28 depuis ses cartes réelles.
        for (name in listOf(
            "Bizzbee", "Bricorama", "C&A", "Centrakor", "Damart", "Galeries Lafayette",
            "La Boucherie", "La Coupole", "La Foir'Fouille", "Les 3 Brasseurs", "Top Office",
        )) {
            val brand = catalog.byName(name)
            assertTrue("absent : $name", brand != null)
            assertTrue("sans logo : $name", catalog.logoAssetUri(brand) != null)
        }
    }

    @Test
    fun le_repli_par_nom_est_insensible_a_la_casse_et_aux_accents() {
        assertEquals("Centrakor", catalog.byName("centrakor")?.name)
        assertEquals("La Foir'Fouille", catalog.byName("la foir fouille")?.name)
        assertTrue(catalog.byName("enseigne inconnue") == null)
    }
}
