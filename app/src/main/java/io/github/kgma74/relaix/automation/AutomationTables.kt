package io.github.kgma74.relaix.automation

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One run of an automation on one received SMS.
 *
 * Written before the first action, so a crash in the middle never runs the
 * same automation twice on the same SMS — a USSD step can move money. Also
 * what the automation's limits count, and the report the gateway is sent.
 */
@Entity(tableName = "automation_runs")
data class RunRecord(
    @PrimaryKey val id: String,
    val automationId: String,
    /** The SMS it ran on: sender, time and body hashed, enough to never run twice. */
    val smsKey: String,
    val sender: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val ok: Boolean = false,
    val error: String = "",
    /** The steps, as the AutomationStep messages they are reported as. */
    val stepsBase64: String = "",
    /** The gateway acknowledged the report. */
    val reported: Boolean = false,
    /** Skipped because its limits were reached: not counted against them. */
    val skipped: Boolean = false,
)

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(run: RunRecord): Long

    @Query("SELECT COUNT(*) FROM automation_runs WHERE automationId = :automationId AND smsKey = :smsKey")
    suspend fun countFor(automationId: String, smsKey: String): Int

    @Query("SELECT COUNT(*) FROM automation_runs WHERE automationId = :automationId AND skipped = 0 AND startedAtMillis >= :since")
    suspend fun countSince(automationId: String, since: Long): Int

    @Query(
        "UPDATE automation_runs SET finishedAtMillis = :finishedAt, ok = :ok, error = :error, stepsBase64 = :steps " +
            "WHERE id = :id",
    )
    suspend fun finish(id: String, finishedAt: Long, ok: Boolean, error: String, steps: String)

    @Query("SELECT * FROM automation_runs WHERE reported = 0 AND finishedAtMillis IS NOT NULL ORDER BY startedAtMillis LIMIT 50")
    suspend fun unreported(): List<RunRecord>

    @Query("UPDATE automation_runs SET reported = 1 WHERE id IN (:ids)")
    suspend fun markReported(ids: List<String>)

    /** Old runs no longer count against any limit; reported ones can go. */
    @Query("DELETE FROM automation_runs WHERE reported = 1 AND startedAtMillis < :before")
    suspend fun prune(before: Long)
}

/** A webhook the phone has to POST, kept until the receiver takes it. */
@Entity(tableName = "webhook_outbox")
data class OutboxRecord(
    @PrimaryKey val id: String,
    val url: String,
    val body: String,
    val secret: String,
    val attempts: Int = 0,
    val nextAttemptAtMillis: Long,
    val lastError: String = "",
)

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: OutboxRecord)

    @Query("SELECT * FROM webhook_outbox WHERE nextAttemptAtMillis <= :now ORDER BY nextAttemptAtMillis LIMIT 20")
    suspend fun due(now: Long): List<OutboxRecord>

    @Query("SELECT MIN(nextAttemptAtMillis) FROM webhook_outbox")
    suspend fun nextDue(): Long?

    @Query("UPDATE webhook_outbox SET attempts = attempts + 1, nextAttemptAtMillis = :next, lastError = :error WHERE id = :id")
    suspend fun retry(id: String, next: Long, error: String)

    @Query("DELETE FROM webhook_outbox WHERE id = :id")
    suspend fun delete(id: String)
}

/** One use of a SIM's PIN, which the approvals' daily caps count. */
@Entity(tableName = "pin_uses")
data class PinUseRecord(
    @PrimaryKey val id: String,
    val subscriptionId: Int,
    val template: String,
    /** The operation's amount, when the code has one; 0 otherwise. */
    val amount: Double,
    val atMillis: Long,
)

@Dao
interface PinUseDao {
    @Insert
    suspend fun insert(use: PinUseRecord)

    @Query("SELECT COUNT(*) FROM pin_uses WHERE subscriptionId = :sim AND template = :template AND atMillis >= :since")
    suspend fun countSince(sim: Int, template: String, since: Long): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM pin_uses WHERE subscriptionId = :sim AND template = :template AND atMillis >= :since")
    suspend fun totalSince(sim: Int, template: String, since: Long): Double
}
