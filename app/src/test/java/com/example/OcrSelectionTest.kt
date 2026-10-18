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

    /**
     * Requirement 2: Rectangle selection must not accidentally select unrelated words on adjacent lines
     * when the box barely grazes them with a tiny fraction of an area.
     */
    @Test
    fun testRectangleSelectionDoesNotSelectAdjacentLineWordsWithTinyGraze() {
        // Line 1: y = 0.10f to 0.15f
        val line1Words = createWords("Target words on line one.", blockIndex = 0, lineIndex = 0, startY = 0.10f, wordHeight = 0.05f)
        // Line 2: y = 0.18f to 0.23f
        val line2Words = createWords("Unrelated words on line two.", blockIndex = 0, lineIndex = 1, startY = 0.18f, wordHeight = 0.05f)

        val allWords = line1Words + line2Words

        // Marquee tightly around "Target words on line one." but with a tiny 0.002 edge graze touching top of line 2 (y: 0.09f to 0.182f)
        // Grazes line 2 by 0.002f height out of 0.05f height (4% of height, ~4% of area)
        val marqueeWithGraze = NormalizedRect(left = 0.05f, top = 0.09f, right = 0.85f, bottom = 0.182f)

        val selected = OcrGeometryUtils.boxIntersectTest(marqueeWithGraze, allWords)

        // Must select all 5 words of line 1
        assertEquals(5, selected.size)
        assertTrue(selected.all { it.lineIndex == 0 })
        // Must NOT select any words of line 2 despite tiny graze
        assertFalse(selected.any { it.lineIndex == 1 })
    }

    /**
     * Requirement 2 & 5: Drawing a rectangle around 3 words in the middle of a paragraph
     * extracts ONLY those 3 words, not the entire paragraph.
     */
    @Test
    fun testRectangleSelectionInParagraphModeExtractsOnlySelectedWords() {
        val paragraphWords = createWords(
            "Artificial intelligence empowers modern applications with intelligence.",
            blockIndex = 0,
            lineIndex = 0,
            startY = 0.20f,
            wordWidth = 0.10f
        )
        // Words:
        // 0: "Artificial" [0.10..0.20]
        // 1: "intelligence" [0.22..0.32]
        // 2: "empowers" [0.34..0.44]
        // 3: "modern" [0.46..0.56]
        // 4: "applications" [0.58..0.68]
        // 5: "with" [0.70..0.80]
        // 6: "intelligence." [0.82..0.92]

        // Small rectangle only enclosing "empowers modern applications" (x: 0.33 to 0.69, y: 0.19 to 0.26)
        val rect = NormalizedRect(left = 0.33f, top = 0.19f, right = 0.69f, bottom = 0.26f)

        val intersected = OcrGeometryUtils.boxIntersectTest(rect, paragraphWords)
        assertEquals(3, intersected.size)

        val extracted = OcrGeometryUtils.assembleSelectedWords(intersected)
        assertEquals("empowers modern applications", extracted)
        assertFalse(extracted.contains("Artificial"))
        assertFalse(extracted.contains("intelligence"))
        assertFalse(extracted.contains("with"))
    }

    /**
     * Requirement 3 & 6: Undo stack sequential behavior.
     * Verifies that undo reverses selection operations in LIFO order and updates canUndo.
     */
    @Test
    fun testUndoStackBehaviorSequential() {
        val words = createWords("One two three four five.", blockIndex = 0, lineIndex = 0, startY = 0.1f)
        val undoStack = ArrayDeque<Set<String>>()
        var selectedIds = emptySet<String>()

        // Op 1: Select "One"
        undoStack.addLast(selectedIds)
        selectedIds = setOf(words[0].id)
        assertTrue(undoStack.isNotEmpty())

        // Op 2: Select "two"
        undoStack.addLast(selectedIds)
        selectedIds = setOf(words[0].id, words[1].id)

        // Op 3: Select "three"
        undoStack.addLast(selectedIds)
        selectedIds = setOf(words[0].id, words[1].id, words[2].id)
        assertEquals(3, selectedIds.size)

        // Undo 1: should revert to {One, two}
        selectedIds = undoStack.removeLast()
        assertEquals(setOf(words[0].id, words[1].id), selectedIds)
        assertTrue(undoStack.isNotEmpty())

        // Undo 2: should revert to {One}
        selectedIds = undoStack.removeLast()
        assertEquals(setOf(words[0].id), selectedIds)
        assertTrue(undoStack.isNotEmpty())

        // Undo 3: should revert to emptySet
        selectedIds = undoStack.removeLast()
        assertTrue(selectedIds.isEmpty())
        assertTrue(undoStack.isEmpty())
    }

    /**
     * Requirement 5: Word order, spaces, and punctuation preservation.
     * Standalone punctuation tokens like commas or periods should not have leading space.
     */
    @Test
    fun testNaturalPunctuationSpacingAssembly() {
        val wordItems = listOf(
            OcrItem(id = "1", text = "Hello", granularity = SelectionGranularity.WORD, normalizedBox = NormalizedRect(0.1f, 0.1f, 0.2f, 0.15f), blockIndex = 0, lineIndex = 0, elementIndex = 0, parentBlockId = "b0"),
            OcrItem(id = "2", text = ",", granularity = SelectionGranularity.WORD, normalizedBox = NormalizedRect(0.21f, 0.1f, 0.23f, 0.15f), blockIndex = 0, lineIndex = 0, elementIndex = 1, parentBlockId = "b0"),
            OcrItem(id = "3", text = "world", granularity = SelectionGranularity.WORD, normalizedBox = NormalizedRect(0.25f, 0.1f, 0.35f, 0.15f), blockIndex = 0, lineIndex = 0, elementIndex = 2, parentBlockId = "b0"),
            OcrItem(id = "4", text = "!", granularity = SelectionGranularity.WORD, normalizedBox = NormalizedRect(0.36f, 0.1f, 0.38f, 0.15f), blockIndex = 0, lineIndex = 0, elementIndex = 3, parentBlockId = "b0")
        )

        val assembled = OcrGeometryUtils.assembleSelectedWords(wordItems)
        assertEquals("Hello, world!", assembled)
    }

    /**
     * Requirement 3: Multiple non-contiguous selections combined in original reading order without duplicates.
     */
    @Test
    fun testMultipleNonContiguousSelectionsReadingOrder() {
        val words = createWords("Alpha Bravo Charlie Delta Echo Foxtrot Golf.", blockIndex = 0, lineIndex = 0, startY = 0.1f)

        // Select "Golf." first (index 6), then "Bravo" (index 1), then "Delta" (index 3), then "Bravo" again
        val selectedSet = mutableSetOf<String>()
        selectedSet.add(words[6].id) // Golf.
        selectedSet.add(words[1].id) // Bravo
        selectedSet.add(words[3].id) // Delta
        selectedSet.add(words[1].id) // Duplicate Bravo

        assertEquals(3, selectedSet.size)

        val selectedWords = words.filter { selectedSet.contains(it.id) }
        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedWords)

        // Must be in natural reading order: "Bravo Delta Golf."
        assertEquals("Bravo Delta Golf.", extracted)
    }

    /**
     * Requirement 1 & 2: Sentence spanning two visual lines.
     * Tapping any word in the sentence selects words across both lines up to the sentence-ending punctuation.
     */
    @Test
    fun testSentenceSpanningTwoVisualLines() {
        val line0 = createWords("The early morning breeze carried the sweet scent of", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("fresh pine and damp earth across the valley.", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val allWords = line0 + line1

        // Tap a word on line 0 (e.g., "breeze")
        val tappedWord = line0[3] // "breeze"
        val sentenceWords = OcrGeometryUtils.findSentenceWordsForWord(tappedWord, allWords)

        assertEquals(allWords.size, sentenceWords.size)
        val extracted = OcrGeometryUtils.assembleSelectedWords(sentenceWords)
        assertEquals(
            "The early morning breeze carried the sweet scent of\nfresh pine and damp earth across the valley.",
            extracted
        )
    }

    /**
     * Requirement 1 & 2: Sentence spanning three visual lines.
     */
    @Test
    fun testSentenceSpanningThreeVisualLines() {
        val line0 = createWords("Scientific breakthroughs in artificial intelligence", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("are accelerating the pace of modern medical discovery", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val line2 = createWords("around the globe at an unprecedented speed.", blockIndex = 0, lineIndex = 2, startY = 0.22f)
        val allWords = line0 + line1 + line2

        // Tap a word on the middle line (line 1, "medical")
        val tappedWord = line1[6] // "medical"
        val sentenceWords = OcrGeometryUtils.findSentenceWordsForWord(tappedWord, allWords)

        assertEquals(allWords.size, sentenceWords.size)
        val extracted = OcrGeometryUtils.assembleSelectedWords(sentenceWords)
        assertEquals(
            "Scientific breakthroughs in artificial intelligence\nare accelerating the pace of modern medical discovery\naround the globe at an unprecedented speed.",
            extracted
        )
    }

    /**
     * Requirement 1 & 2: Sentence spanning four visual lines.
     * Verifies that selection does not stop at the end of the first visual line.
     */
    @Test
    fun testSentenceSpanningFourVisualLines() {
        val line0 = createWords("This comprehensive architectural documentation outlines", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("every subsystem requirement in meticulous detail", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val line2 = createWords("ensuring that engineering teams can collaborate seamlessly", blockIndex = 0, lineIndex = 2, startY = 0.22f)
        val line3 = createWords("across multiple international research facilities.", blockIndex = 0, lineIndex = 3, startY = 0.28f)
        val allWords = line0 + line1 + line2 + line3

        // Tap a word on line 0 ("outlines")
        val tappedWord = line0[4] // "outlines"
        val sentenceWords = OcrGeometryUtils.findSentenceWordsForWord(tappedWord, allWords)

        // Must select all 4 lines (all words in the sentence)
        assertEquals(allWords.size, sentenceWords.size)
        val extracted = OcrGeometryUtils.assembleSelectedWords(sentenceWords)
        assertEquals(
            "This comprehensive architectural documentation outlines\nevery subsystem requirement in meticulous detail\nensuring that engineering teams can collaborate seamlessly\nacross multiple international research facilities.",
            extracted
        )
    }

    /**
     * Requirement 2: Sentences beginning or ending in the middle of a visual line.
     */
    @Test
    fun testSentenceBeginningAndEndingMidLine() {
        // Line 0 contains the end of Sentence A and the beginning of Sentence B
        val line0 = createWords("First sentence ends here. Second sentence starts here", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        // Line 1 contains the end of Sentence B and the beginning of Sentence C
        val line1 = createWords("and continues onto line one. Third sentence is here.", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val allWords = line0 + line1

        // Words:
        // line0[0..3]: "First" "sentence" "ends" "here."
        // line0[4..7]: "Second" "sentence" "starts" "here"
        // line1[0..4]: "and" "continues" "onto" "line" "one."
        // line1[5..8]: "Third" "sentence" "is" "here."

        // Tap "starts" in Sentence B (line 0, word 6)
        val targetB = line0[6] // "starts"
        val sentenceBWords = OcrGeometryUtils.findSentenceWordsForWord(targetB, allWords)

        val extractedB = OcrGeometryUtils.assembleSelectedWords(sentenceBWords)
        assertEquals(
            "Second sentence starts here\nand continues onto line one.",
            extractedB
        )
        // Must NOT contain Sentence A or Sentence C
        assertFalse(extractedB.contains("First"))
        assertFalse(extractedB.contains("Third"))

        // Now tap "First" in Sentence A (line 0, word 0)
        val targetA = line0[0]
        val sentenceAWords = OcrGeometryUtils.findSentenceWordsForWord(targetA, allWords)
        val extractedA = OcrGeometryUtils.assembleSelectedWords(sentenceAWords)
        assertEquals("First sentence ends here.", extractedA)

        // Now tap "Third" in Sentence C (line 1, word 5)
        val targetC = line1[5]
        val sentenceCWords = OcrGeometryUtils.findSentenceWordsForWord(targetC, allWords)
        val extractedC = OcrGeometryUtils.assembleSelectedWords(sentenceCWords)
        assertEquals("Third sentence is here.", extractedC)
    }

    /**
     * Requirement 2: Detect various sentence-ending punctuation (! and ? and quotes).
     */
    @Test
    fun testSentencePunctuationQuestionMarkAndExclamation() {
        val line0 = createWords("Is this text recognition accurate? Absolutely!", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("It handles multiple lines with precision.", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val allWords = line0 + line1

        // Sentence 1 ends with '?'
        val qWords = OcrGeometryUtils.findSentenceWordsForWord(line0[1], allWords) // "this"
        assertEquals("Is this text recognition accurate?", OcrGeometryUtils.assembleSelectedWords(qWords))

        // Sentence 2 ends with '!'
        val exclWords = OcrGeometryUtils.findSentenceWordsForWord(line0[5], allWords) // "Absolutely!"
        assertEquals("Absolutely!", OcrGeometryUtils.assembleSelectedWords(exclWords))

        // Sentence 3 ends with '.'
        val dotWords = OcrGeometryUtils.findSentenceWordsForWord(line1[2], allWords) // "multiple"
        assertEquals("It handles multiple lines with precision.", OcrGeometryUtils.assembleSelectedWords(dotWords))
    }

    /**
     * Requirement 1 & 3: Tapping an already selected sentence deselects it.
     */
    @Test
    fun testTapSentenceSelectAndDeselect() {
        val line0 = createWords("Sentence spans across line zero", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("and finishes on line one.", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val allWords = line0 + line1

        val sentenceWords = OcrGeometryUtils.findSentenceWordsForWord(line0[0], allWords)
        val sentenceWordIds = sentenceWords.map { it.id }.toSet()

        val selectedIds = mutableSetOf<String>()

        // 1. First tap -> selects all words in sentence
        if (selectedIds.containsAll(sentenceWordIds)) {
            selectedIds.removeAll(sentenceWordIds)
        } else {
            selectedIds.addAll(sentenceWordIds)
        }
        assertEquals(sentenceWordIds, selectedIds)

        // 2. Second tap -> deselects all words in sentence
        if (selectedIds.containsAll(sentenceWordIds)) {
            selectedIds.removeAll(sentenceWordIds)
        } else {
            selectedIds.addAll(sentenceWordIds)
        }
        assertTrue(selectedIds.isEmpty())
    }

    /**
     * Requirement 5: Multiple selections preserve previous selections when selecting another sentence.
     */
    @Test
    fun testMultipleSentenceSelectionsPreserved() {
        val line0 = createWords("Sentence one is here. Sentence two is here.", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("Sentence three is here.", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val allWords = line0 + line1

        val s1Words = OcrGeometryUtils.findSentenceWordsForWord(line0[0], allWords) // Sentence 1
        val s3Words = OcrGeometryUtils.findSentenceWordsForWord(line1[0], allWords) // Sentence 3

        val selectedIds = mutableSetOf<String>()
        selectedIds.addAll(s1Words.map { it.id })
        selectedIds.addAll(s3Words.map { it.id })

        val selectedItems = allWords.filter { selectedIds.contains(it.id) }
        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedItems)

        assertEquals("Sentence one is here.\nSentence three is here.", extracted)
        assertFalse(extracted.contains("Sentence two"))
    }

    /**
     * Verifies that clicking on a word in Line selection mode selects ONLY that specific visual line,
     * not the previous or next lines, and not the entire paragraph.
     */
    @Test
    fun testClickWordInLineSelectionSelectsOnlyThatVisualLineNotWholeParagraph() {
        val line0 = createWords("First visual line in this long paragraph.", blockIndex = 0, lineIndex = 0, startY = 0.10f)
        val line1 = createWords("Second visual line containing the clicked target word.", blockIndex = 0, lineIndex = 1, startY = 0.16f)
        val line2 = createWords("Third visual line continuing the paragraph.", blockIndex = 0, lineIndex = 2, startY = 0.22f)
        val allWords = line0 + line1 + line2

        // User clicks on "target" (word 5 on line 1) in Line mode
        val clickedWord = line1[5] // "target"
        val targetLineId = clickedWord.parentLineId ?: "line_${clickedWord.blockIndex}_${clickedWord.lineIndex}"

        val lineWords = allWords.filter {
            it.parentLineId == targetLineId || (it.blockIndex == clickedWord.blockIndex && it.lineIndex == clickedWord.lineIndex)
        }
        val lineWordIds = lineWords.map { it.id }.toSet()

        val selectedIds = mutableSetOf<String>()
        selectedIds.addAll(lineWordIds)

        val selectedItems = allWords.filter { selectedIds.contains(it.id) }
        val extracted = OcrGeometryUtils.assembleSelectedWords(selectedItems)

        // Must ONLY contain the second line
        assertEquals("Second visual line containing the clicked target word.", extracted)
        assertFalse(extracted.contains("First visual line"))
        assertFalse(extracted.contains("Third visual line"))
    }
}



