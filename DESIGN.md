# Mahjong Shoujo — Revised Architecture & Design

Offline Riichi Mahjong scoring app for Android.  
This document supersedes the original DESIGN.md. It preserves all core architectural decisions
while fixing six weaknesses discovered during review: akadora representation, IR enrichment,
correction schema, `:cv:api` purity, evaluation strategy, and open-meld boundary.

---

## 1. What stays unchanged

These decisions are settled and not revisited here:

- **Multi-module structure** — `:domain`, `:cv:api`, `:cv:baseline`, `:correction`, `:app`
- **Scoring engine** — deterministic, pure Kotlin, zero Android dependencies, fully JVM-testable
- **Baseline model is a bootstrap** — screenshot-domain only; the architecture is designed for replacement
- **Correction logging is a first-class retraining hook** — not an afterthought
- **App/domain code depends on CV abstractions, not on any concrete model class**
- **`AppModule` is the single model-wiring/replacement point**
- **`TileRecognitionEngine` interface** — the only thing `:app` ever calls for inference
- **`TileId` is the stable 34-value canonical tile identity** — never a raw class index

The high-level pipeline is also unchanged:

```
[Image / manual input]
        │
        ▼
[RecognitionInput (platform-neutral)]
        │
        ▼
[TileRecognitionEngine.recognize()]   ← adapter-specific: baseline, real-photo, manual
        │
        ▼  TileRecognitionResult      ←── STABLE IR BOUNDARY
        │
        ▼
[CorrectionScreen / VM]               ← user confirms/corrects, CorrectionLogger persists
        │
        ▼  RecognizedHand (confirmed)
        │
        ▼
[RoundContextScreen / VM]             ← winds, honba, riichi flags, dora
        │
        ▼
[ScoringEngineImpl]                   ← deterministic, pure Kotlin
        │
        ▼  ScoringResult
        │
        ▼
[ScoringResultScreen]
```

---

## 2. Module boundaries (unchanged structure, one revised constraint)

```
mahjong_shoujo/
├── :domain              — pure Kotlin/JVM. Scoring engine, all domain models,
│                          RecognizedHand IR. No Android, no TFLite, no Hilt.
│
├── :cv:api              — pure Kotlin/JVM module (see §5 for rationale).
│                          Interfaces + IR data classes only.
│                          Uses ByteArray-based input, NOT android.graphics.Bitmap.
│                          No Android SDK dependency.
│
├── :cv:baseline         — Android library. The ONLY module that imports TFLite.
│                          Owns Bitmap→ByteArray conversion, adapter, postprocessor,
│                          TileIdMapper. Everything here is replaceable.
│
├── :correction          — Android library. Room entity, CorrectionLogger, Dao,
│                          JsonlCorrectionExporter. Retraining pipeline entry point.
│
└── :app                 — Android application. Compose UI, ViewModels, Hilt DI.
                           AppModule is the single model-replacement point.
                           TileId → Tile mapper lives here.
```

**Dependency graph** (arrows = "depends on"):

```
:app         → :domain, :cv:api, :cv:baseline, :correction
:cv:baseline → :cv:api
:correction  → :cv:api
:domain      → (stdlib only)
:cv:api      → (stdlib + coroutines — no Android SDK)
```

---

## 3. Revised tile identity design

### 3.1 The problem with extra enum values for akadora

Adding `MAN_5_RED`, `PIN_5_RED`, `SOU_5_RED` as permanent members of `TileId` (or `Tile`)
is wrong for two reasons:

1. **It encodes a training artifact in the domain model.** The baseline model may output
   a separate class index for red fives because of how its label set was assembled.
   A future model might output the same class index for 5m and rely on a separate channel
   or a secondary head to flag red-dora. The domain should not have to change when the
   model does.

2. **It conflates tile identity (what the tile is) with tile variant (a scoring modifier).**
   A red 5-man is still a 5-man for purposes of set detection, waits, and all structural
   yaku. It is only distinct for han counting. Baking the distinction into the enum leaks
   a scoring rule into the CV type system.

