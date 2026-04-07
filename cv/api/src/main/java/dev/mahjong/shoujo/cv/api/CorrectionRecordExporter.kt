package dev.mahjong.shoujo.cv.api

import java.io.File

/**
 * Exports accumulated correction records as labeled data for future model training.
 *
 * This interface lives in :cv:api because it is the boundary between the inference-time
 * correction logger and any future offline training pipeline.
 *
 * Implementations:
 *   - JsonlCorrectionExporter    ← writes JSONL per-record files (Phase 2)
 *   - ZipCorrectionExporter      ← bundles image + labels for hand-off (Phase 3+)
 *
 * IMPORTANT: This is reserved for future fine-tuning hooks.
 * Do not add ML-framework-specific code here. Keep the contract file-based and portable.
 */
interface CorrectionRecordExporter {
    /**
     * Exports all pending correction records to [targetDir].
     * Returns the number of records written.
     */
    suspend fun exportPending(targetDir: File): Int

    /**
     * Returns the number of correction records that have not yet been exported.
     */
    suspend fun pendingCount(): Int
}
