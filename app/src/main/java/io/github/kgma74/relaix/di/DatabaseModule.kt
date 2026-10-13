package io.github.kgma74.relaix.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kgma74.relaix.jobs.JobDao
import io.github.kgma74.relaix.inbound.InboundDao
import io.github.kgma74.relaix.jobs.MIGRATION_1_2
import io.github.kgma74.relaix.jobs.MIGRATION_2_3
import io.github.kgma74.relaix.jobs.MIGRATION_3_4
import io.github.kgma74.relaix.automation.OutboxDao
import io.github.kgma74.relaix.automation.PinUseDao
import io.github.kgma74.relaix.automation.RunDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun provideJobDao(database: RelaixDatabase): JobDao = database.jobDao()

    @Provides
    fun provideInboundDao(database: RelaixDatabase): InboundDao = database.inboundDao()

    @Provides
    fun provideRunDao(database: RelaixDatabase): RunDao = database.runDao()

    @Provides
    fun provideOutboxDao(database: RelaixDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun providePinUseDao(database: RelaixDatabase): PinUseDao = database.pinUseDao()
}
