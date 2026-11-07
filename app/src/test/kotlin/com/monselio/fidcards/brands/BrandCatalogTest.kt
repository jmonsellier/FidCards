package com.monselio.fidcards.brands

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cohérence du catalogue embarqué (docs/PROJET.md §6.3) : chaque enseigne a sa
 * couleur de fond officielle, et chaque logo déclaré existe bien dans les assets
 * après optimisation (tools/optimize-logos.py).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrandCatalogTest {

    private val catalog = BrandCatalog(ApplicationProvider.getApplicationContext())

    @Test
    fun identifiantsEtNomsUniques() {
        val brands = catalog.brands
        assertTrue(brands.size > 200)
        assertEquals(brands.size, brands.map { it.id }.toSet().size)
        assertEquals(brands.size, brands.map { it.name.lowercase() }.toSet().size)
    }

    @Test
    fun chaqueEnseigneAUneCouleurDeFond() {
        val sansCouleur = catalog.brands.filter { it.colorArgb == null }.map { it.id }
        assertTrue("sans couleur : $sansCouleur", sansCouleur.isEmpty())
    }

    @Test
    fun chaqueLogoDeclareEstEmbarque() {
        val absents = catalog.brands.filter { it.logo != null && catalog.logoAssetUri(it) == null }.map { it.id }
        assertTrue("logos absents des assets : $absents", absents.isEmpty())
    }
}
