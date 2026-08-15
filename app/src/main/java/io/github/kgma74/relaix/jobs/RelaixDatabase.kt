package io.github.kgma74.relaix.jobs

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JobRecord::class], version = 2, exportSchema = false)
@TypeConverters(JobStateConverter::class)
abstract class RelaixDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
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
