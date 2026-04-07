# Mahjong Shoujo — Architecture & Design

Offline Riichi Mahjong scoring app for Android.
CV abstraction layer designed for baseline-to-real-photo model replacement.

---

## 1. High-level pipeline

```
[Image / manual input]
        │
        ▼
[ImagePreprocessor]          ← letterbox + normalise   (cv:baseline, replaceable)
        │
        ▼
[BaselineAdapter]            ← TFLite interpreter run  (cv:baseline, replaceable)
        │
        ▼
[YoloPostProcessor]          ← decode + NMS + sort     (cv:baseline, replaceable)
        │
        ▼ TileRecognitionResult  ←── STABLE IR BOUNDARY
        │
        ▼
[CorrectionScreen / VM]      ← UI highlights low-confidence slots, user edits
        │ CorrectionLogger ──────────────────────► Room → JsonlCorrectionExporter
        ▼ RecognizedHand (confirmed)
        │
        ▼
[RoundContextScreen / VM]    ← winds, honba, riichi flags, dora
        │
        ▼
[ScoringEngineImpl]          ← deterministic, pure Kotlin, zero Android deps
        │
        ▼ ScoringResult
        │
        ▼
[ScoringResultScreen]        ← yaku list, han, fu, points, explanation
```

---

## 2. Module boundaries

```
mahjong_shoujo/
├── :domain              — pure Kotlin/JVM. Scoring engine, all domain models,
│                          RecognizedHand IR. No Android, no TFLite, no Hilt.
│                          Fully testable on JVM.
│
├── :cv:api              — Android library. Interfaces + IR data classes only.
│                          TileRecognitionEngine, TileId, DetectedTile,
│                          TileRecognitionResult, ModelInfo, CorrectionRecordExporter.
│                          No model-specific code, no TFLite import.
│
├── :cv:baseline         — Android library. The ONLY module that imports TFLite.
│                          BaselineAdapter, BaselineConfig, ImagePreprocessor,
│                          YoloPostProcessor, TileIdMapper.
│                          Everything here is replaceable with zero app-wide effect.
│
├── :correction          — Android library. CorrectionRecord (Room entity),
│                          CorrectionLogger, CorrectionDao, JsonlCorrectionExporter.
│                          The retraining data pipeline entry point.
│
└── :app                 — Android application. Compose UI, ViewModels, Hilt DI.
                           AppModule is the single model-replacement point.
                           The TileId → Tile mapper lives here (the only place
                           that sees both :cv:api and :domain).
```

**Dependency graph** (arrows = "depends on"):

```
:app         → :domain, :cv:api, :cv:baseline, :correction
:cv:baseline → :cv:api
:correction  → :cv:api
:domain      → (stdlib only)
:cv:api      → (stdlib + coroutines)
```

---

## 3. CV abstraction layer

All defined in `:cv:api`. All app/domain code depends only on these — never on
a concrete model class.

| Type | Purpose |
|---|---|
| `TileRecognitionEngine` | The single interface `:app` calls for inference |
| `TileId` | Stable 34-value enum — canonical tile identity |
| `RecognitionInput` | Sealed: `BitmapInput` / `UriInput` + `CaptureType` |
| `DetectedTile` | One detection: candidates, bbox, groupHint, positionIndex |
| `RecognitionCandidate` | `(tileId, confidence)` pair |
| `NormalizedBbox` | Coordinates in [0,1], model-agnostic |
| `ModelInfo` | modelId, version, architecture, trainingDomain |
| `TileRecognitionResult` | Full IR output of one recognition pass |
| `RecognitionOutcome` | Sealed `Success` / `Failure` — adapter must not throw |
| `CorrectionRecordExporter` | File-based export interface for future training |

```kotlin
interface TileRecognitionEngine {
    val modelInfo: ModelInfo
    suspend fun recognize(input: RecognitionInput): RecognitionOutcome
    fun isReady(): Boolean
    fun release()
}
```

