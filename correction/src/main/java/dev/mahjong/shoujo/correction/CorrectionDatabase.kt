package dev.mahjong.shoujo.correction

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.mahjong.shoujo.cv.api.TileId
import dev.mahjong.shoujo.cv.api.model.CaptureType
import dev.mahjong.shoujo.cv.api.model.LayoutRole

@Database(entities = [CorrectionRecord::class], version = 2, exportSchema = true)
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
    @TypeConverter fun correctionTypeToString(v: CorrectionType): String = v.name
    @TypeConverter fun stringToCorrectionType(v: String): CorrectionType = CorrectionType.valueOf(v)
    @TypeConverter fun layoutRoleToString(v: LayoutRole?): String? = v?.name
    @TypeConverter fun stringToLayoutRole(v: String?): LayoutRole? = v?.let { LayoutRole.valueOf(it) }
}
