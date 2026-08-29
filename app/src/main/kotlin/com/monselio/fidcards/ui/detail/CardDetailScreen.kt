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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.monselio.fidcards.R
import com.monselio.fidcards.barcode.generateBarcodeBitmap
import com.monselio.fidcards.barcode.rotatedQuarter
import com.monselio.fidcards.brands.Brand
import com.monselio.fidcards.data.BarcodeFormat
import com.monselio.fidcards.data.Card
import com.monselio.fidcards.ui.home.colorFromName
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
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    val loaded = state as? CardDetailState.Loaded
    val cardColor = loaded?.let { cardColor(it.card, brand) }

    // La couleur d'enseigne court jusque sous la barre d'état : sans cet
    // accord, l'horloge et la batterie disparaissent sur les enseignes très
    // sombres (Bershka) comme très claires (Cultura). L'agrandi, lui, est blanc.
    val view = LocalView.current
    DisposableEffect(activity, view, cardColor, zoomed) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        val lightBackground = if (zoomed) true else cardColor?.let { it.luminance() >= 0.5f }
        if (controller != null && lightBackground != null) {
            controller.isAppearanceLightStatusBars = lightBackground
        }
        onDispose {
            if (controller != null && previous != null) {
                controller.isAppearanceLightStatusBars = previous
            }
        }
    }

    Scaffold { innerPadding ->
        when (val current = state) {
            // Premier passage avant l'arrivée du flux : rien à annoncer.
            CardDetailState.Loading -> Unit

            CardDetailState.Missing -> MissingCard(
                onBack = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
            )

            is CardDetailState.Loaded -> if (zoomed) {
                ZoomedBarcode(
                    card = current.card,
                    onDismiss = { zoomed = false },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                CardFace(
                    card = current.card,
                    brandLogoUri = brandLogoUri,
                    cardColor = cardColor ?: cardColor(current.card, brand),
                    showBack = showBack,
                    onToggleBack = { showBack = !showBack },
                    onZoom = { zoomed = true },
                    onBack = onBack,
                    onEdit = onEdit,
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

/** Couleur de fond : celle de l'enseigne, sinon celle de la carte, sinon dérivée du nom. */
private fun cardColor(card: Card, brand: Brand?): Color =
    brand?.colorArgb?.let(::Color)
        ?: card.colorArgb?.let(::Color)
        ?: colorFromName(card.name)

/**
 * La carte plein écran : en-tête ancré en haut, cartouche centré dans l'espace
 * restant. Ce bloc central défile dès qu'il ne tient plus — code matriciel,
 * verso en portrait, paysage ou grande taille de police — au lieu d'être rogné,
 * et il n'est plus superposé à l'en-tête, dont il interceptait les touches.
 */
@Composable
private fun CardFace(
    card: Card,
    brandLogoUri: String?,
    cardColor: Color,
    showBack: Boolean,
    onToggleBack: () -> Unit,
    onZoom: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    contentPadding: PaddingValues,
) {
    val onCardColor = if (cardColor.luminance() < 0.5f) Color.White else Color(0xFF1A1A1A)

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
                .padding(contentPadding)
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
                    text = card.name,
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

            brandLogoUri?.let { uri ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(min = 48.dp, max = 132.dp)
                            .padding(8.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Cartouche blanc quel que soit le thème : un code
                // inversé n'est pas lu (§14). Un toucher l'agrandit.
                Surface(
                    onClick = { if (!showBack) onZoom() },
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Crossfade(targetState = showBack, label = "recto-verso") { back ->
                        if (back && card.backImagePath != null) {
                            AsyncImage(
                                model = File(card.backImagePath.orEmpty()),
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
                                    value = card.barcodeValue,
                                    format = card.barcodeFormat,
                                )
                                NumberText(
                                    value = card.numberToDisplay,
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
                if (card.backImagePath != null) {
                    TextButton(
                        onClick = onToggleBack,
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

/**
 * La carte a disparu — supprimée alors qu'un raccourci d'écran d'accueil la
 * désignait encore. Sans ce cas, l'écran restait vide et sans issue.
 */
@Composable
private fun MissingCard(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.detail_card_missing),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.detail_back))
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
        // 48 dp : cible tactile minimale, que Surface(onClick) n'impose pas
        // d'elle-même contrairement à IconButton.
        modifier = Modifier.size(48.dp),
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