### 3.2 What stays stable across model replacement

`TileId` remains a 34-value enum (plus `UNKNOWN`). It encodes *what tile this is*,
not whether it is a variant. This never changes regardless of model.

### 3.3 The akadora flag

Red-dora status is a Boolean flag carried alongside the tile identity, independently,
at two levels:

**In `:cv:api` (the IR):** `DetectedTile` gains an `isAkadora: Boolean` field.
The adapter sets this flag when its `TileIdMapper` determines the detected class index
corresponds to a red five. This logic lives entirely inside the mapper — the rest of
the app never sees class indices.

**In `:domain` (the scoring type):** `Tile.NumberTile` gains an `isAkadora: Boolean`
field (defaulting to `false`). The scoring engine reads it when counting red dora.

```kotlin
// cv:api — IR level
data class DetectedTile(
    val candidates: List<RecognitionCandidate>,
    val bbox: NormalizedBbox?,
    val isAkadora: Boolean = false,          // new: set by adapter mapper
    // ... other fields
)

// domain — scoring level
data class NumberTile(
    val suit: NumberSuit,
    val number: Int,
    val isAkadora: Boolean = false,          // new: flows through IrMapper
) : Tile()
```

**The mapper boundary (in `:app`):** `IrMapper.toTile(detectedTile)` reads
`detectedTile.isAkadora` and sets `NumberTile.isAkadora` accordingly. This is the
only place the CV flag crosses into the domain type.

### 3.4 What is model-specific and must stay inside the adapter

- The raw class index (e.g. index 37 = "red 5-man" in the baseline label set)
- The logic that decides `isAkadora = true` for that index
- `TileIdMapper.BASELINE_CLASS_MAP` — maps class index → `(TileId, isAkadora)`

The domain type `Tile` does not know that class indices exist. The interface
`TileRecognitionEngine` does not expose class indices. Only `TileIdMapper` does.

---

## 4. Revised stable intermediate representation

### 4.1 Design principles

The stable IR (`TileRecognitionResult` and its children) must:

- Express everything a model could usefully tell us, without committing to
  fields that only one specific model architecture can populate
- Use `null` to mean "this model did not provide this information" — never use
  a sentinel value that could be confused with a real value
- Remain model-agnostic: adding a new model must not require changing these classes

### 4.2 Enriched `DetectedTile`

```kotlin
data class DetectedTile(
    // ── Classification ────────────────────────────────────────────────
    /** Top-k candidates, highest confidence first. Never empty. */
    val candidates: List<RecognitionCandidate>,

    /** Whether the model flagged this as an akadora (red five). */
    val isAkadora: Boolean = false,

    // ── Geometry ──────────────────────────────────────────────────────
    /** Normalised [0,1] bbox. Null for pure-classifier models. */
    val bbox: NormalizedBbox?,

    /**
     * Clockwise rotation of the tile in degrees (0, 90, 180, 270).
     * Null if model does not predict orientation.
     * Primarily useful for real-photo models where tiles may be rotated.
     */
    val orientationDegrees: Int? = null,

    // ── Layout semantics ──────────────────────────────────────────────
    /**
     * Structural role of this tile within the hand layout.
     * Null if the model does not segment the hand.
     */
    val layoutRole: LayoutRole? = null,

    /**
     * Opaque group identifier. Tiles with the same non-null groupId
     * are hypothesised by the model to belong to the same meld or hand segment.
     * Group semantics (meld type etc.) are determined in the correction stage.
     */
    val groupId: String? = null,

    /**
     * Suggested display/reading order index within its group, 0-based.
     * Null if ordering cannot be inferred.
     */
    val readingOrder: Int? = null,

    // ── Uncertainty ───────────────────────────────────────────────────
    /**
     * Overall detection confidence (from the detector head, distinct from
     * classification confidence). Null for models without a detection head.
     */
    val detectionConfidence: Float? = null,

    // ── Debug ─────────────────────────────────────────────────────────
    /**
     * Adapter-specific key/value metadata (e.g. raw YOLO objectness score).
     * Never used for decisions; for logging and diagnostics only.
     */
    val debugMetadata: Map<String, String> = emptyMap(),
)
```

