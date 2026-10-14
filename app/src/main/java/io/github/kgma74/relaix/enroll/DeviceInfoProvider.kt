package io.github.kgma74.relaix.enroll

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityManager
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
            .setHardwareId(hardwareId())
            // The default zone for the availability windows: "08:00" in the
            // rules means eight o'clock where the phone is.
            .setTimeZone(java.time.ZoneId.systemDefault().id)
            // What this agent can do beyond SMS, so the server sends it
            // nothing it would drop. "ussd_menu" only while the accessibility
            // service that types into the USSD dialog is on in Android's
            // settings: the server fails a job whose capability the device
            // does not list, so a capability claimed and not had is worse than
            // one offered when it is there.
            .addAllCapabilities(
                buildList {
                    add("ussd")
                    if (ussdMenusEnabled()) add("ussd_menu")
                    add("ussd_pin")
                    add("inbound_sms")
                    add("automations")
                    add("automation_reply_sms")
                    add("automation_webhook")
                    add("automation_ussd")
                },
            )
            .build()

    /**
     * Whether Android has Relaix's accessibility service switched on.
     *
     * Asked of the platform rather than of the bound service: enrollment and
     * every `Register` come right after a boot, when the service may not have
     * been bound yet, and a capability reported one connection late is a job
     * the server refuses outright instead of one it retries.
     */
    private fun ussdMenusEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        if (!manager.isEnabled) return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
    }

    /**
     * Settings.Secure.ANDROID_ID: what lets the server keep one entry per
     * phone. Scoped to this app's signing key, the user and the handset, so it
     * survives a reinstall — exactly the case it is for — yet means nothing to
     * any other app, and needs no permission. The server stores only an HMAC
     * of it. It does change on a factory reset, after which the phone is,
     * reasonably, a new device.
     */
    @SuppressLint("HardwareIds")
    private fun hardwareId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

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
