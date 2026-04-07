package dev.mahjong.shoujo.cv.baseline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mahjong.shoujo.cv.api.TileRecognitionEngine
import dev.mahjong.shoujo.cv.api.model.ModelInfo
import dev.mahjong.shoujo.cv.api.model.RecognitionInput
import dev.mahjong.shoujo.cv.api.model.RecognitionOutcome
import dev.mahjong.shoujo.cv.api.model.TileRecognitionResult
import dev.mahjong.shoujo.cv.baseline.postprocessing.TileIdMapper
import dev.mahjong.shoujo.cv.baseline.postprocessing.YoloPostProcessor
import dev.mahjong.shoujo.cv.baseline.preprocessing.ImagePreprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements [TileRecognitionEngine] using the mahjong-utils-app-style TFLite detector.
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │  BASELINE-SPECIFIC. Everything in this class is         │
 * │  replaceable.  The rest of the app sees only the        │
 * │  TileRecognitionEngine interface.                       │
 * └─────────────────────────────────────────────────────────┘
 *
 * What is wrapped here:
 *   - TFLite interpreter lifecycle (load, run, close)
 *   - GPU delegate setup
 *   - Input preprocessing (letterbox + normalise)
 *   - YOLO output post-processing (decode + NMS)
 *   - Class index → TileId mapping
 *
 * What is NOT allowed to leak out:
 *   - Any TFLite import outside this module
 *   - Any reference to Majsoul, screenshot-specific logic, or class label strings
 *   - Any YOLO-specific output tensor layout details
 *
 * Screenshot-domain assumptions (explicitly flagged so future adapters know what to change):
 *   [SCREENSHOT-ASSUMPTION] Input images are clean, high-contrast digital renders.
 *   [SCREENSHOT-ASSUMPTION] Background is always uniform (dark, Majsoul-style).
 *   [SCREENSHOT-ASSUMPTION] Tiles are horizontally aligned and evenly spaced.
 *   [SCREENSHOT-ASSUMPTION] No blur, noise, or perspective distortion expected.
 *
 * Phase 3 replacement path:
 *   - Create RealPhotoAdapter : TileRecognitionEngine
 *   - Bind it instead of BaselineAdapter in AppModule
 *   - Delete or archive this file
 */
@Singleton
class BaselineAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : TileRecognitionEngine {

    override val modelInfo: ModelInfo = BaselineConfig.MODEL_INFO

    // TFLite interpreter — type is Any to avoid leaking the TFLite API into the signature.
    // TODO(Phase 1): replace Any with org.tensorflow.lite.Interpreter (import stays here)
    private var interpreter: Any? = null

    private val preprocessor  = ImagePreprocessor(context)
    private val postProcessor = YoloPostProcessor(TileIdMapper())
    private var ready = false

    /**
     * Loads the TFLite model from assets.
     * Call this from the DI initialisation path (e.g., an @Provides fun or init block).
     */
    fun load() {
        // TODO(Phase 1): implement model loading
        //   1. Open assets/${BaselineConfig.MODEL_ASSET_PATH}
        //   2. Create Interpreter.Options (+ GPU delegate if BaselineConfig.USE_GPU_DELEGATE)
        //   3. Construct Interpreter(modelBuffer, options)
        //   4. Set ready = true
        Timber.w("BaselineAdapter.load() not yet implemented — Phase 1 TODO")
    }

    override fun isReady(): Boolean = ready

    override suspend fun recognize(input: RecognitionInput): RecognitionOutcome {
        if (!ready) {
            return RecognitionOutcome.Failure.ModelNotReady("Baseline model not loaded")
        }
        return withContext(Dispatchers.Default) {
            try {
                val startMs = System.currentTimeMillis()

                // TODO(Phase 1): handle formats other than JPEG/PNG via input.format
                val bitmap: Bitmap = when (input) {
                    is RecognitionInput.BytesInput ->
                        BitmapFactory.decodeByteArray(input.bytes, 0, input.bytes.size)
                            ?: return@withContext RecognitionOutcome.Failure.InputError(
                                "Failed to decode image bytes (format=${input.format})"
                            )
                }

                // TODO(Phase 1): letterbox → model input tensor
                val letterboxResult = preprocessor.letterbox(bitmap)
                val inputTensor     = preprocessor.toModelInput(letterboxResult.resizedBitmap)

                // TODO(Phase 1): run interpreter, get output tensor
                val rawOutput = FloatArray(0) // placeholder

                // TODO(Phase 1): post-process raw output
                val detectedTiles = postProcessor.process(rawOutput, letterboxResult)

                val elapsedMs = System.currentTimeMillis() - startMs

                RecognitionOutcome.Success(
                    TileRecognitionResult(
                        tiles           = detectedTiles,
                        modelInfo       = modelInfo,
                        captureType     = input.captureType,
                        processingTimeMs = elapsedMs,
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "BaselineAdapter inference error")
                RecognitionOutcome.Failure.InferenceError(e)
            }
        }
    }

    override fun release() {
        // TODO(Phase 1): call interpreter.close()
        interpreter = null
        ready = false
    }
}
