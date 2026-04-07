package dev.mahjong.shoujo.cv.baseline.postprocessing

import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.DetectedTile
import dev.mahjong.shoujo.cv.api.model.NormalizedBbox
import dev.mahjong.shoujo.cv.api.model.RecognitionCandidate
import dev.mahjong.shoujo.cv.baseline.BaselineConfig
import dev.mahjong.shoujo.cv.baseline.preprocessing.LetterboxResult

/**
 * Converts raw YOLO model output tensors into a list of [DetectedTile] IR values.
 *
 * BASELINE-SPECIFIC: Every detail here is tied to the YOLO output format used
 * by the mahjong-utils-app model. A different model architecture (e.g. EfficientDet,
 * RT-DETR) would use a completely different post-processor.
 *
 * This class is the only place in the codebase that knows about:
 *   - YOLO grid/anchor output layout
 *   - Sigmoid / softmax decoding
 *   - NMS (non-maximum suppression) strategy
 *   - Coordinate decoding and de-letterboxing
 *
 * TODO(Phase 1): implement based on mahjong-utils-app post-processing logic.
 *               Do NOT copy code verbatim — adapt it to produce DetectedTile IR output.
 */
class YoloPostProcessor(
    private val tileIdMapper: TileIdMapper,
    private val confidenceThreshold: Float = BaselineConfig.CONFIDENCE_THRESHOLD,
    private val iouThreshold: Float = BaselineConfig.IOU_THRESHOLD,
    private val maxDetections: Int = BaselineConfig.MAX_DETECTIONS,
) {

    /**
     * Processes raw model output into stable IR [DetectedTile] values.
     *
     * @param rawOutput  The flat float array from the TFLite output tensor.
     * @param letterbox  Metadata for mapping coordinates back to original image space.
     * @return           List of detections sorted left-to-right by bbox centre-x.
     */
    fun process(rawOutput: FloatArray, letterbox: LetterboxResult): List<DetectedTile> {
        // TODO(Phase 1): decode YOLO output
        // TODO(Phase 1): apply NMS
        // TODO(Phase 1): de-letterbox bbox coordinates
        // TODO(Phase 1): map class indices to TileId via TileIdMapper
        // TODO(Phase 1): sort results left-to-right
        return emptyList()
    }

    /** Non-maximum suppression — keep only the best box per overlapping cluster. */
    private fun nms(raw: List<RawDetection>): List<RawDetection> {
        // TODO(Phase 1): implement IoU-based NMS
        return emptyList()
    }

    private fun iou(a: NormalizedBbox, b: NormalizedBbox): Float {
        // TODO(Phase 1): implement intersection-over-union
        return 0f
    }
}

/** Internal representation before IR conversion — never exposed outside this file. */
internal data class RawDetection(
    val classIndex: Int,
    val confidence: Float,
    val bbox: NormalizedBbox,
    val topKClasses: List<Pair<Int, Float>>,
)
