package io.github.kgma74.relaix.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the control plane endpoint across restarts.
 *
 * Plain DataStore, deliberately: the endpoint is not a secret — it is a host
 * and a port that anyone scanning the same QR would see. The `device_token`
 * that arrives with it *is* a credential and gets encrypted storage of its
 * own; keeping the two apart avoids paying key-store cost for settings and,
 * more importantly, avoids the reverse mistake of treating a credential as
 * ordinary configuration.
 */
@Singleton
class EndpointStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Emits null until enrollment has stored an endpoint. */
    val endpoint: Flow<ServerEndpoint?> = dataStore.data.map { prefs ->
        val host = prefs[KEY_HOST]
        val port = prefs[KEY_PORT]
        val useTls = prefs[KEY_TLS]
        if (host == null || port == null || useTls == null) null
        else ServerEndpoint(host = host, port = port, useTls = useTls)
    }

    suspend fun save(endpoint: ServerEndpoint) {
        dataStore.edit { prefs ->
            prefs[KEY_HOST] = endpoint.host
            prefs[KEY_PORT] = endpoint.port
            prefs[KEY_TLS] = endpoint.useTls
        }
    }

    /** Used when a device is un-enrolled and must forget where it belonged. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_HOST)
            prefs.remove(KEY_PORT)
            prefs.remove(KEY_TLS)
        }
    }

    private companion object {
        val KEY_HOST = stringPreferencesKey("endpoint_host")
        val KEY_PORT = intPreferencesKey("endpoint_port")
        val KEY_TLS = booleanPreferencesKey("endpoint_tls")
    }
}
