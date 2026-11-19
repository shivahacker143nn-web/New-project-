package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationMessageEntity::class,
        MemoryFactEntity::class,
        CustomVoiceProfileEntity::class,
        AssistantRuleEntity::class,
        SavedPhotoEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AishuDatabase : RoomDatabase() {
    abstract fun aishuDao(): AishuDao

    companion object {
        @Volatile
        private var INSTANCE: AishuDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN pitchMultiplier REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN rateMultiplier REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN warmth REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN expressiveness REAL NOT NULL DEFAULT 0.65")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN similarityBoost REAL NOT NULL DEFAULT 0.82")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN baseGenderHint TEXT NOT NULL DEFAULT 'NEUTRAL'")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN estimatedPitchHz REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN sampleRateHz INTEGER NOT NULL DEFAULT 44100")
                db.execSQL("ALTER TABLE custom_voice_profiles ADD COLUMN clarityScore REAL NOT NULL DEFAULT 0.78")
            }
        }

        fun getInstance(context: Context): AishuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AishuDatabase::class.java,
                    "aishu_local_vault.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
