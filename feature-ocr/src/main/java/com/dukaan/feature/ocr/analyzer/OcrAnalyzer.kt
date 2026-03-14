package com.dukaan.feature.ocr.analyzer

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions

class OcrAnalyzer(
    private val onTextStabilized: (isStable: Boolean, text: String) -> Unit
) : ImageAnalysis.Analyzer {

    // Devanagari recognizer also handles Latin script — supports Hindi + English bills
    private val recognizer = TextRecognition.getClient(
        DevanagariTextRecognizerOptions.Builder().build()
    )
    private var lastDetectedText: String = ""
    private var stableFrameCount = 0
    private var isProcessing = false
    private var lastProcessedTime = 0L

    @Volatile
    var latestText: String = ""
        private set

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (isProcessing || (now - lastProcessedTime) < MIN_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isProcessing = true
            lastProcessedTime = now
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    if (text.isNotBlank() && text.length > MIN_TEXT_LENGTH) {
                        latestText = text
                        if (isSimilar(text, lastDetectedText)) {
                            stableFrameCount++
                        } else {
                            stableFrameCount = 0
                        }
                        lastDetectedText = text
                        onTextStabilized(stableFrameCount >= STABLE_THRESHOLD, text)
                    } else {
                        stableFrameCount = 0
                        onTextStabilized(false, "")
                    }
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun isSimilar(a: String, b: String): Boolean {
        if (b.isEmpty()) return false
        val ratio = minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        return ratio > 0.85
    }

    companion object {
        private const val MIN_INTERVAL_MS = 500L
        private const val MIN_TEXT_LENGTH = 20
        private const val STABLE_THRESHOLD = 3
    }
}
