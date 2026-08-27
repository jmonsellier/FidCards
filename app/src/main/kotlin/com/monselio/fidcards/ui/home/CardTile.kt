package com.monselio.fidcards.ui.home

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
import com.monselio.fidcards.data.Card
import kotlin.math.abs

/**
 * Vignette de la grille d'accueil : logo si disponible, sinon pastille de couleur
 * avec initiales — fréquente, donc soignée (docs/PROJET.md §5.1).
 *
 * [logoModel] : source d'image pour Coil (asset du catalogue ou fichier local), nulle
 * s'il n'y a pas de logo. [logoOnDark] : pastille sombre exigée par le logo (§6.3.1),
 * indépendamment du thème.
 */
@Composable
fun CardTile(
    card: Card,
    logoModel: Any?,
    logoOnDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.586f),
                contentAlignment = Alignment.Center,
            ) {
                if (logoModel != null) {
                    // Cartouche du logo : blanc ou sombre selon le champ `background`
                    // de l'enseigne, jamais selon le thème (un logo noir disparaîtrait
                    // en thème sombre).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (logoOnDark) Color(0xFF20242B) else Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = logoModel,
                            contentDescription = card.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                } else {
                    InitialsSwatch(card)
                }
            }
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun InitialsSwatch(card: Card) {
    val color = card.colorArgb?.let { Color(it) } ?: colorFromName(card.name)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(card.name),
            color = Color.White,
            fontSize = 40.sp,
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
