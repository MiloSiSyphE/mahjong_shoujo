package dev.mahjong.shoujo.correction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionDao {

    @Insert
    suspend fun insert(record: CorrectionRecord): Long

    @Query("SELECT * FROM correction_records WHERE is_exported = 0 ORDER BY timestamp_ms ASC")
    suspend fun getPendingExport(): List<CorrectionRecord>

    @Query("SELECT COUNT(*) FROM correction_records WHERE is_exported = 0")
    fun pendingExportCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM correction_records WHERE was_model_wrong = 1")
    fun totalCorrectionCount(): Flow<Int>

    @Update
    suspend fun update(record: CorrectionRecord)

    @Query("UPDATE correction_records SET is_exported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)
}