**Implementations:**
- `BaselineAdapter` — wraps mahjong-utils-app TFLite detector (Phase 1)
- `ManualTileRecognitionEngine` — returns empty result, for Phase 0 manual-only flow (implicit: engine never loaded)
- `RealPhotoAdapter` — fine-tuned model (Phase 3+, not yet created)

---

## 4. Stable intermediate representation

`TileRecognitionResult` is the IR boundary. Survives model replacement because:

- `TileId` is canonical (never a raw model class index)
- `NormalizedBbox` is geometry, not pixel coordinates
- `ModelInfo` is logged but never used for decisions downstream
- `debugMetadata: Map<String,String>` absorbs adapter-specific noise without polluting the schema

`RecognizedHand` (`:domain`) is the post-correction IR — what flows into the scoring engine.
It records `modelSuggestion`, `modelConfidence`, `confirmedTile`, and `wasCorrected` per slot.

```kotlin
// cv:api — raw CV output
data class TileRecognitionResult(
    val tiles: List<DetectedTile>,
    val modelInfo: ModelInfo,
    val captureType: CaptureType,
    val processingTimeMs: Long,
    val debugMetadata: Map<String, String> = emptyMap(),
)

// domain — post-correction, pre-scoring
data class RecognizedHand(
    val slots: List<TileSlot>,
    val isComplete: Boolean,
)

data class TileSlot(
    val index: Int,
    val modelSuggestion: Tile?,
    val modelConfidence: Float?,
    val confirmedTile: Tile?,
    val wasCorrected: Boolean,
    val isWinningTile: Boolean,
    val meldIndex: Int?,
)
```

---

## 5. Baseline adapter plan

### What is wrapped (all inside `:cv:baseline`, nowhere else)

| Class | Responsibility |
|---|---|
| `BaselineConfig` | All baseline-specific constants (model path, input size, thresholds) |
| `BaselineAdapter` | TFLite interpreter lifecycle; dispatches to `Dispatchers.Default` |
| `ImagePreprocessor` | Letterbox resize + float normalisation |
| `YoloPostProcessor` | YOLO tensor decode + NMS + de-letterbox |
| `TileIdMapper` | The only place class indices exist; maps index → `TileId` |

### Screenshot-domain assumptions (explicitly flagged in `BaselineAdapter.kt`)

- `[SCREENSHOT-ASSUMPTION]` Input images are clean, high-contrast digital renders
- `[SCREENSHOT-ASSUMPTION]` Background is always uniform (dark, Majsoul-style)
- `[SCREENSHOT-ASSUMPTION]` Tiles are horizontally aligned and evenly spaced
- `[SCREENSHOT-ASSUMPTION]` No blur, noise, or perspective distortion expected

### What stays replaceable

- `TileIdMapper.BASELINE_CLASS_MAP` is the only class-index mapping; new model = new map
- Every constant in `BaselineConfig` has a `TODO(Phase 1)` confirming it must be verified
  against the actual mahjong-utils-app assets before use
- Phase 3: create `RealPhotoAdapter : TileRecognitionEngine`, change one line in `AppModule`

---

## 6. Manual correction flow

1. `CorrectionScreen` renders one `TileSlotCard` per detected tile, in spatial order
2. A card shows `modelSuggestion` text; **red border + warning icon** if `isLowConfidence` (confidence < 0.70)
3. Tapping opens `TilePicker` (TODO Phase 1: full 34-tile grid by suit)
4. Selecting a tile sets `confirmedTile`; `wasCorrected = confirmedTile ≠ modelSuggestion`
5. **"Confirm Hand"** bulk-accepts remaining unedited slots; only enabled when all confirmed
6. The app never auto-proceeds past this screen — the user always has the final word

Design principle: **the app must never pretend certainty when the model is unsure.**
Low-confidence slots are always highlighted; the user can never be "surprised" by a wrong tile.

---

## 7. Correction logging for future retraining

### Schema (`CorrectionRecord` — Room entity, JSONL export)

