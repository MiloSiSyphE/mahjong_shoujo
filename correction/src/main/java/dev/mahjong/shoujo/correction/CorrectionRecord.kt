package dev.mahjong.shoujo.correction

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.CaptureType

/**
 * Schema for a single correction event.
 *
 * A correction record is created whenever the user changes or confirms a tile
 * that differed from (or was absent in) the model's prediction.
 *
 * Each record is a future training example:
 *   input  = (imageHash, bbox) → the image crop of the tile
 *   label  = correctedTileId
 *   weight = inverse of modelConfidence (uncertain predictions are more valuable)
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
     * May be a screenshot or a camera photo.
     * Null if the image was not saved (e.g., transient bitmap).
     */
    @ColumnInfo(name = "image_path")
    val imagePath: String?,

    /** How the image was captured. */
    @ColumnInfo(name = "capture_type")
    val captureType: CaptureType,

    // ── Bounding box (normalised) ──────────────────────────────────────────────

    /** Normalised [0,1] bounding box of the tile in the source image. */
    @ColumnInfo(name = "bbox_left")   val bboxLeft:   Float?,
    @ColumnInfo(name = "bbox_top")    val bboxTop:    Float?,
    @ColumnInfo(name = "bbox_right")  val bboxRight:  Float?,
    @ColumnInfo(name = "bbox_bottom") val bboxBottom: Float?,

    // ── Model prediction ───────────────────────────────────────────────────────

    /** ID string of the model that produced the original prediction. */
    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    @ColumnInfo(name = "model_architecture")
    val modelArchitecture: String,

    /** The top-1 tile predicted by the model, or null if no detection was found. */
    @ColumnInfo(name = "predicted_tile_id")
    val predictedTileId: TileId?,

    /** Confidence of the top-1 prediction. Null if no detection. */
    @ColumnInfo(name = "predicted_confidence")
    val predictedConfidence: Float?,

    /**
     * JSON array of the top-k alternatives the model produced, e.g.:
     * [{"tile":"MAN_3","confidence":0.81},{"tile":"MAN_2","confidence":0.09}]
     * Stored as a string to avoid schema churn when k changes.
     */
    @ColumnInfo(name = "top_k_candidates_json")
    val topKCandidatesJson: String?,

    // ── Correction ─────────────────────────────────────────────────────────────

    /** The tile identity confirmed by the user. This is the ground-truth label. */
    @ColumnInfo(name = "corrected_tile_id")
    val correctedTileId: TileId,

    /**
     * True if the user actively changed the model's prediction.
     * False if the user accepted it (but we still log for confidence calibration).
     */
    @ColumnInfo(name = "was_model_wrong")
    val wasModelWrong: Boolean,

    // ── Export state ───────────────────────────────────────────────────────────

    /** False until this record has been included in a [CorrectionRecordExporter] export. */
    @ColumnInfo(name = "is_exported")
    val isExported: Boolean = false,
)
