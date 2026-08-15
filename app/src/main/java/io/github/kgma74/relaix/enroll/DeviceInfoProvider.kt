package io.github.kgma74.relaix.enroll

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.kgma74.relaix.health.SimProvider
import smsgateway.v1.Device
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes this handset to the control plane.
 *
 * Sent at enrollment and again on every `Register`, so the server's view
 * tracks OS updates, app updates and SIM changes without a separate flow
 * (protocol.md §6).
 */
@Singleton
class DeviceInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val simProvider: SimProvider,
) {
    fun deviceInfo(label: String): Device.DeviceInfo =
        Device.DeviceInfo.newBuilder()
            .setLabel(label.ifBlank { defaultLabel() })
            .setPhoneNumber(phoneNumber())
            .setManufacturer(Build.MANUFACTURER.orEmpty())
            .setModel(Build.MODEL.orEmpty())
            .setOsVersion(Build.VERSION.RELEASE.orEmpty())
            .setAgentVersion(agentVersion())
            .setCarrier(carrier())
            // Sent on every Register, not only at enrollment: a SIM swap
            // changes this list, and the contract already re-sends DeviceInfo
            // for exactly that reason.
            .addAllSims(simProvider.sims())
            .build()

    private fun defaultLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /**
     * The device's own MSISDN, when the platform will say.
     *
     * Documented as "may be empty" in the contract for good reason: reading it
     * needs READ_PHONE_NUMBERS, many carriers simply do not write it to the
     * SIM, and it was removed from the public API on Android 11+. An empty
     * string is a normal outcome, not a failure — so this never throws and
     * never blocks enrollment.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // line1Number: no replacement that works without a carrier privilege
    private fun phoneNumber(): String {
        if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_NUMBERS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ""
        }
        return try {
            telephony()?.line1Number.orEmpty()
        } catch (_: SecurityException) {
            ""
        }
    }

    private fun carrier(): String = try {
        telephony()?.networkOperatorName.orEmpty()
    } catch (_: SecurityException) {
        ""
    }

    private fun telephony(): TelephonyManager? =
        context.getSystemService(TelephonyManager::class.java)

    private fun agentVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
}
