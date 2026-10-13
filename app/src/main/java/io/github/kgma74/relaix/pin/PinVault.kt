package io.github.kgma74.relaix.pin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.kgma74.relaix.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SIMs' PINs, on this phone and nowhere else.
 *
 * Encrypted with the keystore key (SecretCipher), never displayed back, never
 * logged, never put in any message to the gateway: a USSD code reaches the
 * phone with {pin} in it, and the PIN is put in its place just before dialing
 * — and only by [PinGuard], for a code its owner approved here.
 */
@Singleton
class PinVault @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    /** The subscription ids that have a PIN. */
    val configured: Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs.asMap().keys.map { it.name }.filter { it.startsWith(PREFIX) }.mapNotNull { it.removePrefix(PREFIX).toIntOrNull() }.toSet()
    }

    suspend fun set(subscriptionId: Int, pin: String) {
        require(pin.matches(Regex("[0-9]{4,8}"))) { "a PIN is 4 to 8 digits" }
        dataStore.edit { it[key(subscriptionId)] = cipher.encrypt(pin) }
    }

    suspend fun clear(subscriptionId: Int) {
        dataStore.edit { it.remove(key(subscriptionId)) }
    }

    /** For PinGuard only. Null when none is set or it no longer decrypts. */
    internal suspend fun get(subscriptionId: Int): String? {
        val blob = dataStore.data.first()[key(subscriptionId)] ?: return null
        return try {
            cipher.decrypt(blob)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun key(subscriptionId: Int) = stringPreferencesKey("$PREFIX$subscriptionId")

    private companion object {
        const val PREFIX = "sim_pin_"
    }
}