```
id                      long         auto-generated primary key
timestamp_ms            long         unix epoch ms of user correction
image_hash              string       SHA-256 of source image bytes (dedup key)
image_path              string?      local device path of saved source image
capture_type            enum         SCREENSHOT | CAMERA_PHOTO | GALLERY_PHOTO
bbox_{left,top,right,bottom}  float? normalised [0,1] detection box
model_id                string       e.g. "baseline-majsoul-v1"
model_version           string       e.g. "1.0.0-baseline"
model_architecture      string       e.g. "yolov5"
predicted_tile_id       enum?        model's top-1 prediction, or null if no detection
predicted_confidence    float?       confidence of top-1 prediction
top_k_candidates_json   string?      JSON array of top-k alternatives
corrected_tile_id       enum         ground-truth label confirmed by user  ← THE LABEL
was_model_wrong         bool         corrected_tile_id ≠ predicted_tile_id
is_exported             bool         false until written to disk by exporter
```

### Export format (JSONL — one JSON object per line)

```json
{"id":42,"timestamp_ms":1700000000000,"image_hash":"sha256...","image_path":"/storage/.../photo.jpg",
 "capture_type":"CAMERA_PHOTO","bbox":{"left":0.12,"top":0.45,"right":0.18,"bottom":0.65},
 "model_id":"baseline-majsoul-v1","model_version":"1.0.0-baseline","model_architecture":"yolov5",
 "predicted_tile":"MAN_3","predicted_confidence":0.81,
 "top_k_candidates":[{"tile":"MAN_3","confidence":0.81},{"tile":"MAN_2","confidence":0.09}],
 "corrected_tile":"MAN_4","was_model_wrong":true}
```

### Usage in a training script

```python
records = [json.loads(l) for l in open("corrections.jsonl")]

# Filter to real-photo corrections only (target domain)
real_photo_errors = [
    r for r in records
    if r["capture_type"] == "CAMERA_PHOTO" and r["was_model_wrong"]
]

# Crop tiles by bbox, label with corrected_tile
for r in real_photo_errors:
    img   = Image.open(r["image_path"])
    w, h  = img.size
    box   = r["bbox"]
    crop  = img.crop((box["left"]*w, box["top"]*h, box["right"]*w, box["bottom"]*h))
    label = r["corrected_tile"]   # e.g. "MAN_4"
    # save crop + label → training dataset
```

**Phase 3 TODO:** add image crop export (save each bbox crop as JPEG alongside JSONL).
**Phase 3 TODO:** add zip export mode for easy device → workstation transfer.

---

## 8. Scoring engine boundary

```kotlin
interface ScoringEngine {
    fun score(hand: Hand, context: RoundContext): ScoringResult
}
```

- **Pure function** — same inputs, same output, always
- No coroutines, no I/O, no Android runtime dependency
- Knows nothing about CV, camera, models, or capture type
- Takes only `Hand` (domain type) + `RoundContext` (domain type)
- Fully testable on the JVM without a device

### `ScoringEngineImpl` internal structure

Implement each helper independently, test-first, one at a time:

| Helper | What it does |
|---|---|
| `decompose(hand)` | Enumerate all valid 4-meld + 1-pair partitions |
| `decomposeChiitoitsu(hand)` | 7-pair decomposition |
| `decomposeKokushi(hand)` | Kokushi musou check |
| `detectYaku(decomp, hand, context)` | Yaku detection over a given decomposition |
| `calculateFu(decomp, hand, context)` | Fu breakdown per decomposition |
| `countDora(hand, context)` | Indicator → actual dora tile mapping |
| `lookupPoints(han, fu, isDealer, isTsumo)` | Standard point table lookup |

Start with `tanyao`, `pinfu`, and `riichi`; add one yaku at a time with a failing test first.

---

## 9. Phased implementation plan

### Phase 0 — Manual input + scoring engine

**Goal:** App builds, runs, and can score a hand entered manually.

