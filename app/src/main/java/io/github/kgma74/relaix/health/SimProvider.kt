package io.github.kgma74.relaix.health

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import smsgateway.v1.Device
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates the SIMs this handset can send from.
 *
 * A dual-SIM phone is really two sending nodes: the carrier meters each SIM
 * separately and the number a recipient sees is the SIM's, not the phone's.
 * Reporting them is what lets a caller name one on a job instead of being
 * stuck with whatever the phone's settings happen to say.
 */
@Singleton
class SimProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Active subscriptions, or empty when the permission is missing.
     *
     * Empty is not an error: a single-SIM phone with no READ_PHONE_STATE still
     * sends fine on its default subscription. The list only adds the ability
     * to choose.
     */
    @SuppressLint("MissingPermission")
    fun sims(): List<Device.SimInfo> {
        if (!hasPermission()) return emptyList()

        val manager = context.getSystemService(SubscriptionManager::class.java)
            ?: return emptyList()

        val active: List<SubscriptionInfo> = try {
            manager.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }

        val defaultSmsSub = defaultSmsSubscriptionId()

        return active.map { info ->
            Device.SimInfo.newBuilder()
                .setSubscriptionId(info.subscriptionId)
                .setSlotIndex(info.simSlotIndex)
                .setPhoneNumber(phoneNumberOf(manager, info))
                .setCarrier(info.carrierName?.toString().orEmpty())
                .setDisplayName(info.displayName?.toString().orEmpty())
                .setIsDefaultSms(info.subscriptionId == defaultSmsSub)
                .build()
        }
    }

    /** Per-SIM signal and readiness, refreshed on every heartbeat. */
    @SuppressLint("MissingPermission")
    fun health(sentLastHourBySub: Map<Int, Int> = emptyMap()): List<Device.SimHealth> {
        if (!hasPermission()) return emptyList()

        val manager = context.getSystemService(SubscriptionManager::class.java)
            ?: return emptyList()
        val active = try {
            manager.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }

        return active.map { info ->
            // A TelephonyManager per subscription: the default one answers for
            // whichever SIM the system prefers, which is the question nobody
            // asked here.
            val telephony = context.getSystemService(TelephonyManager::class.java)
                ?.createForSubscriptionId(info.subscriptionId)

            Device.SimHealth.newBuilder()
                .setSubscriptionId(info.subscriptionId)
                .setSignalStrength(signalLevel(telephony))
                .setSimReady(telephony?.simState == TelephonyManager.SIM_STATE_READY)
                .setSentLastHour(sentLastHourBySub[info.subscriptionId] ?: 0)
                .build()
        }
    }

    /**
     * Whether the handset can send from this subscription right now.
     *
     * Checked before accepting a job rather than discovered at the radio: a
     * missing SIM produces a generic platform failure, which tells an operator
     * nothing, while a refusal can name the problem.
     */
    fun hasSubscription(subscriptionId: Int): Boolean {
        if (subscriptionId <= 0) return true // the default subscription
        return sims().any { it.subscriptionId == subscriptionId }
    }

    private fun defaultSmsSubscriptionId(): Int = try {
        SmsManager.getDefaultSmsSubscriptionId()
    } catch (_: Exception) {
        SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    /**
     * The SIM's own MSISDN, when the platform will say. Usually it will not —
     * carriers rarely write it — so an empty string is the normal outcome.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // SubscriptionInfo.number: the only option below API 33
    private fun phoneNumberOf(manager: SubscriptionManager, info: SubscriptionInfo): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPhoneNumber(info.subscriptionId)
        } else {
            info.number.orEmpty()
        }
    } catch (_: Exception) {
        ""
    }

    private fun signalLevel(telephony: TelephonyManager?): Int {
        // Same API-28 floor as the per-device reading: below it there is no
        // listener-free way to ask, and 0 is the value that keeps the device
        // out of the ready set rather than crashing.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return 0
        return try {
            telephony?.signalStrength?.level ?: 0
        } catch (_: SecurityException) {
            0
        }
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
}
