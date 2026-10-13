package io.github.kgma74.relaix.inbound

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A received SMS waiting for the server to acknowledge it.
 *
 * On disk, not in memory: an SMS that arrives while the phone is offline, or
 * just before the process is killed, must still reach the server. The row is
 * deleted once the server says it has it (SmsReceivedAck), so the table only
 * ever holds what is in transit.
 */
@Entity(tableName = "inbound")
data class InboundRecord(
    /** Generated here, stable across resends: the server's idempotency key. */
    @PrimaryKey val id: String,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    /** The SIM it arrived on; 0 when the platform did not say. */
    val subscriptionId: Int = 0,
)

@Dao
interface InboundDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: InboundRecord)

    @Query("SELECT * FROM inbound ORDER BY receivedAtMillis LIMIT :limit")
    suspend fun pending(limit: Int = 100): List<InboundRecord>

    @Query("DELETE FROM inbound WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}
