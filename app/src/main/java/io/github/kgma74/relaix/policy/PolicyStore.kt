package io.github.kgma74.relaix.policy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import smsgateway.v1.Device
import javax.inject.Inject
import java.util.Base64
import javax.inject.Singleton

/**
 * The phone's copy of its rules, as last sent by the server.
 *
 * A copy and nothing more: the server enforces the rules, and replaces this
 * on every RegisterAck and every change. Kept on disk so the Règles screen
 * shows something meaningful offline, marked by its version.
 */
@Singleton
class PolicyStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Null until the server has sent rules once. */
    val policy: Flow<Device.DevicePolicy?> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { encoded ->
            runCatching { Device.DevicePolicy.parseFrom(Base64.getDecoder().decode(encoded)) }.getOrNull()
        }
    }

    suspend fun save(policy: Device.DevicePolicy) {
        // java.util.Base64, not android.util: the same encoding as before
        // (standard alphabet, padded, unwrapped), and usable in JVM tests.
        dataStore.edit { it[KEY] = Base64.getEncoder().encodeToString(policy.toByteArray()) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("device_policy")
    }
}
