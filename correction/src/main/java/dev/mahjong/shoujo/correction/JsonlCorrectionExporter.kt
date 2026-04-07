package dev.mahjong.shoujo.correction

import dev.mahjong.shoujo.cv.api.CorrectionRecordExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports pending correction records as a JSONL file (one JSON object per line).
 *
 * Output format (one line per record):
 * {
 *   "id": 42,
 *   "timestamp_ms": 1700000000000,
 *   "image_hash": "sha256...",
 *   "image_path": "/storage/.../photo.jpg",
 *   "capture_type": "CAMERA_PHOTO",
 *   "bbox": {"left": 0.12, "top": 0.45, "right": 0.18, "bottom": 0.65},
 *   "model_id": "baseline-majsoul-v1",
 *   "model_version": "1.0.0-baseline",
 *   "predicted_tile": "MAN_3",
 *   "predicted_confidence": 0.81,
 *   "top_k_candidates": [...],
 *   "corrected_tile": "MAN_4",
 *   "was_model_wrong": true
 * }
 *
 * This file can be consumed directly by a Python training script, e.g.:
 *   records = [json.loads(line) for line in open("corrections.jsonl")]
 *   # filter by model_id / capture_type, crop images by bbox, build a dataset
 *
 * RETRAINING HOOK: This is the primary data handoff point between the app and
 * the offline training pipeline (Phase 3).
 * TODO(Phase 3): add image crop export (save each bbox crop as a separate JPEG).
 * TODO(Phase 3): add a zip export mode that bundles JSONL + crops for easy transfer.
 */
@Singleton
class JsonlCorrectionExporter @Inject constructor(
    private val dao: CorrectionDao,
) : CorrectionRecordExporter {

    override suspend fun pendingCount(): Int =
        withContext(Dispatchers.IO) { dao.getPendingExport().size }

    override suspend fun exportPending(targetDir: File): Int = withContext(Dispatchers.IO) {
        val pending = dao.getPendingExport()
        if (pending.isEmpty()) return@withContext 0

        targetDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(targetDir, "corrections_$timestamp.jsonl")

        var written = 0
        outFile.bufferedWriter().use { writer ->
            for (record in pending) {
                writer.write(record.toJsonLine())
                writer.newLine()
                written++
            }
        }

        dao.markExported(pending.map { it.id })
        Timber.i("Exported $written correction records → ${outFile.absolutePath}")
        written
    }

    private fun CorrectionRecord.toJsonLine(): String = buildString {
        append("""{"id":$id""")
        append(""","timestamp_ms":$timestampMs""")
        append(""","image_hash":"$imageHash"""")
        append(""","image_path":${imagePath?.let { "\"$it\"" } ?: "null"}""")
        append(""","capture_type":"${captureType.name}"""")
        if (bboxLeft != null) {
            append(""","bbox":{"left":$bboxLeft,"top":$bboxTop,"right":$bboxRight,"bottom":$bboxBottom}""")
        } else {
            append(""","bbox":null""")
        }
        append(""","model_id":"$modelId"""")
        append(""","model_version":"$modelVersion"""")
        append(""","model_architecture":"$modelArchitecture"""")
        append(""","predicted_tile":${predictedTileId?.let { "\"${it.name}\"" } ?: "null"}""")
        append(""","predicted_confidence":${predictedConfidence ?: "null"}""")
        append(""","top_k_candidates":${topKCandidatesJson ?: "null"}""")
        append(""","corrected_tile":"${correctedTileId.name}"""")
        append(""","was_model_wrong":$wasModelWrong""")
        append("}")
    }
}
