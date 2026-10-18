package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.ocr.NormalizedRect
import com.example.ocr.OcrGeometryUtils
import com.example.ocr.SelectionGranularity
import com.example.ocr.SelectionTool
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OcrDetectedBorder
import com.example.ui.theme.OcrDetectedFill
import com.example.ui.theme.OcrMarqueeBorder
import com.example.ui.theme.OcrMarqueeFill
import com.example.ui.theme.OcrSelectedBorder
import com.example.ui.theme.OcrSelectedFill
import com.example.viewmodel.OcrViewModel
import com.example.viewmodel.Screen

@Composable
fun SelectionCanvasScreen(
    viewModel: OcrViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUri by viewModel.imageUri.collectAsState()
    val ocrDoc by viewModel.ocrDocument.collectAsState()
    val isProcessing by viewModel.isProcessingOcr.collectAsState()
    val errorMessage by viewModel.ocrErrorMessage.collectAsState()
    val granularity by viewModel.granularity.collectAsState()
    val selectionTool by viewModel.selectionTool.collectAsState()
    val selectedIds by viewModel.selectedItemIds.collectAsState()
    val activeMarquee by viewModel.activeMarqueeRect.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val zoom by viewModel.zoom.collectAsState()
    val pan by viewModel.pan.collectAsState()

    BackHandler {
        viewModel.navigateTo(Screen.HOME)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val density = LocalDensity.current
            val containerSize = remember(maxWidth, maxHeight, density) {
                with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            }
            val doc = ocrDoc

            val displayLayout = remember(doc, containerSize) {
                if (doc != null) {
                    OcrGeometryUtils.calculateDisplayLayout(doc.imageWidth, doc.imageHeight, containerSize)
                } else {
                    OcrGeometryUtils.DisplayLayout(containerSize.width, containerSize.height, 0f, 0f)
                }
            }

            // Interactive Gestures Layer
            var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
            var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectionTool, displayLayout, zoom, pan, doc, granularity) {
                        detectCanvasGestures(
                            selectionTool = selectionTool,
                            isZoomed = zoom > 1.05f,
                            onTransform = { zoomDelta, panDelta ->
                                viewModel.updateTransform(zoomDelta, panDelta)
                            },
                            onDragStart = { startPt ->
                                val normStart = OcrGeometryUtils.screenToNormalized(
                                    startPt, containerSize, displayLayout, zoom, pan
                                )
                                dragStartOffset = normStart
                                dragCurrentOffset = normStart
                                viewModel.onMarqueeDragProgress(
                                    NormalizedRect(normStart.x, normStart.y, normStart.x, normStart.y)
                                )
                            },
                            onDrag = { curPt ->
                                val normCur = OcrGeometryUtils.screenToNormalized(
                                    curPt, containerSize, displayLayout, zoom, pan
                                )
                                dragCurrentOffset = normCur
                                val start = dragStartOffset
                                if (start != null) {
                                    val marquee = OcrGeometryUtils.createNormalizedRect(start, normCur)
                                    viewModel.onMarqueeDragProgress(marquee)
                                }
                            },
                            onDragEnd = { endPt ->
                                val start = dragStartOffset
                                val normEnd = OcrGeometryUtils.screenToNormalized(
                                    endPt, containerSize, displayLayout, zoom, pan
                                )
                                if (start != null) {
                                    val marquee = OcrGeometryUtils.createNormalizedRect(start, normEnd)
                                    viewModel.onMarqueeDragComplete(marquee)
                                }
                                dragStartOffset = null
                                dragCurrentOffset = null
                            },
                            onTap = { tapPt ->
                                val normPt = OcrGeometryUtils.screenToNormalized(
                                    tapPt, containerSize, displayLayout, zoom, pan
                                )
                                if (doc != null) {
                                    when (granularity) {
                                        SelectionGranularity.WORD -> {
                                            val hit = OcrGeometryUtils.hitTest(normPt, doc.words)
                                            if (hit != null) {
                                                viewModel.toggleItemSelection(hit)
                                            }
                                        }
                                        SelectionGranularity.LINE -> {
                                            // In Line mode, clicking on a word or line selects only that visual line
                                            val wordHit = OcrGeometryUtils.hitTest(normPt, doc.words)
                                            if (wordHit != null) {
                                                viewModel.selectLineAt(wordHit)
                                            } else {
                                                val lineHit = OcrGeometryUtils.hitTest(normPt, doc.lines)
                                                if (lineHit != null) {
                                                    viewModel.toggleItemSelection(lineHit)
                                                }
                                            }
                                        }
                                        SelectionGranularity.PARAGRAPH -> {
                                            val hit = OcrGeometryUtils.hitTest(normPt, doc.blocks)
                                            if (hit != null) {
                                                viewModel.toggleItemSelection(hit)
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Transformed Image and Canvas Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = pan.x,
                            translationY = pan.y
                        )
                ) {
                    // Document Image
                    imageUri?.let { uri ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Document to OCR",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Vector Bounding Box Overlay Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (doc != null && displayLayout.displayedWidth > 0f) {
                            val detectedItems = doc.getItemsForGranularity(granularity)

                            // 1. Draw subtle detected text boundaries for the active granularity
                            for (item in detectedItems) {
                                val rect = OcrGeometryUtils.normalizedToLayoutRect(item.normalizedBox, displayLayout)
                                drawRect(
                                    color = OcrDetectedFill,
                                    topLeft = rect.topLeft,
                                    size = rect.size
                                )
                                drawRect(
                                    color = OcrDetectedBorder,
                                    topLeft = rect.topLeft,
                                    size = rect.size,
                                    style = Stroke(width = 1.2f)
                                )
                            }

                            // 2. Draw highlighted selected individual words with vibrant neon amber
                            for (word in doc.words) {
                                if (selectedIds.contains(word.id)) {
                                    val wordRect = OcrGeometryUtils.normalizedToLayoutRect(word.normalizedBox, displayLayout)
                                    drawRect(
                                        color = OcrSelectedFill,
                                        topLeft = wordRect.topLeft,
                                        size = wordRect.size
                                    )
                                    drawRect(
                                        color = OcrSelectedBorder,
                                        topLeft = wordRect.topLeft,
                                        size = wordRect.size,
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                            }

                            // 3. Draw live active dragging marquee rectangle and intersecting words
                            activeMarquee?.let { marquee ->
                                val marqueeRect = OcrGeometryUtils.normalizedToLayoutRect(marquee, displayLayout)
                                drawRect(
                                    color = OcrMarqueeFill,
                                    topLeft = marqueeRect.topLeft,
                                    size = marqueeRect.size
                                )
                                drawRect(
                                    color = OcrMarqueeBorder,
                                    topLeft = marqueeRect.topLeft,
                                    size = marqueeRect.size,
                                    style = Stroke(
                                        width = 2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                    )
                                )

                                // Real-time preview of words covered by the drag box
                                for (word in doc.words) {
                                    if (!selectedIds.contains(word.id) && marquee.intersects(word.normalizedBox)) {
                                        val wRect = OcrGeometryUtils.normalizedToLayoutRect(word.normalizedBox, displayLayout)
                                        drawRect(
                                            color = OcrSelectedFill.copy(alpha = 0.25f),
                                            topLeft = wRect.topLeft,
                                            size = wRect.size
                                        )
                                        drawRect(
                                            color = NeonCyan,
                                            topLeft = wRect.topLeft,
                                            size = wRect.size,
                                            style = Stroke(width = 1.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Scanning Beam Progress Overlay
            if (isProcessing) {
                OcrScanningProgressOverlay()
            }

            // Error Card if OCR failed or zero text
            if (errorMessage != null && !isProcessing) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .widthIn(max = 480.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = errorMessage ?: "No text found",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.retryOcr() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                            Button(
                                onClick = { viewModel.navigateTo(Screen.HOME) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Pick Another Image")
                            }
                        }
                    }
                }
            }

            // Top Control Bar (Granularity & Zoom indicators)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(DarkNavy.copy(alpha = 0.95f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.HOME) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("button_selection_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to home",
                            tint = Color.White
                        )
                    }

                    // Zoom reset badge (if zoomed)
                    if (zoom > 1.05f) {
                        Surface(
                            onClick = { viewModel.resetZoom() },
                            color = NeonCyan.copy(alpha = 0.2f),
                            shape = CircleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterCenterFocus,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset ${String.format("%.1f", zoom)}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Pinch to Zoom",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Granularity Selector Chips (Word, Line, Paragraph)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SelectionGranularity.entries.forEach { gran ->
                                val isSelected = granularity == gran
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.setGranularity(gran) }
                                        .testTag("granularity_${gran.name.lowercase()}"),
                                    color = if (isSelected) NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = gran.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Selection Control Dock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, DarkNavy.copy(alpha = 0.96f), DarkNavy)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tools & Multi-select Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tool Toggle: Tap vs Box Select
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Tap Tool
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setSelectionTool(SelectionTool.TAP) }
                                    .testTag("tool_tap"),
                                color = if (selectionTool == SelectionTool.TAP) NeonAmber else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = if (selectionTool == SelectionTool.TAP) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.tap_to_select),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (selectionTool == SelectionTool.TAP) Color.Black else Color.White
                                    )
                                }
                            }

                            // Box Select Tool
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setSelectionTool(SelectionTool.RECTANGLE) }
                                    .testTag("tool_box_select"),
                                color = if (selectionTool == SelectionTool.RECTANGLE) NeonAmber else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CropSquare,
                                        contentDescription = null,
                                        tint = if (selectionTool == SelectionTool.RECTANGLE) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.drag_to_select),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (selectionTool == SelectionTool.RECTANGLE) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Bulk Actions: Undo, Select All & Clear
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Undo Last Selection
                        if (canUndo) {
                            IconButton(
                                onClick = { viewModel.undoLastSelection() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("button_undo_selection")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo last selection",
                                    tint = NeonAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.selectAll() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("button_select_all")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.select_all),
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (selectedIds.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("button_clear_selection")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_selection),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Primary Extract Button with Selection Count Badge
                val count = remember(selectedIds, activeMarquee, doc) {
                    val marquee = activeMarquee
                    if (marquee != null && doc != null) {
                        val intersectingIds = doc.words.filter { marquee.intersects(it.normalizedBox) }.map { it.id }
                        (selectedIds + intersectingIds).size
                    } else {
                        selectedIds.size
                    }
                }
                Button(
                    onClick = { viewModel.confirmSelection() },
                    enabled = count > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .height(54.dp)
                        .testTag("button_confirm_selection"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonAmber,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.15f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (count > 0) {
                                "${stringResource(R.string.confirm_selection)} ($count ${if (count == 1) "word" else "words"})"
                            } else {
                                "Select Text to Extract"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrScanningProgressOverlay() {
    val transition = rememberInfiniteTransition(label = "scanner")
    val scanFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        // Scanning laser bar
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * scanFraction
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        NeonCyan.copy(alpha = 0.8f),
                        NeonCyan,
                        NeonCyan.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 4.dp.toPx()
            )
        }

        // Center card with spinner
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkNavy.copy(alpha = 0.9f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = NeonCyan,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
                Text(
                    text = stringResource(R.string.scanning_image),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White
                )
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectCanvasGestures(
    selectionTool: SelectionTool,
    isZoomed: Boolean,
    onTransform: (zoomDelta: Float, panDelta: Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onTap: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var isMultiTouch = false
        var isDragging = false
        val touchSlop = viewConfiguration.touchSlop
        val startPos = down.position
        var currentPos = down.position
        var lastPos = down.position

        do {
            val event = awaitPointerEvent()
            val pressedChanges = event.changes.filter { it.pressed }
            val count = pressedChanges.size

            if (count >= 2) {
                // Multi-touch: Zoom and Pan
                isMultiTouch = true
                if (isDragging) {
                    isDragging = false
                    onDragEnd(currentPos)
                }
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onTransform(zoomChange, panChange)
                }
                event.changes.forEach { it.consume() }
            } else if (count == 1 && !isMultiTouch) {
                val change = pressedChanges.first()
                currentPos = change.position
                val dragDistance = (currentPos - startPos).getDistance()

                if (selectionTool == SelectionTool.RECTANGLE || !isZoomed) {
                    if (!isDragging && dragDistance > touchSlop) {
                        isDragging = true
                        onDragStart(startPos)
                    }
                    if (isDragging) {
                        onDrag(currentPos)
                        change.consume()
                    }
                } else {
                    // Tap tool: if zoomed in, allow smooth single-finger panning
                    if (isZoomed && dragDistance > touchSlop) {
                        val panDelta = currentPos - lastPos
                        onTransform(1f, panDelta)
                        change.consume()
                    }
                }
                lastPos = currentPos
            }
        } while (event.changes.any { it.pressed })

        // When pointer lifts
        if (isDragging) {
            onDragEnd(currentPos)
        } else if (!isMultiTouch) {
            val totalDistance = (currentPos - startPos).getDistance()
            if (totalDistance <= touchSlop) {
                onTap(startPos)
            }
        }
    }
}

