package com.visionaidplusplus.mnnllm.android.lowvision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.roundToInt

/**
 * Advanced image enhancement processor specifically designed for low vision accessibility.
 */
object ImageProcessor {

    enum class EnhancementMode {
        ORIGINAL,
        STANDARD,
        MACULAR_DEGENERATION,
        GLAUCOMA,
        DIABETIC_RETINOPATHY,
        RETINITIS_PIGMENTOSA,
        COLOR_BLIND_PROTAN,
        COLOR_BLIND_DEUTAN,
        COLOR_BLIND_TRITAN,
        ENHANCED_IMAGE,
        NIGHT_VISION
    }

    data class EnhancementConfig(
        val mode: EnhancementMode = EnhancementMode.ORIGINAL,
        val magnificationLevel: Double = 1.0,
        val edgeEnhancementStrength: Double = 1.5,
        val contrastBoost: Double = 2.0,
        val brightnessAdjust: Double = 1.2,
        val saturationBoost: Double = 1.3,
        val applyColorFilter: Boolean = false,
        val colorFilterMatrix: FloatArray? = null
    )

    fun initOpenCV(): Boolean {
        return OpenCVLoader.initDebug()
    }

    fun enhance(srcBitmap: Bitmap, config: EnhancementConfig = EnhancementConfig()): Bitmap {
        if (config.mode == EnhancementMode.ORIGINAL) {
            val safeBitmap = if (srcBitmap.config != Bitmap.Config.ARGB_8888) {
                srcBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: srcBitmap
            } else {
                srcBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: srcBitmap
            }
            return safeBitmap
        }

        // Ensure bitmap is in ARGB_8888 format for consistent processing
        val safeBitmap = if (srcBitmap.config != Bitmap.Config.ARGB_8888) {
            srcBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: srcBitmap
        } else {
            srcBitmap
        }

        var processedMat = Mat()
        Utils.bitmapToMat(safeBitmap, processedMat)

        // FIX: Ensure the Mat is CV_8UC3 (3-channel RGB)
        // BitmapToMat might create CV_8UC4 (RGBA) depending on the bitmap format
        if (processedMat.channels() == 4) {
            val rgbMat = Mat()
            Imgproc.cvtColor(processedMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            processedMat.release()
            processedMat = rgbMat
        }

        // Verify type is CV_8U (8-bit unsigned)
        if (processedMat.depth() != CvType.CV_8U) {
            val convertedMat = Mat()
            processedMat.convertTo(convertedMat, CvType.CV_8U)
            processedMat.release()
            processedMat = convertedMat
        }

        // Step 1: Noise reduction (FIXED - separate src and dst)
        processedMat = applyNoiseReduction(processedMat)

        // Step 2: Apply mode-specific enhancements
        processedMat = when (config.mode) {
            EnhancementMode.MACULAR_DEGENERATION ->
                enhanceForMacularDegeneration(processedMat, config)
            EnhancementMode.GLAUCOMA ->
                enhanceForGlaucoma(processedMat, config)
            EnhancementMode.DIABETIC_RETINOPATHY ->
                enhanceForDiabeticRetinopathy(processedMat, config)
            EnhancementMode.RETINITIS_PIGMENTOSA ->
                enhanceForRetinitisPigmentosa(processedMat, config)
            EnhancementMode.COLOR_BLIND_PROTAN ->
                applyColorBlindFilter(processedMat, ColorBlindType.PROTAN)
            EnhancementMode.COLOR_BLIND_DEUTAN ->
                applyColorBlindFilter(processedMat, ColorBlindType.DEUTAN)
            EnhancementMode.COLOR_BLIND_TRITAN ->
                applyColorBlindFilter(processedMat, ColorBlindType.TRITAN)
            EnhancementMode.ENHANCED_IMAGE ->
                applyEnhancedImage(processedMat, config)
            EnhancementMode.NIGHT_VISION ->
                applyNightVision(processedMat, config)
            else -> applyStandardEnhancement(processedMat, config)
        }

        // Step 3: Edge enhancement
        if (config.edgeEnhancementStrength > 0) {
            processedMat = applyAdvancedEdgeEnhancement(processedMat, config.edgeEnhancementStrength)
        }

        // Step 4: Smart magnification
        if (config.magnificationLevel > 1.0) {
            processedMat = applyQualityPreservingMagnification(processedMat, config.magnificationLevel)
        }

        // Step 5: Optional color filter overlay
        var resultBitmap = matToBitmap(processedMat)

        if (config.applyColorFilter && config.colorFilterMatrix != null) {
            resultBitmap = applyColorMatrixFilter(resultBitmap, config.colorFilterMatrix)
        }

        processedMat.release()

        // Cleanup temporary bitmap if we created one
        if (safeBitmap !== srcBitmap) {
            safeBitmap.recycle()
        }

        return resultBitmap
    }

    /**
     * FIXED: Bilateral filter with separate source and destination Mats
     * and proper type checking
     */
    private fun applyNoiseReduction(src: Mat): Mat {
        // Create destination Mat - MUST be different from source
        val denoised = Mat()

        try {
            // Verify we have 3 or 1 channel 8-bit image as required by bilateralFilter
            if (src.channels() == 3 && src.depth() == CvType.CV_8U) {
                Imgproc.bilateralFilter(src, denoised, 9, 75.0, 75.0)
                src.release() // Release source since we're done with it
                return denoised
            } else if (src.channels() == 1 && src.depth() == CvType.CV_8U) {
                Imgproc.bilateralFilter(src, denoised, 9, 75.0, 75.0)
                src.release()
                return denoised
            } else {
                // Fallback: Convert to 3-channel if needed, or use Gaussian blur
                val tempMat = Mat()
                when {
                    src.channels() == 1 -> {
                        Imgproc.cvtColor(src, tempMat, Imgproc.COLOR_GRAY2RGB)
                    }
                    src.channels() == 4 -> {
                        Imgproc.cvtColor(src, tempMat, Imgproc.COLOR_RGBA2RGB)
                    }
                    else -> {
                        src.copyTo(tempMat)
                    }
                }

                if (tempMat.depth() != CvType.CV_8U) {
                    tempMat.convertTo(tempMat, CvType.CV_8U)
                }

                Imgproc.bilateralFilter(tempMat, denoised, 9, 75.0, 75.0)
                tempMat.release()
                src.release()
                return denoised
            }
        } catch (e: Exception) {
            // If bilateral filter fails, fallback to Gaussian blur
            src.release()
            denoised.release()

            val fallback = Mat()
            Imgproc.GaussianBlur(src, fallback, Size(5.0, 5.0), 0.0)
            return fallback
        }
    }

    private fun applyAdvancedCLAHE(src: Mat, clipLimit: Double = 3.0): Mat {
        // Ensure we're working with 3-channel RGB
        val rgbMat = if (src.channels() != 3) {
            val converted = Mat()
            when (src.channels()) {
                1 -> Imgproc.cvtColor(src, converted, Imgproc.COLOR_GRAY2RGB)
                4 -> Imgproc.cvtColor(src, converted, Imgproc.COLOR_RGBA2RGB)
                else -> src.copyTo(converted)
            }
            converted
        } else {
            src.clone()
        }

        val lab = Mat()
        Imgproc.cvtColor(rgbMat, lab, Imgproc.COLOR_RGB2Lab)

        val channels = ArrayList<Mat>()
        Core.split(lab, channels)

        val tileSizes = listOf(Size(8.0, 8.0), Size(16.0, 16.0), Size(32.0, 32.0))
        val lChannel = channels[0].clone()

        tileSizes.forEach { tileSize ->
            val clahe = Imgproc.createCLAHE(clipLimit / tileSizes.size, tileSize)
            val tempChannel = Mat()
            clahe.apply(channels[0], tempChannel)
            Core.addWeighted(lChannel, 0.7, tempChannel, 0.3, 0.0, lChannel)
            tempChannel.release()
        }

        channels[0] = lChannel
        Core.merge(channels, lab)

        val enhanced = Mat()
        Imgproc.cvtColor(lab, enhanced, Imgproc.COLOR_Lab2RGB)

        // Cleanup
        rgbMat.release()
        channels.forEach { it.release() }
        lab.release()
        src.release()

        return enhanced
    }

    private fun applyAdvancedEdgeEnhancement(src: Mat, strength: Double): Mat {
        val blurred = Mat()
        // FIX: Ensure different src and dst for GaussianBlur too
        Imgproc.GaussianBlur(src, blurred, Size(0.0, 0.0), 3.0)

        val sharpened = Mat()
        Core.addWeighted(src, 1.0 + strength, blurred, -strength, 0.0, sharpened)

        blurred.release()
        src.release()
        return sharpened
    }

    private fun enhanceStructure(src: Mat, config: EnhancementConfig): Mat {
        // Convert to grayscale for morphology operations
        val gray = Mat()
        if (src.channels() == 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        } else if (src.channels() == 4) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            src.copyTo(gray)
        }

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(15.0, 15.0))
        val tophat = Mat()
        Imgproc.morphologyEx(gray, tophat, Imgproc.MORPH_TOPHAT, kernel)

