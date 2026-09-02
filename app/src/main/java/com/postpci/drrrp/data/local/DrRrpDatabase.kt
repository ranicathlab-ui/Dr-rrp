package com.postpci.drrrp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.postpci.drrrp.data.local.dao.AlertDao
import com.postpci.drrrp.data.local.dao.BleedingEventDao
import com.postpci.drrrp.data.local.dao.DailyEntryDao
import com.postpci.drrrp.data.local.dao.MessageDao
import com.postpci.drrrp.data.local.dao.PatientBaselineDao
import com.postpci.drrrp.data.local.entity.AlertEntity
import com.postpci.drrrp.data.local.entity.BleedingEventEntity
import com.postpci.drrrp.data.local.entity.DailyEntryEntity
import com.postpci.drrrp.data.local.entity.MessageEntity
import com.postpci.drrrp.data.local.entity.PatientBaselineEntity
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        PatientBaselineEntity::class,
        DailyEntryEntity::class,
        BleedingEventEntity::class,
        AlertEntity::class,
        MessageEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class DrRrpDatabase : RoomDatabase() {
    abstract fun patientBaselineDao(): PatientBaselineDao
    abstract fun dailyEntryDao(): DailyEntryDao
    abstract fun bleedingEventDao(): BleedingEventDao
    abstract fun alertDao(): AlertDao
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DB_NAME = "drrrp-encrypted.db"

        /**
         * This is patient health data, so the on-disk SQLite file is encrypted at rest with
         * SQLCipher; the passphrase itself lives only in the Android Keystore-backed
         * EncryptedSharedPreferences (see [DatabaseKeyProvider]), not in code.
         *
         * `fallbackToDestructiveMigration` is a pre-release-only convenience — this app hasn't
         * shipped yet, so there's no real user data to preserve across schema changes while the
         * entity set is still growing stage to stage. Replace with real [androidx.room.migration.Migration]
         * steps (and drop this fallback) before any production release.
         *
         * IMPORTANT: the fallback only fires on a version *change* — Room still hard-fails with
         * "forgot to update the version number" if the schema drifts (e.g. a new entity) while
         * `version` stays the same. Bump `version` in the `@Database` annotation above every time
         * an entity/column changes, even pre-release.
         */
        fun build(context: Context): DrRrpDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = DatabaseKeyProvider.getOrCreateKey(context)
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context.applicationContext, DrRrpDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}