### 4.3 `LayoutRole`

Replaces the previous `GroupHint` enum. More expressive and extensible:

```kotlin
enum class LayoutRole {
    /** Part of the concealed tiles dealt to the player. */
    CLOSED_HAND,
    /** The winning tile (tsumo or ron). */
    WINNING_TILE,
    /** A tile in an open meld (chi/pon/kan). */
    OPEN_MELD,
    /** A tile in a declared kan (open or concealed). */
    KAN,
    /** Role not yet assigned or model did not provide a hint. */
    UNKNOWN,
}
```

`groupId` provides the grouping within `OPEN_MELD` / `KAN`. For example, all three
tiles in the first open chi share `groupId = "open-0"`. This avoids encoding meld
index into the enum, which forced a hard upper bound of four melds before.

### 4.4 Baseline vs future model fill rates

| Field | Baseline (screenshots) | Future real-photo model |
|---|---|---|
| `candidates` | filled | filled |
| `isAkadora` | filled (via TileIdMapper) | filled |
| `bbox` | filled | filled |
| `orientationDegrees` | `null` (assumed upright) | likely filled |
| `layoutRole` | partially filled (CLOSED_HAND / OPEN_MELD) | likely fully filled |
| `groupId` | filled for open melds | filled |
| `readingOrder` | filled (left-to-right sort) | filled |
| `detectionConfidence` | filled (YOLO objectness) | filled |

The IR is not broken by partial fill. Downstream code must treat every nullable field
as "model did not provide this" and degrade gracefully to user confirmation.

### 4.5 `TileRecognitionResult` (minimal change)

```kotlin
data class TileRecognitionResult(
    val tiles: List<DetectedTile>,
    val modelInfo: ModelInfo,
    val captureType: CaptureType,
    val processingTimeMs: Long,
    val debugMetadata: Map<String, String> = emptyMap(),
)
```

No structural change needed here. Enrichment flows through `DetectedTile`.

---

## 5. Recommendation: `:cv:api` as a pure Kotlin module

### 5.1 The problem

`RecognitionInput` currently imports `android.graphics.Bitmap` and `android.net.Uri`.
This makes `:cv:api` an Android library, which:

- Prevents testing it on the JVM without Robolectric
- Couples the interface contract to Android types
- Blocks any future server-side or desktop use of the same IR

### 5.2 Recommendation: make `:cv:api` a pure `kotlin("jvm")` module

Change `cv/api/build.gradle.kts` from `android.library` to a pure Kotlin JVM module.

```kotlin
// cv/api/build.gradle.kts — revised
plugins {
    alias(libs.plugins.kotlin.jvm)   // was: android.library
}
dependencies {
    implementation(libs.coroutines.core)
}
```

### 5.3 What moves out of `:cv:api`

Remove `android.graphics.Bitmap` and `android.net.Uri` from `RecognitionInput`.
Replace with a platform-neutral input type:

```kotlin
// cv:api — platform-neutral input
sealed class RecognitionInput {
    abstract val captureType: CaptureType

    /**
     * Raw image bytes (e.g. JPEG or PNG) with declared dimensions.
     * The adapter is responsible for decoding. This is the canonical input
     * format for the pure interface — no Android SDK required to represent it.
     */
    data class BytesInput(
        val bytes: ByteArray,
        val widthPx: Int,
        val heightPx: Int,
        val format: ImageFormat,
        override val captureType: CaptureType,
    ) : RecognitionInput()
}

enum class ImageFormat { JPEG, PNG, RGB_888 }
```

Android-specific conversions (`Bitmap → ByteArray`, `Uri → ByteArray`) belong in
`:cv:baseline` or `:app`, not in `:cv:api`. A thin extension function or utility class
in `:app` wraps this for the UI layer.

