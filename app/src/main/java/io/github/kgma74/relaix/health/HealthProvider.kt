package io.github.kgma74.relaix.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import smsgateway.v1.Device
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshots what the scheduler needs to decide whether this handset is fit to
 * take work.
 *
 * Read on demand rather than kept as observed state: a heartbeat every few
 * tens of seconds does not justify holding broadcast receivers and a
 * telephony callback alive for the life of the process, and a value read at
 * send time cannot be stale.
 */
@Singleton
class HealthProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun snapshot(sentLastHour: Int = 0): Device.DeviceHealth =
        Device.DeviceHealth.newBuilder()
            .setBatteryLevel(batteryLevel())
            .setIsCharging(isCharging())
            .setSignalStrength(signalLevel())
            .setNetworkType(networkType())
            .setSimReady(isSimReady())
            .setSentLastHour(sentLastHour)
            .setPermissionsOk(permissionsGranted())
            .build()

    private fun batteryManager(): BatteryManager? =
        context.getSystemService(BatteryManager::class.java)

    private fun batteryLevel(): Int =
        batteryManager()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

    /**
     * A sticky broadcast rather than BATTERY_PROPERTY_STATUS: the property
     * reports "charging" on some vendors while merely connected to a slow
     * source, and the pair (level, charging) drives a scheduling decision.
     */
    private fun isCharging(): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?: return false
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * The vendor-calibrated 0-4 bucket, never raw dBm.
     *
     * Android reports signal on a different raw scale per radio technology —
     * GSM RSSI, LTE RSRP and NR SS-RSRP occupy different ranges — so a server
     * comparing raw values across a mixed fleet would be comparing
     * incomparable numbers (protocol.md §3).
     *
     * Returns 0 when unknown, which is also the value that excludes the
     * device from the ready set: a phone whose signal cannot be read is not
     * one to hand an SMS to.
     */
    private fun signalLevel(): Int {
        if (!hasPhoneStatePermission()) return 0
        // getSignalStrength() only exists from API 28. On 26-27 there is no
        // equivalent that does not require a listener, so the device reports
        // "unknown" and the server treats it as unusable — the conservative
        // outcome, and better than crashing with NoSuchMethodError.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        return try {
            telephony()?.signalStrength?.level ?: 0
        } catch (_: SecurityException) {
            0
        }
    }

    private fun networkType(): String {
        if (!hasPhoneStatePermission()) return ""
        return try {
            when (telephony()?.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "NR"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "GSM"
                TelephonyManager.NETWORK_TYPE_UNKNOWN, null -> ""
                else -> "OTHER"
            }
        } catch (_: SecurityException) {
            ""
        }
    }

    /**
     * SIM_STATE_READY only. A locked or absent SIM leaves a phone that is
     * otherwise perfectly healthy and completely unable to send.
     */
    private fun isSimReady(): Boolean =
        telephony()?.simState == TelephonyManager.SIM_STATE_READY

    /**
     * Whether the agent can actually send today. The user can revoke SEND_SMS
     * at any moment, and without this the failure mode is a device that keeps
     * accepting jobs and failing every one.
     */
    private fun permissionsGranted(): Boolean = granted(Manifest.permission.SEND_SMS)

    private fun hasPhoneStatePermission(): Boolean = granted(Manifest.permission.READ_PHONE_STATE)

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun telephony(): TelephonyManager? =
        context.getSystemService(TelephonyManager::class.java)
}