1. Implement `ScoringEngineImpl` helpers (test-first, one yaku at a time)
2. Wire `RoundContextViewModel.onSubmit()` → scoring engine → shared result holder → `ScoringResultScreen`
3. Add a dora tile picker to `RoundContextScreen`
4. Add manual tile entry UI to `MainScreen` → produces `RecognizedHand` with all slots user-confirmed
5. The "Load image" button remains grayed out (model not ready)

**Safe to build now.** All of `:domain` and the `:app` manual path.

---

### Phase 1 — Plug in baseline adapter

**Goal:** App can load a screenshot, recognize tiles, prefill the correction screen.

1. Copy `.tflite` asset from `mahjong-utils-app` → `cv/baseline/src/main/assets/models/`
2. Fill in `TileIdMapper.BASELINE_CLASS_MAP` from the model's label file
3. Implement `ImagePreprocessor.letterbox()` and `toModelInput()`
4. Implement `YoloPostProcessor.process()` and `nms()`
5. Implement `BaselineAdapter.load()` and the full inference path
6. Call `adapter.load()` in `AppModule.provideTileRecognitionEngine()`
7. Implement the full `TilePicker` composable (34-tile grid)
8. Wire `MainViewModel` → `CorrectionViewModel` via a Hilt-scoped result holder

**Model-agnostic parts unchanged:** IR types, `TileRecognitionEngine` interface, all of `:domain`, all of `:correction`.

---

### Phase 2 — Correction logging + export

**Goal:** Every user correction is persisted and exportable for future training.

1. Compute SHA-256 of source image in `MainViewModel`, propagate with result
2. Save source image to a private app directory, store path in result
3. Complete `CorrectionViewModel.onTileSelected()` wiring to `CorrectionLogger`
4. Add settings screen with "Export correction data" button → `JsonlCorrectionExporter`
5. Add image crop export (save each bbox crop as JPEG alongside JSONL)

---

### Phase 3 — Transfer-learning-ready replacement path

**Goal:** Real-photo fine-tuned model replaces baseline with one line changed in `AppModule`.

1. Implement `RealPhotoAdapter : TileRecognitionEngine` (new class, new `:cv:realphoto` module)
2. Change `AppModule.provideTileRecognitionEngine()` to return it — **one line**
3. Use Phase 2 JSONL exports + image crops as fine-tuning training data
4. Validate with `ModelInfo.trainingDomain == REAL_PHOTO` shown in the UI model-info label

---

## 10. Important flags

### Safe to build now for Phase 0 MVP

- All of `:domain` (Tile, Hand, RoundContext, ScoringResult, Yaku, ScoringEngine)
- `RoundContextScreen` + `RoundContextViewModel`
- `ScoringResultScreen` + `ScoringResultViewModel`
- `CorrectionRecord` schema and `CorrectionLogger` (even without real CV data)
- Manual tile entry path through `MainScreen`

### Must remain model-agnostic forever

- `TileRecognitionEngine` interface
- `TileId`, `DetectedTile`, `TileRecognitionResult`, `ModelInfo`
- `RecognizedHand` / `TileSlot`
- `ScoringEngine` interface and all of `:domain`
- `CorrectionRecord` schema and `CorrectionRecordExporter`
- `AppModule.provideTileRecognitionEngine()` — binding type must stay `TileRecognitionEngine`

### Hooks reserved for Phase 3 fine-tuning on real-photo target-domain data

- `CorrectionRecordExporter` interface (file-based, training-script-consumable from day one)
- `JsonlCorrectionExporter.exportPending()` + image crop TODO
- `ModelInfo.trainingDomain` — lets you filter correction records by source domain
- `CorrectionRecord.capture_type` — lets you separate screenshot vs real-photo training examples
- `BaselineConfig.MODEL_INFO.modelId` — lets you bucket training data by which model made predictions
- `[SCREENSHOT-ASSUMPTION]` comments in `BaselineAdapter` — explicit checklist of what changes for real-photo input
