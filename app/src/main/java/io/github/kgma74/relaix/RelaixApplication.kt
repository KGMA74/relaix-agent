package io.github.kgma74.relaix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and root of the Hilt object graph.
 *
 * The agent's long-lived pieces — the gRPC channel, the token store, the
 * connection service — outlive any single screen and are shared between the
 * UI and a foreground service, so they hang off the application component
 * rather than being built where they happen to be first needed.
 */
@HiltAndroidApp
class RelaixApplication : Application()
