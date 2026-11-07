package com.monselio.fidcards.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.get
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.monselio.fidcards.data.Card
import kotlin.math.abs

/**
 * Vignette de la grille d'accueil : la face de la carte telle que l'enseigne la
 * dessine — fond plein à sa couleur officielle, logo dans sa version prévue pour
 * ce fond (catalogue). Identique en thème clair et sombre : c'est la carte, pas
 * le cadre. À défaut de logo : photo recto plein cadre, sinon pastille à
 * initiales sur la couleur de la carte.
 */
@Composable
fun CardTile(
    card: Card,
    logoModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPhoto: Boolean = false,
    brandColorArgb: Int? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
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
                    logoModel != null -> LogoFace(card, logoModel, brandColorArgb)
                    else -> InitialsSwatch(card, brandColorArgb)
                }
            }
        }
        Text(
            text = card.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
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
private fun LogoFace(card: Card, logoModel: Any, brandColorArgb: Int?) {
    // Fond : la couleur officielle de l'enseigne, telle quelle — le logo du
    // catalogue est la variante dessinée pour ce fond. Un logo personnel (fichier
    // de l'utilisateur, sans couleur au catalogue) impose son propre fond opaque
    // s'il en a un ; sinon il reste sur blanc, le papier pour lequel il est fait.
    val brandColor = brandColorArgb?.let(::Color)
    var tileColor by remember(logoModel, brandColor) { mutableStateOf(brandColor ?: Color.White) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tileColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(logoModel)
                // Bitmap logiciel seulement quand il faut lire les pixels.
                .allowHardware(brandColor != null)
                .build(),
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                if (brandColor == null) {
                    runCatching { edgeBackgroundColor(state.result.image.toBitmap()) }
                        .getOrNull()
                        ?.let { tileColor = it }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun InitialsSwatch(card: Card, brandColorArgb: Int? = null) {
    val color = cardFaceColor(card, brandColorArgb)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(card.name),
            color = contentColorOn(color),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}

/**
 * Couleur de la face d'une carte, partagée par la vignette et l'écran carte :
 * couleur choisie par l'utilisateur, sinon couleur officielle de l'enseigne,
 * sinon couleur stable dérivée du nom.
 */
internal fun cardFaceColor(card: Card, brandColorArgb: Int?): Color =
    card.colorArgb?.let(::Color)
        ?: brandColorArgb?.let(::Color)
        ?: colorFromName(card.name)

/** Texte et icônes posés sur une face de carte : blanc sur fond sombre, quasi-noir sinon. */
internal fun contentColorOn(background: Color): Color =
    if (background.luminance() < 0.4f) Color.White else Color(0xFF1A1A1A)

/** « E.Leclerc » → « EL », « Carrefour » → « C ». */
internal fun initialsOf(name: String): String =
    name.split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

/** Couleur stable dérivée du nom : teinte issue du hachage, franche comme une vraie carte. */
internal fun colorFromName(name: String): Color {
    val hue = (abs(name.lowercase().hashCode()) % 360).toFloat()
    return Color.hsl(hue = hue, saturation = 0.65f, lightness = 0.42f)
}

/**
 * Couleur de bord d'un logo : échantillonne le pourtour du bitmap ; renvoie la
 * couleur moyenne si tous les échantillons sont opaques et quasi uniformes,
 * null sinon (fond transparent ou hétérogène → fond blanc). Par
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
        pixels += bitmap[x, 0]
        pixels += bitmap[x, h - 1]
    }
    for (y in 0 until h step stepY) {
        pixels += bitmap[0, y]
        pixels += bitmap[w - 1, y]
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
