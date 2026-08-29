package com.monselio.fidcards.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.monselio.fidcards.R
import com.monselio.fidcards.barcode.UpcE
import com.monselio.fidcards.data.BarcodeFormat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import zxingcpp.BarcodeReader

/**
 * Entrée principale de l'ajout (docs/PROJET.md §5.3) : scan par la caméra avec
 * détection automatique du format. Repli complet en saisie manuelle si la
 * permission est refusée — la caméra n'est jamais indispensable (§7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onScanned: (value: String, format: BarcodeFormat) -> Unit,
    onManualEntry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var refused by remember { mutableStateOf(false) }
    // « Ne plus demander » : le système renverra un refus immédiat, sans dialogue.
    // Insister avec le même bouton ne produirait rien de visible.
    var deniedForever by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        refused = !granted
        deniedForever = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Retour des réglages système : la permission a pu être accordée là-bas, et
    // rien d'autre ne viendrait le dire à cet écran.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !hasPermission && context.hasCameraPermission()) {
                hasPermission = true
                refused = false
                deniedForever = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (hasPermission && !cameraUnavailable) {
                CameraScanner(
                    onScanned = onScanned,
                    onCameraUnavailable = { cameraUnavailable = true },
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.scan_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = onManualEntry,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.scan_manual_entry))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            when {
                                cameraUnavailable -> R.string.scan_camera_unavailable
                                deniedForever -> R.string.scan_permission_denied_forever
                                else -> R.string.scan_permission_rationale
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onManualEntry,
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        Text(stringResource(R.string.scan_manual_entry))
                    }
                    if (refused && !cameraUnavailable) {
                        OutlinedButton(
                            onClick = {
                                if (deniedForever) {
                                    context.openAppSettings()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (deniedForever) R.string.scan_open_settings
                                    else R.string.scan_retry_permission,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/** Seul chemin de rattrapage après un refus définitif : la fiche de l'app. */
private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun CameraScanner(
    onScanned: (String, BarcodeFormat) -> Unit,
    onCameraUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnUnavailable by rememberUpdatedState(onCameraUnavailable)
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val mainExecutor = ContextCompat.getMainExecutor(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val reader = BarcodeReader().apply {
                    options.formats = SCANNABLE_FORMATS.keys
                    options.tryHarder = true
                    options.tryRotate = true
                    options.tryInvert = false
                    // PLAIN : en mode HRI (défaut), zxing-cpp étendrait l'UPC-E en
                    // UPC-A 12 chiffres, que le générateur UPC-E refuserait ensuite.
                    options.textMode = BarcodeReader.TextMode.PLAIN
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    imageProxy.use { proxy ->
                        val results = runCatching { reader.read(proxy) }.getOrDefault(emptyList())
                        val hit = results.firstNotNullOfOrNull { result ->
                            val format = SCANNABLE_FORMATS[result.format]
                            val text = result.text
                            when {
                                format == null || text.isNullOrEmpty() -> null
                                // zxing-cpp rapporte l'UPC-E sous forme étendue :
                                // retour à la forme courte régénérable (voir UpcE),
                                // sinon repli sur le format porteur des mêmes chiffres.
                                format == BarcodeFormat.UPC_E ->
                                    UpcE.toCompact(text)?.let { it to BarcodeFormat.UPC_E }
                                        ?: when (text.length) {
                                            13 -> text to BarcodeFormat.EAN_13
                                            12 -> text to BarcodeFormat.UPC_A
                                            else -> null
                                        }
                                else -> text to format
                            }
                        }
                        if (hit != null && delivered.compareAndSet(false, true)) {
                            mainExecutor.execute { currentOnScanned(hit.first, hit.second) }
                        }
                    }
                }
                provider.unbindAll()
                runCatching {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { currentOnUnavailable() }
            }, mainExecutor)
            previewView
        },
    )
}

/**
 * Restreindre la détection aux 12 formats du modèle garantit que zxing-cpp
 * rapporte les valeurs canoniques de son enum (pas les variantes ITF_14, etc.).
 */
private val SCANNABLE_FORMATS: Map<BarcodeReader.Format, BarcodeFormat> = mapOf(
    BarcodeReader.Format.EAN_13 to BarcodeFormat.EAN_13,
    BarcodeReader.Format.EAN_8 to BarcodeFormat.EAN_8,
    BarcodeReader.Format.UPC_A to BarcodeFormat.UPC_A,
    BarcodeReader.Format.UPC_E to BarcodeFormat.UPC_E,
    BarcodeReader.Format.CODE_128 to BarcodeFormat.CODE_128,
    BarcodeReader.Format.CODE_39 to BarcodeFormat.CODE_39,
    BarcodeReader.Format.ITF to BarcodeFormat.ITF,
    BarcodeReader.Format.CODABAR to BarcodeFormat.CODABAR,
    BarcodeReader.Format.QR_CODE to BarcodeFormat.QR_CODE,
    BarcodeReader.Format.DATA_MATRIX to BarcodeFormat.DATA_MATRIX,
    BarcodeReader.Format.PDF_417 to BarcodeFormat.PDF_417,
    BarcodeReader.Format.AZTEC to BarcodeFormat.AZTEC,
)