### 5.4 Tradeoffs

| | Android library | Pure Kotlin JVM |
|---|---|---|
| JVM unit tests for the interface | Requires Robolectric | Works natively |
| Can be used by non-Android code | No | Yes |
| Bitmap convenience for adapters | Built-in | Adapter converts |
| Complexity at the call site | None | Tiny: `bitmap.toByteArray()` helper in `:app` |

The added call-site complexity is one extension function. The benefit — a truly
portable, JVM-testable interface module — is worth it.

### 5.5 What stays in `:cv:api`

All interfaces, all IR data classes, `TileId`, `LayoutRole`, `CaptureType`,
`ModelInfo`, `RecognitionOutcome`, `CorrectionRecordExporter`. None of these require
Android.

---

## 6. Revised correction schema and correction flow

### 6.1 Why the original schema was insufficient

The original `CorrectionRecord` was designed around a single interaction type:
"user changed the predicted tile identity." This covers the most common case but
misses several others that are valuable training signals:

- The model detected a tile that was not there (false positive)
- The model missed a tile entirely (false negative / missing tile insertion)
- The bounding box was wrong (bbox adjustment)
- The model grouped tiles incorrectly (grouping change)
- The model assigned the wrong layout role (layout role assignment)

### 6.2 `CorrectionType`

```kotlin
enum class CorrectionType {
    /** User accepted the model's prediction without change. */
    ACCEPTED,
    /** User corrected the tile identity (including akadora flag). */
    CLASSIFICATION_CORRECTION,
    /** User deleted a false-positive detection. */
    FALSE_POSITIVE_DELETION,
    /** User inserted a tile the model missed entirely. */
    MISSING_TILE_INSERTION,
    /** User adjusted the bounding box. */
    BBOX_ADJUSTMENT,
    /** User reassigned a tile's layout role (e.g. CLOSED_HAND → WINNING_TILE). */
    LAYOUT_ROLE_ASSIGNMENT,
    /** User changed which group a tile belongs to. */
    GROUPING_CHANGE,
}
```

### 6.3 Revised `CorrectionRecord` schema

The schema evolves from a single flat record to a type-discriminated record.
Fields irrelevant to the correction type are null.

```
id                          long         auto PK
timestamp_ms                long         unix epoch ms
image_hash                  string       SHA-256 of source image
image_path                  string?      local device path
capture_type                enum         SCREENSHOT | CAMERA_PHOTO | GALLERY_PHOTO
model_id                    string
model_version               string
model_architecture          string

correction_type             enum         CorrectionType (new)

── Relevant for: ACCEPTED, CLASSIFICATION_CORRECTION, FALSE_POSITIVE_DELETION,
                 BBOX_ADJUSTMENT, LAYOUT_ROLE_ASSIGNMENT, GROUPING_CHANGE ──
predicted_tile_id           enum?        model top-1, null for MISSING_TILE_INSERTION
predicted_confidence        float?
top_k_candidates_json       string?
predicted_is_akadora        bool?        new: model's akadora hypothesis

── Relevant for: CLASSIFICATION_CORRECTION, MISSING_TILE_INSERTION, ACCEPTED ──
corrected_tile_id           enum?        ground-truth tile id (null for deletions)
corrected_is_akadora        bool?        new: user-confirmed akadora status

── Relevant for all spatial corrections ──
bbox_{left,top,right,bottom}  float?     original predicted bbox
corrected_bbox_{l,t,r,b}    float?       new: user-adjusted bbox (null = no change)

── Relevant for LAYOUT_ROLE_ASSIGNMENT ──
predicted_layout_role       enum?        model's LayoutRole hypothesis
corrected_layout_role       enum?        user-assigned LayoutRole

── Relevant for GROUPING_CHANGE ──
predicted_group_id          string?
corrected_group_id          string?

── Export state ──
was_model_wrong             bool         derived: corrected ≠ predicted
is_exported                 bool
```

