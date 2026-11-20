package com.example.etikoppakavoterdirectory.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VoterEntity::class,
        DatasetInfoEntity::class,
        VoterUserDataEntity::class,
        TagEntity::class,
        VoterTagEntity::class,
        HouseholdEntity::class,
        HouseholdMemberEntity::class,
        HouseholdLockEntity::class,
        BlockLabelEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class VoterDatabase : RoomDatabase() {
    abstract fun voterDao(): VoterDao
    abstract fun userDataDao(): UserDataDao
    abstract fun householdDao(): HouseholdDao

    companion object {
        const val NAME = "voters.db"

        /**
         * Version 2 -> 3 (app 2.0): adds the household tables and the default neutral tags.
         * Purely additive: no existing table, row or column is touched, so favourites, visits,
         * visit times, blocks, notes, tags and tag assignments are all preserved.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `households` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `house_no` TEXT, `created_at` INTEGER NOT NULL DEFAULT 0, " +
                        "`updated_at` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `household_members` (`gp_serial_no` INTEGER NOT NULL, " +
                        "`household_id` INTEGER NOT NULL, PRIMARY KEY(`gp_serial_no`), " +
                        "FOREIGN KEY(`household_id`) REFERENCES `households`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_household_members_household_id` ON `household_members` (`household_id`)")
                insertDefaultTags(db)
            }
        }

        /**
         * Version 3 -> 4 (app 3.0, all 14 wards).
         *
         * Voters were identified by GP serial number alone, which is not guaranteed to be unique
         * across wards. Every table is re-keyed on the stable voter id (ward * 100000 + serial).
         * All existing rows are copied - nothing is deleted or reset:
         *  - voters keep every column (the old data is all Ward 11);
         *  - favourites, visits, visit times, blocks, notes and tag links move to the new voter id;
         *  - existing households were all created by the user, so they become *locked* households
         *    and their members are locked, which means automatic grouping never changes them;
         *  - dataset_info becomes one row per ward (the stored dataset version is kept, so nothing is
         *    re-imported).
         * The data is first copied into temporary tables, the old tables are dropped child-first
         * (no foreign-key cascade can fire), the new tables are created with Room's exact schema and
         * the rows are copied back. No table rename is used, so it works with the older SQLite of
         * Android 8. The whole migration runs in one transaction: it either completes or changes nothing.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val legacy = VoterIds.LEGACY_WARD
                val f = VoterIds.FACTOR
                fun idOf(serialExpr: String) =
                    "(COALESCE((SELECT ov.ward_no FROM voters ov WHERE ov.gp_serial_no = $serialExpr), $legacy) * $f + $serialExpr)"

                // 1. Copy everything into temporary tables (computing the new ids).
                db.execSQL("CREATE TEMP TABLE _m_voters AS SELECT (ward_no * $f + gp_serial_no) AS voter_id, * FROM voters")
                db.execSQL(
                    "CREATE TEMP TABLE _m_user AS SELECT ${idOf("u.gp_serial_no")} AS voter_id, u.favourite, u.visited, " +
                        "u.visited_at, u.block, u.note, u.updated_at FROM voter_user_data u"
                )
                db.execSQL("CREATE TEMP TABLE _m_vtags AS SELECT ${idOf("t.gp_serial_no")} AS voter_id, t.tag_id FROM voter_tags t")
                db.execSQL("CREATE TEMP TABLE _m_members AS SELECT ${idOf("m.gp_serial_no")} AS voter_id, m.household_id FROM household_members m")
                db.execSQL(
                    "CREATE TEMP TABLE _m_households AS SELECT h.id, COALESCE((SELECT ov.ward_no FROM household_members hm " +
                        "JOIN voters ov ON ov.gp_serial_no = hm.gp_serial_no WHERE hm.household_id = h.id LIMIT 1), $legacy) AS ward_no, " +
                        "h.name, h.house_no, h.created_at, h.updated_at FROM households h"
                )
                db.execSQL(
                    "CREATE TEMP TABLE _m_info AS SELECT COALESCE((SELECT ward_no FROM voters LIMIT 1), $legacy) AS ward_no, " +
                        "generated_at, record_count, needs_review_count, imported_at FROM dataset_info"
                )

                // 2. Drop the old tables, children first.
                listOf("household_members", "households", "voter_tags", "voter_user_data", "voters", "dataset_info")
                    .forEach { db.execSQL("DROP TABLE `$it`") }

                // 3. Create the version-4 tables (identical to Room's generated schema 4.json).
                V4_SCHEMA.forEach(db::execSQL)

                // 4. Copy the rows back.
                val voterCols = "`voter_id`, `gp_serial_no`, `ward_no`, `name_en`, `name_te`, `relationship`, `relative_name_en`, " +
                    "`relative_name_te`, `age`, `gender`, `house_no`, `assembly_serial`, `epic_no`, `photo_filename`, " +
                    "`photo_width_px`, `photo_height_px`, `pdf_page`, `pdf_relation_label`, `excel_review_status`, " +
                    "`needs_review`, `review_issue_codes`, `search_text`"
                db.execSQL("INSERT INTO voters ($voterCols) SELECT $voterCols FROM _m_voters")
                db.execSQL(
                    "INSERT INTO dataset_info (ward_no, generated_at, record_count, needs_review_count, imported_at, grouping_key) " +
                        "SELECT ward_no, generated_at, record_count, needs_review_count, imported_at, NULL FROM _m_info"
                )
                db.execSQL(
                    "INSERT INTO voter_user_data (voter_id, favourite, visited, visited_at, block, note, updated_at) " +
                        "SELECT voter_id, favourite, visited, visited_at, block, note, updated_at FROM _m_user"
                )
                db.execSQL("INSERT OR IGNORE INTO voter_tags (voter_id, tag_id) SELECT voter_id, tag_id FROM _m_vtags")
                db.execSQL(
                    "INSERT INTO households (id, ward_no, name, house_no, created_at, updated_at, auto_key, locked, needs_review, review_note) " +
                        "SELECT id, ward_no, name, house_no, created_at, updated_at, NULL, 1, 0, NULL FROM _m_households"
                )
                db.execSQL("INSERT INTO household_members (voter_id, household_id) SELECT voter_id, household_id FROM _m_members")
                db.execSQL("INSERT OR IGNORE INTO household_locks (voter_id, locked_at) SELECT voter_id, $now FROM _m_members")

                listOf("_m_voters", "_m_user", "_m_vtags", "_m_members", "_m_households", "_m_info")
                    .forEach { db.execSQL("DROP TABLE `$it`") }
            }
        }

        /** CREATE statements of the version-4 tables that MIGRATION_3_4 rebuilds (copied from schemas/.../4.json). */
        internal val V4_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `voters` (`voter_id` INTEGER NOT NULL, `gp_serial_no` INTEGER NOT NULL, `ward_no` INTEGER NOT NULL, `name_en` TEXT, `name_te` TEXT, `relationship` TEXT, `relative_name_en` TEXT, `relative_name_te` TEXT, `age` INTEGER, `gender` TEXT, `house_no` TEXT, `assembly_serial` TEXT, `epic_no` TEXT, `photo_filename` TEXT, `photo_width_px` INTEGER, `photo_height_px` INTEGER, `pdf_page` INTEGER, `pdf_relation_label` TEXT, `excel_review_status` TEXT, `needs_review` INTEGER NOT NULL, `review_issue_codes` TEXT NOT NULL, `search_text` TEXT NOT NULL, PRIMARY KEY(`voter_id`))",
            "CREATE INDEX IF NOT EXISTS `index_voters_house_no` ON `voters` (`house_no`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_voters_ward_no_gp_serial_no` ON `voters` (`ward_no`, `gp_serial_no`)",
            "CREATE TABLE IF NOT EXISTS `dataset_info` (`ward_no` INTEGER NOT NULL, `generated_at` TEXT NOT NULL, `record_count` INTEGER NOT NULL, `needs_review_count` INTEGER NOT NULL, `imported_at` INTEGER NOT NULL, `grouping_key` TEXT, PRIMARY KEY(`ward_no`))",
            "CREATE TABLE IF NOT EXISTS `voter_user_data` (`voter_id` INTEGER NOT NULL, `favourite` INTEGER NOT NULL DEFAULT 0, `visited` INTEGER NOT NULL DEFAULT 0, `visited_at` INTEGER, `block` TEXT, `note` TEXT, `updated_at` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`voter_id`))",
            "CREATE TABLE IF NOT EXISTS `voter_tags` (`voter_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`voter_id`, `tag_id`), FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_voter_tags_tag_id` ON `voter_tags` (`tag_id`)",
            "CREATE TABLE IF NOT EXISTS `households` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ward_no` INTEGER NOT NULL, `name` TEXT NOT NULL, `house_no` TEXT, `created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL DEFAULT 0, `auto_key` TEXT, `locked` INTEGER NOT NULL DEFAULT 0, `needs_review` INTEGER NOT NULL DEFAULT 0, `review_note` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_households_ward_no` ON `households` (`ward_no`)",
            "CREATE TABLE IF NOT EXISTS `household_members` (`voter_id` INTEGER NOT NULL, `household_id` INTEGER NOT NULL, PRIMARY KEY(`voter_id`), FOREIGN KEY(`household_id`) REFERENCES `households`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_household_members_household_id` ON `household_members` (`household_id`)",
            "CREATE TABLE IF NOT EXISTS `household_locks` (`voter_id` INTEGER NOT NULL, `locked_at` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`voter_id`))",
        )

        /** Version 4 -> 5 (app 3.1): adds the optional per-ward block labels. Purely additive. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `block_labels` (`ward_no` INTEGER NOT NULL, `block` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, `updated_at` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`ward_no`, `block`))"
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        /** INSERT OR IGNORE + the case-insensitive unique index on tags.name: never creates duplicates. */
        fun insertDefaultTags(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            DefaultTags.NAMES.forEach { name ->
                db.execSQL("INSERT OR IGNORE INTO tags (name, created_at) VALUES (?, ?)", arrayOf<Any>(name, now))
            }
        }

        private val seedOnCreate = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = insertDefaultTags(db)
        }

        /** Real migrations plus first-install defaults. Never destructive (no fallbackToDestructiveMigration). */
        fun <T : RoomDatabase> RoomDatabase.Builder<T>.configure(): RoomDatabase.Builder<T> =
            addMigrations(*ALL_MIGRATIONS).addCallback(seedOnCreate)

        @Volatile
        private var instance: VoterDatabase? = null

        fun get(context: Context): VoterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, VoterDatabase::class.java, NAME)
                    .configure()
                    .build()
                    .also { instance = it }
            }
    }
}
