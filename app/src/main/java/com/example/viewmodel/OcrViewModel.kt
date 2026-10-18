package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SavedClipEntity
import com.example.ocr.NormalizedRect
import com.example.ocr.OcrDocument
import com.example.ocr.OcrEngine
import com.example.ocr.OcrGeometryUtils
import com.example.ocr.OcrItem
import com.example.ocr.SelectionGranularity
import com.example.ocr.SelectionTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen {
    HOME,
    CAMERA,
    SELECTION,
    RESULTS,
    SAVED_CLIPS
}

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val clipDao = db.clipDao()
    private val ocrEngine = OcrEngine(application)

    val savedClips: StateFlow<List<SavedClipEntity>> = clipDao.getAllClips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _ocrDocument = MutableStateFlow<OcrDocument?>(null)
    val ocrDocument: StateFlow<OcrDocument?> = _ocrDocument.asStateFlow()

    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr.asStateFlow()

    private val _ocrErrorMessage = MutableStateFlow<String?>(null)
    val ocrErrorMessage: StateFlow<String?> = _ocrErrorMessage.asStateFlow()

    private val _granularity = MutableStateFlow(SelectionGranularity.PARAGRAPH)
    val granularity: StateFlow<SelectionGranularity> = _granularity.asStateFlow()

    private val _selectionTool = MutableStateFlow(SelectionTool.TAP)
    val selectionTool: StateFlow<SelectionTool> = _selectionTool.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    private val undoStack = ArrayDeque<Set<String>>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _activeMarqueeRect = MutableStateFlow<NormalizedRect?>(null)
    val activeMarqueeRect: StateFlow<NormalizedRect?> = _activeMarqueeRect.asStateFlow()

    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText.asStateFlow()

    // Transform State
    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _pan = MutableStateFlow(Offset.Zero)
    val pan: StateFlow<Offset> = _pan.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun onImageSelected(uri: Uri) {
        _imageUri.value = uri
        _selectedItemIds.value = emptySet()
        undoStack.clear()
        _canUndo.value = false
        _activeMarqueeRect.value = null
        _zoom.value = 1f
        _pan.value = Offset.Zero
        _ocrErrorMessage.value = null
        _currentScreen.value = Screen.SELECTION

        processOcr(uri)
    }

    fun retryOcr() {
        val uri = _imageUri.value ?: return
        processOcr(uri)
    }

    private fun processOcr(uri: Uri) {
        viewModelScope.launch {
            _isProcessingOcr.value = true
            _ocrErrorMessage.value = null
            val result = ocrEngine.processImageUri(uri)
            _isProcessingOcr.value = false
            result.onSuccess { doc ->
                if (doc.blocks.isEmpty()) {
                    _ocrErrorMessage.value = "No text detected in this image. You can retake or choose another photo."
                }
                _ocrDocument.value = doc
            }.onFailure { err ->
                _ocrErrorMessage.value = err.localizedMessage ?: "Failed to process image OCR"
            }
        }
    }

    fun setGranularity(newGranularity: SelectionGranularity) {
        _granularity.value = newGranularity
        // Keep existing selections intact when switching granularity!
    }

    fun setSelectionTool(newTool: SelectionTool) {
        _selectionTool.value = newTool
        _activeMarqueeRect.value = null
    }

    private fun recordUndoState() {
        undoStack.addLast(_selectedItemIds.value)
        if (undoStack.size > 30) {
            undoStack.removeFirst()
        }
        _canUndo.value = true
    }

    fun undoLastSelection() {
        if (undoStack.isNotEmpty()) {
            _selectedItemIds.value = undoStack.removeLast()
            _canUndo.value = undoStack.isNotEmpty()
        }
    }

    fun selectLineAt(targetWord: OcrItem) {
        val doc = _ocrDocument.value ?: return
        recordUndoState()

        val targetLineId = targetWord.parentLineId ?: "line_${targetWord.blockIndex}_${targetWord.lineIndex}"
        val lineWords = doc.words.filter {
            it.parentLineId == targetLineId || (it.blockIndex == targetWord.blockIndex && it.lineIndex == targetWord.lineIndex)
        }
        val lineWordIds = lineWords.map { it.id }.toSet()
        if (lineWordIds.isEmpty()) return

        val current = _selectedItemIds.value.toMutableSet()
        if (current.containsAll(lineWordIds)) {
            current.removeAll(lineWordIds)
        } else {
            current.addAll(lineWordIds)
        }
        _selectedItemIds.value = current
    }

    fun toggleItemSelection(item: OcrItem) {
        val doc = _ocrDocument.value ?: return
        recordUndoState()

        val current = _selectedItemIds.value.toMutableSet()
        when (item.granularity) {
            SelectionGranularity.WORD -> {
                if (current.contains(item.id)) {
                    current.remove(item.id)
                } else {
                    current.add(item.id)
                }
            }
            SelectionGranularity.LINE -> {
                // Tapping line selects or deselects all words in that visual line
                val targetLineId = item.parentLineId ?: item.id
                val lineWords = doc.words.filter {
                    it.parentLineId == targetLineId || (it.blockIndex == item.blockIndex && it.lineIndex == item.lineIndex)
                }
                val lineWordIds = lineWords.map { it.id }.toSet()
                if (lineWordIds.isNotEmpty()) {
                    if (current.containsAll(lineWordIds)) {
                        current.removeAll(lineWordIds)
                    } else {
                        current.addAll(lineWordIds)
                    }
                }
            }
            SelectionGranularity.PARAGRAPH -> {
                // Tapping paragraph selects or deselects all words in that paragraph
                val blockWordIds = doc.words.filter { it.parentBlockId == item.id }.map { it.id }.toSet()
                if (blockWordIds.isNotEmpty()) {
                    if (current.containsAll(blockWordIds)) {
                        current.removeAll(blockWordIds)
                    } else {
                        current.addAll(blockWordIds)
                    }
                }
            }
        }
        _selectedItemIds.value = current
    }

    fun onMarqueeDragProgress(marquee: NormalizedRect) {
        _activeMarqueeRect.value = marquee
    }

    fun onMarqueeDragComplete(marquee: NormalizedRect) {
        _activeMarqueeRect.value = null
        val doc = _ocrDocument.value ?: return
        // Regardless of active granularity, rectangle drag selects only individual words overlapping the box
        val intersectedWords = OcrGeometryUtils.boxIntersectTest(marquee, doc.words)
        if (intersectedWords.isNotEmpty()) {
            recordUndoState()
            val current = _selectedItemIds.value.toMutableSet()
            intersectedWords.forEach { current.add(it.id) }
            _selectedItemIds.value = current
        }
    }

    fun selectAll() {
        val doc = _ocrDocument.value ?: return
        recordUndoState()
        _selectedItemIds.value = doc.words.map { it.id }.toSet()
    }

    fun clearSelection() {
        if (_selectedItemIds.value.isNotEmpty()) {
            recordUndoState()
            _selectedItemIds.value = emptySet()
        }
        _activeMarqueeRect.value = null
    }

    fun updateTransform(zoomDelta: Float, panDelta: Offset) {
        val newZoom = (_zoom.value * zoomDelta).coerceIn(1f, 5f)
        _zoom.value = newZoom
        if (newZoom == 1f) {
            _pan.value = Offset.Zero
        } else {
            _pan.value = _pan.value + panDelta
        }
    }

    fun resetZoom() {
        _zoom.value = 1f
        _pan.value = Offset.Zero
    }

    fun confirmSelection() {
        val doc = _ocrDocument.value ?: return
        val selectedWords = doc.words.filter { _selectedItemIds.value.contains(it.id) }
        val assembled = OcrGeometryUtils.assembleSelectedWords(selectedWords)
        _extractedText.value = assembled
        _currentScreen.value = Screen.RESULTS
    }

    fun saveCurrentClipToHistory(sourceNote: String = "Smart OCR Capture") {
        val text = _extractedText.value
        if (text.isBlank()) return

        viewModelScope.launch {
            clipDao.insertClip(
                SavedClipEntity(
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    sourceNote = sourceNote
                )
            )
            _statusMessage.value = "Saved to Clips History!"
        }
    }

    fun deleteClip(clip: SavedClipEntity) {
        viewModelScope.launch {
            clipDao.deleteClip(clip)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
    }
}