### 6.4 Which fields matter per correction type

| Field | ACCEPTED | CLASS_CORR | FP_DEL | MISSING | BBOX_ADJ | ROLE_ASSIGN | GROUP_CHANGE |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| predicted_tile_id | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| corrected_tile_id | ✓ | ✓ | — | ✓ | — | — | — |
| predicted/corrected_is_akadora | ✓ | ✓ | — | ✓ | — | — | — |
| corrected_bbox | — | — | — | ✓ | ✓ | — | — |
| predicted/corrected_layout_role | — | — | — | — | — | ✓ | — |
| predicted/corrected_group_id | — | — | — | — | — | — | ✓ |

### 6.5 Phase 2 vs Phase 3+ implementation boundary

**Implement in Phase 2:**
- `ACCEPTED` — log accepted predictions for confidence calibration
- `CLASSIFICATION_CORRECTION` — the existing use case, now with `isAkadora` fields added
- `FALSE_POSITIVE_DELETION` — user taps a "delete this detection" button on a slot card

**Defer to Phase 3+:**
- `MISSING_TILE_INSERTION` — requires a UI for adding a tile not in the model output
- `BBOX_ADJUSTMENT` — requires a drag handle on the bbox overlay; skip until real-photo
- `LAYOUT_ROLE_ASSIGNMENT` — covered by the open-meld flow in the correction screen
- `GROUPING_CHANGE` — requires drag-to-regroup UI; skip until real-photo

The schema is defined in full now so that Room migrations are forward-planned.
Columns for deferred types are nullable and default to null — no migration required
when Phase 3 fills them.

### 6.6 Correction flow (updated)

1. `CorrectionScreen` renders one `TileSlotCard` per detected tile
2. Low-confidence and `UNKNOWN` tiles are highlighted with a warning
3. Tapping a card opens `TilePicker` → logs `CLASSIFICATION_CORRECTION` or `ACCEPTED`
4. A "×" button on each card allows deletion → logs `FALSE_POSITIVE_DELETION`
5. **"Confirm Hand"** bulk-accepts remaining slots → logs `ACCEPTED` for each
6. User never bypasses this screen — always the final word

---

## 7. `:cv:api` is now a pure Kotlin module

(See §5 above for the full rationale and tradeoffs.)

Summary of changes relative to the original scaffold:

- `cv/api/build.gradle.kts`: `android.library` → `kotlin("jvm")`
- `RecognitionInput`: remove `Bitmap`/`Uri` variants, add `BytesInput`
- `BitmapInput` and `UriInput` move to `:app` or `:cv:baseline` as adapter-local types
- A `Bitmap.toRecognitionInput(captureType)` extension lives in `:app` (one function)

No other `:cv:api` types change.

---

## 8. Evaluation plan

### 8.1 Why single-tile accuracy is insufficient

A model with 95% per-tile accuracy on a 13-tile hand has a ~51% chance of returning
at least one wrong tile (`0.95^13 ≈ 0.51`). For a scoring app, **full-hand exact match
rate is the product-level metric** — not per-tile accuracy.

Similarly, the user correction load (how many tiles the user must fix before confirming)
is more directly related to app usability than raw accuracy.

### 8.2 Benchmark suites

#### Screenshot benchmark (validates baseline model against its training domain)

- Source: rendered screenshots from Majsoul, Tenhou, etc.
- Labeled: hand-verified ground truth with `isAkadora` flags
- Size target: ≥ 500 hands covering all 34 tile types and akadora variants
- Tests: tile identity + akadora flag per tile; full-hand reconstruction

#### Real-photo benchmark (validates fine-tuned model against target domain)

- Source: device camera photos of physical mahjong tiles
- Conditions: varied lighting, table surfaces, tile sets, slight angles
- Size target: ≥ 300 hands from at least 3 different tile sets
- Tests: same as screenshot benchmark plus orientation recovery

#### Hard-case benchmark (regression suite for known failure modes)

