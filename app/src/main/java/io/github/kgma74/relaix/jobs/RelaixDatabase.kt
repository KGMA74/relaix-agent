package io.github.kgma74.relaix.jobs

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(entities = [JobRecord::class], version = 1, exportSchema = false)
@TypeConverters(JobStateConverter::class)
abstract class RelaixDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
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
