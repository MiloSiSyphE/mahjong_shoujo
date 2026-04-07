package dev.mahjong.shoujo.correction

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.CaptureType
import dev.mahjong.shoujo.cv.api.model.LayoutRole

/**
 * Type of user interaction that generated a correction record.
 * The correction_type column discriminates which other fields are relevant.
 */
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

/**
 * Schema for a single correction event.
 *
 * This is a type-discriminated record: [correctionType] indicates which fields
 * are populated. Fields irrelevant to the correction type are null.
 * All columns added in v2 are nullable so no migration is required beyond the
 * schema version bump (using fallbackToDestructiveMigration during development).
 *
 * Design goals:
 *   - Lossless: preserves original prediction so we can measure model improvement.
 *   - Portable: the exported JSONL can be loaded by any training script.
 *   - Versioned: modelId + modelVersion allow bucketing data by source model.
 *   - Private: imagePath is a local device path; it is NEVER uploaded automatically.
 */
@Entity(tableName = "correction_records")
@JsonClass(generateAdapter = true)
data class CorrectionRecord(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // ── Timing ─────────────────────────────────────────────────────────────────

    /** Unix epoch ms when the user submitted the correction. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,

    // ── Source image ───────────────────────────────────────────────────────────

    /**
     * SHA-256 hash of the source image bytes.
     * Used to deduplicate records from the same image across sessions.
     */
    @ColumnInfo(name = "image_hash")
    val imageHash: String,

    /**
     * Absolute path to the source image on device storage.
     * Null if the image was not saved (e.g., transient bitmap).
     */
    @ColumnInfo(name = "image_path")
    val imagePath: String?,

    /** How the image was captured. */
    @ColumnInfo(name = "capture_type")
    val captureType: CaptureType,

    // ── Model metadata ─────────────────────────────────────────────────────────

    /** ID string of the model that produced the original prediction. */
    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    @ColumnInfo(name = "model_architecture")
    val modelArchitecture: String,

    // ── Correction type ────────────────────────────────────────────────────────

    /** Discriminates which other fields in this record are relevant. */
    @ColumnInfo(name = "correction_type")
    val correctionType: CorrectionType,

    // ── Prediction (relevant for all types except MISSING_TILE_INSERTION) ─────

    /** The top-1 tile predicted by the model. Null for MISSING_TILE_INSERTION. */
    @ColumnInfo(name = "predicted_tile_id")
    val predictedTileId: TileId?,

    /** Confidence of the top-1 prediction. Null if no detection. */
    @ColumnInfo(name = "predicted_confidence")
    val predictedConfidence: Float?,

    /**
     * JSON array of the top-k alternatives the model produced, e.g.:
     * [{"tile":"MAN_3","confidence":0.81},{"tile":"MAN_2","confidence":0.09}]
     */
    @ColumnInfo(name = "top_k_candidates_json")
    val topKCandidatesJson: String?,

    /** Model's akadora hypothesis for the predicted tile. Null if not applicable. */
    @ColumnInfo(name = "predicted_is_akadora")
    val predictedIsAkadora: Boolean?,

    // ── Ground truth (relevant for ACCEPTED, CLASSIFICATION_CORRECTION, MISSING_TILE_INSERTION) ──

    /** Ground-truth tile id confirmed by the user. Null for FALSE_POSITIVE_DELETION. */
    @ColumnInfo(name = "corrected_tile_id")
    val correctedTileId: TileId?,

    /** User-confirmed akadora status. Null for deletion and spatial-only corrections. */
    @ColumnInfo(name = "corrected_is_akadora")
    val correctedIsAkadora: Boolean?,

    // ── Bounding box (normalised) ──────────────────────────────────────────────

    /** Normalised [0,1] bounding box of the tile as predicted by the model. */
    @ColumnInfo(name = "bbox_left")   val bboxLeft:   Float?,
    @ColumnInfo(name = "bbox_top")    val bboxTop:    Float?,
    @ColumnInfo(name = "bbox_right")  val bboxRight:  Float?,
    @ColumnInfo(name = "bbox_bottom") val bboxBottom: Float?,

    /** User-adjusted bbox. Null unless correction type is BBOX_ADJUSTMENT or MISSING_TILE_INSERTION. */
    @ColumnInfo(name = "corrected_bbox_left")   val correctedBboxLeft:   Float? = null,
    @ColumnInfo(name = "corrected_bbox_top")    val correctedBboxTop:    Float? = null,
    @ColumnInfo(name = "corrected_bbox_right")  val correctedBboxRight:  Float? = null,
    @ColumnInfo(name = "corrected_bbox_bottom") val correctedBboxBottom: Float? = null,

    // ── Layout role (relevant for LAYOUT_ROLE_ASSIGNMENT) ─────────────────────

    /** Model's LayoutRole hypothesis. Null if not applicable. */
    @ColumnInfo(name = "predicted_layout_role")
    val predictedLayoutRole: LayoutRole? = null,

    /** User-assigned LayoutRole. Null if not applicable. */
    @ColumnInfo(name = "corrected_layout_role")
    val correctedLayoutRole: LayoutRole? = null,

    // ── Grouping (relevant for GROUPING_CHANGE) ───────────────────────────────

    /** Model's group id hypothesis. Null if not applicable. */
    @ColumnInfo(name = "predicted_group_id")
    val predictedGroupId: String? = null,

    /** User-assigned group id. Null if not applicable. */
    @ColumnInfo(name = "corrected_group_id")
    val correctedGroupId: String? = null,

    // ── Export state ───────────────────────────────────────────────────────────

    /**
     * True if the user actively changed the model's prediction.
     * False if the user accepted it (still logged for confidence calibration).
     */
    @ColumnInfo(name = "was_model_wrong")
    val wasModelWrong: Boolean,

    /** False until this record has been included in a [CorrectionRecordExporter] export. */
    @ColumnInfo(name = "is_exported")
    val isExported: Boolean = false,
)
