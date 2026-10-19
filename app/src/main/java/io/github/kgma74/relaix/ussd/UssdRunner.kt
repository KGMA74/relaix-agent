package io.github.kgma74.relaix.ussd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the network answered to a USSD code, or why it did not. */
data class UssdOutcome(
    val success: Boolean,
    /** One entry per screen the network displayed. */
    val responses: List<String> = emptyList(),
    val errorCode: String = "",
    val errorMessage: String = "",
)

/**
 * Dials a USSD code and waits for the network's answer.
 *
 * `TelephonyManager.sendUssdRequest` handles one request and one answer: a
 * code that opens a menu returns that menu's text and the session ends, so
 * menus are answered with all-in-one codes (`*144*2*1*…#`) where the carrier
 * accepts them.
 *
 * A job that arrives with `replies` is the other case: the carrier wants the
 * answers one screen at a time, so the code is dialed the same way and the
 * menu it opens is answered by [UssdDialogDriver] through the accessibility
 * service — the only way into the dialog the system puts up. That service has
 * to be switched on in Android's settings; a job asking for replies without it
 * is refused at accept time rather than run and left half-answered.
 *
 * One session at a time for the whole handset: the radio runs a single USSD
 * dialog, and a second request while one is open fails on most phones.
 */
@Singleton
class UssdRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val driver: UssdDialogDriver,
) {
    private val session = Mutex()

    fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Whether a menu can be answered, not only a code run whole. */
    fun menuCapable(): Boolean = driver.enabled.value

    /**
     * @param subscriptionId the SIM to dial from, 0 for the default one.
     * @param replies typed in order into the menus the code opens; empty runs
     *   the code alone. May carry {pin}, already filled in by the caller.
     */
    suspend fun run(
        code: String,
        subscriptionId: Int,
        replies: List<String> = emptyList(),
    ): UssdOutcome = session.withLock {
        if (!permitted()) {
            return@withLock UssdOutcome(false, errorCode = "PERMISSION_DENIED", errorMessage = "CALL_PHONE permission not granted")
        }
        val base = context.getSystemService(TelephonyManager::class.java)
            ?: return@withLock UssdOutcome(false, errorCode = "NO_TELEPHONY", errorMessage = "no telephony on this device")
        val telephony = if (subscriptionId != 0) base.createForSubscriptionId(subscriptionId) else base

        if (replies.isEmpty()) return@withLock runOnce(telephony, code)

        if (!menuCapable()) {
            return@withLock UssdOutcome(
                success = false,
                errorCode = "USSD_ACCESSIBILITY_REQUIRED",
                errorMessage = "answering a menu needs Relaix's accessibility service, which is off",
            )
        }
        if (!driver.begin(replies)) {
            return@withLock UssdOutcome(
                success = false,
                errorCode = "USSD_MENU_BUSY",
                errorMessage = "another USSD menu is already running on this phone",
            )
        }
        // The dialog only exists on a screen somebody is looking at: a fleet
        // phone left dark renders nothing for the service to type into.
        val screen = keepScreenOn()
        try {
            withTimeoutOrNull(MENU_TIMEOUT_MS) {
                // Not sendUssdRequest: a reply that opens a menu comes back to
                // its callback as a failure, with no dialog drawn and nothing
                // left to answer. Dialed as a call, the phone app runs the
                // session itself and puts up the dialog the service types into;
                // the first screen is read from there like every other one.
                placeMenuCall(telephony, subscriptionId, code)?.let { (errorCode, message) ->
                    driver.dialFailed(errorCode, message)
                }
                driver.await()
            } ?: UssdOutcome(
                success = false,
                errorCode = "TIMEOUT",
                errorMessage = "the menu did not finish in ${MENU_TIMEOUT_MS / 1_000} s",
            )
        } finally {
            // The menu is over, answered or not: its dialog left up would hold
            // the radio's session open and fail the next code.
            withContext(NonCancellable) { driver.close() }
            driver.end()
            screen.off()
        }
    }

    /**
     * A code with no replies, by `sendUssdRequest`. Many phones report a
     * short code's answer (*160#, *444#) as a failure — the network sent it as
     * a notice, or as a menu — while the phone app shows it in its dialog.
     * With the accessibility service on, that dialog is watched while the
     * code runs: a failure with a screen in it is the answer, and the dialog
     * is closed, which ends the session.
     */
    private suspend fun runOnce(telephony: TelephonyManager, code: String): UssdOutcome {
        val watching = menuCapable() && driver.begin(emptyList())
        try {
            val outcome = withTimeoutOrNull(TIMEOUT_MS) { dial(telephony, code) }
                ?: UssdOutcome(false, errorCode = "TIMEOUT", errorMessage = "the network did not answer in ${TIMEOUT_MS / 1_000} s")
            if (outcome.success || !watching) return outcome
            // A dark screen draws no dialog for the service to read.
            val screen = keepScreenOn()
            try {
                val shown = withTimeoutOrNull(CAPTURE_MS) { driver.await() }
                if (shown == null || shown.responses.isEmpty()) return outcome
                Log.d(TAG, "the code was reported failed, but its answer is in the dialog: taken from there")
                return UssdOutcome(true, shown.responses)
            } finally {
                withContext(NonCancellable) { driver.close() }
                screen.off()
            }
        } finally {
            if (watching) driver.end()
        }
    }

    private suspend fun dial(telephony: TelephonyManager, code: String): UssdOutcome =
        suspendCancellableCoroutine { continuation ->
            val callback = object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(tm: TelephonyManager, request: String, response: CharSequence) {
                    if (continuation.isActive) continuation.resume(UssdOutcome(true, listOf(response.toString())))
                }

                override fun onReceiveUssdResponseFailed(tm: TelephonyManager, request: String, failureCode: Int) {
                    val (code, message) = when (failureCode) {
                        TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "USSD_SERVICE_UNAVAILABLE" to "USSD is not available on this network now"
                        else -> "USSD_FAILED" to "the network refused or could not run the code"
                    }
                    if (continuation.isActive) continuation.resume(UssdOutcome(false, errorCode = code, errorMessage = message))
                }
            }
            try {
                telephony.sendUssdRequest(code, callback, Handler(Looper.getMainLooper()))
            } catch (e: SecurityException) {
                continuation.resume(UssdOutcome(false, errorCode = "PERMISSION_DENIED", errorMessage = e.message.orEmpty()))
            }
        }

    /**
     * Dials [code] the way the keypad would, on the SIM [subscriptionId].
     * Null once the call is placed, else why it could not be.
     */
    private fun placeMenuCall(telephony: TelephonyManager, subscriptionId: Int, code: String): Pair<String, String>? {
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return "NO_TELEPHONY" to "no telecom service on this device"
        val extras = Bundle()
        if (subscriptionId != 0) {
            val account = phoneAccountFor(telecom, telephony, subscriptionId)
                ?: return "USSD_FAILED" to "no calling account for subscription $subscriptionId"
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
        }
        return try {
            // Uri.fromParts encodes '#', which a tel: string would otherwise
            // cut the code at.
            telecom.placeCall(Uri.fromParts("tel", code, null), extras)
            null
        } catch (e: SecurityException) {
            "PERMISSION_DENIED" to e.message.orEmpty()
        }
    }

    /**
     * The calling account behind a subscription. Asked of the platform from
     * API 30; before that the account id is the subscription id on AOSP and
     * most OEMs, and the ICCID on the rest.
     */
    @SuppressLint("MissingPermission") // READ_PHONE_STATE is granted at enrollment, CALL_PHONE checked by run
    private fun phoneAccountFor(telecom: TelecomManager, telephony: TelephonyManager, subscriptionId: Int): PhoneAccountHandle? {
        val accounts = runCatching { telecom.callCapablePhoneAccounts }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            accounts.firstOrNull { telephony.getSubscriptionId(it) == subscriptionId }?.let { return it }
        }
        val iccId = runCatching {
            context.getSystemService(SubscriptionManager::class.java)
                ?.getActiveSubscriptionInfo(subscriptionId)?.iccId
        }.getOrNull()
        return accounts.firstOrNull { it.id == subscriptionId.toString() || (iccId != null && it.id == iccId) }
    }

    /**
     * Turns the screen on for as long as a menu may run.
     *
     * Not for the one-shot case: there, `sendUssdRequest` answers without
     * anything being drawn. A menu is different — the dialog is a window, and
     * an unrendered window has nothing for the accessibility service to find.
     * Held with a timeout so a session that hangs cannot leave the screen on.
     */
    @Suppress("DEPRECATION") // the window-flag alternative belongs to our own windows
    private fun keepScreenOn(): PowerManager.WakeLock? {
        val power = context.getSystemService(PowerManager::class.java) ?: return null
        if (power.isInteractive) {
            Log.d(TAG, "menu session, screen already on")
            return null
        }
        val lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "relaix:ussd-menu",
        ).apply {
            setReferenceCounted(false)
            acquire(SCREEN_LOCK_TIMEOUT_MS)
        }
        Log.d(TAG, "menu session, screen was off: keeping it on for the dialog")
        return lock
    }

    private fun PowerManager.WakeLock?.off() {
        if (this != null && isHeld) release()
    }

    private companion object {
        const val TAG = "RelaixUssd"

        // Networks answer within seconds; a menu left open times out on the
        // network side well before this.
        const val TIMEOUT_MS = 60_000L

        // A menu is a chain of round trips, each one a screen to read and a
        // reply to type: five steps at the network's pace is well inside this.
        const val MENU_TIMEOUT_MS = 180_000L

        const val SCREEN_LOCK_TIMEOUT_MS = MENU_TIMEOUT_MS + 30_000L

        // A failed code's dialog: drawn within a second or two of the failure,
        // then read once it has stopped changing (Waits.settleMillis).
        const val CAPTURE_MS = 10_000L
    }
}