- Ambiguous tiles (e.g. 1-man vs 1-sou in certain tile sets)
- Partially occluded tiles
- Rotated tiles (90°/180°)
- Akadora tiles in low-contrast backgrounds
- Mixed-set hands (tile visual styles differ within the same hand)

### 8.3 Metrics

| Metric | Description | Threshold (indicative) |
|---|---|---|
| Tile top-1 accuracy | Fraction of tiles where top prediction is correct | ≥ 0.97 (screenshot) |
| Tile top-3 accuracy | Fraction where correct tile is in top 3 | ≥ 0.995 (screenshot) |
| Akadora precision/recall | For the isAkadora flag specifically | ≥ 0.95 / ≥ 0.90 |
| Detection precision | TP / (TP + FP) — no false positives | ≥ 0.98 |
| Detection recall | TP / (TP + FN) — no missed tiles | ≥ 0.98 |
| Full-hand exact match | Fraction of hands where all tiles correct with no user edit | ≥ 0.80 (screenshot target) |
| Mean corrections per hand | Average number of user tile-edits needed | ≤ 0.5 (screenshot target) |
| Correction-adjusted usability | % hands where user needed ≤ 1 correction | ≥ 0.95 |

The correction-adjusted usability metrics come for free once `CorrectionLogger` is
live — `was_model_wrong` count per session is an exact measure of user correction load.

### 8.4 When to run

- Screenshot benchmark: after every model update, in CI if feasible
- Real-photo benchmark: before shipping Phase 3 model
- Hard-case benchmark: whenever a new failure mode is reported

---

## 9. Revised open-meld / structure reconstruction boundary

### 9.1 The four-stage boundary

The model, the correction screen, the user, and the domain engine each own a distinct
stage. These must not be conflated.

**Stage 1 — CV hypothesis (inside `TileRecognitionResult`):**  
The model assigns `layoutRole` and `groupId` to tiles. It can reasonably infer:

- Which tiles are likely in the closed hand (`LayoutRole.CLOSED_HAND`)
- Which tiles are spatially clustered into a separate group (`LayoutRole.OPEN_MELD`)
- A rough left-to-right reading order within each group

The model cannot reliably determine:
- Whether a group is a chi, pon, or kan (without explicit training)
- Which tile in a chi is the "claimed" tile (required for scoring open chi)
- Whether a kan is a shouminkan, daiminkan, or ankan

**Stage 2 — Correction-stage confirmation (in `CorrectionViewModel`):**  
The correction screen presents the model's grouping hypothesis visually.
The user can:
- Accept the grouping as-is
- Delete a false-positive meld tile
- Re-assign a tile from closed hand to an open meld (deferred to Phase 3 UI)

At the end of the correction screen, grouping is confirmed but **meld type is not yet known**.

**Stage 3 — User meld-type confirmation (in `RoundContextScreen`):**  
A confirmed open meld group is presented to the user as "Group A (3 tiles)."
The user selects: Chi / Pon / Kan. For chi, the user selects which tile was claimed.
This is the minimum required for correct fu and yaku calculation.

**Stage 4 — Domain conversion (in `IrMapper`):**  
`IrMapper` converts the confirmed `RecognizedHand` + round context into a `Hand`
with explicit `Meld` objects (`Chi`, `Pon`, `Kan`). The scoring engine only ever
sees clean domain types.

### 9.2 `RecognizedHand` carries group confirmation, not meld type

```kotlin
// domain ir — post-correction, pre-scoring
data class RecognizedHand(
    val closedTiles: List<TileSlot>,
    val openMelds: List<ConfirmedMeldGroup>,  // groups confirmed by user
    val isComplete: Boolean,
)

data class ConfirmedMeldGroup(
    val groupId: String,
    val tiles: List<TileSlot>,
    val meldType: MeldType?,    // null until user confirms in RoundContextScreen
    val claimedTileIndex: Int?, // for chi: index of the claimed tile (0..2)
)

enum class MeldType { CHI, PON, KAN }
```

