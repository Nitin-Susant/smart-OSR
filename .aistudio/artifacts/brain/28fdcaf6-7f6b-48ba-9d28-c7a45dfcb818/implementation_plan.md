# Smart OCR Scanner for Android

A modern, offline-first Android application designed for high-precision text recognition and selective extraction from images. Users can capture documents via CameraX or import images via Android Photo Picker, view detected text regions overlaid with interactive neon bounding boxes, select specific words, lines, paragraphs, or rectangular regions, and extract, copy, export, and manage saved text clips.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during Phase 1 clarification:
> - **Selection Granularity**: Interactive toggle between **Word**, **Line**, and **Paragraph** selection hierarchy on tap, allowing instant micro or macro text targeting.
> - **Export & Persistence**: Export to standalone `.txt` files plus an on-device **Saved Clips History** (powered by local Room database) so extracted quotes and passages remain accessible anytime.
> - **Visual Overlays**: Semi-transparent neon highlight boxes (cyber-amber / electric-cyan tint) with subtle crisp outlines that remain razor-sharp during zoom and pan operations.

---

## 1. Overview & Core Concept

- **What It Does**: Smart OCR turns camera captures and photo imports into interactive text canvases. Instead of dumping a chaotic wall of OCR text, it enables users to tap individual words, lines, or paragraphs, or drag a selection box across any arbitrary area of the document to extract *only* the desired text in correct natural reading order.
- **Target Audience / Persona**: Students studying textbooks, professionals transcribing receipts and documents, researchers capturing snippets, and anyone needing selective text quotes without re-typing.
- **Key Value**: 100% on-device private processing (no cloud dependencies for OCR), pixel-perfect coordinate mapping that preserves selections across fluid pinch-to-zoom and pan gestures, and clean 1-tap clipboard/export workflows.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Home Screen**:
   - Clean, modern dashboard displaying the app identity ("Smart OCR"), quick-launch actions (**Take a Photo** with CameraX, **Choose Image** via Android Photo Picker), and a recent **Saved Clips** quick-access tray.
2. **Camera Capture Flow**:
   - CameraX full-screen viewfinder with document guide frame, flash toggle, and capture shutter.
   - Immediate post-capture review modal with **Retake** or **Continue to OCR** actions.
3. **Photo Picker Flow**:
   - Native Android zero-permission Photo Picker for seamless JPG, JPEG, and PNG selection.
4. **Interactive Text Selection Canvas**:
   - Asynchronous OCR processing with an elegant indeterminate progress scanner.
   - Dual-layer interactive Compose canvas:
     - Background: Full-fidelity image with hardware-accelerated smooth zoom (up to 5x) and 2D pan.
     - Foreground: Vector bounding box overlays for detected text.
   - **Control Dock**:
     - Mode switcher: **Tap Select** vs. **Drag Rectangle**.
     - Granularity switcher: **Word** | **Line** | **Paragraph**.
     - Action buttons: **Select All**, **Clear**, and **Confirm Selection** showing real-time selected word count badge.
5. **Extracted Results Screen**:
   - Formatted, clean text viewer displaying solely the selected text in natural reading order.
   - Actions: **Copy to Clipboard** (with animated confirmation toast), **Share**, **Export as TXT**, **Save to History**, and **Return to Image**.

### Visual Identity & Theme
- **Aesthetic Direction**: Precision Utilitarian & Modern Tech. Clean dark/light slate foundations with electric neon accents (Cyan `#00D2FF` and Amber `#FFB300`) for high-contrast visibility against diverse document backgrounds.
- **Color Palette**:
  - `Background`: Deep Obsidian (`#0F1115`) in Dark Mode / Crisp Polar (`#F8FAFC`) in Light Mode
  - `Surface`: Elevated Card Slate (`#1A1D24` / `#FFFFFF`) with subtle border strokes
  - `Primary Highlight`: Electric Amber (`#FFB703`) for selected text bounding boxes
  - `Detected Text Outline`: Soft Cyan (`#22D3EE` with 30% alpha fill)
  - `Selection Marquee`: Dynamic dashed border with gradient fill
- **Typography**: Clean, highly legible sans-serif hierarchy with monospaced metadata badges for character counts and bounding box dimensions.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Precise Image-to-Canvas Geometry & Coordinate Mapping
- **Chosen Approach**: Normalizing ML Kit bounding boxes to relative `[0..1]` unit coordinates based on the source image's intrinsic pixel dimensions (`width x height`). All touch events and zoom/pan scale matrices (`scale`, `offset`) are translated through an inverted transformation matrix to map screen touch coordinates to image coordinates before testing hit bounds.
- **Why**: Eliminates bounding box drift or distortion when rotating, scaling, or panning images of arbitrary aspect ratios on phones and tablets.
- **Alternatives Considered**: Screen-pixel coordinate baking (fails when screen is resized or rotated).

### Decision 2: Spatial Selection Engine (Tap & Drag Rectangle)
- **Chosen Approach**:
  - **Tap Selection**: Tests point-in-rect against the currently selected hierarchy level (element/word, line, or block/paragraph).
  - **Drag Selection**: Performs 2D AABB (Axis-Aligned Bounding Box) intersection between the drag rectangle and OCR items, selecting all contained or intersecting blocks.
  - **Order Sorting**: Sorts selected items top-to-bottom, left-to-right (`y` primary, `x` secondary) to guarantee natural reading order in multi-block selections.
- **Why**: Allows users to effortlessly grab a single line or sweep across a multi-sentence paragraph without frustrating edge misses.

### Decision 3: Local Storage Strategy
- **Chosen Approach**: Room Database for storing extracted text clips with timestamp, title preview, and character count; standard Android SAF/Media/cache file sharing for `.txt` export.
- **Why**: 100% offline, privacy-safe, zero network latency, and durable across app launches.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       Smart OCR App                         │
├──────────────────────────────┬──────────────────────────────┤
│          UI Layer            │       State Management       │
│  - HomeScreen                │   - OcrViewModel             │
│  - CameraScreen (CameraX)    │   - UiState (Idle, Scanning, │
│  - SelectionCanvasScreen     │     Ready, Selected, Error)  │
│  - ResultsScreen             │   - SelectionState (Items,   │
│  - SavedClipsScreen          │     TransformMatrix, Rect)   │
├──────────────────────────────┴──────────────────────────────┤
│                      Domain & Services                      │
│  - MlKitOcrEngine (Google ML Kit Text Recognition)          │
│  - GeometryUtils (Zoom/Pan Transforms & AABB Intersect)     │
│  - ReadingOrderSorter (Spatial column & line sorting)       │
├─────────────────────────────────────────────────────────────┤
│                     Data & Persistence                      │
│  - Room Database (ClipDao, SavedClipEntity)                 │
│  - LocalTxtExporter (Android SAF / Intent File Sharing)      │
└─────────────────────────────────────────────────────────────┘
```

### Component & State Mapping
- `OcrViewModel`: Holds active image URI/Bitmap, recognized hierarchical OCR model (`OcrDocument` containing `OcrBlock` -> `OcrLine` -> `OcrWord`), selected item IDs set (`Set<String>`), selection mode (`TAP` vs `RECTANGLE`), granularity (`WORD`, `LINE`, `PARAGRAPH`), and pan/zoom state.
- `SelectionCanvas`: Employs Jetpack Compose `pointerInput` with `detectTransformGestures` for pinch-zoom & pan when navigating, and `detectDragGestures` / `detectTapGestures` when selecting text.
- `MlKitOcrEngine`: Wraps `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` running on `Dispatchers.Default`.
