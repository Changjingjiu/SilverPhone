package com.silverphone.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.silverphone.app.domain.CountryCode
import com.silverphone.app.domain.FontPreset

/**
 * The single Room database.
 *
 * version = 1 with schema export on: there is no historical schema to migrate
 * from and this build must not invent migration paths it does not need. There is
 * deliberately no destructive-migration fallback, because silently emptying the
 * database would destroy the only copy of the family's configuration.
 */
@Database(
    entities = [
        ContactEntity::class,
        ContactPhotoEntity::class,
        AppSettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    abstract fun contactPhotoDao(): ContactPhotoDao

    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        private const val DATABASE_NAME = "silverphone.db"

        /**
         * Adds the language and dialling-prefix preferences.
         *
         * A real migration rather than a destructive rebuild: existing contacts,
         * photos, the font size and the ordering all have to survive an update. Both
         * columns have defaults, so the ALTER is safe on a populated table.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN languageTag TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN countryCode TEXT NOT NULL DEFAULT '+86'",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            // Seed the single settings row so reads never have to
                            // cope with a missing row.
                            db.execSQL(
                                "INSERT INTO app_settings " +
                                    "(id, fontPreset, contactsRevision, languageTag, countryCode) " +
                                    "VALUES (?, ?, ?, ?, ?)",
                                arrayOf<Any>(
                                    AppSettingsEntity.SINGLETON_ID,
                                    FontPreset.DEFAULT.name,
                                    0L,
                                    "",
                                    CountryCode.DEFAULT,
                                ),
                            )
                        }
                    },
                )
                .build()
    }
}
