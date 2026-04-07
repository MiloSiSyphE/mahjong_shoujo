package dev.mahjong.shoujo.correction

import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.DetectedTile
import dev.mahjong.shoujo.cv.api.model.TileRecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records tile corrections for future model retraining.
 *
 * Called from the correction UI whenever the user confirms or changes a tile identity.
 * Writes to Room asynchronously — never blocks the UI thread.
 *
 * RETRAINING HOOK: The records stored here are the raw material for creating a
 * target-domain labeled dataset.  See [JsonlCorrectionExporter] for the export path.
 */
@Singleton
class CorrectionLogger @Inject constructor(
    private val dao: CorrectionDao,
) {

    /**
     * Logs a single tile correction.
     *
     * @param recognitionResult  The full CV result this correction belongs to.
     * @param detectedTile       The specific CV detection being corrected. Null if
     *                           the user added a tile with no model candidate.
     * @param correctedTileId    The ground-truth label the user confirmed. Null for deletions.
     * @param correctionType     The type of correction (default: CLASSIFICATION_CORRECTION).
     * @param imageHash          SHA-256 of the source image (pre-computed by caller).
     * @param imagePath          Local file path of the saved source image, if available.
     */
    suspend fun log(
        recognitionResult: TileRecognitionResult,
        detectedTile: DetectedTile?,
        correctedTileId: TileId?,
        correctionType: CorrectionType = CorrectionType.CLASSIFICATION_CORRECTION,
        imageHash: String,
        imagePath: String?,
    ) = withContext(Dispatchers.IO) {
        val topK = detectedTile?.candidates
        val predicted = topK?.firstOrNull()

        val record = CorrectionRecord(
            timestampMs           = System.currentTimeMillis(),
            imageHash             = imageHash,
            imagePath             = imagePath,
            captureType           = recognitionResult.captureType,
            modelId               = recognitionResult.modelInfo.modelId,
            modelVersion          = recognitionResult.modelInfo.version,
            modelArchitecture     = recognitionResult.modelInfo.architecture,
            correctionType        = correctionType,
            predictedTileId       = predicted?.tileId,
            predictedConfidence   = predicted?.confidence,
            topKCandidatesJson    = topK?.toJson(),
            predictedIsAkadora    = detectedTile?.isAkadora,
            correctedTileId       = correctedTileId,
            correctedIsAkadora    = null, // TODO(Phase 2): pass from correction UI
            bboxLeft              = detectedTile?.bbox?.left,
            bboxTop               = detectedTile?.bbox?.top,
            bboxRight             = detectedTile?.bbox?.right,
            bboxBottom            = detectedTile?.bbox?.bottom,
            wasModelWrong         = predicted?.tileId != correctedTileId,
        )
        dao.insert(record)
        Timber.d("Logged correction[$correctionType]: ${predicted?.tileId} → $correctedTileId (wrong=${record.wasModelWrong})")
    }
}

// ---------------------------------------------------------------------------
// Internal serialisation helpers — keep them local, not part of the public API
// ---------------------------------------------------------------------------

private fun List<dev.mahjong.shoujo.cv.api.model.RecognitionCandidate>.toJson(): String =
    joinToString(prefix = "[", postfix = "]") {
        """{"tile":"${it.tileId.name}","confidence":${it.confidence}}"""
    }
