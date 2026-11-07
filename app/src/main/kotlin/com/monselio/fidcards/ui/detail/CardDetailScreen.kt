package com.monselio.fidcards.ui.detail

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.monselio.fidcards.R
import com.monselio.fidcards.barcode.generateBarcodeBitmap
import com.monselio.fidcards.barcode.rotatedQuarter
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.ui.home.cardFaceColor
import com.monselio.fidcards.ui.home.contentColorOn
import java.io.File

/**
 * Le cœur de l'app (docs/PROJET.md §5.2), revu façon carte de wallet (demande
 * du 2026-08-28) : la carte s'affiche en portrait aux couleurs de l'enseigne,
 * code sur cartouche blanc — jamais inversé (§14). Toucher le code l'agrandit
 * au maximum, les formats linéaires pivotant de 90° pour occuper toute la
 * hauteur de l'écran : la taille du paysage, sans bascule d'orientation.
 * Luminosité forcée à 100 % puis restaurée, écran maintenu allumé.
 */
@Composable
fun CardDetailScreen(
    cardId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: CardDetailViewModel = viewModel(
        key = "card-$cardId",
        factory = CardDetailViewModel.factory(cardId),
    ),
) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    val brand by viewModel.brand.collectAsStateWithLifecycle()
    val brandLogoUri by viewModel.brandLogoUri.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            window.attributes = window.attributes.also {
                it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.also {
                    it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    var zoomed by rememberSaveable { mutableStateOf(false) }
    var showBack by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = zoomed) { zoomed = false }

    // Réglage « agrandissement automatique », appliqué une seule fois à
    // l'ouverture — refermer l'agrandi ne doit pas le rouvrir.
    val autoZoom by viewModel.autoZoom.collectAsStateWithLifecycle()
    var autoZoomApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoZoom) {
        val enabled = autoZoom ?: return@LaunchedEffect
        if (!autoZoomApplied) {
            autoZoomApplied = true
            if (enabled) zoomed = true
        }
    }

    Scaffold { innerPadding ->
        val current = card ?: return@Scaffold
        if (zoomed) {
            ZoomedBarcode(
                card = current,
                onDismiss = { zoomed = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@Scaffold
        }

        val cardColor = cardFaceColor(current, brand?.colorArgb)
        val onCardColor = contentColorOn(cardColor)

        // Écran entier aux couleurs de l'enseigne (refonte 2026), barres
        // système comprises ; le contenu respecte les insets.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    ScrimIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.detail_back),
                        tint = onCardColor,
                    )
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = onCardColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    ScrimIconButton(
                        onClick = onEdit,
                        icon = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.detail_edit),
                        tint = onCardColor,
                    )
                }

                // Logo posé à même le fond, comme sur la carte : le catalogue
                // fournit la variante dessinée pour cette couleur.
                brandLogoUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .height(64.dp)
                            .widthIn(max = 220.dp),
                    )
                }
            }

            // Centré dans l'écran entier, comme la maquette — le centrage dans
            // l'espace restant sous l'en-tête décalait le cartouche vers le bas.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Cartouche blanc quel que soit le thème : un code
                // inversé n'est pas lu (§14). Un toucher l'agrandit.
                Surface(
                    onClick = { if (!showBack) zoomed = true },
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Crossfade(targetState = showBack, label = "recto-verso") { back ->
                        if (back && current.backImagePath != null) {
                            AsyncImage(
                                model = File(current.backImagePath.orEmpty()),
                                contentDescription = stringResource(R.string.detail_back_photo),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                            )
                        } else {
                            Column(
                                modifier = Modifier.padding(vertical = 22.dp, horizontal = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                BarcodeImage(
                                    value = current.barcodeValue,
                                    format = current.barcodeFormat,
                                )
                                NumberText(
                                    value = current.numberToDisplay,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.detail_zoom_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCardColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (current.backImagePath != null) {
                    TextButton(
                        onClick = { showBack = !showBack },
                        colors = ButtonDefaults.textButtonColors(contentColor = onCardColor),
                    ) {
                        Text(
                            stringResource(
                                if (showBack) R.string.detail_show_front else R.string.detail_show_back,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrimIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tint.copy(alpha = 0.16f),
        contentColor = tint,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

/**
 * Code agrandi au maximum, en portrait : un format linéaire pivote de 90° (le
 * bitmap est tourné pixel à pixel, rotation sans perte) pour courir le long de
 * la hauteur de l'écran, numéro pivoté avec lui — l'ensemble se lit téléphone
 * tourné, comme en paysage. Retour par le bouton ou le geste système
 * uniquement : pas de fermeture au toucher, l'écran se tend au caissier.
 */
@Composable
private fun ZoomedBarcode(card: Card, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.background(Color.White),
    ) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        val wide = card.barcodeFormat.wide

        val bitmap = remember(card.barcodeValue, card.barcodeFormat, maxWidthPx, maxHeightPx) {
            if (wide) {
                // Lu téléphone tourné : longueur ~82 % de la hauteur, barres ~45 %
                // de la largeur — grand sans être envahissant.
                generateBarcodeBitmap(
                    value = card.barcodeValue,
                    format = card.barcodeFormat,
                    maxWidthPx = (maxHeightPx * 82) / 100,
                    barHeightPx = (maxWidthPx * 45) / 100,
                )?.rotatedQuarter()
            } else {
                val side = (minOf(maxWidthPx, maxHeightPx) * 72) / 100
                generateBarcodeBitmap(card.barcodeValue, card.barcodeFormat, side, side)
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap == null) {
                Text(
                    text = stringResource(R.string.detail_generation_error),
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                )
            } else {
                val widthDp = with(density) { bitmap.width.toDp() }
                val heightDp = with(density) { bitmap.height.toDp() }
                if (wide) {
                    // Vu en paysage (téléphone tourné vers la gauche) : nom en
                    // haut, code au centre, numéro en dessous. En portrait, le
                    // haut paysage est à droite, le bas à gauche.
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberText(
                            value = card.numberToDisplay,
                            modifier = Modifier
                                .rotateClockwise()
                                .padding(top = 6.dp),
                        )
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.size(widthDp, heightDp),
                        )
                        ZoomTitle(
                            name = card.name,
                            modifier = Modifier
                                .rotateClockwise()
                                .padding(top = 10.dp),
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ZoomTitle(name = card.name, modifier = Modifier.padding(bottom = 16.dp))
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.size(widthDp, heightDp),
                        )
                        NumberText(
                            value = card.numberToDisplay,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }

        // Croix de fermeture « en haut à droite » du point de vue paysage :
        // en bas à droite du portrait pour un format linéaire. Déclarée en
        // dernier : toujours au-dessus du contenu.
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(if (wide) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.detail_back),
                tint = Color.Black,
            )
        }
    }
}

@Composable
private fun ZoomTitle(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        color = Color.Black.copy(alpha = 0.65f),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Pivote un composable de 90° en échangeant réellement ses dimensions mesurées,
 * pour qu'il se place dans un Row comme une colonne verticale.
 */
private fun Modifier.rotateClockwise(): Modifier = this
    .graphicsLayer(rotationZ = 90f)
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            ),
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width - placeable.height) / 2,
                y = -(placeable.height - placeable.width) / 2,
            )
        }
    }

@Composable
private fun BarcodeImage(value: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        val barHeightPx = with(density) { 110.dp.roundToPx() }
        val bitmap = remember(value, format, maxWidthPx, barHeightPx) {
            generateBarcodeBitmap(value, format, maxWidthPx, barHeightPx)
        }
        if (bitmap == null) {
            Text(
                text = stringResource(R.string.detail_generation_error),
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        } else {
            // Affiché à sa taille exacte en pixels : aucun ré-échantillonnage.
            val widthDp = with(density) { bitmap.width.toDp() }
            val heightDp = with(density) { bitmap.height.toDp() }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(widthDp, heightDp),
            )
        }
    }
}

@Composable
private fun NumberText(value: String, modifier: Modifier = Modifier) {
    val fontSize = when {
        value.length <= 16 -> 26.sp
        value.length <= 26 -> 20.sp
        else -> 16.sp
    }
    Text(
        text = value,
        color = Color.Black,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
