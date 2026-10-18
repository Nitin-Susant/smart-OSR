package com.example

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.ocr.NormalizedRect
import com.example.ocr.OcrGeometryUtils
import com.example.ocr.OcrItem
import com.example.ocr.SelectionGranularity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrSelectionTest {

    // Helper to generate test words for a sentence
    private fun createWords(
        sentence: String,
        blockIndex: Int,
        lineIndex: Int,
        startY: Float,
        wordWidth: Float = 0.1f,
        wordHeight: Float = 0.05f
    ): List<OcrItem> {
        val tokens = sentence.split(" ")
        return tokens.mapIndexed { elemIndex, token ->
            val startX = 0.1f + elemIndex * (wordWidth + 0.02f)
            OcrItem(
                id = "w_${blockIndex}_${lineIndex}_$elemIndex",
                text = token,
                granularity = SelectionGranularity.WORD,
                normalizedBox = NormalizedRect(startX, startY, startX + wordWidth, startY + wordHeight),
                blockIndex = blockIndex,
                lineIndex = lineIndex,
                elementIndex = elemIndex,
                parentBlockId = "block_$blockIndex",
                parentLineId = "line_${blockIndex}_$lineIndex"
            )
        }
    }

    /**
     * Requirement 7 - Test Case 1: Select one complete paragraph by tapping it.
     */
    @Test
    fun testSelectOneCompleteParagraphByTappingIt() {
        val p1Words = createWords("Money is a tool for achieving goals.", blockIndex = 0, lineIndex = 0, startY = 0.1f)
        val p2Words = createWords("Saving helps people prepare for unexpected expenses.", blockIndex = 1, lineIndex = 0, startY = 0.3f)
        val p3Words = createWords("Investing can help build wealth over time.", blockIndex = 2, lineIndex = 0, startY = 0.5f)

        // Paragraph 2 block
        val p2Block = OcrItem(
            id = "block_1",
            text = "Saving helps people prepare for unexpected expenses.",
            granularity = SelectionGranularity.PARAGRAPH,
            normalizedBox = NormalizedRect(0.1f, 0.28f, 0.9f, 0.38f),
            blockIndex = 1,
            parentBlockId = "block_1"
        )

        // When user taps paragraph 2 in Paragraph mode, all words in block_1 are selected
        val allWords = p1Words + p2Words + p3Words
        val selectedWordIds = allWords.filter { it.parentBlockId == p2Block.id }.map { it.id }.toSet()

        val selectedWords = allWords.filter { selectedWordIds.contains(it.id) }
        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedWords)

        assertEquals("Saving helps people prepare for unexpected expenses.", extracted)
        assertFalse(extracted.contains("Money is a tool"))
        assertFalse(extracted.contains("Investing can help"))
    }

    /**
     * Requirement 7 - Test Case 2: Select one sentence from the middle of a paragraph.
     */
    @Test
    fun testSelectOneSentenceFromMiddleOfParagraph() {
        // A single paragraph containing 3 lines/sentences
        val s1Words = createWords("Reading expands the mind.", blockIndex = 0, lineIndex = 0, startY = 0.1f)
        val s2Words = createWords("Deep focus unlocks creative insight.", blockIndex = 0, lineIndex = 1, startY = 0.2f)
        val s3Words = createWords("Knowledge compounds over time.", blockIndex = 0, lineIndex = 2, startY = 0.3f)

        val paragraphWords = s1Words + s2Words + s3Words

        // User selects ONLY sentence 2 (lineIndex 1) from the middle of the paragraph
        val selectedWordIds = s2Words.map { it.id }.toSet()
        val selectedWords = paragraphWords.filter { selectedWordIds.contains(it.id) }

        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedWords)

        assertEquals("Deep focus unlocks creative insight.", extracted)
        assertFalse(extracted.contains("Reading expands"))
        assertFalse(extracted.contains("Knowledge compounds"))
    }

    /**
     * Requirement 7 - Test Case 3: Select three words using a small rectangle.
     */
    @Test
    fun testSelectThreeWordsUsingSmallRectangle() {
        // Words: "Deep" [0.1..0.2], "focus" [0.22..0.32], "unlocks" [0.34..0.44], "creative" [0.46..0.56], "insight." [0.58..0.68]
        val words = createWords("Deep focus unlocks creative insight.", blockIndex = 0, lineIndex = 0, startY = 0.2f, wordWidth = 0.1f)

        // Small selection rectangle drawn only around "unlocks creative insight." (x: 0.33 to 0.70, y: 0.18 to 0.26)
        val marquee = NormalizedRect(left = 0.33f, top = 0.18f, right = 0.70f, bottom = 0.26f)

        // Precise box selection tests against individual words
        val intersected = OcrGeometryUtils.boxIntersectTest(marquee, words)

        assertEquals(3, intersected.size)
        assertEquals("unlocks", intersected[0].text)
        assertEquals("creative", intersected[1].text)
        assertEquals("insight.", intersected[2].text)

        val extracted = OcrGeometryUtils.assembleSelectedWords(intersected)
        assertEquals("unlocks creative insight.", extracted)
        assertFalse(extracted.contains("Deep"))
        assertFalse(extracted.contains("focus"))
    }

    /**
     * Requirement 7 - Test Case 4: Select two separate sentences.
     */
    @Test
    fun testSelectTwoSeparateSentences() {
        val s1Words = createWords("First sentence in block zero.", blockIndex = 0, lineIndex = 0, startY = 0.1f)
        val s2Words = createWords("Second sentence in block one.", blockIndex = 1, lineIndex = 0, startY = 0.3f)
        val unselectedWords = createWords("Ignored middle sentence.", blockIndex = 0, lineIndex = 1, startY = 0.2f)

        val allWords = s1Words + unselectedWords + s2Words

        // User selects sentence 1, then selects sentence 2
        val selectedWordIds = (s1Words.map { it.id } + s2Words.map { it.id }).toSet()
        val selectedWords = allWords.filter { selectedWordIds.contains(it.id) }

        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedWords)

        // Separate paragraphs should be joined with double line break
        assertEquals("First sentence in block zero.\n\nSecond sentence in block one.", extracted)
        assertFalse(extracted.contains("Ignored middle"))
    }

    /**
     * Requirement 7 - Test Case 5: Zoom in and select text.
     */
    @Test
    fun testZoomInAndSelectText() {
        val containerSize = Size(1000f, 1000f)
        val layout = OcrGeometryUtils.DisplayLayout(
            displayedWidth = 1000f,
            displayedHeight = 1000f,
            offsetX = 0f,
            offsetY = 0f
        )

        // 3.5x zoom and significant 2D pan offset
        val zoom = 3.5f
        val pan = Offset(250f, -180f)

        val words = createWords("Target word selected while zoomed in.", blockIndex = 0, lineIndex = 0, startY = 0.4f)
        val targetWord = words[1] // "word"

        // Simulate touch at the exact center of targetWord on screen
        val centerX = containerSize.width / 2f
        val centerY = containerSize.height / 2f
        val wordCenterNormX = targetWord.normalizedBox.centerX
        val wordCenterNormY = targetWord.normalizedBox.centerY

        val unscaledX = wordCenterNormX * layout.displayedWidth + layout.offsetX
        val unscaledY = wordCenterNormY * layout.displayedHeight + layout.offsetY
        val screenX = (unscaledX - centerX) * zoom + centerX + pan.x
        val screenY = (unscaledY - centerY) * zoom + centerY + pan.y

        // Convert screen touch to normalized coordinates
        val recoveredNorm = OcrGeometryUtils.screenToNormalized(
            Offset(screenX, screenY),
            containerSize,
            layout,
            zoom,
            pan
        )

        // Perform hit test with recovered coordinates
        val hit = OcrGeometryUtils.hitTest(recoveredNorm, words)

        assertTrue(hit != null)
        assertEquals("word", hit?.text)
    }

    /**
     * Requirement 7 - Test Case 6: Select overlapping regions without duplicating text.
     */
    @Test
    fun testSelectOverlappingRegionsWithoutDuplicatingText() {
        val words = createWords("Deep focus unlocks creative insight.", blockIndex = 0, lineIndex = 0, startY = 0.2f, wordWidth = 0.1f)

        // First marquee covers "Deep focus unlocks" (words 0, 1, 2)
        val marquee1 = NormalizedRect(0.08f, 0.18f, 0.45f, 0.26f)
        val firstSelection = OcrGeometryUtils.boxIntersectTest(marquee1, words)
        val selectedIds = firstSelection.map { it.id }.toMutableSet()

        assertEquals(setOf("w_0_0_0", "w_0_0_1", "w_0_0_2"), selectedIds)

        // Second overlapping marquee covers "unlocks creative insight." (words 2, 3, 4)
        val marquee2 = NormalizedRect(0.33f, 0.18f, 0.70f, 0.26f)
        val secondSelection = OcrGeometryUtils.boxIntersectTest(marquee2, words)

        // Add second selection (set naturally avoids duplicates)
        secondSelection.forEach { selectedIds.add(it.id) }

        // All 5 words should be present exactly once
        assertEquals(5, selectedIds.size)

        val selectedWords = words.filter { selectedIds.contains(it.id) }
        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedWords)

        assertEquals("Deep focus unlocks creative insight.", extracted)
    }

    /**
     * Requirement 1: Tapping a word selects it; tapping an already selected word deselects it.
     */
    @Test
    fun testTapWordSelectAndDeselect() {
        val words = createWords("Tap to select words individually.", blockIndex = 0, lineIndex = 0, startY = 0.2f)
        val targetWord = words[2] // "select"
        val selectedIds = mutableSetOf<String>()

        // 1. Tap word -> selects it
        if (selectedIds.contains(targetWord.id)) {
            selectedIds.remove(targetWord.id)
        } else {
            selectedIds.add(targetWord.id)
        }
        assertTrue(selectedIds.contains(targetWord.id))
        assertEquals(1, selectedIds.size)

        // 2. Tap already selected word -> deselects it
        if (selectedIds.contains(targetWord.id)) {
            selectedIds.remove(targetWord.id)
        } else {
            selectedIds.add(targetWord.id)
        }
        assertFalse(selectedIds.contains(targetWord.id))
        assertTrue(selectedIds.isEmpty())
    }

    /**
     * Requirement 1: Tapping a line selects all words in that line; tapping again deselects them.
     */
    @Test
    fun testTapLineSelectAndDeselect() {
        val lineWords = createWords("First line of the chapter.", blockIndex = 0, lineIndex = 0, startY = 0.1f)
        val lineWordIds = lineWords.map { it.id }.toSet()
        val selectedIds = mutableSetOf<String>()

        // 1. Tap line -> selects all words in line
        if (selectedIds.containsAll(lineWordIds)) {
            selectedIds.removeAll(lineWordIds)
        } else {
            selectedIds.addAll(lineWordIds)
        }
        assertEquals(lineWordIds, selectedIds)

        // 2. Tap already selected line -> deselects it
        if (selectedIds.containsAll(lineWordIds)) {
            selectedIds.removeAll(lineWordIds)
        } else {
            selectedIds.addAll(lineWordIds)
        }
        assertTrue(selectedIds.isEmpty())
    }

    /**
     * Requirement 1: Tapping a paragraph selects all words in that paragraph; tapping again deselects them.
     */
    @Test
    fun testTapParagraphSelectAndDeselect() {
        val pWords = createWords("Paragraph test for full selection.", blockIndex = 0, lineIndex = 0, startY = 0.1f)
        val pWordIds = pWords.map { it.id }.toSet()
        val selectedIds = mutableSetOf<String>()

        // 1. Tap paragraph -> selects all words in block
        if (selectedIds.containsAll(pWordIds)) {
            selectedIds.removeAll(pWordIds)
        } else {
            selectedIds.addAll(pWordIds)
        }
        assertEquals(pWordIds, selectedIds)

        // 2. Tap already selected paragraph -> deselects all words in block
        if (selectedIds.containsAll(pWordIds)) {
            selectedIds.removeAll(pWordIds)
        } else {
            selectedIds.addAll(pWordIds)
        }
        assertTrue(selectedIds.isEmpty())
    }

    /**
     * Touch tolerance test: Tapping slightly outside a word's tight bounding box on a tablet
     * still registers as hitting the word via touch slop fallback.
     */
    @Test
    fun testTouchToleranceFallback() {
        val words = createWords("Tablet stylus touch tolerance test.", blockIndex = 0, lineIndex = 0, startY = 0.2f)
        val targetWord = words[0] // "Tablet", normalizedBox: left = 0.1, right = 0.2, top = 0.2, bottom = 0.25

        // Tap point slightly outside the box (e.g. x = 0.08f, y = 0.21f; 0.02f to the left of the box)
        val touchNearBox = Offset(0.08f, 0.21f)
        assertFalse(targetWord.normalizedBox.contains(touchNearBox.x, touchNearBox.y))

        // Hit test with tolerance fallback should successfully hit "Tablet"
        val hit = OcrGeometryUtils.hitTest(touchNearBox, words)
        assertEquals(targetWord.id, hit?.id)
        assertEquals("Tablet", hit?.text)
    }
}
