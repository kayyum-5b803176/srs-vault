package com.srspassword.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.srspassword.app.algorithm.CardState

@Database(
    entities  = [PasswordCard::class],
    version   = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passwordCardDao(): PasswordCardDao
}

class Converters {
    @TypeConverter fun fromCardState(v: CardState): String = v.name
    @TypeConverter fun toCardState(v: String): CardState = CardState.valueOf(v)

    @TypeConverter fun fromReviewType(v: ReviewType): String = v.name
    @TypeConverter fun toReviewType(v: String): ReviewType =
        runCatching { ReviewType.valueOf(v) }.getOrDefault(ReviewType.VISUAL)
}

/** Adds reviewType column; existing rows default to VISUAL. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE password_cards ADD COLUMN reviewType TEXT NOT NULL DEFAULT 'VISUAL'"
        )
    }
}
