package com.visionaidplusplus.mnnllm.android.lowvision

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Advanced OCR Processor with layout analysis and intelligent text processing
 * for low vision users. Extracts text with spatial information and applies
 * NLP cleaning techniques.
 */
object OcrProcessor {

    data class TextElement(
        val text: String,
        val boundingBox: Rect?,
        val type: ElementType,
        val centerX: Int,
        val centerY: Int,
        val recognizedLanguage: String?
    ) {
        enum class ElementType {
            BLOCK, LINE, WORD, SYMBOL
        }
    }

    data class ProcessedTextResult(
        val rawText: String,
        val cleanedText: String,
        val structuredText: String,
        val elements: List<TextElement>,
        val centerWeightedText: String,
        val language: String?
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Main entry point with advanced processing pipeline
     */
    suspend fun extractText(bitmap: Bitmap): ProcessedTextResult = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            // Extract elements with spatial information
            val elements = extractElementsWithLayout(result)

            // Calculate image center for center-weighted extraction
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2

            // Process text through pipeline
            val rawText = result.text
            val cleanedText = applyAdvancedCleaning(rawText)
            val structuredText = reconstructStructuredText(elements)
            val centerWeightedText = generateCenterWeightedText(elements, centerX, centerY)

            // Get primary language
            val primaryLanguage = elements.firstOrNull { !it.recognizedLanguage.isNullOrBlank() }?.recognizedLanguage
                ?: result.textBlocks.firstOrNull()?.recognizedLanguage

            ProcessedTextResult(
                rawText = rawText,
                cleanedText = cleanedText,
                structuredText = structuredText,
                elements = elements,
                centerWeightedText = centerWeightedText,
                language = primaryLanguage
            )

        } catch (e: Exception) {
            ProcessedTextResult(
                rawText = "",
                cleanedText = "",
                structuredText = "",
                elements = emptyList(),
                centerWeightedText = "Error: ${e.message}",
                language = null
            )
        }
    }

    /**
     * Extract all text elements with their bounding boxes and spatial info
     */
    private fun extractElementsWithLayout(result: Text): List<TextElement> {
        val elements = mutableListOf<TextElement>()

        result.textBlocks.forEach { block ->
            elements.add(createTextElement(
                block.text,
                block.boundingBox,
                TextElement.ElementType.BLOCK,
                block.recognizedLanguage
            ))

            block.lines.forEach { line ->
                elements.add(createTextElement(
                    line.text,
                    line.boundingBox,
                    TextElement.ElementType.LINE,
                    line.recognizedLanguage
                ))

                line.elements.forEach { element ->
                    elements.add(createTextElement(
                        element.text,
                        element.boundingBox,
                        TextElement.ElementType.WORD,
                        element.recognizedLanguage
                    ))
                }
            }
        }

        return elements
    }

    private fun createTextElement(
        text: String,
        boundingBox: Rect?,
        type: TextElement.ElementType,
        language: String?
    ): TextElement {
        val centerX = boundingBox?.let { (it.left + it.right) / 2 } ?: 0
        val centerY = boundingBox?.let { (it.top + it.bottom) / 2 } ?: 0

        return TextElement(
            text = text,
            boundingBox = boundingBox,
            type = type,
            centerX = centerX,
            centerY = centerY,
            recognizedLanguage = language
        )
    }

    /**
     * Generate text sorted by proximity to center (for low vision focus)
     */
    private fun generateCenterWeightedText(
        elements: List<TextElement>,
        centerX: Int,
        centerY: Int
    ): String {
        // Filter to line level for readability
        val lines = elements.filter { it.type == TextElement.ElementType.LINE }

        if (lines.isEmpty()) return ""

        // Sort by distance from center
        val sortedLines = lines.sortedBy { element ->
            val dx = element.centerX - centerX
            val dy = element.centerY - centerY
            (dx * dx + dy * dy).toFloat()
        }

        return sortedLines.joinToString("\n") { it.text }
    }

    /**
     * Reconstruct text preserving layout structure
     */
    private fun reconstructStructuredText(elements: List<TextElement>): String {
        val blocks = elements.filter { it.type == TextElement.ElementType.BLOCK }

        return blocks.joinToString("\n\n") { block ->
            // Get lines belonging to this block (approximate by proximity)
            val blockLines = elements.filter {
                it.type == TextElement.ElementType.LINE &&
                        it.centerY >= (block.boundingBox?.top ?: 0) &&
                        it.centerY <= (block.boundingBox?.bottom ?: Int.MAX_VALUE)
            }.sortedBy { it.centerY }

            blockLines.joinToString("\n") { it.text }
        }
    }

    /**
     * Advanced text cleaning using multiple NLP techniques
     */
    private fun applyAdvancedCleaning(text: String): String {
        if (text.isBlank()) return "No readable text detected"

        var cleaned = text

        // 1. Unicode normalization (NFKC for compatibility)
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFKC)

        // 2. Remove control characters except newlines
        cleaned = cleaned.replace(Regex("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F\\x7F]"), "")

        // 3. Fix hyphenation at line breaks (e.g., "informa- tion" -> "information")
        cleaned = cleaned.replace(Regex("(\\w)-\\s*\\n\\s*(\\w)"), "$1$2")

        // 4. Normalize whitespace (collapse multiple spaces, normalize tabs)
        cleaned = cleaned.replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
        cleaned = cleaned.replace(Regex(" {2,}"), " ")

        // 5. Fix common OCR errors using regex patterns
        cleaned = fixCommonOcrErrors(cleaned)

        // 6. Normalize punctuation spacing
        cleaned = cleaned.replace(Regex("\\s+([.,;:!?)])"), "$1")
        cleaned = cleaned.replace(Regex("([({])\\s+"), "$1")

        // 7. Fix line breaks (preserve paragraph structure)
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")

        // 8. Remove isolated single characters (likely OCR noise)
        cleaned = cleaned.replace(Regex("(?m)^\\s*\\w\\s*$\\n?"), "")

        // 9. Trim lines
        cleaned = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        return cleaned.trim()
    }

    /**
     * Fix common OCR misrecognitions using regex patterns
     */
    private fun fixCommonOcrErrors(text: String): String {
        var fixed = text

        // Character substitutions (careful not to change intentional numbers)
        fixed = fixed.replace(Regex("(?i)(?<![0-9])o(?![0-9])")) { match ->
            if (match.value == "0") "o" else match.value
        }

        // Fix common ligatures and combinations using word boundaries
        fixed = fixed.replace(Regex("(?i)rn\\b"), "m")
        fixed = fixed.replace(Regex("(?i)nn\\b"), "m")
        fixed = fixed.replace(Regex("(?i)\\bcl\\b"), "d")
        fixed = fixed.replace(Regex("(?i)ﬁ"), "fi")
        fixed = fixed.replace(Regex("(?i)ﬂ"), "fl")

        // Fix email patterns - remove spaces in emails
        fixed = fixed.replace(Regex("(?i)([a-z0-9._%+-]+)\\s*@\\s*([a-z0-9.-]+)\\s*\\.\\s*([a-z]{2,})")) { matchResult ->
            val groups = matchResult.groupValues
            "${groups[1]}@${groups[2]}.${groups[3]}".lowercase()
        }

        // Fix URL patterns - remove spaces in URLs
        fixed = fixed.replace(Regex("(?i)(https?://|www\\.)\\s*([^\\s]+)")) { matchResult ->
            matchResult.value.replace(" ", "").lowercase()
        }

        // Fix date patterns (various formats)
        val datePatterns = listOf(
            Regex("(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{2,4})"),
            Regex("(\\d{4})\\s*[/-]\\s*(\\d{1,2})\\s*[/-]\\s*(\\d{1,2})")
        )

        datePatterns.forEach { pattern ->
            fixed = fixed.replace(pattern) { matchResult ->
                matchResult.value.replace(" ", "")
            }
        }

        // Fix phone number patterns
        fixed = fixed.replace(Regex("(\\d{3})\\s*[.-]\\s*(\\d{3})\\s*[.-]\\s*(\\d{4})")) { matchResult ->
            val groups = matchResult.groupValues
            "${groups[1]}-${groups[2]}-${groups[3]}"
        }

        return fixed
    }

    /**
     * Extract text specifically from center region (for low vision focus)
     */
    suspend fun extractCenterText(bitmap: Bitmap, centerRegionPercent: Float = 0.5f): String =
        withContext(Dispatchers.IO) {
            val width = bitmap.width
            val height = bitmap.height

            val cropWidth = (width * centerRegionPercent).toInt()
            val cropHeight = (height * centerRegionPercent).toInt()
            val left = (width - cropWidth) / 2
            val top = (height - cropHeight) / 2

            if (cropWidth <= 0 || cropHeight <= 0 || left < 0 || top < 0 ||
                left + cropWidth > width || top + cropHeight > height) {
                return@withContext "Invalid crop region"
            }

            val centerBitmap = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)

            try {
                val result = extractText(centerBitmap)
                result.cleanedText
            } catch (e: Exception) {
                "Error extracting center text: ${e.message}"
            } finally {
                centerBitmap.recycle()
            }
        }

    /**
     * Get text blocks with their center positions for UI overlay
     */
    suspend fun getTextBlocksWithPosition(bitmap: Bitmap): List<Pair<String, Rect>> =
        withContext(Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(image).await()

                result.textBlocks.mapNotNull { block ->
                    block.boundingBox?.let { block.text to it }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Simple text extraction (backward compatible)
     */
    suspend fun extractTextSimple(bitmap: Bitmap): String {
        return extractText(bitmap).cleanedText
    }
}