`meldType` is null after the correction screen and filled after the round context screen.
The domain engine is never called with a null `meldType` for any open meld.

### 9.3 What is deferred

- Drag-to-regroup UI in the correction screen — **Phase 3**
- Automatic meld-type inference from tile identity (e.g. three-of-a-kind → pon) — **optional Phase 2 hint only, never authoritative**
- Shouminkan vs daiminkan distinction — **Phase 3** (scoring difference is minor; Phase 2 treats all kans as daiminkan for fu purposes and documents this)
- Full open-hand yaku detection (tanyao open, ittsuu open, etc.) — **Phase 2** (scoring engine already handles open hands; just ensure IrMapper produces correct Meld types)

### 9.4 V1 open-hand support without overbuilding

Phase 2 can support open hands by asking the user to confirm meld types on the round
context screen. This is a small UI addition with no architectural changes. The CV layer
provides grouping hints; the user provides meld type. The scoring engine handles the rest.
No labeling tool, no drag UI, no new CV training needed.

---

## 10. Updated phased plan

### Phase 0 — Manual input + scoring engine

**Goal:** App builds, runs, scores a manually-entered hand.

- Implement `ScoringEngineImpl` helpers (test-first: tanyao, pinfu, riichi first)
- Wire `RoundContextViewModel` → scoring engine → `ScoringResultScreen`
- Add manual tile entry to `MainScreen` → produces `RecognizedHand` with all slots user-confirmed
- Add dora tile picker to `RoundContextScreen`
- The image button is grayed out (model not loaded)

Safe to build now. All of `:domain`, all of `:app` manual path.

---

### Phase 1 — Plug in baseline adapter

**Goal:** Load a screenshot, recognize tiles, prefill the correction screen.

- Migrate `:cv:api` to pure Kotlin JVM (`kotlin("jvm")` plugin)
- Add `BytesInput` to `RecognitionInput`; add `Bitmap.toRecognitionInput()` extension in `:app`
- Copy `.tflite` asset into `cv/baseline/src/main/assets/models/`
- Fill `TileIdMapper.BASELINE_CLASS_MAP` — maps class index → `(TileId, isAkadora)`
- Implement `ImagePreprocessor.letterbox()`, `YoloPostProcessor.process()`, `nms()`
- Implement `BaselineAdapter.load()` and full inference path
- Implement `TilePicker` composable (34-tile grid)
- Wire `MainViewModel` → `CorrectionViewModel` via Hilt-scoped result holder
- Add `DetectedTile.isAkadora` to IR; add `isAkadora` to `Tile.NumberTile`; update `IrMapper`

Model-agnostic parts are unchanged: `:domain`, `:correction`, `TileRecognitionEngine` interface.

---

### Phase 2 — Correction logging + export + open-hand confirmation

**Goal:** All corrections persisted; open-hand scoring works.

- Implement revised `CorrectionRecord` schema (type-discriminated, all columns defined)
- Wire `CorrectionViewModel` to log `ACCEPTED` and `CLASSIFICATION_CORRECTION` per slot
- Add "×" button to `TileSlotCard` → log `FALSE_POSITIVE_DELETION`
- Add SHA-256 image hash; save source image to private storage
- Add "Export correction data" in settings → `JsonlCorrectionExporter`
- Add open meld group UI to `CorrectionScreen` (display grouping hypothesis, accept/delete)
- Add meld-type picker to `RoundContextScreen` for each `ConfirmedMeldGroup`
- Update `IrMapper` to convert `ConfirmedMeldGroup` → `Meld` domain types

---

### Phase 3 — Real-photo model + full correction tooling

**Goal:** Replace baseline with fine-tuned real-photo model. Enrich correction data.

- Implement `RealPhotoAdapter : TileRecognitionEngine` (new `:cv:realphoto` module)
- Change one line in `AppModule.provideTileRecognitionEngine()`
- Add image crop export (JPEG per bbox) alongside JSONL
- Add zip export mode for device → workstation transfer
- Implement `MISSING_TILE_INSERTION` correction type in UI + logger
- Add `BBOX_ADJUSTMENT` if real-photo model bbox quality is poor
- Add `GROUPING_CHANGE` if real-photo model grouping quality is poor
- Validate with `ModelInfo.trainingDomain == REAL_PHOTO` shown in UI

