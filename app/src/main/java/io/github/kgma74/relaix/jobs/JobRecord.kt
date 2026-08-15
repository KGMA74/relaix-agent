package io.github.kgma74.relaix.jobs

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per job this handset has been handed.
 *
 * The table exists for one reason: job delivery is at-least-once, so the same
 * `SendSmsJob` can arrive twice — after a reconnect, or because the server
 * never saw the ack. An agent that resends is an agent that bills the
 * operator twice and shows the recipient a duplicate, so "have I already
 * handled this id?" has to survive the process being killed. An in-memory set
 * would forget exactly when it matters, since a crash mid-send is the case
 * that produces the duplicate.
 */
@Entity(tableName = "jobs")
data class JobRecord(
    /** Server-assigned; the idempotency key (protocol.md §3). */
    @PrimaryKey val jobId: String,
    val recipient: String,
    val status: JobState,
    /** Which SIM this was sent from; 0 is the handset's default subscription.
     *  Kept per-job so `sent_last_hour` can be broken down per SIM — the
     *  carrier meters the SIM, not the handset, so the device-wide sum is the
     *  wrong number to rate-limit any one SIM against. */
    val subscriptionId: Int = 0,
    /** Parts actually handed to the network, for cost reconciliation. */
    val partsSent: Int = 0,
    val errorCode: String = "",
    val errorMessage: String = "",
    val receivedAtMillis: Long,
    val completedAtMillis: Long? = null,
    /** After this, the agent must not send: a late OTP is worse than none. */
    val expiresAtMillis: Long? = null,
)

enum class JobState {
    /** Accepted, not yet handed to SmsManager. */
    ACCEPTED,

    /** Handed to SmsManager; waiting for the sent receipt. */
    SENDING,

    /** The handset gave the message to the network. */
    SENT,

    /** A delivery report came back. Never guaranteed: many carriers send none. */
    DELIVERED,

    FAILED,
    CANCELLED,
    ;

    /** Terminal states need no further work and must never be re-sent. */
    val isTerminal: Boolean
        get() = this == SENT || this == DELIVERED || this == FAILED || this == CANCELLED
}
