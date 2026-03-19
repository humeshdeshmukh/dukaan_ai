package com.dukaan.feature.ocr.util

import android.graphics.Bitmap
import android.graphics.Matrix
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object ImageUtil {

    private var isOpenCVInitialized = false

    /**
     * Initializes OpenCV if not already initialized.
     */
    fun initializeOpenCV(): Boolean {
        if (isOpenCVInitialized) return true
        isOpenCVInitialized = OpenCVLoader.initDebug()
        return isOpenCVInitialized
    }

    /**
     * Resizes a bitmap maintaining aspect ratio so its maximum dimension is [maxDimension].
     */
    fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap // Already small enough
        }

        val scale = maxDimension.toFloat() / Math.max(width, height)
        val matrix = Matrix().apply { postScale(scale, scale) }

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    /**
     * Preprocesses an image using OpenCV for better OCR accuracy.
     * sequence: Grayscale -> CLAHE (Contrast) -> Denoise -> Sharpen
     */
    fun preprocessImageForOcr(bitmap: Bitmap): Bitmap {
        if (!initializeOpenCV()) {
            android.util.Log.e("ImageUtil", "OpenCV initialization failed, returning original bitmap")
            return bitmap
        }

        try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            // 1. Convert to Grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            // 2. Enhance Contrast using CLAHE (Contrast Limited Adaptive Histogram Equalization)
            // This is better than global equalization for documents with uneven lighting
            val claheMat = Mat()
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(grayMat, claheMat)

            // 3. Denoise / Smooth
            val denoiseMat = Mat()
            Imgproc.GaussianBlur(claheMat, denoiseMat, Size(3.0, 3.0), 0.0)

            // 4. Sharpen (Unsharp Masking equivalent)
            // Destination = Source + (Source - Blurred) * amount
            val sharpenedMat = Mat()
            val blurredMat = Mat()
            Imgproc.GaussianBlur(denoiseMat, blurredMat, Size(5.0, 5.0), 0.0)
            org.opencv.core.Core.addWeighted(denoiseMat, 1.5, blurredMat, -0.5, 0.0, sharpenedMat)

            // Convert back to Bitmap
            val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(sharpenedMat, resultBitmap)

            // Clean up Mats
            srcMat.release()
            grayMat.release()
            claheMat.release()
            denoiseMat.release()
            blurredMat.release()
            sharpenedMat.release()

            return resultBitmap

        } catch (e: Exception) {
            android.util.Log.e("ImageUtil", "Error preprocessing image with OpenCV: ${e.message}", e)
            return bitmap
        }
    }
}
