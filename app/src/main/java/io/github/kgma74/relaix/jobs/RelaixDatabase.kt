package io.github.kgma74.relaix.jobs

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.kgma74.relaix.automation.OutboxDao
import io.github.kgma74.relaix.automation.OutboxRecord
import io.github.kgma74.relaix.automation.PinUseDao
import io.github.kgma74.relaix.automation.PinUseRecord
import io.github.kgma74.relaix.automation.RunDao
import io.github.kgma74.relaix.automation.RunRecord
import io.github.kgma74.relaix.inbound.InboundDao
import io.github.kgma74.relaix.inbound.InboundRecord

@Database(
    entities = [JobRecord::class, InboundRecord::class, RunRecord::class, OutboxRecord::class, PinUseRecord::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(JobStateConverter::class)
abstract class RelaixDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun inboundDao(): InboundDao
    abstract fun runDao(): RunDao
    abstract fun outboxDao(): OutboxDao
    abstract fun pinUseDao(): PinUseDao
}

/** Automations: forwarded fields, runs, the phone's webhook outbox, PIN uses. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inbound ADD COLUMN automationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inbound ADD COLUMN fieldsJson TEXT NOT NULL DEFAULT '{}'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS automation_runs (" +
                "id TEXT NOT NULL PRIMARY KEY, automationId TEXT NOT NULL, smsKey TEXT NOT NULL, sender TEXT NOT NULL, " +
                "startedAtMillis INTEGER NOT NULL, finishedAtMillis INTEGER, ok INTEGER NOT NULL, error TEXT NOT NULL, " +
                "stepsBase64 TEXT NOT NULL, reported INTEGER NOT NULL, skipped INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS webhook_outbox (" +
                "id TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, body TEXT NOT NULL, secret TEXT NOT NULL, " +
                "attempts INTEGER NOT NULL, nextAttemptAtMillis INTEGER NOT NULL, lastError TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS pin_uses (" +
                "id TEXT NOT NULL PRIMARY KEY, subscriptionId INTEGER NOT NULL, template TEXT NOT NULL, " +
                "amount REAL NOT NULL, atMillis INTEGER NOT NULL)",
        )
    }
}

/** Adds the queue of received SMS waiting for the server. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS inbound (" +
                "id TEXT NOT NULL PRIMARY KEY, sender TEXT NOT NULL, body TEXT NOT NULL, " +
                "receivedAtMillis INTEGER NOT NULL, subscriptionId INTEGER NOT NULL)",
        )
    }
}

/**
 * Adds subscriptionId so `sent_last_hour` can be broken down per SIM.
 *
 * Not `fallbackToDestructiveMigration`: the job ledger is what stops a
 * redelivered job from being sent twice (see DatabaseModule), and that
 * guarantee has to survive this schema change same as any other.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Existing rows predate per-SIM tracking; 0 (the default subscription)
        // is the honest answer for what they were sent from, since a
        // single-SIM phone's only jobs used it.
        db.execSQL("ALTER TABLE jobs ADD COLUMN subscriptionId INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Stores the state by name rather than ordinal: an ordinal silently
 * remaps every stored row the day someone inserts a value into the middle of
 * the enum.
 */
class JobStateConverter {
    @TypeConverter
    fun toState(value: String): JobState = JobState.valueOf(value)

    @TypeConverter
    fun fromState(state: JobState): String = state.name
}
