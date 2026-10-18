package com.example.ocr

import android.graphics.Rect

/**
 * Normalized 2D bounding rectangle in [0..1] coordinate space
 * relative to the source image width and height.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }

    fun intersects(other: NormalizedRect): Boolean {
        return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
    }

    fun distanceTo(x: Float, y: Float): Float {
        val dx = kotlin.math.max(0f, kotlin.math.max(left - x, x - right))
        val dy = kotlin.math.max(0f, kotlin.math.max(top - y, y - bottom))
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        fun fromPixelRect(rect: Rect, imgWidth: Int, imgHeight: Int): NormalizedRect {
            val safeWidth = imgWidth.coerceAtLeast(1).toFloat()
            val safeHeight = imgHeight.coerceAtLeast(1).toFloat()
            return NormalizedRect(
                left = (rect.left.toFloat() / safeWidth).coerceIn(0f, 1f),
                top = (rect.top.toFloat() / safeHeight).coerceIn(0f, 1f),
                right = (rect.right.toFloat() / safeWidth).coerceIn(0f, 1f),
                bottom = (rect.bottom.toFloat() / safeHeight).coerceIn(0f, 1f)
            )
        }
    }
}

enum class SelectionGranularity(val label: String) {
    WORD("Word"),
    LINE("Line"),
    PARAGRAPH("Paragraph")
}

enum class SelectionTool(val label: String) {
    TAP("Tap"),
    RECTANGLE("Box Select")
}

data class OcrItem(
    val id: String,
    val text: String,
    val granularity: SelectionGranularity,
    val normalizedBox: NormalizedRect,
    val blockIndex: Int,
    val lineIndex: Int = 0,
    val elementIndex: Int = 0,
    val parentBlockId: String,
    val parentLineId: String? = null
)

data class OcrDocument(
    val imageWidth: Int,
    val imageHeight: Int,
    val blocks: List<OcrItem>,
    val lines: List<OcrItem>,
    val words: List<OcrItem>,
    val fullText: String
) {
    fun getItemsForGranularity(granularity: SelectionGranularity): List<OcrItem> {
        return when (granularity) {
            SelectionGranularity.PARAGRAPH -> blocks
            SelectionGranularity.LINE -> lines
            SelectionGranularity.WORD -> words
        }
    }
}
