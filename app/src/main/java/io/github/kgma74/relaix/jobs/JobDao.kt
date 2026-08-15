package io.github.kgma74.relaix.jobs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    /**
     * Records a job only if its id is new.
     *
     * IGNORE rather than REPLACE: a redelivered job must not overwrite the
     * outcome of the first attempt. Returns -1 when the row already existed,
     * which is exactly the duplicate signal the caller needs.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(job: JobRecord): Long

    @Query("SELECT * FROM jobs WHERE jobId = :jobId")
    suspend fun find(jobId: String): JobRecord?

    @Query(
        """
        UPDATE jobs
        SET status = :status,
            partsSent = :partsSent,
            errorCode = :errorCode,
            errorMessage = :errorMessage,
            completedAtMillis = :completedAtMillis
        WHERE jobId = :jobId
        """,
    )
    suspend fun updateOutcome(
        jobId: String,
        status: JobState,
        partsSent: Int,
        errorCode: String,
        errorMessage: String,
        completedAtMillis: Long?,
    )

    @Query("UPDATE jobs SET status = :status WHERE jobId = :jobId")
    suspend fun updateStatus(jobId: String, status: JobState)

    /**
     * Jobs still in flight, reported to the server on every Register so it can
     * reconcile instead of blindly re-dispatching work the handset already
     * holds.
     */
    @Query("SELECT jobId FROM jobs WHERE status IN ('ACCEPTED', 'SENDING')")
    suspend fun inFlightJobIds(): List<String>

    @Query("SELECT * FROM jobs ORDER BY receivedAtMillis DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<JobRecord>>

    /** Feeds `sent_last_hour`, the advisory rate-limit signal in DeviceHealth. */
    @Query(
        """
        SELECT COALESCE(SUM(partsSent), 0) FROM jobs
        WHERE completedAtMillis IS NOT NULL
          AND completedAtMillis >= :sinceMillis
          AND status IN ('SENT', 'DELIVERED')
        """,
    )
    suspend fun partsSentSince(sinceMillis: Long): Int

    /**
     * The same count broken down per SIM, for `SimHealth.sent_last_hour`: the
     * carrier meters each SIM separately, so the device-wide total above is
     * the wrong number to rate-limit any one of them against.
     */
    @Query(
        """
        SELECT subscriptionId, COALESCE(SUM(partsSent), 0) AS partsSent FROM jobs
        WHERE completedAtMillis IS NOT NULL
          AND completedAtMillis >= :sinceMillis
          AND status IN ('SENT', 'DELIVERED')
        GROUP BY subscriptionId
        """,
    )
    suspend fun partsSentSinceBySubscription(sinceMillis: Long): Map<
        @MapColumn(columnName = "subscriptionId") Int,
        @MapColumn(columnName = "partsSent") Int,
        >

    /**
     * Drops jobs the server has given up on, so the handset stops offering
     * them at the next Register.
     */
    @Query("UPDATE jobs SET status = 'CANCELLED' WHERE jobId IN (:jobIds) AND status IN ('ACCEPTED', 'SENDING')")
    suspend fun cancelAll(jobIds: List<String>)
}
