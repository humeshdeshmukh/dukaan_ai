package com.dukaan.feature.ocr.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScannerScreen(
    viewModel: OcrViewModel,
    onBackClick: () -> Unit,
    onBillDetected: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()

    // Lock to portrait while scanner is active
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val original = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = original ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var isTextStable by remember { mutableStateOf(false) }
    var latestStableText by remember { mutableStateOf("") }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    // Navigate to result screen when bill is parsed
    LaunchedEffect(uiState.scannedBill) {
        if (uiState.scannedBill != null) {
            onBillDetected()
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Bill") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    isFlashOn = isFlashOn,
                    onTextStabilized = { stable, text ->
                        isTextStable = stable
                        if (text.isNotBlank()) latestStableText = text
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
                    text = if (isTextStable) "Bill detected! Tap capture"
                           else "Align bill within the frame",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isTextStable) Color(0xFF4CAF50) else Color.White,
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Processing bill...",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
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
                            contentDescription = "Pick from gallery",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Capture button
                    Surface(
                        onClick = {
                            if (!uiState.isScanning && latestStableText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.onTextRecognized(latestStableText)
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = if (isTextStable) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.9f),
                        border = BorderStroke(4.dp, Color.White)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Capture",
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
                            contentDescription = "Toggle flash",
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
                                Text("Retry")
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
                        "Camera permission is required to scan bills.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
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
    isFlashOn: Boolean,
    onTextStabilized: (isStable: Boolean, text: String) -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, OcrAnalyzer { isStable, text ->
                    onTextStabilized(isStable, text)
                })

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
