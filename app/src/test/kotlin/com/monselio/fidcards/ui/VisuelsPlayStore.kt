package com.monselio.fidcards.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.monselio.fidcards.FidCardsApplication
import com.monselio.fidcards.R
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.data.DebugSeed
import com.monselio.fidcards.ui.detail.CardDetailScreen
import com.monselio.fidcards.ui.home.CardTile
import com.monselio.fidcards.ui.home.HomeScreen
import com.monselio.fidcards.ui.theme.BricolageGrotesque
import com.monselio.fidcards.ui.theme.FidCardsTheme
import com.monselio.fidcards.ui.theme.InstrumentSans
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visuels de la fiche Play Store (docs/play-store/), rendus depuis l'UI réelle :
 * icône 512 px, bannière 1024 × 500, captures téléphone mises en forme 1080 × 1920. Ignorés par
 * défaut ; `./gradlew testDebugUnitTest --tests '*VisuelsPlayStore*' -Pcaptures`
 * les écrit dans app/build/captures/play/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// API 34 : Robolectric exige Java 21 au-delà, et le projet reste sur le JDK 17.
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class VisuelsPlayStore {

    @get:Rule
    val compose = createComposeRule()

    private val app: FidCardsApplication = ApplicationProvider.getApplicationContext()
    private val dir = File(System.getProperty("fidcards.captures.dir").orEmpty(), "play")

    private val ids = listOf(
        "carrefour", "lafoirfouille", "decathlon", "sephora", "ikea", "picard",
        "fnac", "eleclerc", "leroymerlin", "gifi", "monoprix", "kiabi",
        "intermarche", "boulanger", "starbucks",
    )

    @Before
    fun preparer() {
        assumeTrue(System.getProperty("fidcards.captures") == "true")
        dir.mkdirs()
        val catalog = app.container.brandCatalog
        runBlocking {
            val repository = app.container.cardRepository
            // L'amorçage debug insère ses cartes en tâche de fond : attendre puis vider.
            repeat(50) { if (repository.count() == 0) Thread.sleep(100) }
            Thread.sleep(300)
            repository.getAll().forEach { repository.delete(it) }
            ids.forEachIndexed { i, id ->
                val brand = requireNotNull(catalog.byId(id)) { id }
                repository.insert(
                    Card(
                        name = brand.name,
                        barcodeValue = DebugSeed.ean13("2" + (10000000000 + i * 7919L)),
                        barcodeFormat = BarcodeFormat.EAN_13,
                        brandId = brand.id,
                        createdAt = 0,
                        score = (ids.size - i).toDouble(),
                        lastUsedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun capturer(nom: String) {
        // Coil décode hors du thread principal : laisser finir puis stabiliser.
        repeat(8) {
            Thread.sleep(250)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(File(dir, "$nom.png").path)
    }

    /**
     * Captures de la fiche en panorama : une seule image de 4 × 360 dp (4320 × 1920 px),
     * fond continu et cartes à cheval sur deux captures, découpée en quatre
     * captures 1080 × 1920 qui se suivent dans la fiche.
     */
    @Test
    @Config(qualifiers = "w1440dp-h640dp-xxhdpi")
    fun captures_panorama() {
        val catalog = app.container.brandCatalog
        val parId = runBlocking { app.container.cardRepository.getAll() }.associateBy { it.brandId }
        compose.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF00797E), Color(0xFF00585C), Color(0xFF00797E), Color(0xFF004A4E)),
                        ),
                    ),
            ) {
                // Vague claire continue d'un bout à l'autre du panorama.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val vague = Path().apply {
                        moveTo(0f, size.height * 0.62f)
                        for (i in 0 until 4) {
                            val x = size.width * i / 4
                            cubicTo(
                                x + size.width / 12, size.height * 0.50f,
                                x + size.width / 6, size.height * 0.80f,
                                x + size.width / 4, size.height * 0.62f,
                            )
                        }
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(vague, Color.White.copy(alpha = 0.07f))
                }
                DIAPOS.forEachIndexed { i, d ->
                    Diapositive(d, parId, Modifier.offset(x = (i * 360).dp).width(360.dp))
                }
                // Cartes à cheval sur les jointures : elles lient les captures entre elles.
                listOf("gifi" to -8f, "ikea" to 6f, "sephora" to -5f).forEachIndexed { i, (id, angle) ->
                    val brand = requireNotNull(catalog.byId(id))
                    CardTile(
                        card = Card(name = "", barcodeValue = "0", barcodeFormat = BarcodeFormat.CODE_128, brandId = id, createdAt = 0),
                        logoModel = catalog.logoAssetUri(brand),
                        onClick = {},
                        brandColorArgb = brand.colorArgb,
                        modifier = Modifier
                            .offset(x = ((i + 1) * 360 - 48).dp, y = (250 + i % 2 * 120).dp)
                            .width(96.dp)
                            .rotate(angle),
                    )
                }
            }
        }
        val panorama = File(dir, "panorama.png")
        capturer(panorama.nameWithoutExtension)
        val image = BitmapFactory.decodeFile(panorama.path)
        val largeur = image.width / DIAPOS.size
        DIAPOS.forEachIndexed { i, d ->
            File(dir, "capture-${i + 1}-${d.nom}.png").outputStream().use {
                Bitmap.createBitmap(image, i * largeur, 0, largeur, image.height)
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Composable
    private fun Diapositive(d: Diapo, parId: Map<String?, Card>, modifier: Modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
            Text(
                d.titre,
                fontFamily = BricolageGrotesque,
                fontSize = 32.sp,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(top = 40.dp, start = 14.dp, end = 14.dp),
            )
            Text(
                d.sousTitre,
                fontFamily = InstrumentSans,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFBFEFF1),
                modifier = Modifier.padding(top = 8.dp, bottom = 22.dp, start = 14.dp, end = 14.dp),
            )
            Box(
                modifier = Modifier
                    .shadow(24.dp, RoundedCornerShape(22.dp))
                    .background(Color(0xFF0E1213), RoundedCornerShape(22.dp))
                    .padding(7.dp),
            ) {
                val echelle = ECRAN_REDUIT / 360f
                Box(
                    modifier = Modifier
                        .size(ECRAN_REDUIT.dp, (640 * echelle).dp)
                        .clip(RoundedCornerShape(15.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(360.dp, 640.dp)
                            .graphicsLayer {
                                scaleX = echelle
                                scaleY = echelle
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    ) {
                        FidCardsTheme(darkTheme = d.sombre) {
                            val id = d.carte?.let { parId.getValue(it).id }
                            if (id == null) {
                                HomeScreen(onCardClick = {}, onAddCard = {}, onOpenSettings = {})
                            } else {
                                CardDetailScreen(cardId = id, onBack = {}, onEdit = {})
                            }
                        }
                    }
                }
            }
        }
    }

    private data class Diapo(
        val nom: String,
        val titre: String,
        val sousTitre: String,
        val sombre: Boolean,
        val carte: String? = null,
    )

    private companion object {
        /** Largeur de l'écran réduit dans le cadre, en dp (675 px en xxhdpi). */
        const val ECRAN_REDUIT = 225f

        // Arguments vérifiables dans le code : aucune permission INTERNET, aucun SDK
        // de pub ni de mesure, aucun compte ; tri par récence d'usage.
        val DIAPOS = listOf(
            Diapo("rapide", "Votre carte\nen un toucher", "Les plus utilisées remontent en tête", sombre = false),
            Diapo("hors-ligne", "Marche\nsans réseau", "Même au fond du magasin, sans 4G ni Wi-Fi", sombre = false, carte = "carrefour"),
            Diapo("sans-pub", "Zéro pub,\nzéro compte", "Rien à créer, rien à subir", sombre = true),
            Diapo("privee", "Vos cartes\nrestent chez vous", "Aucun accès à Internet, aucun traceur", sombre = true, carte = "lafoirfouille"),
        )
    }

    /** Bannière de la fiche : 512 × 250 dp en xhdpi, soit 1024 × 500 px. */
    @Test
    @Config(qualifiers = "w512dp-h250dp-xhdpi")
    fun banniere() {
        val catalog = app.container.brandCatalog
        val tuiles = listOf("carrefour", "lafoirfouille", "sephora", "ikea").map { requireNotNull(catalog.byId(it)) }
        compose.setContent {
            FidCardsTheme(darkTheme = false) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF00696D))
                        .padding(horizontal = 28.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "FidCards",
                            fontFamily = BricolageGrotesque,
                            fontSize = 44.sp,
                            color = Color.White,
                        )
                        Text(
                            "Vos cartes de fidélité.\nSans pub, hors ligne.",
                            fontFamily = InstrumentSans,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFFD7F4F5),
                        )
                    }
                    Box(modifier = Modifier.width(230.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tuiles.chunked(2).forEachIndexed { ligne, paire ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.rotate(if (ligne == 0) -4f else 3f),
                                ) {
                                    paire.forEach { brand ->
                                        CardTile(
                                            card = Card(
                                                name = "",
                                                barcodeValue = "0",
                                                barcodeFormat = BarcodeFormat.CODE_128,
                                                brandId = brand.id,
                                                createdAt = 0,
                                            ),
                                            logoModel = catalog.logoAssetUri(brand),
                                            onClick = {},
                                            brandColorArgb = brand.colorArgb,
                                            modifier = Modifier.width(110.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        capturer("banniere-1024x500")
    }

    /**
     * Icône haute résolution : fond et premier plan de l'icône adaptative, cadrés
     * sur sa zone visible (72 dp sur 108, comme un lanceur), 512 px plein cadre ;
     * Play applique lui-même l'arrondi.
     */
    @Test
    fun icone() {
        val bitmap = createBitmap(512, 512)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ContextCompat.getColor(app, R.color.ic_launcher_background))
        requireNotNull(ContextCompat.getDrawable(app, R.drawable.ic_launcher_foreground)).run {
            val marge = 512 * (108 - 72) / 72 / 2
            setBounds(-marge, -marge, 512 + marge, 512 + marge)
            draw(canvas)
        }
        File(dir, "icone-512.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
