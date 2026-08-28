package com.monselio.fidcards.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.monselio.fidcards.data.Card
import kotlin.math.abs

/**
 * Vignette de la grille d'accueil : uniquement la face de la carte, sans bandeau
 * de texte. Logo si disponible (le fond de la tuile adopte la couleur de bord du
 * logo quand elle est uniforme — il « déborde » alors sur toute la carte), sinon
 * photo recto plein cadre, sinon pastille de couleur portant initiales et nom.
 */
@Composable
fun CardTile(
    card: Card,
    logoModel: Any?,
    logoOnDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPhoto: Boolean = false,
    brandColorArgb: Int? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
        ) {
            Box(
                modifier = Modifier.aspectRatio(1.586f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    logoModel != null && isPhoto -> {
                        // Photo recto : la carte elle-même, plein cadre.
                        AsyncImage(
                            model = logoModel,
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    logoModel != null -> LogoFace(card, logoModel, logoOnDark, brandColorArgb)
                    else -> InitialsSwatch(card, brandColorArgb)
                }
            }
        }
        Text(
            text = card.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

@Composable
private fun LogoFace(card: Card, logoModel: Any, logoOnDark: Boolean, brandColorArgb: Int?) {
    // Fond de la tuile :
    // 1. pastel de la couleur primaire officielle de l'enseigne (catalogue),
    //    appliqué immédiatement — assombri pour les logos exigeant un fond
    //    sombre (§6.3.1) ;
    // 2. au chargement du logo : un fichier à fond opaque uniforme impose son
    //    fond (le logo « déborde », un pastel derrière montrerait un rectangle) ;
    // 3. sans couleur au catalogue : pastel dérivé de la teinte dominante des
    //    pixels, ou cartouche neutre si le logo est monochrome.
    val brandPastel = remember(brandColorArgb, logoOnDark) {
        brandColorArgb?.let { pastelOf(it, logoOnDark) }
    }
    var tileColor by remember(logoModel) { mutableStateOf(brandPastel) }
    val fallback = if (logoOnDark) Color(0xFF20242B) else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tileColor ?: fallback),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(logoModel)
                // Bitmap logiciel : indispensable pour lire les pixels.
                .allowHardware(false)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                runCatching {
                    val bitmap = state.result.image.toBitmap()
                    val edge = edgeBackgroundColor(bitmap)
                    tileColor = when {
                        edge != null -> edge
                        brandPastel != null -> brandPastel
                        else -> dominantPastel(bitmap, logoOnDark)
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun InitialsSwatch(card: Card, brandColorArgb: Int? = null) {
    // Priorité : couleur choisie par l'utilisateur, couleur officielle de
    // l'enseigne, couleur dérivée du nom. Le nom, lui, est sous la tuile.
    val color = card.colorArgb?.let { Color(it) }
        ?: brandColorArgb?.let { Color(it) }
        ?: colorFromName(card.name)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(card.name),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

/** « E.Leclerc » → « EL », « Carrefour » → « C ». */
internal fun initialsOf(name: String): String =
    name.split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

/** Couleur stable dérivée du nom : teinte issue du hachage, tons maîtrisés. */
internal fun colorFromName(name: String): Color {
    val hue = (abs(name.lowercase().hashCode()) % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.45f, lightness = 0.45f)
}

/**
 * Couleur de bord d'un logo : échantillonne le pourtour du bitmap ; renvoie la
 * couleur moyenne si tous les échantillons sont opaques et quasi uniformes,
 * null sinon (fond transparent ou hétérogène → cartouche par défaut). Par
 * construction, cette couleur contraste avec l'intérieur du logo : c'est son
 * propre fond.
 */
internal fun edgeBackgroundColor(bitmap: Bitmap): Color? {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 8 || h < 8) return null

    val pixels = ArrayList<Int>(96)
    val stepX = maxOf(1, w / 24)
    val stepY = maxOf(1, h / 24)
    for (x in 0 until w step stepX) {
        pixels += bitmap.getPixel(x, 0)
        pixels += bitmap.getPixel(x, h - 1)
    }
    for (y in 0 until h step stepY) {
        pixels += bitmap.getPixel(0, y)
        pixels += bitmap.getPixel(w - 1, y)
    }

    if (pixels.any { (it ushr 24) < 250 }) return null

    val r = pixels.map { (it shr 16) and 0xFF }
    val g = pixels.map { (it shr 8) and 0xFF }
    val b = pixels.map { it and 0xFF }
    val rAvg = r.average().toInt()
    val gAvg = g.average().toInt()
    val bAvg = b.average().toInt()
    val uniform = pixels.indices.all {
        abs(r[it] - rAvg) <= 14 && abs(g[it] - gAvg) <= 14 && abs(b[it] - bAvg) <= 14
    }
    if (!uniform) return null
    return Color(rAvg, gAvg, bAvg)
}

/**
 * Teinte dominante du logo → fond pastel de la tuile. Histogramme de teintes
 * (36 seaux de 10°) sur les pixels opaques et saturés ; les gris, blancs et
 * noirs sont ignorés. Null si le logo est monochrome : le cartouche neutre
 * reste alors le meilleur fond.
 */
internal fun dominantPastel(bitmap: Bitmap, dark: Boolean): Color? {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 8 || h < 8) return null

    val stepX = maxOf(1, w / 48)
    val stepY = maxOf(1, h / 48)
    val weight = FloatArray(36)
    val hueSum = FloatArray(36)
    val satSum = FloatArray(36)

    for (y in 0 until h step stepY) {
        for (x in 0 until w step stepX) {
            val p = bitmap.getPixel(x, y)
            if ((p ushr 24) < 200) continue
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            if (delta < 1e-5f) continue
            val lightness = (max + min) / 2f
            val sat = delta / (1f - abs(2f * lightness - 1f)).coerceAtLeast(1e-5f)
            if (sat < 0.2f || lightness < 0.08f || lightness > 0.95f) continue
            var hue = when (max) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            if (hue < 0f) hue += 360f
            val bucket = (hue / 10f).toInt().coerceIn(0, 35)
            weight[bucket] += sat
            hueSum[bucket] += hue * sat
            satSum[bucket] += sat * sat
        }
    }

    val best = weight.indices.maxBy { weight[it] }
    if (weight[best] <= 0f) return null
    val hue = hueSum[best] / weight[best]
    val sat = satSum[best] / weight[best]
    return pastelHsl(hue, sat, dark)
}

/** Pastel d'une couleur primaire d'enseigne : même teinte, très éclaircie. */
internal fun pastelOf(argb: Int, dark: Boolean): Color {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta < 1e-5f) return if (dark) Color(0xFF20242B) else Color(0xFFEEEEEE)
    val lightness = (max + min) / 2f
    val sat = delta / (1f - abs(2f * lightness - 1f)).coerceAtLeast(1e-5f)
    var hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    if (hue < 0f) hue += 360f
    return pastelHsl(hue, sat, dark)
}

private fun pastelHsl(hue: Float, sat: Float, dark: Boolean): Color = if (dark) {
    Color.hsl(hue, (sat * 0.5f).coerceIn(0.2f, 0.5f), 0.22f)
} else {
    Color.hsl(hue, (sat * 0.55f).coerceIn(0.15f, 0.45f), 0.90f)
}
