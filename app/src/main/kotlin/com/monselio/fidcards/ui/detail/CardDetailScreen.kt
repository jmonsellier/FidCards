package com.monselio.fidcards.ui.detail

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monselio.fidcards.R
import com.monselio.fidcards.barcode.generateBarcodeBitmap
import com.monselio.fidcards.data.BarcodeFormat

/**
 * Le cœur de l'app (docs/PROJET.md §5.2) : luminosité à 100 %, écran maintenu
 * allumé, paysage pour les formats larges, code régénéré depuis la valeur brute
 * (jamais depuis une photo), numéro en gros sous le code — le plan B des caisses
 * dont le lecteur laser ne lit pas un écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Paysage imposé pour les seuls formats larges, même si la rotation système est
    // verrouillée — comportement voulu (§14). Les formats carrés restent libres.
    val wide = card?.barcodeFormat?.wide
    DisposableEffect(wide, activity) {
        if (wide == true) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = card?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.detail_edit),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = card ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cartouche blanc quel que soit le thème : un code inversé n'est pas lu (§14).
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
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
                            .padding(top = 20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeImage(value: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth
        val barHeightPx = with(density) { 130.dp.roundToPx() }
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
            // Affiché à sa taille exacte en pixels : aucun ré-échantillonnage possible.
            // FilterQuality.None garde le nearest neighbour même en cas d'arrondi dp.
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
        value.length <= 16 -> 30.sp
        value.length <= 26 -> 24.sp
        else -> 18.sp
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
