package com.example.ocr

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.max
import kotlin.math.min

object OcrGeometryUtils {

    /**
     * Calculates the fitted image display rectangle (like ContentScale.Fit) inside the container.
     */
    data class DisplayLayout(
        val displayedWidth: Float,
        val displayedHeight: Float,
        val offsetX: Float,
        val offsetY: Float
    )

    fun calculateDisplayLayout(
        imageWidth: Int,
        imageHeight: Int,
        containerSize: Size
    ): DisplayLayout {
        if (imageWidth <= 0 || imageHeight <= 0 || containerSize.width <= 0 || containerSize.height <= 0) {
            return DisplayLayout(containerSize.width, containerSize.height, 0f, 0f)
        }

        val scale = min(
            containerSize.width / imageWidth.toFloat(),
            containerSize.height / imageHeight.toFloat()
        )
        val dispW = imageWidth * scale
        val dispH = imageHeight * scale
        val offX = (containerSize.width - dispW) / 2f
        val offY = (containerSize.height - dispH) / 2f

        return DisplayLayout(
            displayedWidth = dispW,
            displayedHeight = dispH,
            offsetX = offX,
            offsetY = offY
        )
    }

    /**
     * Converts a screen touch point into normalized image coordinates [0..1]
     * taking into account user zoom, pan, and letterbox offset.
     */
    fun screenToNormalized(
        touchPoint: Offset,
        containerSize: Size,
        layout: DisplayLayout,
        zoom: Float,
        pan: Offset
    ): Offset {
        val centerX = containerSize.width / 2f
        val centerY = containerSize.height / 2f

        // Reverse graphicsLayer transform (origin at center)
        val untransformedX = (touchPoint.x - pan.x - centerX) / zoom + centerX
        val untransformedY = (touchPoint.y - pan.y - centerY) / zoom + centerY

        // Reverse letterbox offset
        val normX = ((untransformedX - layout.offsetX) / layout.displayedWidth.coerceAtLeast(1f))
        val normY = ((untransformedY - layout.offsetY) / layout.displayedHeight.coerceAtLeast(1f))

        return Offset(normX, normY)
    }

    /**
     * Converts normalized [0..1] coordinates into layout pixel space (before user zoom/pan).
     */
    fun normalizedToLayoutRect(
        normRect: NormalizedRect,
        layout: DisplayLayout
    ): androidx.compose.ui.geometry.Rect {
        val left = layout.offsetX + normRect.left * layout.displayedWidth
        val top = layout.offsetY + normRect.top * layout.displayedHeight
        val right = layout.offsetX + normRect.right * layout.displayedWidth
        val bottom = layout.offsetY + normRect.bottom * layout.displayedHeight
        return androidx.compose.ui.geometry.Rect(left, top, right, bottom)
    }

    /**
     * Performs a tap hit-test against items in the active granularity.
     * If multiple overlap, returns the one with the smallest area (highest precision).
     */
    fun hitTest(
        normPoint: Offset,
        items: List<OcrItem>
    ): OcrItem? {
        val matching = items.filter { item ->
            item.normalizedBox.contains(normPoint.x, normPoint.y)
        }
        if (matching.isNotEmpty()) {
            return matching.minByOrNull { it.normalizedBox.width * it.normalizedBox.height }
        }
        // Fallback for tablet touch & stylus tolerance: find nearest item within ~3.5% normalized distance
        val maxSlop = 0.035f
        val nearby = items.mapNotNull { item ->
            val dist = item.normalizedBox.distanceTo(normPoint.x, normPoint.y)
            if (dist <= maxSlop) item to dist else null
        }
        return nearby.minByOrNull { it.second }?.first
    }

    /**
     * Tests which items intersect with the normalized selection rectangle.
     */
    fun boxIntersectTest(
        selectionRect: NormalizedRect,
        items: List<OcrItem>
    ): List<OcrItem> {
        return items.filter { item ->
            selectionRect.intersects(item.normalizedBox)
        }
    }

    /**
     * Creates a normalized rectangle from two diagonal corner points.
     */
    fun createNormalizedRect(p1: Offset, p2: Offset): NormalizedRect {
        val left = min(p1.x, p2.x).coerceIn(0f, 1f)
        val right = max(p1.x, p2.x).coerceIn(0f, 1f)
        val top = min(p1.y, p2.y).coerceIn(0f, 1f)
        val bottom = max(p1.y, p2.y).coerceIn(0f, 1f)
        return NormalizedRect(left, top, right, bottom)
    }

    /**
     * Combines selected words in natural reading order without duplicate text.
     * Preserves sentence spacing, line breaks, paragraph breaks, and punctuation.
     */
    fun assembleSelectedWords(words: Collection<OcrItem>): String {
        if (words.isEmpty()) return ""

        val sorted = words.sortedWith(
            compareBy<OcrItem> { it.blockIndex }
                .thenBy { it.lineIndex }
                .thenBy { it.elementIndex }
        )

        val sb = StringBuilder()
        var lastLine = -1
        var lastBlock = -1

        for (word in sorted) {
            if (lastBlock != -1) {
                if (word.blockIndex != lastBlock) {
                    sb.append("\n\n")
                } else if (word.lineIndex != lastLine) {
                    sb.append("\n")
                } else {
                    sb.append(" ")
                }
            }
            sb.append(word.text.trim())
            lastBlock = word.blockIndex
            lastLine = word.lineIndex
        }

        return sb.toString()
    }

    /**
     * Combines selected items in natural reading order without duplicate text.
     */
    fun assembleSelectedText(
        selectedItems: Collection<OcrItem>,
        granularity: SelectionGranularity
    ): String {
        if (selectedItems.isEmpty()) return ""

        // If items are words, use word-level assembly
        if (selectedItems.firstOrNull()?.granularity == SelectionGranularity.WORD) {
            return assembleSelectedWords(selectedItems)
        }

        val sorted = selectedItems.sortedWith(
            compareBy<OcrItem> { it.blockIndex }
                .thenBy { it.lineIndex }
                .thenBy { it.elementIndex }
        )

        return when (granularity) {
            SelectionGranularity.PARAGRAPH -> {
                sorted.joinToString(separator = "\n\n") { it.text.trim() }
            }
            SelectionGranularity.LINE -> {
                val sb = StringBuilder()
                var lastBlock = -1
                for (item in sorted) {
                    if (lastBlock != -1) {
                        if (item.blockIndex != lastBlock) {
                            sb.append("\n\n")
                        } else {
                            sb.append("\n")
                        }
                    }
                    sb.append(item.text.trim())
                    lastBlock = item.blockIndex
                }
                sb.toString()
            }
            SelectionGranularity.WORD -> {
                assembleSelectedWords(selectedItems)
            }
        }
    }
}
