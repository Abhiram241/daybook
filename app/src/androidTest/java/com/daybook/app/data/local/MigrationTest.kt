package com.daybook.app.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * Migration coverage for the Room schema (REV-32 / REV-43).
 *
 * Only 3→4 … 6→7 are exercised here: schema v2 predates `exportSchema = true`, so there is
 * no `2.json` for MigrationTestHelper to open. The 1→2 gap is handled at runtime by
 * `fallbackToDestructiveMigrationFrom(1)` in DatabaseModule.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate3To4_addsNotifPermissionAskedColumn() {
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        db.query("SELECT * FROM app_settings").use { cursor ->
            assertTrue(cursor.columnNames.contains("notif_permission_asked"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_dropsColorTagColumn() {
        helper.createDatabase(TEST_DB, 4).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)

        db.query("SELECT * FROM app_settings").use { cursor ->
            assertEquals(-1, cursor.getColumnIndex("color_tag"))
            assertTrue(cursor.columnNames.contains("accent_color"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_addsProfilePhotoPathColumn() {
        helper.createDatabase(TEST_DB, 5).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        db.query("SELECT * FROM app_settings").use { cursor ->
            assertTrue(cursor.columnNames.contains("profile_photo_path"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_addsFontChoiceColumn() {
        helper.createDatabase(TEST_DB, 6).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        db.query("SELECT * FROM app_settings").use { cursor ->
            assertTrue(cursor.columnNames.contains("font_choice"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To8_addsColumnsAndCategoryTable() {
        helper.createDatabase(TEST_DB, 7).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        db.query("SELECT * FROM habits").use { assertTrue(it.columnNames.contains("type")) }
        db.query("SELECT * FROM food_med_occurrences").use { assertTrue(it.columnNames.contains("description")) }
        db.query("SELECT * FROM food_med_tasks").use { assertTrue(it.columnNames.contains("custom_category")) }
        db.query("SELECT * FROM app_settings").use { assertTrue(it.columnNames.contains("habit_checkin_time")) }
        db.query("SELECT * FROM custom_categories").use { assertTrue(it.columnNames.contains("name")) }
    }

    /** The load-bearing one: a habit that existed at v7 must come out of the migration INDIVIDUAL. */
    @Test
    @Throws(IOException::class)
    fun migrate7To8_preservesExistingRowsAndDefaultsHabitTypeToIndividual() {
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL(
                "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                    "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        db.query("SELECT title, type, times_json FROM habits WHERE id = 'h1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Stretch", c.getString(0))
            assertEquals("INDIVIDUAL", c.getString(1))
            assertEquals("07:00", c.getString(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_addsPromptColumnAndPromptTable() {
        helper.createDatabase(TEST_DB, 8).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)
        db.query("SELECT * FROM food_med_tasks").use { assertTrue(it.columnNames.contains("prompt_message")) }
        db.query("SELECT * FROM custom_prompts").use { assertTrue(it.columnNames.contains("name")) }
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_preservesRows() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id,custom_category) " +
                    "VALUES ('t1','Lunch','FOOD','AUTO','restaurant',1,'12:00','',0,10,2001,NULL)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)
        db.query("SELECT label, prompt_message FROM food_med_tasks WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Lunch", c.getString(0)); assertTrue(c.isNull(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11_addsOutsideFoodColumns() {
        helper.createDatabase(TEST_DB, 10).apply {
            execSQL(
                "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                    "VALUES ('t1','Lunch','FOOD','AUTO','restaurant',1,'12:00','',0,10,2001)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11)
        db.query("SELECT * FROM food_med_occurrences").use {
            assertTrue(it.columnNames.contains("outside_food"))
        }
        db.query("SELECT label, default_outside_food FROM food_med_tasks WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Lunch", c.getString(0))
            assertTrue(c.isNull(1))   // existing row -> NULL, i.e. OFF
        }
    }

    /**
     * v11 -> v12: adds the two `occurrence_id` indices (D6) and drops the dead `app_settings`
     * columns `backup_reminder_enabled` / `last_backup_export_at`. Populated tables must survive
     * with every value intact.
     */
    @Test
    @Throws(IOException::class)
    fun migrate11To12_addsEventIndices_dropsDeadSettingsColumns_keepsData() {
        helper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id,type) " +
                    "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL')"
            )
            execSQL(
                "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                    "VALUES ('t1','Lunch','FOOD','AUTO','restaurant',1,'12:00','',0,10,2001)"
            )
            execSQL(
                "INSERT INTO habit_occurrences (id,habit_id,scheduled_for,status,snooze_count," +
                    "responded_at,notification_id,created_at) " +
                    "VALUES ('h1:100','h1',100,'COMPLETED',0,120,1001,1)"
            )
            execSQL(
                "INSERT INTO food_med_occurrences (id,task_id,scheduled_for,status,snooze_count," +
                    "response_text,responded_at,notification_id,created_at) " +
                    "VALUES ('t1:200','t1',200,'LOGGED',0,'soup',210,2001,1)"
            )
            execSQL(
                "INSERT INTO habit_events (occurrence_id,action,timestamp) VALUES ('h1:100','SHOWN',90)"
            )
            execSQL(
                "INSERT INTO food_med_events (occurrence_id,action,timestamp) VALUES ('t1:200','REPLIED',210)"
            )
            execSQL(
                "INSERT INTO app_settings (id,default_snooze_minutes,backup_reminder_enabled," +
                    "last_backup_export_at,onboarding_completed,user_name,accent_color," +
                    "notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time) " +
                    "VALUES (1,15,1,9999,1,'Alex','LAVENDER',1,'/tmp/p.jpg','LITERATA','08:30')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)

        // Indices created.
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN " +
                "('index_habit_events_occurrence_id','index_food_med_events_occurrence_id')"
        ).use { c -> assertEquals(2, c.count) }

        // Event rows intact.
        db.query("SELECT occurrence_id, action FROM habit_events").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("h1:100", c.getString(0)); assertEquals("SHOWN", c.getString(1))
        }
        db.query("SELECT occurrence_id FROM food_med_events").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("t1:200", c.getString(0))
        }

        // Dead columns gone, every kept value preserved.
        db.query("SELECT * FROM app_settings WHERE id = 1").use { c ->
            assertEquals(-1, c.getColumnIndex("backup_reminder_enabled"))
            assertEquals(-1, c.getColumnIndex("last_backup_export_at"))
            assertTrue(c.moveToFirst())
            assertEquals(15, c.getInt(c.getColumnIndex("default_snooze_minutes")))
            assertEquals("Alex", c.getString(c.getColumnIndex("user_name")))
            assertEquals("LAVENDER", c.getString(c.getColumnIndex("accent_color")))
            assertEquals("LITERATA", c.getString(c.getColumnIndex("font_choice")))
            assertEquals("08:30", c.getString(c.getColumnIndex("habit_checkin_time")))
            assertEquals("/tmp/p.jpg", c.getString(c.getColumnIndex("profile_photo_path")))
        }

        // Occurrence rows untouched.
        db.query("SELECT status FROM habit_occurrences WHERE id = 'h1:100'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("COMPLETED", c.getString(0))
        }
        db.query("SELECT response_text FROM food_med_occurrences WHERE id = 't1:200'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("soup", c.getString(0))
        }
    }

    // ------------------------------------------------------------------ v12 -> v13 (v0.5.3 Phase 2)

    /** Full v12 fixture: both definition tables, both occurrence tables, both event tables (incl.
     *  one ORPHANED event), app_settings. Shared by the v12->v13 cases below. */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedV12() {
        execSQL(
            "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                "active_days_json,is_archived,snooze_interval_minutes,notification_id,type) " +
                "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL')"
        )
        execSQL(
            "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                "VALUES ('t1','Lunch','FOOD','AUTO','restaurant',1,'12:00','',0,10,2001)"
        )
        // scheduled_for = 2024-03-09 16:00:00 UTC -> local_date 2024-03-09 on a UTC emulator.
        execSQL(
            "INSERT INTO habit_occurrences (id,habit_id,scheduled_for,status,snooze_count," +
                "responded_at,notification_id,created_at) " +
                "VALUES ('h1:1710000000000','h1',1710000000000,'COMPLETED',0,1710000005000,1001,1)"
        )
        execSQL(
            "INSERT INTO food_med_occurrences (id,task_id,scheduled_for,status,snooze_count," +
                "response_text,responded_at,notification_id,created_at) " +
                "VALUES ('t1:1710000600000','t1',1710000600000,'LOGGED',0,'soup',1710000700000,2001,1)"
        )
        execSQL("INSERT INTO habit_events (occurrence_id,action,timestamp) VALUES ('h1:1710000000000','SHOWN',1709999999000)")
        execSQL("INSERT INTO habit_events (occurrence_id,action,timestamp) VALUES ('h1:1710000000000','COMPLETED',1710000005000)")
        execSQL("INSERT INTO food_med_events (occurrence_id,action,timestamp) VALUES ('t1:1710000600000','REPLIED',1710000700000)")
        // Orphaned: occurrence_id points at a row that does not exist -> item_id must stay NULL.
        execSQL("INSERT INTO food_med_events (occurrence_id,action,timestamp) VALUES ('t1:9999999999999','SHOWN',1)")
        execSQL(
            "INSERT INTO app_settings (id,default_snooze_minutes,onboarding_completed,user_name," +
                "accent_color,notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time) " +
                "VALUES (1,15,1,'Alex','LAVENDER',1,'/tmp/p.jpg','LITERATA','08:30')"
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_addsScheduledForIndices() {
        helper.createDatabase(TEST_DB, 12).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN " +
                "('index_habit_occurrences_scheduled_for','index_food_med_occurrences_scheduled_for'," +
                "'index_habit_occurrences_status_scheduled_for','index_food_med_occurrences_status_scheduled_for')"
        ).use { c -> assertEquals(4, c.count) }
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_addsLocalDateAndBackfillsFromScheduledFor() {
        helper.createDatabase(TEST_DB, 12).apply { seedV12(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        // The emulator's default zone is UTC, so SQLite `localtime` == UTC here.
        val expected = Instant.ofEpochMilli(1710000000000L)
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
        db.query("SELECT local_date FROM habit_occurrences WHERE id = 'h1:1710000000000'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(!c.isNull(0))
            assertEquals(expected, c.getString(0))
        }
        // Column value must equal a fresh evaluation of the backfill expression for that row.
        db.query(
            "SELECT local_date, date(scheduled_for/1000,'unixepoch','localtime') AS expr " +
                "FROM food_med_occurrences WHERE id = 't1:1710000600000'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(c.getString(1), c.getString(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_addsItemIdAndBackfillsFromOccurrence() {
        helper.createDatabase(TEST_DB, 12).apply { seedV12(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.query("SELECT item_id FROM habit_events WHERE occurrence_id = 'h1:1710000000000' AND action = 'SHOWN'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("h1", c.getString(0))
        }
        db.query("SELECT item_id FROM food_med_events WHERE occurrence_id = 't1:1710000600000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("t1", c.getString(0))
        }
        // Orphaned event -> item_id IS NULL.
        db.query("SELECT item_id FROM food_med_events WHERE occurrence_id = 't1:9999999999999'").use { c ->
            assertTrue(c.moveToFirst()); assertTrue(c.isNull(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_addsItemIdTimestampIndices() {
        helper.createDatabase(TEST_DB, 12).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name IN " +
                "('index_habit_events_item_id_timestamp','index_food_med_events_item_id_timestamp')"
        ).use { c -> assertEquals(2, c.count) }
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13_keepsEveryV12Row() {
        helper.createDatabase(TEST_DB, 12).apply { seedV12(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.query("SELECT title, type, times_json FROM habits WHERE id = 'h1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Stretch", c.getString(0)); assertEquals("INDIVIDUAL", c.getString(1)); assertEquals("07:00", c.getString(2))
        }
        db.query("SELECT label FROM food_med_tasks WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Lunch", c.getString(0))
        }
        db.query("SELECT status FROM habit_occurrences WHERE id = 'h1:1710000000000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("COMPLETED", c.getString(0))
        }
        db.query("SELECT response_text, status FROM food_med_occurrences WHERE id = 't1:1710000600000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("soup", c.getString(0)); assertEquals("LOGGED", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM habit_events").use { c -> assertTrue(c.moveToFirst()); assertEquals(2, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM food_med_events").use { c -> assertTrue(c.moveToFirst()); assertEquals(2, c.getInt(0)) }
        db.query("SELECT default_snooze_minutes, user_name, accent_color, font_choice, habit_checkin_time FROM app_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(15, c.getInt(0)); assertEquals("Alex", c.getString(1)); assertEquals("LAVENDER", c.getString(2))
            assertEquals("LITERATA", c.getString(3)); assertEquals("08:30", c.getString(4))
        }
    }

    // ------------------------------------------------------------------ v13 -> v14 (v0.5.4 Phase 2)

    /** v13 fixture with a JOURNAL task (+ LOGGED occurrence + REPLIED event) AND a FOOD task
     *  (+ occurrence + event), plus a habit and app_settings. The D5 wipe must remove only the
     *  JOURNAL rows. */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedV13WithJournal() {
        execSQL(
            "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                "active_days_json,is_archived,snooze_interval_minutes,notification_id,type,local_date) " +
                "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL',NULL)"
        )
        execSQL(
            "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                "VALUES ('food1','Lunch','FOOD','AUTO','restaurant',1,'12:00','',0,10,2001)"
        )
        execSQL(
            "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                "VALUES ('jrnl1','Journal','JOURNAL','AUTO','book',1,'21:00','',0,10,2002)"
        )
        // FOOD occurrence + event — must SURVIVE the migration untouched.
        execSQL(
            "INSERT INTO food_med_occurrences (id,task_id,scheduled_for,status,snooze_count," +
                "response_text,responded_at,notification_id,created_at,description,local_date) " +
                "VALUES ('food1:1710000600000','food1',1710000600000,'LOGGED',0,'soup',1710000700000,2001,1,NULL,'2024-03-09')"
        )
        execSQL(
            "INSERT INTO food_med_events (occurrence_id,action,timestamp,item_id) " +
                "VALUES ('food1:1710000600000','REPLIED',1710000700000,'food1')"
        )
        // JOURNAL occurrence + event — must be DELETED (D5).
        execSQL(
            "INSERT INTO food_med_occurrences (id,task_id,scheduled_for,status,snooze_count," +
                "response_text,responded_at,notification_id,created_at,description,local_date) " +
                "VALUES ('jrnl1:1710004200000','jrnl1',1710004200000,'LOGGED',0,'a good day'," +
                "1710004300000,2002,1,'longer notes','2024-03-09')"
        )
        execSQL(
            "INSERT INTO food_med_events (occurrence_id,action,timestamp,item_id) " +
                "VALUES ('jrnl1:1710004200000','REPLIED',1710004300000,'jrnl1')"
        )
        execSQL(
            "INSERT INTO habit_occurrences (id,habit_id,scheduled_for,status,snooze_count," +
                "responded_at,notification_id,created_at,local_date) " +
                "VALUES ('h1:1710000000000','h1',1710000000000,'COMPLETED',0,1710000005000,1001,1,'2024-03-09')"
        )
        execSQL(
            "INSERT INTO app_settings (id,default_snooze_minutes,onboarding_completed,user_name," +
                "accent_color,notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time) " +
                "VALUES (1,15,1,'Alex','LAVENDER',1,'/tmp/p.jpg','LITERATA','08:30')"
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_createsJournalQuestionsTableAndSeedsOneRow() {
        helper.createDatabase(TEST_DB, 13).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)
        db.query("SELECT id, text, position FROM journal_questions").use { c ->
            assertEquals(1, c.count)
            assertTrue(c.moveToFirst())
            assertEquals("seed-default-0", c.getString(0))
            assertEquals("What's on your mind?", c.getString(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_addsQaJsonColumn() {
        helper.createDatabase(TEST_DB, 13).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)
        db.query("SELECT * FROM food_med_occurrences").use {
            assertTrue(it.columnNames.contains("qa_json"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_discardsJournalOccurrenceData_keepsFoodMed() {
        helper.createDatabase(TEST_DB, 13).apply { seedV13WithJournal(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        // JOURNAL task's occurrence + event: GONE.
        db.query("SELECT COUNT(*) FROM food_med_occurrences WHERE task_id = 'jrnl1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM food_med_events WHERE occurrence_id = 'jrnl1:1710004200000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        // The JOURNAL *task* row itself is kept (definitions are untouched by this migration).
        db.query("SELECT COUNT(*) FROM food_med_tasks WHERE id = 'jrnl1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0))
        }
        // FOOD task's occurrence + event: INTACT, every value preserved.
        db.query(
            "SELECT response_text, status, responded_at, local_date FROM food_med_occurrences WHERE id = 'food1:1710000600000'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("soup", c.getString(0))
            assertEquals("LOGGED", c.getString(1))
            assertEquals(1710000700000L, c.getLong(2))
            assertEquals("2024-03-09", c.getString(3))
        }
        db.query("SELECT action, item_id FROM food_med_events WHERE occurrence_id = 'food1:1710000600000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("REPLIED", c.getString(0)); assertEquals("food1", c.getString(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_keepsEveryV13Row() {
        helper.createDatabase(TEST_DB, 13).apply { seedV13WithJournal(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        db.query("SELECT title, type FROM habits WHERE id = 'h1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Stretch", c.getString(0)); assertEquals("INDIVIDUAL", c.getString(1))
        }
        db.query("SELECT status, local_date FROM habit_occurrences WHERE id = 'h1:1710000000000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("COMPLETED", c.getString(0)); assertEquals("2024-03-09", c.getString(1))
        }
        db.query("SELECT label FROM food_med_tasks WHERE id = 'food1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("Lunch", c.getString(0))
        }
        db.query("SELECT response_text, local_date FROM food_med_occurrences WHERE id = 'food1:1710000600000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("soup", c.getString(0)); assertEquals("2024-03-09", c.getString(1))
        }
        db.query("SELECT default_snooze_minutes, user_name, accent_color, font_choice, habit_checkin_time FROM app_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(15, c.getInt(0)); assertEquals("Alex", c.getString(1)); assertEquals("LAVENDER", c.getString(2))
            assertEquals("LITERATA", c.getString(3)); assertEquals("08:30", c.getString(4))
        }
    }

    // ------------------------------------------------------------------ v14 -> v15 (v0.5.5)

    @Test
    @Throws(IOException::class)
    fun migrate14To15_addsStreakColumns() {
        helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id,type) " +
                    "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL')"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        db.query("SELECT * FROM habits").use { c ->
            assertTrue(c.columnNames.contains("streak_started_at"))
            assertTrue(c.columnNames.contains("streak_longest"))
        }
        // An existing v14 habit row reads streak_longest = 0, streak_started_at = null.
        db.query("SELECT streak_started_at, streak_longest FROM habits WHERE id = 'h1'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
            assertEquals(0, c.getInt(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To15() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 15, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15
        )
    }

    // ------------------------------------------------------------------ v15 -> v16 (Customization)

    @Test
    @Throws(IOException::class)
    fun migrate15To16_addsAllCustomizationColumns() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id,type," +
                    "streak_started_at,streak_longest) " +
                    "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL',NULL,0)"
            )
            execSQL(
                "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id) " +
                    "VALUES ('t1','Lunch','FOOD','AUTO','restaurant',1,'12:00','',0,10,2001)"
            )
            execSQL(
                "INSERT INTO app_settings (id,default_snooze_minutes,onboarding_completed,user_name," +
                    "accent_color,notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time) " +
                    "VALUES (1,10,1,'Alex','LAVENDER',1,'/tmp/p.jpg','LITERATA','08:30')"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        val settingsCols = listOf(
            "week_start", "clock_24h", "calendar_default_expanded", "greeting_tone",
            "greeting_time_word", "hero_style", "habit_sort", "intake_sort", "habit_show_archived",
            "intake_show_archived", "home_hide_resolved", "reduce_motion", "quiet_hours_enabled",
            "quiet_start", "quiet_end", "streak_mode", "show_streaks", "streak_rest_days",
            "default_landing_tab", "nav_tabs"
        )
        db.query("SELECT * FROM app_settings WHERE id = 1").use { c ->
            settingsCols.forEach { assertTrue("missing $it", c.columnNames.contains(it)) }
            assertTrue(c.moveToFirst())
            // New columns read their SQL defaults on the pre-existing row.
            assertEquals("MONDAY", c.getString(c.getColumnIndex("week_start")))
            assertEquals(0, c.getInt(c.getColumnIndex("clock_24h")))
            assertEquals(0, c.getInt(c.getColumnIndex("calendar_default_expanded")))
            assertEquals("WARM", c.getString(c.getColumnIndex("greeting_tone")))
            assertEquals(1, c.getInt(c.getColumnIndex("greeting_time_word")))
            assertEquals("COUNT_LEFT", c.getString(c.getColumnIndex("hero_style")))
            assertEquals("ADDED", c.getString(c.getColumnIndex("habit_sort")))
            assertEquals("ADDED", c.getString(c.getColumnIndex("intake_sort")))
            assertEquals(0, c.getInt(c.getColumnIndex("home_hide_resolved")))
            assertEquals(0, c.getInt(c.getColumnIndex("reduce_motion")))
            assertEquals(0, c.getInt(c.getColumnIndex("quiet_hours_enabled")))
            assertEquals("22:00", c.getString(c.getColumnIndex("quiet_start")))
            assertEquals("07:00", c.getString(c.getColumnIndex("quiet_end")))
            assertEquals("STRICT", c.getString(c.getColumnIndex("streak_mode")))
            assertEquals(1, c.getInt(c.getColumnIndex("show_streaks")))
            assertEquals("", c.getString(c.getColumnIndex("streak_rest_days")))
            assertEquals("home", c.getString(c.getColumnIndex("default_landing_tab")))
            assertEquals("home,routines,foodmed", c.getString(c.getColumnIndex("nav_tabs")))
            // Pre-existing values untouched.
            assertEquals("Alex", c.getString(c.getColumnIndex("user_name")))
            assertEquals("08:30", c.getString(c.getColumnIndex("habit_checkin_time")))
        }
        // rec 8 nullable columns present + NULL on a pre-existing row.
        db.query("SELECT prompt_message, motivation FROM habits WHERE id = 'h1'").use { c ->
            assertTrue(c.columnNames.contains("prompt_message"))
            assertTrue(c.columnNames.contains("motivation"))
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT motivation FROM food_med_tasks WHERE id = 't1'").use { c ->
            assertTrue(c.columnNames.contains("motivation"))
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To16() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 16, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16
        )
    }

    // ------------------------------------------------------------------ v16 -> v17 (Journal-as-habit)

    /** v16 fixture with ALL FOUR `food_med_tasks.type` values (FOOD/MED/CUSTOM/JOURNAL), each with
     *  one occurrence + one event, plus a habit, plus one journal_questions row. The migration must
     *  purge ONLY the JOURNAL task and its children — FOOD/MED/CUSTOM survive byte-for-byte. */
    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedV16WithAllTaskTypes() {
        execSQL(
            "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                "active_days_json,is_archived,snooze_interval_minutes,notification_id,type," +
                "streak_started_at,streak_longest,prompt_message,motivation) " +
                "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL',NULL,0,NULL,NULL)"
        )
        execSQL(
            "INSERT INTO habit_occurrences (id,habit_id,scheduled_for,status,snooze_count," +
                "responded_at,notification_id,created_at,local_date) " +
                "VALUES ('h1:1710000000000','h1',1710000000000,'COMPLETED',0,1710000005000,1001,1,'2024-03-09')"
        )
        listOf("food1" to "FOOD", "med1" to "MED", "custom1" to "CUSTOM", "jrnl1" to "JOURNAL")
            .forEachIndexed { i, (id, type) ->
                val notifId = 3000 + i
                execSQL(
                    "INSERT INTO food_med_tasks (id,label,type,color_tag,icon_key,created_at,times_json," +
                        "active_days_json,is_archived,snooze_interval_minutes,notification_id,custom_category," +
                        "prompt_message,default_red_flag,default_suspected_food,default_outside_food,motivation) " +
                        "VALUES ('$id','Label $id','$type','AUTO','restaurant',1,'12:00','',0,10,$notifId," +
                        "NULL,NULL,NULL,NULL,NULL,NULL)"
                )
                execSQL(
                    "INSERT INTO food_med_occurrences (id,task_id,scheduled_for,status,snooze_count," +
                        "response_text,responded_at,notification_id,created_at,description,red_flag," +
                        "suspected_food,outside_food,local_date,qa_json) " +
                        "VALUES ('$id:1710000600000','$id',1710000600000,'LOGGED',0,'ans',1710000700000," +
                        "$notifId,1,NULL,NULL,NULL,NULL,'2024-03-09',NULL)"
                )
                execSQL(
                    "INSERT INTO food_med_events (occurrence_id,action,timestamp,item_id) " +
                        "VALUES ('$id:1710000600000','REPLIED',1710000700000,'$id')"
                )
            }
        execSQL(
            "INSERT INTO journal_questions (id,text,position) VALUES ('seed-default-0','What''s on your mind?',0)"
        )
        execSQL(
            "INSERT INTO app_settings (id,default_snooze_minutes,onboarding_completed,user_name," +
                "accent_color,notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time) " +
                "VALUES (1,15,1,'Alex','LAVENDER',1,'/tmp/p.jpg','LITERATA','08:30')"
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_addsJournalColumns() {
        helper.createDatabase(TEST_DB, 16).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)
        db.query("SELECT * FROM habits").use {
            assertTrue(it.columnNames.contains("journal_questions_json"))
        }
        db.query("SELECT * FROM habit_occurrences").use {
            assertTrue(it.columnNames.contains("qa_json"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_newHabitColumnDefaultsToEmptyString() {
        helper.createDatabase(TEST_DB, 16).apply {
            execSQL(
                "INSERT INTO habits (id,title,description,color_tag,icon_key,created_at,times_json," +
                    "active_days_json,is_archived,snooze_interval_minutes,notification_id,type," +
                    "streak_started_at,streak_longest,prompt_message,motivation) " +
                    "VALUES ('h1','Stretch','','AUTO','task',1,'07:00','',0,10,1001,'INDIVIDUAL',NULL,0,NULL,NULL)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)
        db.query("SELECT journal_questions_json FROM habits WHERE id = 'h1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_purgesOnlyJournalTaskAndItsChildren_childrenBeforeParent() {
        helper.createDatabase(TEST_DB, 16).apply { seedV16WithAllTaskTypes(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        // JOURNAL task + its occurrence + its event: ALL gone.
        db.query("SELECT COUNT(*) FROM food_med_tasks WHERE type = 'JOURNAL'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM food_med_occurrences WHERE task_id = 'jrnl1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM food_med_events WHERE occurrence_id = 'jrnl1:1710000600000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
        }
        // No orphans: total food_med_events / food_med_occurrences row counts drop by exactly 1 each
        // (only the JOURNAL row's children), from the 4 tasks seeded.
        db.query("SELECT COUNT(*) FROM food_med_tasks").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM food_med_occurrences").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM food_med_events").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(3, c.getInt(0))
        }
        // FOOD/MED/CUSTOM rows are byte-for-byte untouched.
        for (id in listOf("food1", "med1", "custom1")) {
            db.query("SELECT label FROM food_med_tasks WHERE id = '$id'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Label $id", c.getString(0))
            }
            db.query("SELECT response_text, status FROM food_med_occurrences WHERE id = '$id:1710000600000'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("ans", c.getString(0)); assertEquals("LOGGED", c.getString(1))
            }
            db.query("SELECT action FROM food_med_events WHERE occurrence_id = '$id:1710000600000'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("REPLIED", c.getString(0))
            }
        }
        // Habit rows (a wholly different table) are untouched.
        db.query("SELECT status FROM habit_occurrences WHERE id = 'h1:1710000000000'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("COMPLETED", c.getString(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_dropsJournalQuestionsTable() {
        helper.createDatabase(TEST_DB, 16).apply { seedV16WithAllTaskTypes(); close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='journal_questions'").use { c ->
            assertEquals(0, c.count)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To17() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 17, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17
        )
    }

    // ------------------------------------------------------------------ v17 -> v18 (3-axis accent)

    @Test
    @Throws(IOException::class)
    fun migrate17To18_addsHabitsAndIntakeAccentColumns() {
        helper.createDatabase(TEST_DB, 17).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)
        db.query("SELECT * FROM app_settings").use {
            assertTrue(it.columnNames.contains("habits_accent_color"))
            assertTrue(it.columnNames.contains("intake_accent_color"))
        }
    }

    /** The load-bearing one: an existing user's single accent must survive as BOTH new columns,
     *  not silently reset to the new fresh-install LAVENDER default (SD-2). */
    @Test
    @Throws(IOException::class)
    fun migrate17To18_copiesExistingAccentIntoBothNewColumns() {
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                "INSERT INTO app_settings (id,default_snooze_minutes,onboarding_completed,user_name," +
                    "accent_color,notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time) " +
                    "VALUES (1,15,1,'Alex','CORAL',1,'/tmp/p.jpg','LITERATA','08:30')"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)
        db.query("SELECT accent_color, habits_accent_color, intake_accent_color FROM app_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("CORAL", c.getString(0))
            assertEquals("CORAL", c.getString(1))
            assertEquals("CORAL", c.getString(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To18() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 18, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18
        )
    }

    // ------------------------------------------------------------------ v18 -> v19 (check-for-updates toggle)

    @Test
    @Throws(IOException::class)
    fun migrate18To19_addsCheckForUpdatesEnabledColumn() {
        helper.createDatabase(TEST_DB, 18).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)
        db.query("SELECT * FROM app_settings").use {
            assertTrue(it.columnNames.contains("check_for_updates_enabled"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate18To19_defaultsToEnabled() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(
                "INSERT INTO app_settings (id,default_snooze_minutes,onboarding_completed,user_name," +
                    "accent_color,notif_permission_asked,profile_photo_path,font_choice,habit_checkin_time," +
                    "habits_accent_color,intake_accent_color) " +
                    "VALUES (1,15,1,'Alex','CORAL',1,'/tmp/p.jpg','LITERATA','08:30','CORAL','CORAL')"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)
        db.query("SELECT check_for_updates_enabled FROM app_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To19() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 19, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To8() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 8, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll_3To9() {
        helper.createDatabase(TEST_DB, 3).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 9, true,
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
        )
    }

    @Test
    fun fullOpenAtLatestVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(
                MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19
            )
            .fallbackToDestructiveMigrationFrom(1)
            .build()
            .apply { openHelper.writableDatabase; close() }
    }
}
