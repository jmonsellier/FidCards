package com.monselio.fidcards.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import com.monselio.fidcards.ui.edit.EditCardScreen
import com.monselio.fidcards.ui.settings.SettingsScreen
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.background
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
import com.monselio.fidcards.ui.theme.md_theme_light_background
import com.monselio.fidcards.ui.theme.md_theme_light_onPrimaryContainer
import com.monselio.fidcards.ui.theme.md_theme_light_primary
import com.monselio.fidcards.ui.theme.md_theme_light_primaryContainer
import com.monselio.fidcards.ui.theme.md_theme_light_secondaryContainer
import com.monselio.fidcards.ui.theme.md_theme_light_tertiaryContainer
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
                        // Decathlon en QR : un code carré reste lisible en plein écran sans pivoter.
                        barcodeValue = if (id == "decathlon") "2090001234567" else DebugSeed.ean13("2" + (10000000000 + i * 7919L)),
                        barcodeFormat = if (id == "decathlon") BarcodeFormat.QR_CODE else BarcodeFormat.EAN_13,
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
     * Captures de la fiche en panorama : une seule image de 5 × 360 dp (5400 × 1920 px),
     * fond continu (seul le code-barres en filigrane passe d'une capture à l'autre), découpée en
     * cinq captures 1080 × 1920, une par écran de l'app : chacune contient entière son titre,
     * son téléphone et ses encarts.
     */
    @Test
    @Config(qualifiers = "w1800dp-h640dp-xxhdpi")
    fun captures_panorama() {
        val parId = runBlocking { app.container.cardRepository.getAll() }.associateBy { it.brandId }
        compose.setContent {
            Box(modifier = Modifier.fillMaxSize().background(FOND)) {
                FondCodeBarres()
                DIAPOS.forEachIndexed { i, d ->
                    Diapositive(d, parId, Modifier.offset(x = (i * 360).dp).size(360.dp, 640.dp))
                }
            }
        }
        // Plein écran : toucher le cartouche du code, comme l'utilisateur (le réglage
        // d'agrandissement automatique vaudrait pour toutes les captures à la fois).
        compose.waitForIdle()
        DIAPOS.forEachIndexed { i, d ->
            if (d.ecran != Ecran.PLEIN_ECRAN) return@forEachIndexed
            val noeuds = compose.onAllNodes(hasClickAction())
            val liste = noeuds.fetchSemanticsNodes()
            val largeurDiapo = 360 * app.resources.displayMetrics.density
            val cartouche = liste.indices
                .filter { liste[it].boundsInRoot.left >= i * largeurDiapo && liste[it].boundsInRoot.right <= (i + 1) * largeurDiapo }
                .maxBy { liste[it].boundsInRoot.width }
            noeuds[cartouche].performClick()
        }
        // Formulaire : l'enseigne saisie, comme après le choix dans l'autocomplétion.
        DIAPOS.indexOfFirst { it.ecran == Ecran.AJOUT }.let { i ->
            val champs = compose.onAllNodes(hasSetTextAction())
            val liste = champs.fetchSemanticsNodes()
            val largeurDiapo = 360 * app.resources.displayMetrics.density
            val nom = liste.indices
                .filter { liste[it].boundsInRoot.left >= i * largeurDiapo && liste[it].boundsInRoot.right <= (i + 1) * largeurDiapo }
                .minBy { liste[it].boundsInRoot.top }
            champs[nom].performTextInput("Sephora")
        }
        // Réglages : la section Sauvegarde entière à l'écran.
        // (performScrollTo ne défile pas : l'écran réduit par graphicsLayer paraît déjà visible.)
        compose.onAllNodes(hasScrollAction()).let { defilables ->
            val i = DIAPOS.indexOfFirst { it.ecran == Ecran.REGLAGES }
            val largeurDiapo = 360 * app.resources.displayMetrics.density
            val liste = defilables.fetchSemanticsNodes()
            val reglages = liste.indices.single { liste[it].boundsInRoot.left >= i * largeurDiapo }
            // Tout en bas, puis remonter d'« À propos » : la version du build debug reste hors champ.
            defilables[reglages].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 2000f) }
            defilables[reglages].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -480f) }
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

    /** Un grand code-barres en filigrane, légèrement incliné, qui court d'une capture à l'autre. */
    @Composable
    private fun FondCodeBarres() {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hauteur = 250.dp.toPx()
            val module = 3.dp.toPx()
            val alea = java.util.Random(13)
            rotate(-5f, pivot = Offset(size.width / 2, size.height * 0.62f)) {
                var x = -size.width * 0.05f
                var barre = true
                while (x < size.width * 1.05f) {
                    val largeur = module * (1 + alea.nextInt(if (barre) 3 else 4))
                    if (barre) {
                        drawRect(
                            PASTILLE.copy(alpha = 0.7f),
                            topLeft = Offset(x, size.height * 0.62f - hauteur / 2),
                            size = Size(largeur, hauteur),
                        )
                    }
                    x += largeur
                    barre = !barre
                }
            }
        }
    }

    /** Une capture : titre au mot surligné, téléphone Android entier, encarts qui le chevauchent. */
    @Composable
    private fun Diapositive(d: Diapo, parId: Map<String?, Card>, modifier: Modifier) {
        Box(modifier = modifier) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 18.dp, top = 30.dp)) {
                Text(d.titre, style = TITRE)
                Text(
                    d.surligne,
                    style = TITRE,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .offset(x = (-6).dp)
                        .background(d.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Text(
                    buildAnnotatedString {
                        append(d.sousTitre)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SARCELLE)) { append(d.sousTitreFort) }
                    },
                    fontFamily = InstrumentSans,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = ENCRE.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            val xTelephone = if (d.telephoneAGauche) MARGE else 360 - MARGE - LARGEUR_TELEPHONE
            Telephone(d, parId, Modifier.offset(x = xTelephone.dp, y = HAUT_TELEPHONE.dp))
            val xEncarts = if (d.telephoneAGauche) 360 - MARGE - LARGEUR_ENCART else MARGE
            // Sur l'écran carte, les encarts laissent le code-barres dégagé.
            d.encarts.zip(d.ys).forEach { (e, y) -> Encart(e, d.accent, Modifier.offset(x = xEncarts.dp, y = y.dp)) }
        }
    }

    /**
     * Téléphone Android : corps à bords fins, poinçon de caméra, boutons latéraux, barre
     * d'état et barre de gestes autour de l'écran réel de l'app (360 × 760 dp, format 19:9).
     */
    @Composable
    private fun Telephone(d: Diapo, parId: Map<String?, Card>, modifier: Modifier) {
        val catalog = app.container.brandCatalog
        val echelle = ECRAN_REDUIT / 360f
        Box(modifier = modifier) {
            // Boutons marche/arrêt et volume, sur la tranche droite.
            listOf(84 to 30, 132 to 56).forEach { (y, h) ->
                Box(
                    Modifier
                        .offset(x = (LARGEUR_TELEPHONE - 1).dp, y = y.dp)
                        .size(3.dp, h.dp)
                        .background(Color(0xFF2A2E32), RoundedCornerShape(2.dp)),
                )
            }
            Box(
                modifier = Modifier
                    .shadow(22.dp, RoundedCornerShape(30.dp), ambientColor = ENCRE, spotColor = ENCRE)
                    .background(Color(0xFF1B1E21), RoundedCornerShape(30.dp))
                    .border(1.dp, Color(0xFF3C4146), RoundedCornerShape(30.dp))
                    .padding(BORDURE.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(ECRAN_REDUIT.dp, (HAUTEUR_ECRAN * echelle).dp)
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(360.dp, HAUTEUR_ECRAN.dp)
                            .graphicsLayer {
                                scaleX = echelle
                                scaleY = echelle
                                transformOrigin = TransformOrigin(0f, 0f)
                            },
                    ) {
                        FidCardsTheme(darkTheme = false) {
                            val fond = when (d.ecran) {
                                Ecran.CARTE -> Color(requireNotNull(catalog.byId(requireNotNull(d.carte))?.colorArgb))
                                Ecran.PLEIN_ECRAN -> Color.White
                                else -> MaterialTheme.colorScheme.background
                            }
                            val encre = if (fond.luminance() > 0.5f) Color(0xFF1F1E1B) else Color.White
                            // Les barres recouvrent l'écran d'1 dp : pas de liseré à la jonction.
                            Box(modifier = Modifier.fillMaxSize().background(fond)) {
                                Box(modifier = Modifier.padding(top = 29.dp, bottom = 21.dp)) {
                                    when (d.ecran) {
                                        Ecran.LISTE -> HomeScreen(onCardClick = {}, onAddCard = {}, onOpenSettings = {})
                                        Ecran.CARTE, Ecran.PLEIN_ECRAN ->
                                            CardDetailScreen(cardId = parId.getValue(d.carte).id, onBack = {}, onEdit = {})
                                        // Formulaire tel qu'il s'ouvre après un scan.
                                        Ecran.AJOUT -> EditCardScreen(
                                            cardId = null,
                                            scannedValue = DebugSeed.ean13("300000012345"),
                                            scannedFormat = BarcodeFormat.EAN_13.name,
                                            onDone = {},
                                            onDeleted = {},
                                        )
                                        Ecran.REGLAGES -> SettingsScreen(onBack = {})
                                    }
                                }
                                BarreEtat(encre, fond, avion = d.modeAvion)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(22.dp).background(fond),
                                ) {
                                    Box(Modifier.size(110.dp, 5.dp).background(encre.copy(alpha = 0.55f), CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Barre d'état Android : heure, poinçon de caméra au centre, réseau (ou mode avion) et batterie. */
    @Composable
    private fun BarreEtat(encre: Color, fond: Color, avion: Boolean) {
        Box(modifier = Modifier.fillMaxWidth().height(30.dp).background(fond).padding(horizontal = 22.dp)) {
            Text(
                "10:24",
                fontFamily = InstrumentSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = encre,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Box(Modifier.align(Alignment.Center).size(15.dp).background(Color(0xFF0A0B0C), CircleShape))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                if (avion) {
                    Icon(AVION, contentDescription = null, tint = encre, modifier = Modifier.size(16.dp))
                } else {
                    Canvas(Modifier.size(14.dp)) {
                        drawPath(
                            Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() },
                            encre,
                        )
                    }
                }
                Canvas(Modifier.size(9.dp, 15.dp)) {
                    drawRoundRect(encre, topLeft = Offset(size.width * 0.3f, 0f), size = Size(size.width * 0.4f, 2.dp.toPx()))
                    drawRoundRect(
                        encre,
                        topLeft = Offset(0f, 1.5.dp.toPx()),
                        size = Size(size.width, size.height - 1.5.dp.toPx()),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
        }
    }

    /** Encart flottant : pastille d'icône, libellé, et un badge qui donne la réponse. */
    @Composable
    private fun Encart(e: Encart, accent: Color, modifier: Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .width(LARGEUR_ENCART.dp)
                .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = ENCRE, spotColor = ENCRE)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).background(accent, CircleShape),
            ) {
                Icon(e.icone, contentDescription = null, tint = ENCRE, modifier = Modifier.size(18.dp))
                if (e.barre) {
                    // Barre « désactivé » à la Material : trait d'encre détouré de la couleur du fond.
                    Canvas(Modifier.size(20.dp)) {
                        val a = Offset(size.width * 0.12f, size.height * 0.12f)
                        val b = Offset(size.width * 0.88f, size.height * 0.88f)
                        drawLine(accent, a, b, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(ENCRE, a, b, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 9.dp)) {
                Text(
                    e.libelle,
                    fontFamily = InstrumentSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = ENCRE,
                )
                Text(
                    e.badge,
                    fontFamily = InstrumentSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = SARCELLE,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .background(PASTILLE, RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }

    private data class Encart(
        val icone: ImageVector,
        val libelle: String,
        val badge: String,
        val barre: Boolean = false,
    )

    private data class Diapo(
        val nom: String,
        val titre: String,
        val surligne: String,
        val sousTitre: String,
        val sousTitreFort: String,
        val accent: Color,
        val telephoneAGauche: Boolean,
        val ecran: Ecran,
        val encarts: List<Encart>,
        val carte: String? = null,
        val modeAvion: Boolean = false,
        /** Ordonnée de chaque encart, en dp : ils laissent dégagé l'essentiel de l'écran. */
        val ys: List<Int> = listOf(232, 312, 392),
    )

    private enum class Ecran { PLEIN_ECRAN, CARTE, LISTE, AJOUT, REGLAGES }

    private companion object {
        /** Écran de l'app dans le téléphone : 360 × 760 dp réels, réduits à 210 dp de large. */
        const val HAUTEUR_ECRAN = 760
        const val ECRAN_REDUIT = 210f
        const val BORDURE = 6
        const val LARGEUR_TELEPHONE = 210 + 2 * BORDURE
        const val HAUT_TELEPHONE = 166
        const val MARGE = 16
        const val LARGEUR_ENCART = 168

        // Pictogrammes Material (Apache-2.0) absents de material-icons-core.
        val AVION = vecteur(
            "M21,16v-2l-8,-5V3.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S10,2.67 10,3.5V9l-8,5v2l8,-2.5V19l-2,1.5V22l3.5,-1 3.5,1v-1.5L13,19v-5.5l8,2.5z",
        )
        val RESEAU = vecteur("M2,22h20V2z")
        val WIFI = vecteur(
            "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
        )
        val PUB = vecteur(
            "M18,11v2h4v-2h-4zM16,17.61c0.96,0.71 2.21,1.65 3.2,2.39 0.4,-0.53 0.8,-1.07 1.2,-1.6 -0.99,-0.74 -2.24,-1.68 -3.2,-2.4 -0.4,0.54 -0.8,1.08 -1.2,1.61zM20.4,5.6c-0.4,-0.53 -0.8,-1.07 -1.2,-1.6 -0.99,0.74 -2.24,1.68 -3.2,2.4 0.4,0.53 0.8,1.07 1.2,1.6 0.96,-0.72 2.21,-1.65 3.2,-2.4zM4,9c-1.1,0 -2,0.9 -2,2v2c0,1.1 0.9,2 2,2h1v4h2v-4h1l5,3V6L8,9H4zM15.5,12c0,-1.33 -0.58,-2.53 -1.5,-3.35v6.69c0.92,-0.81 1.5,-2.01 1.5,-3.34z",
        )
        val SOLEIL = vecteur(
            "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1L2,11c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1L13,2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1 -0.55,0 -1,0.45 -1,1z",
        )

        val PHOTO = vecteur(
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
        )
        val CODE = vecteur("M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z")
        val OEIL = vecteur(
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )

        fun vecteur(chemin: String) = ImageVector.Builder("picto", 24.dp, 24.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(chemin).toNodes(), fill = SolidColor(Color.Black))
            .build()

        // Couleurs du thème de l'app (ui/theme/Color.kt).
        val FOND = md_theme_light_background
        val PASTILLE = md_theme_light_secondaryContainer
        val ENCRE = md_theme_light_onPrimaryContainer
        val SARCELLE = md_theme_light_primary

        val TITRE = TextStyle(fontFamily = BricolageGrotesque, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 35.sp, color = ENCRE)

        // Arguments vérifiables dans le code : aucune permission INTERNET, aucun SDK de pub
        // ni de mesure, aucun compte ni achat ; tri par récence d'usage ; luminosité forcée
        // au maximum sur l'écran carte (CardDetailScreen) ; 12 formats ; catalogue de plus de
        // 240 enseignes ; photos recto verso facultatives.
        val DIAPOS = listOf(
            Diapo(
                "plein-ecran", "Ouvrez, scannez,", "c'est réglé",
                "La luminosité monte ", "toute seule.",
                accent = md_theme_light_primaryContainer, telephoneAGauche = true, ecran = Ecran.PLEIN_ECRAN, carte = "decathlon",
                ys = listOf(196, 504, 570),
                encarts = listOf(
                    Encart(SOLEIL, "Luminosité", "Au maximum"),
                    Encart(Icons.Filled.Search, "Code en grand", "D'un toucher"),
                    Encart(Icons.Filled.Star, "12 formats", "EAN, QR, Aztec…"),
                ),
            ),
            Diapo(
                "hors-ligne", "Pas de réseau ?", "Aucun souci",
                "Aucune connexion : ", "100 % hors ligne.",
                accent = md_theme_light_secondaryContainer, telephoneAGauche = false, ecran = Ecran.CARTE, carte = "carrefour",
                modeAvion = true, ys = listOf(188, 302, 522),
                encarts = listOf(
                    Encart(AVION, "Mode avion", "Fonctionne"),
                    Encart(RESEAU, "Sans 4G ni 5G", "Fonctionne"),
                    Encart(WIFI, "Sans Wi-Fi", "Fonctionne"),
                ),
            ),
            Diapo(
                "sans-pub", "Gratuite.", "Vraiment.",
                "Ni pub, ni compte, ", "ni abonnement.",
                accent = md_theme_light_primaryContainer, telephoneAGauche = true, ecran = Ecran.LISTE,
                encarts = listOf(
                    Encart(PUB, "Publicités", "Aucune", barre = true),
                    Encart(Icons.Filled.Person, "Inscription", "Inutile", barre = true),
                    Encart(CODE, "Code source", "Libre, GPL-3.0"),
                ),
            ),
            Diapo(
                "ajout", "Nouvelle carte ?", "Scannez-la",
                "Plus de 240 enseignes ", "reconnues.",
                accent = md_theme_light_tertiaryContainer, telephoneAGauche = false, ecran = Ecran.AJOUT,
                ys = listOf(438, 504, 570),
                encarts = listOf(
                    Encart(PHOTO, "Scan du code", "Appareil photo"),
                    Encart(Icons.Filled.ShoppingCart, "Enseignes", "Couleur et logo"),
                    Encart(PHOTO, "Photos", "Recto et verso"),
                ),
            ),
            Diapo(
                "privee", "Vos cartes restent", "chez vous",
                "Aucun serveur, ", "aucun intermédiaire.",
                accent = md_theme_light_secondaryContainer, telephoneAGauche = true, ecran = Ecran.REGLAGES,
                encarts = listOf(
                    Encart(Icons.Filled.Lock, "Accès Internet", "Non demandé"),
                    Encart(OEIL, "Traceurs", "Aucun", barre = true),
                    // Sauvegarde automatique facultative vers un dossier choisi (backup/AutoBackup.kt).
                    Encart(Icons.Filled.Refresh, "Sauvegarde auto", "Drive, Dropbox…"),
                ),
            ),
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