---

## 11. Code-level implications for the current scaffold

These are the minimal changes needed to bring the scaffold into alignment with this design.
Do not touch anything not listed here.

### 11.1 `TileId.kt` — no change needed

Stays at 34 values + UNKNOWN. No akadora entries.

### 11.2 `DetectedTile.kt` — add fields

Add to the existing `DetectedTile` data class:
- `isAkadora: Boolean = false`
- `orientationDegrees: Int? = null`
- `layoutRole: LayoutRole? = null` (replaces `GroupHint` — see below)
- `groupId: String? = null` (replaces `OPEN_MELD_0..3` enumeration)
- `readingOrder: Int? = null`
- `detectionConfidence: Float? = null`

Remove `GroupHint` enum; replace with `LayoutRole` enum.
Update `BaselineAdapter`/`YoloPostProcessor` to produce `LayoutRole` + `groupId` strings
instead of `GroupHint` values.

### 11.3 `RecognitionInput.kt` — break Android dependency

Remove `BitmapInput` and `UriInput`. Add `BytesInput`.
Add `Bitmap.toRecognitionInput(captureType)` extension in `:app`.
Change `cv/api/build.gradle.kts` plugin to `kotlin("jvm")`.

### 11.4 `Tile.kt` — add `isAkadora` to `NumberTile`

```kotlin
data class NumberTile(
    val suit: NumberSuit,
    val number: Int,
    val isAkadora: Boolean = false,
) : Tile()
```

Update `IrMapper` in `:app` to pass `detectedTile.isAkadora` through.

### 11.5 `CorrectionRecord.kt` — add columns

Add the new columns specified in §6.3. All new columns nullable, defaulting to null.
Bump Room schema version and write a migration (or use `fallbackToDestructiveMigration`
during development until Phase 2 is complete).

### 11.6 `TileIdMapper.kt` — return `(TileId, isAkadora)` pairs

Change `BASELINE_CLASS_MAP` to map `Int → Pair<TileId, Boolean>` (second value = isAkadora).
Pass the flag through to `DetectedTile`.

### 11.7 `RecognizedHand.kt` — add `openMelds`

Introduce `ConfirmedMeldGroup` and replace the flat `slots` list with
`closedTiles: List<TileSlot>` + `openMelds: List<ConfirmedMeldGroup>`.
`IrMapper` reconstructs groups from `DetectedTile.groupId`.

---

## 12. Safe to build now vs deferred

### Safe to build now (Phase 0 / Phase 1)

- All of `:domain` including `Tile.NumberTile.isAkadora`
- `:cv:api` pure Kotlin migration (mechanical change, no logic)
- `DetectedTile` field additions (all nullable with defaults — backward compatible)
- `LayoutRole` replacing `GroupHint`
- `TileIdMapper` returning `(TileId, isAkadora)` pairs
- `CorrectionRecord` schema with new nullable columns
- `CorrectionType.ACCEPTED` and `CLASSIFICATION_CORRECTION` logging in Phase 2 wiring

### Deferred to Phase 2

- `FALSE_POSITIVE_DELETION` UI and logging
- Open meld group display in `CorrectionScreen`
- Meld-type picker in `RoundContextScreen`
- `ConfirmedMeldGroup` plumbing through `RecognizedHand` → `IrMapper`
- JSONL export with akadora fields

### Deferred to Phase 3+

- `MISSING_TILE_INSERTION`, `BBOX_ADJUSTMENT`, `GROUPING_CHANGE` correction types
- Image crop JPEG export
- Evaluation benchmark harness (can be a Python script against exported JSONL)
- `RealPhotoAdapter` and `:cv:realphoto` module
- Drag-to-regroup UI
