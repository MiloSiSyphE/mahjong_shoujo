package dev.mahjong.shoujo.correction

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.CaptureType

@Database(entities = [CorrectionRecord::class], version = 1, exportSchema = true)
@TypeConverters(CorrectionTypeConverters::class)
abstract class CorrectionDatabase : RoomDatabase() {
    abstract fun correctionDao(): CorrectionDao

    companion object {
        fun create(context: Context): CorrectionDatabase =
            Room.databaseBuilder(context, CorrectionDatabase::class.java, "corrections.db")
                .fallbackToDestructiveMigration() // TODO: add proper migrations before v1 release
                .build()
    }
}

class CorrectionTypeConverters {
    @TypeConverter fun tileIdToString(v: TileId?): String? = v?.name
    @TypeConverter fun stringToTileId(v: String?): TileId? = v?.let { TileId.valueOf(it) }
    @TypeConverter fun captureTypeToString(v: CaptureType): String = v.name
    @TypeConverter fun stringToCaptureType(v: String): CaptureType = CaptureType.valueOf(v)
}
