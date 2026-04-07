package dev.mahjong.shoujo.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mahjong.shoujo.correction.CorrectionDao
import dev.mahjong.shoujo.correction.CorrectionDatabase
import dev.mahjong.shoujo.correction.JsonlCorrectionExporter
import dev.mahjong.shoujo.cv.api.CorrectionRecordExporter
import dev.mahjong.shoujo.cv.api.TileRecognitionEngine
import dev.mahjong.shoujo.cv.baseline.BaselineAdapter
import dev.mahjong.shoujo.domain.engine.ScoringEngine
import dev.mahjong.shoujo.domain.engine.ScoringEngineImpl
import javax.inject.Singleton

/**
 * Central DI wiring.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  MODEL REPLACEMENT POINT (Phase 3)                                      │
 * │                                                                          │
 * │  To swap the baseline model for a real-photo model:                     │
 * │    1. Implement RealPhotoAdapter : TileRecognitionEngine               │
 * │    2. Change provideTileRecognitionEngine() to return RealPhotoAdapter │
 * │    3. Nothing else in the codebase changes.                             │
 * └─────────────────────────────────────────────────────────────────────────┘
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── CV layer ───────────────────────────────────────────────────────────────

    /**
     * Binds the active recognition engine.
     *
     * Phase 0: This returns [BaselineAdapter] but the adapter is not-ready (model not loaded).
     *          The UI handles the not-ready state and shows "Enter manually" as the only option.
     * Phase 1: Call adapter.load() here (or in an initializer) to activate recognition.
     * Phase 3: Replace BaselineAdapter with RealPhotoAdapter here.
     */
    @Provides
    @Singleton
    fun provideTileRecognitionEngine(
        @ApplicationContext context: Context,
    ): TileRecognitionEngine {
        val adapter = BaselineAdapter(context)
        // TODO(Phase 1): adapter.load()
        return adapter
    }

    // ── Correction logging ─────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideCorrectionDatabase(@ApplicationContext context: Context): CorrectionDatabase =
        CorrectionDatabase.create(context)

    @Provides
    @Singleton
    fun provideCorrectionDao(db: CorrectionDatabase): CorrectionDao = db.correctionDao()

    @Provides
    @Singleton
    fun provideCorrectionRecordExporter(exporter: JsonlCorrectionExporter): CorrectionRecordExporter =
        exporter

    // ── Scoring engine ─────────────────────────────────────────────────────────

    /**
     * The scoring engine is pure domain logic — no Android, no CV, no model.
     * Future implementations (e.g., a faster algorithm or a different rule-set)
     * only require changing this binding.
     */
    @Provides
    @Singleton
    fun provideScoringEngine(): ScoringEngine = ScoringEngineImpl()
}
