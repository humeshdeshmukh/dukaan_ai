package com.dukaan.feature.ocr.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dukaan.feature.ocr.analyzer.OcrAnalyzer
import com.dukaan.core.ui.translation.LocalAppStrings
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScannerScreen(
    viewModel: OcrViewModel,
    onBackClick: () -> Unit,
    onBillDetected: () -> Unit,
    onScannerCancelled: () -> Unit = onBackClick
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current

    // Lock to portrait while scanner is active
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = original ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Navigate to result screen when bill is parsed
    LaunchedEffect(uiState.scannedBill) {
        if (uiState.scannedBill != null) {
            onBillDetected()
        }
    }

    // --- ML Kit Document Scanner (Primary Path) ---
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                val pageUris = scanResult.pages?.map { it.imageUri } ?: emptyList()
                if (pageUris.isNotEmpty()) {
                    viewModel.processScannedPages(context, pageUris)
                } else {
                    onScannerCancelled()
                }
            } else {
                onScannerCancelled()
            }
        } else {
            // User cancelled the scanner
            onScannerCancelled()
        }
    }

    // Track if document scanner has been launched
    var docScannerLaunched by remember { mutableStateOf(false) }

    // Launch ML Kit Document Scanner on first composition (if available)
    LaunchedEffect(uiState.docScannerAvailable) {
        if (uiState.docScannerAvailable && !docScannerLaunched && !uiState.isScanning) {
            docScannerLaunched = true
            try {
                val activity = context as? Activity ?: return@LaunchedEffect
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(5)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        scannerLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                    .addOnFailureListener {
                        // Document scanner not available, fall back to camera
                        viewModel.setDocScannerUnavailable()
                    }
            } catch (e: Exception) {
                viewModel.setDocScannerUnavailable()
            }
        }
    }

    // --- Show scanning animation while processing ---
    if (uiState.isScanning) {
        ScanningAnimationScreen(progress = uiState.scanProgress)
        return
    }

    // --- If document scanner is available, show a waiting/blank screen ---
    if (uiState.docScannerAvailable && !uiState.isScanning && uiState.scannedBill == null && uiState.error == null) {
        // Document scanner is handling the UI — show minimal placeholder
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(strings.scanBill) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        strings.openingScanner,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        return
    }

    // --- Fallback: Original CameraX flow (when Document Scanner unavailable) ---
    FallbackCameraScreen(
        viewModel = viewModel,
        uiState = uiState,
        onBackClick = onBackClick,
        onBillDetected = onBillDetected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FallbackCameraScreen(
    viewModel: OcrViewModel,
    uiState: OcrUiState,
    onBackClick: () -> Unit,
    onBillDetected: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val strings = LocalAppStrings.current

    var isTextStable by remember { mutableStateOf(false) }
    var hasAnyText by remember { mutableStateOf(false) }
    var latestStableText by remember { mutableStateOf("") }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Haptic when text stabilizes
    LaunchedEffect(isTextStable) {
        if (isTextStable) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let { viewModel.processGalleryImage(context, it) }
        }
    )

    // Capture photo + send to Gemini vision
    fun captureAndProcess() {
        if (uiState.isScanning) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Save bill photo
        val photoDir = File(context.filesDir, "bills")
        photoDir.mkdirs()
        val photoFile = File(photoDir, "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    viewModel.processCapturedImage(photoFile.absolutePath)
                }
                override fun onError(exception: ImageCaptureException) {
                    if (latestStableText.isNotBlank()) {
                        viewModel.onTextRecognized(latestStableText)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.scanBill) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (hasCameraPermission) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Camera preview
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = imageCapture,
                    onTextStabilized = { stable, text ->
                        isTextStable = stable
                        if (text.isNotBlank()) {
                            latestStableText = text
                            hasAnyText = true
                        } else {
                            hasAnyText = false
                        }
                    },
                    onCameraReady = { camera -> cameraRef = camera }
                )

                // Scan frame overlay
                ScanFrameOverlay(
                    isTextStable = isTextStable,
                    modifier = Modifier.fillMaxSize()
                )

                // Top guidance text
                Text(
                    text = when {
                        uiState.isScanning -> strings.processing
                        isTextStable -> strings.billDetectedTapCapture
                        hasAnyText -> strings.holdSteady
                        else -> strings.pointCameraAtBill
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        isTextStable -> Color(0xFF4CAF50)
                        hasAnyText -> Color(0xFFFDE68A)
                        else -> Color.White
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Loading overlay
                if (uiState.isScanning) {
                    ScanningAnimationScreen(progress = uiState.scanProgress)
                }

                // Bottom action bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery picker
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = strings.pickFromGallery,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Capture button
                    val canCapture = !uiState.isScanning
                    Surface(
                        onClick = { captureAndProcess() },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = when {
                            !canCapture -> Color.White.copy(alpha = 0.4f)
                            isTextStable -> Color(0xFF4CAF50)
                            else -> Color.White.copy(alpha = 0.9f)
                        },
                        border = BorderStroke(4.dp, Color.White)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = strings.capture,
                                tint = if (isTextStable) Color.White else Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Flash toggle
                    IconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            cameraRef?.cameraControl?.enableTorch(isFlashOn)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = strings.toggleFlash,
                            tint = if (isFlashOn) Color(0xFFFDE68A) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Error snackbar with retry
                uiState.error?.let { error ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp)
                            .padding(horizontal = 16.dp),
                        action = {
                            TextButton(onClick = { viewModel.resetScan() }) {
                                Text(strings.retry)
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        strings.cameraPermissionRequired,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(strings.grantPermission)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanFrameOverlay(
    isTextStable: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isTextStable) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.7f)
    Box(
        modifier = modifier.drawBehind {
            val frameWidth = size.width * 0.85f
            val frameHeight = size.height * 0.55f
            val left = (size.width - frameWidth) / 2
            val top = (size.height - frameHeight) / 2.8f
            val cornerLen = 40.dp.toPx()
            val strokeW = 3.dp.toPx()

            // Semi-transparent background outside the frame
            drawRect(Color.Black.copy(alpha = 0.3f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(16.dp.toPx()),
            )

            // Corner brackets - top left
            drawLine(color, Offset(left, top + cornerLen), Offset(left, top), strokeW)
            drawLine(color, Offset(left, top), Offset(left + cornerLen, top), strokeW)
            // top right
            drawLine(color, Offset(left + frameWidth - cornerLen, top), Offset(left + frameWidth, top), strokeW)
            drawLine(color, Offset(left + frameWidth, top), Offset(left + frameWidth, top + cornerLen), strokeW)
            // bottom left
            drawLine(color, Offset(left, top + frameHeight - cornerLen), Offset(left, top + frameHeight), strokeW)
            drawLine(color, Offset(left, top + frameHeight), Offset(left + cornerLen, top + frameHeight), strokeW)
            // bottom right
            drawLine(color, Offset(left + frameWidth - cornerLen, top + frameHeight), Offset(left + frameWidth, top + frameHeight), strokeW)
            drawLine(color, Offset(left + frameWidth, top + frameHeight - cornerLen), Offset(left + frameWidth, top + frameHeight), strokeW)
        }
    )
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    onTextStabilized: (isStable: Boolean, text: String) -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember {
        OcrAnalyzer { isStable, text ->
            onTextStabilized(isStable, text)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                        imageCapture
                    )
                    onCameraReady(camera)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}
