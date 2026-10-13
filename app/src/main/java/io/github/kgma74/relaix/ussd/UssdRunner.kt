package io.github.kgma74.relaix.ussd

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * One session at a time for the whole handset: the radio runs a single USSD
 * dialog, and a second request while one is open fails on most phones.
 */
@Singleton
class UssdRunner @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val session = Mutex()

    fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** @param subscriptionId the SIM to dial from, 0 for the default one. */
    suspend fun run(code: String, subscriptionId: Int): UssdOutcome = session.withLock {
        if (!permitted()) {
            return@withLock UssdOutcome(false, errorCode = "PERMISSION_DENIED", errorMessage = "CALL_PHONE permission not granted")
        }
        val base = context.getSystemService(TelephonyManager::class.java)
            ?: return@withLock UssdOutcome(false, errorCode = "NO_TELEPHONY", errorMessage = "no telephony on this device")
        val telephony = if (subscriptionId != 0) base.createForSubscriptionId(subscriptionId) else base

        withTimeoutOrNull(TIMEOUT_MS) { dial(telephony, code) }
            ?: UssdOutcome(false, errorCode = "TIMEOUT", errorMessage = "the network did not answer in ${TIMEOUT_MS / 1_000} s")
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

    private companion object {
        // Networks answer within seconds; a menu left open times out on the
        // network side well before this.
        const val TIMEOUT_MS = 60_000L
    }
}
