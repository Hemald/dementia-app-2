package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.FamilyRecognitionTrial
import com.example.data.model.HydrationRecord
import com.example.data.model.Person
import com.example.data.model.PersonalBaseline
import com.example.data.model.PersonalCognitiveStateRecord
import com.example.data.model.PhotoAssociation
import com.example.data.model.PhotoMemory
import com.example.data.model.SelectionCluster
import com.example.data.model.UserProfile
import com.example.data.model.UserPreferences
import com.example.data.model.UserSession

@Database(
    entities = [
        UserProfile::class,
        PersonalBaseline::class,
        PersonalCognitiveStateRecord::class,
        HydrationRecord::class,
        UserSession::class,
        UserPreferences::class,
        Person::class,
        PhotoMemory::class,
        FamilyRecognitionTrial::class,
        SelectionCluster::class,
        PhotoAssociation::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun personalCognitiveStateDao(): PersonalCognitiveStateDao
    abstract fun hydrationDao(): HydrationDao
    abstract fun userSessionDao(): UserSessionDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun personDao(): PersonDao
    abstract fun photoMemoryDao(): PhotoMemoryDao
    abstract fun familyRecognitionTrialDao(): FamilyRecognitionTrialDao
    abstract fun selectionClusterDao(): SelectionClusterDao
    abstract fun photoAssociationDao(): PhotoAssociationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `people` (
                        `person_id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `relationship` TEXT NOT NULL,
                        `cover_photo_uri` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`person_id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photo_memories` (
                        `photo_id` TEXT NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `local_uri` TEXT NOT NULL,
                        `content_hash` TEXT,
                        `taken_timestamp` INTEGER,
                        `added_timestamp` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `optional_location` TEXT,
                        `display_order` INTEGER NOT NULL DEFAULT 0,
                        `width` INTEGER,
                        `height` INTEGER,
                        PRIMARY KEY(`photo_id`),
                        FOREIGN KEY(`person_id`) REFERENCES `people`(`person_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_memories_person_id` ON `photo_memories` (`person_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_memories_person_id_content_hash` ON `photo_memories` (`person_id`, `content_hash`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `family_recognition_trials` (
                        `trial_id` TEXT NOT NULL,
                        `session_id` INTEGER NOT NULL,
                        `person_id` TEXT NOT NULL,
                        `photo_id` TEXT NOT NULL,
                        `trial_started_at` INTEGER NOT NULL,
                        `photo_shown_at` INTEGER NOT NULL,
                        `question_shown_at` INTEGER NOT NULL,
                        `response_at` INTEGER NOT NULL,
                        `recognition_response` TEXT NOT NULL,
                        `recall_response` TEXT,
                        `response_time_ms` INTEGER NOT NULL,
                        `selected` INTEGER NOT NULL,
                        `selection_order` INTEGER,
                        `selection_timestamp` INTEGER,
                        `skipped` INTEGER NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `raw_metadata` TEXT,
                        PRIMARY KEY(`trial_id`),
                        FOREIGN KEY(`person_id`) REFERENCES `people`(`person_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`photo_id`) REFERENCES `photo_memories`(`photo_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_recognition_trials_session_id` ON `family_recognition_trials` (`session_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_recognition_trials_person_id` ON `family_recognition_trials` (`person_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_recognition_trials_photo_id` ON `family_recognition_trials` (`photo_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `selection_clusters` (
                        `cluster_id` TEXT NOT NULL,
                        `session_id` INTEGER NOT NULL,
                        `selected_photo_ids` TEXT NOT NULL,
                        `selection_timestamps` TEXT NOT NULL,
                        `selection_orders` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `directory_person_id` TEXT,
                        `user_response` TEXT,
                        `is_saved_connection` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`cluster_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_selection_clusters_session_id` ON `selection_clusters` (`session_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photo_associations` (
                        `association_id` TEXT NOT NULL,
                        `cluster_id` TEXT NOT NULL,
                        `photo_a_id` TEXT NOT NULL,
                        `photo_b_id` TEXT NOT NULL,
                        `person_a_id` TEXT NOT NULL,
                        `person_b_id` TEXT NOT NULL,
                        `evidence_features` TEXT NOT NULL,
                        `association_score` REAL NOT NULL,
                        `algorithm_version` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'combined',
                        `user_confirmed` INTEGER NOT NULL DEFAULT 0,
                        `user_response` TEXT,
                        PRIMARY KEY(`association_id`),
                        FOREIGN KEY(`cluster_id`) REFERENCES `selection_clusters`(`cluster_id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_associations_cluster_id` ON `photo_associations` (`cluster_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_associations_photo_a_id_photo_b_id` ON `photo_associations` (`photo_a_id`, `photo_b_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_associations_person_a_id_person_b_id` ON `photo_associations` (`person_a_id`, `person_b_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_associations_user_confirmed` ON `photo_associations` (`user_confirmed`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photo_associations` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'combined'")
                db.execSQL("ALTER TABLE `photo_associations` ADD COLUMN `user_confirmed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `photo_associations` ADD COLUMN `user_response` TEXT")

                db.execSQL("ALTER TABLE `selection_clusters` ADD COLUMN `directory_person_id` TEXT")
                db.execSQL("ALTER TABLE `selection_clusters` ADD COLUMN `user_response` TEXT")
                db.execSQL("ALTER TABLE `selection_clusters` ADD COLUMN `is_saved_connection` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_associations_user_confirmed` ON `photo_associations` (`user_confirmed`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photo_associations` ADD COLUMN `confirmed_at` INTEGER")
                db.execSQL("ALTER TABLE `photo_associations` ADD COLUMN `user_note` TEXT")
                db.execSQL("ALTER TABLE `photo_associations` ADD COLUMN `voice_note_uri` TEXT")

                db.execSQL("ALTER TABLE `selection_clusters` ADD COLUMN `user_note` TEXT")
                db.execSQL("ALTER TABLE `selection_clusters` ADD COLUMN `voice_note_uri` TEXT")
                db.execSQL("ALTER TABLE `selection_clusters` ADD COLUMN `user_reflection` TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cognitive_assistant.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                 .fallbackToDestructiveMigration(dropAllTables = false)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