        val blackhat = Mat()
        Imgproc.morphologyEx(gray, blackhat, Imgproc.MORPH_BLACKHAT, kernel)

        val enhancedGray = Mat()
        Core.add(gray, tophat, enhancedGray)
        Core.subtract(enhancedGray, blackhat, enhancedGray)

        val enhanced = Mat()
        Imgproc.cvtColor(enhancedGray, enhanced, Imgproc.COLOR_GRAY2RGB)

        val result = Mat()
        Core.addWeighted(src, 0.5, enhanced, 0.5, 0.0, result)

        gray.release()
        tophat.release()
        blackhat.release()
        enhancedGray.release()
        enhanced.release()
        kernel.release()
        src.release()

        return result
    }

    private fun enhanceForMacularDegeneration(src: Mat, config: EnhancementConfig): Mat {
        var enhanced = applyAdvancedCLAHE(src, 4.0)
        enhanced = applyAdvancedEdgeEnhancement(enhanced, 2.0)
        enhanced = enhanceStructure(enhanced, config)

        val hsv = Mat()
        Imgproc.cvtColor(enhanced, hsv, Imgproc.COLOR_RGB2HSV)
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)

        Core.multiply(channels[1], Scalar(config.saturationBoost), channels[1])

        Core.merge(channels, hsv)
        Imgproc.cvtColor(hsv, enhanced, Imgproc.COLOR_HSV2RGB)

        channels.forEach { it.release() }
        hsv.release()

        return enhanced
    }

    private fun enhanceForGlaucoma(src: Mat, config: EnhancementConfig): Mat {
        var enhanced = applyAdvancedCLAHE(src, 4.5)
        Core.multiply(enhanced, Scalar(config.brightnessAdjust), enhanced)
        enhanced = applyAdvancedEdgeEnhancement(enhanced, 2.5)
        return enhanced
    }

    private fun enhanceForDiabeticRetinopathy(src: Mat, config: EnhancementConfig): Mat {
        val gammaCorrected = Mat()
        val lookUpTable = Mat(1, 256, CvType.CV_8U)
        val lutData = ByteArray(256)
        val gamma = 0.7

        for (i in 0 until 256) {
            lutData[i] = (Math.pow(i / 255.0, gamma) * 255.0).toInt().toByte()
        }
        lookUpTable.put(0, 0, lutData)

        Core.LUT(src, lookUpTable, gammaCorrected)
        lookUpTable.release()

        val enhanced = applyAdvancedCLAHE(gammaCorrected, 2.5)
        gammaCorrected.release()
        src.release()

        return enhanced
    }

    private fun enhanceForRetinitisPigmentosa(src: Mat, config: EnhancementConfig): Mat {
        var enhanced = applyAdvancedCLAHE(src, 4.0)
        Core.multiply(enhanced, Scalar(1.4), enhanced)

        val hsv = Mat()
        Imgproc.cvtColor(enhanced, hsv, Imgproc.COLOR_RGB2HSV)
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)
        Core.multiply(channels[1], Scalar(1.5), channels[1])
        Core.merge(channels, hsv)
        Imgproc.cvtColor(hsv, enhanced, Imgproc.COLOR_HSV2RGB)
        channels.forEach { it.release() }
        hsv.release()
        src.release()

        return enhanced
    }

    private enum class ColorBlindType { PROTAN, DEUTAN, TRITAN }

    private fun applyColorBlindFilter(src: Mat, type: ColorBlindType): Mat {
        val daltonized = Mat()

        when (type) {
            ColorBlindType.PROTAN -> {
                val shiftMatrix = Mat(3, 3, CvType.CV_32F)
                val shiftData = floatArrayOf(
                    0.0f, 2.0f, 0.0f,
                    0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f
                )
                shiftMatrix.put(0, 0, shiftData)
                Core.transform(src, daltonized, shiftMatrix)
                shiftMatrix.release()
            }
            ColorBlindType.DEUTAN -> {
                val shiftMatrix = Mat(3, 3, CvType.CV_32F)
                val shiftData = floatArrayOf(
                    1.0f, 0.0f, 0.0f,
                    0.8f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f
                )
                shiftMatrix.put(0, 0, shiftData)
                Core.transform(src, daltonized, shiftMatrix)
                shiftMatrix.release()
            }
            ColorBlindType.TRITAN -> {
                val shiftMatrix = Mat(3, 3, CvType.CV_32F)
                val shiftData = floatArrayOf(
                    1.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f,
                    0.0f, 1.5f, 0.0f
                )
                shiftMatrix.put(0, 0, shiftData)
                Core.transform(src, daltonized, shiftMatrix)
                shiftMatrix.release()
            }
        }

        src.release()
        return applyAdvancedCLAHE(daltonized, 3.0)
    }

    private fun applyEnhancedImage(src: Mat, config: EnhancementConfig): Mat {
        // 1. Strongly enhance contrast and local brightness via CLAHE (Note: applyAdvancedCLAHE releases src)
        var enhanced = applyAdvancedCLAHE(src, 4.0)
        
        // 2. Globally boost brightness and saturation for low-light visibility
        val hsv = Mat()
        Imgproc.cvtColor(enhanced, hsv, Imgproc.COLOR_RGB2HSV)
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)
        
        // Increase Lightness/Value (brightness)
        Core.multiply(channels[2], Scalar(1.5), channels[2])
        // Increase Saturation (makes colors pop, removing grey dullness)
        Core.multiply(channels[1], Scalar(1.3), channels[1])
        
        Core.merge(channels, hsv)
        Imgproc.cvtColor(hsv, enhanced, Imgproc.COLOR_HSV2RGB)
        
        channels.forEach { it.release() }
        hsv.release()
        
        return enhanced
    }

    private fun applyNightVision(src: Mat, config: EnhancementConfig): Mat {
        val channels = ArrayList<Mat>()
        Core.split(src, channels)

        // Reduce green and blue channels to create red tint
        Core.multiply(channels[1], Scalar(0.3), channels[1])
        Core.multiply(channels[2], Scalar(0.3), channels[2])

        val redTinted = Mat()
        Core.merge(channels, redTinted)
        channels.forEach { it.release() }
        src.release()

        return applyAdvancedCLAHE(redTinted, 2.0)
    }

    private fun applyStandardEnhancement(src: Mat, config: EnhancementConfig): Mat {
        var enhanced = applyAdvancedCLAHE(src, 3.0)
        enhanced = applyAdvancedEdgeEnhancement(enhanced, config.edgeEnhancementStrength)
        Core.multiply(enhanced, Scalar(config.brightnessAdjust), enhanced)
        return enhanced
    }

    private fun applyQualityPreservingMagnification(src: Mat, scale: Double): Mat {
        if (scale <= 1.0) return src

        val newWidth = (src.cols() * scale).roundToInt()
        val newHeight = (src.rows() * scale).roundToInt()
        val newSize = Size(newWidth.toDouble(), newHeight.toDouble())

        val magnified = Mat()
        Imgproc.resize(src, magnified, newSize, 0.0, 0.0, Imgproc.INTER_LANCZOS4)
        src.release()

        return magnified
    }

    private fun applyColorMatrixFilter(bitmap: Bitmap, matrix: FloatArray): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val colorMatrix = ColorMatrix(matrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        // Ensure mat is in a format suitable for bitmap conversion
        val rgbMat = if (mat.channels() == 4) {
            val converted = Mat()
            Imgproc.cvtColor(mat, converted, Imgproc.COLOR_RGBA2RGB)
            converted
        } else if (mat.channels() == 1) {
            val converted = Mat()
            Imgproc.cvtColor(mat, converted, Imgproc.COLOR_GRAY2RGB)
            converted
        } else {
            mat
        }

        val bitmap = Bitmap.createBitmap(rgbMat.cols(), rgbMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbMat, bitmap)

        if (rgbMat !== mat) {
            rgbMat.release()
        }

        return bitmap
    }

    fun enhance(srcBitmap: Bitmap): Bitmap {
        return enhance(srcBitmap, EnhancementConfig())
    }

    fun generateAccessibilityMetadata(bitmap: Bitmap, config: EnhancementConfig): Map<String, Any> {
        return mapOf(
            "originalWidth" to bitmap.width,
            "originalHeight" to bitmap.height,
            "magnificationApplied" to config.magnificationLevel,
            "enhancementMode" to config.mode.name,
            "contrastEnhanced" to true,
            "edgeEnhanced" to (config.edgeEnhancementStrength > 0),
            "recommendedFor" to when(config.mode) {
                EnhancementMode.MACULAR_DEGENERATION -> "Central vision loss, reading assistance"
                EnhancementMode.GLAUCOMA -> "Peripheral vision loss, tunnel vision"
                EnhancementMode.DIABETIC_RETINOPATHY -> "Light sensitivity, glare reduction"
                EnhancementMode.RETINITIS_PIGMENTOSA -> "Night blindness, severe impairment"
                else -> "General low vision enhancement"
            }
        )
    }
}