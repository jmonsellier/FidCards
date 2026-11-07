package com.monselio.fidcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.ui.detail.CardDetailScreen
import com.monselio.fidcards.ui.home.CardTile
import com.monselio.fidcards.ui.home.HomeScreen
import com.monselio.fidcards.ui.theme.FidCardsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures des vrais écrans, rendues sur la JVM (Robolectric + Roborazzi) : de
 * quoi relire une évolution visuelle sans émulateur. Ignorées par défaut ;
 * `./gradlew testDebugUnitTest --tests '*CapturesEcrans*' -Pcaptures` les
 * écrit dans app/build/captures/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// API 34 : Robolectric exige Java 21 au-delà, et le projet reste sur le JDK 17.
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class CapturesEcrans {

    @get:Rule
    val compose = createComposeRule()

    private val app: FidCardsApplication = ApplicationProvider.getApplicationContext()
    private val dir = System.getProperty("fidcards.captures.dir").orEmpty()

    /** Les 11 enseignes des cartes réelles du foyer, puis les grandes enseignes. */
    private val ids = listOf(
        "bizzbee", "bricorama", "ca", "centrakor", "damart", "galerieslafayette",
        "laboucherie", "lacoupole", "lafoirfouille", "les3brasseurs", "topoffice",
        "carrefour", "eleclerc", "decathlon", "fnac", "sephora", "picard", "ikea",
    )

    @Before
    fun preparer() {
        assumeTrue(System.getProperty("fidcards.captures") == "true")
        val catalog = app.container.brandCatalog
        runBlocking {
            val repository = app.container.cardRepository
            // L'amorçage debug (FidCardsApplication) insère ses cartes en tâche de
            // fond au démarrage : attendre qu'il ait fini avant de vider la base.
            repeat(50) { if (repository.count() == 0) Thread.sleep(100) }
            Thread.sleep(300)
            repository.getAll().forEach { repository.delete(it) }
            ids.forEachIndexed { i, id ->
                val brand = requireNotNull(catalog.byId(id)) { id }
                repository.insert(
                    Card(
                        name = brand.name,
                        barcodeValue = "2" + (100000000000 + i * 7919L).toString(),
                        barcodeFormat = BarcodeFormat.EAN_13,
                        brandId = brand.id,
                        createdAt = 0,
                        // Ordre d'affichage stable : score décroissant.
                        score = (ids.size - i).toDouble(),
                        lastUsedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun attendreImages() {
        // Coil décode hors du thread principal : laisser finir puis stabiliser.
        repeat(8) {
            Thread.sleep(250)
            compose.waitForIdle()
        }
    }

    private fun capturer(nom: String) {
        attendreImages()
        compose.onRoot().captureRoboImage("$dir/$nom.png")
    }

    @Test
    fun accueil_et_carte() {
        val dark = mutableStateOf(false)
        val carte = mutableStateOf<Long?>(null)
        compose.setContent {
            FidCardsTheme(darkTheme = dark.value) {
                val id = carte.value
                if (id == null) {
                    HomeScreen(onCardClick = {}, onAddCard = {}, onOpenSettings = {})
                } else {
                    CardDetailScreen(cardId = id, onBack = {}, onEdit = {})
                }
            }
        }
        val cards = runBlocking { app.container.cardRepository.getAll() }
        val parId = cards.associateBy { it.brandId }

        for (sombre in listOf(false, true)) {
            dark.value = sombre
            val suffixe = if (sombre) "sombre" else "clair"
            carte.value = null
            capturer("liste-$suffixe")
            for (id in listOf("lafoirfouille", "carrefour", "bizzbee")) {
                carte.value = parId.getValue(id).id
                capturer("carte-$id-$suffixe")
            }
        }
    }

    /** Planche du catalogue entier, 36 vignettes par image. */
    @Test
    @Config(qualifiers = "w900dp-h1100dp-xhdpi")
    fun planche_catalogue() {
        val catalog = app.container.brandCatalog
        val pages = catalog.brands.chunked(36)
        val page = mutableStateOf(0)
        compose.setContent {
            FidCardsTheme(darkTheme = false) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                ) {
                    pages[page.value].chunked(6).forEach { ligne ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ligne.forEach { brand ->
                                CardTile(
                                    card = Card(
                                        name = brand.name,
                                        barcodeValue = "0",
                                        barcodeFormat = BarcodeFormat.CODE_128,
                                        brandId = brand.id,
                                        createdAt = 0,
                                    ),
                                    logoModel = catalog.logoAssetUri(brand),
                                    onClick = {},
                                    brandColorArgb = brand.colorArgb,
                                    modifier = Modifier.width(136.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        pages.indices.forEach { i ->
            page.value = i
            capturer("catalogue-${i + 1}")
        }
    }
}
