package io.github.kgma74.relaix.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the server gave this handset at enrollment: its identity and its
 * credential.
 *
 * `deviceId` is public — it is the value an operator passes as `deviceId` on
 * `POST /send` — and is stored as-is. `deviceToken` goes through
 * [SecretCipher] first: the server returns it exactly once and keeps only a
 * hash, so this copy is the only one, and it authenticates every message the
 * agent will ever send (protocol.md §3).
 */
@Singleton
class DeviceIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    /** Emits null until this device has enrolled. */
    val deviceId: Flow<String?> = dataStore.data.map { it[KEY_DEVICE_ID] }

    /** True once enrollment has completed and a usable token is on disk. */
    val isEnrolled: Flow<Boolean> = dataStore.data.map {
        it[KEY_DEVICE_ID] != null && it[KEY_DEVICE_TOKEN] != null
    }

    /**
     * Reads and decrypts the token.
     *
     * A suspend function rather than a Flow: the token is needed at a precise
     * moment — building a message — and decrypting it on every emission of an
     * unrelated preference change would be wasteful and would keep plaintext
     * alive longer than necessary.
     */
    suspend fun deviceToken(): String? {
        val blob = dataStore.data.first()[KEY_DEVICE_TOKEN] ?: return null
        return cipher.decrypt(blob)
    }

    suspend fun save(deviceId: String, deviceToken: String) {
        val blob = cipher.encrypt(deviceToken)
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_DEVICE_TOKEN] = blob
        }
    }

    /** Forgets this enrollment; the device would have to enroll again. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_DEVICE_TOKEN)
        }
    }

    private companion object {
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_DEVICE_TOKEN = stringPreferencesKey("device_token_encrypted")
    }
}
