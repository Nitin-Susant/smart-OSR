package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImageUri(uri: Uri): Result<OcrDocument> = withContext(Dispatchers.IO) {
        try {
            // Read dimensions and orientation safely
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val inputImage = InputImage.fromFilePath(context, uri)
            val visionText = processInputImage(inputImage)
            val rotation = inputImage.rotationDegrees
            val effectiveWidth = if (rotation == 90 || rotation == 270) inputImage.height else inputImage.width
            val effectiveHeight = if (rotation == 90 || rotation == 270) inputImage.width else inputImage.height
            val doc = convertVisionText(visionText, effectiveWidth, effectiveHeight)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun processBitmap(bitmap: Bitmap): Result<OcrDocument> = withContext(Dispatchers.IO) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = processInputImage(inputImage)
            val doc = convertVisionText(visionText, bitmap.width, bitmap.height)
            Result.success(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun processInputImage(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        continuation.resume(visionText)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }

    private fun convertVisionText(visionText: Text, imgWidth: Int, imgHeight: Int): OcrDocument {
        val safeWidth = imgWidth.coerceAtLeast(1)
        val safeHeight = imgHeight.coerceAtLeast(1)

        val blockItems = mutableListOf<OcrItem>()
        val lineItems = mutableListOf<OcrItem>()
        val wordItems = mutableListOf<OcrItem>()

        for ((bIdx, block) in visionText.textBlocks.withIndex()) {
            val bBox = block.boundingBox ?: Rect(0, 0, safeWidth, safeHeight)
            val normBBox = NormalizedRect.fromPixelRect(bBox, safeWidth, safeHeight)
            val blockId = "block_$bIdx"

            blockItems.add(
                OcrItem(
                    id = blockId,
                    text = block.text,
                    granularity = SelectionGranularity.PARAGRAPH,
                    normalizedBox = normBBox,
                    blockIndex = bIdx,
                    lineIndex = 0,
                    elementIndex = 0,
                    parentBlockId = blockId
                )
            )

            for ((lIdx, line) in block.lines.withIndex()) {
                val lBox = line.boundingBox ?: bBox
                val normLBox = NormalizedRect.fromPixelRect(lBox, safeWidth, safeHeight)
                val lineId = "line_${bIdx}_$lIdx"

                lineItems.add(
                    OcrItem(
                        id = lineId,
                        text = line.text,
                        granularity = SelectionGranularity.LINE,
                        normalizedBox = normLBox,
                        blockIndex = bIdx,
                        lineIndex = lIdx,
                        elementIndex = 0,
                        parentBlockId = blockId,
                        parentLineId = lineId
                    )
                )

                for ((eIdx, elem) in line.elements.withIndex()) {
                    val eBox = elem.boundingBox ?: lBox
                    val normEBox = NormalizedRect.fromPixelRect(eBox, safeWidth, safeHeight)
                    val wordId = "word_${bIdx}_${lIdx}_$eIdx"

                    wordItems.add(
                        OcrItem(
                            id = wordId,
                            text = elem.text,
                            granularity = SelectionGranularity.WORD,
                            normalizedBox = normEBox,
                            blockIndex = bIdx,
                            lineIndex = lIdx,
                            elementIndex = eIdx,
                            parentBlockId = blockId,
                            parentLineId = lineId
                        )
                    )
                }
            }
        }

        return OcrDocument(
            imageWidth = safeWidth,
            imageHeight = safeHeight,
            blocks = blockItems,
            lines = lineItems,
            words = wordItems,
            fullText = visionText.text
        )
    }

    fun close() {
        recognizer.close()
    }
}
