package io.github.kgma74.relaix.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kgma74.relaix.jobs.JobDao
import io.github.kgma74.relaix.jobs.RelaixDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RelaixDatabase =
        Room.databaseBuilder(context, RelaixDatabase::class.java, "relaix.db")
            // No fallbackToDestructiveMigration: the job ledger is what stops
            // a redelivered job from being sent twice, so wiping it on a
            // schema change would trade a migration chore for duplicate SMS.
            .build()

    @Provides
    fun provideJobDao(database: RelaixDatabase): JobDao = database.jobDao()
}
