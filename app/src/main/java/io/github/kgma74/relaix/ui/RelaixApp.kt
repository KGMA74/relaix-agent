package io.github.kgma74.relaix.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.kgma74.relaix.connect.AgentService
import io.github.kgma74.relaix.connect.ConnectionState
import io.github.kgma74.relaix.ui.components.BrandMark
import io.github.kgma74.relaix.ui.components.StatusDot
import io.github.kgma74.relaix.ui.enroll.EnrollmentScreen
import io.github.kgma74.relaix.ui.enroll.EnrollmentViewModel
import io.github.kgma74.relaix.ui.rules.RulesScreen
import io.github.kgma74.relaix.ui.status.DeviceScreen
import io.github.kgma74.relaix.ui.status.JobsScreen
import io.github.kgma74.relaix.ui.status.OverviewScreen
import io.github.kgma74.relaix.ui.status.StatusViewModel
import io.github.kgma74.relaix.ui.theme.LocalStatusColors
import io.github.kgma74.relaix.ui.theme.soft

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Overview("overview", "État", Icons.Default.Speed),
    Jobs("jobs", "Envois", Icons.AutoMirrored.Filled.Message),
    Rules("rules", "Règles", Icons.Default.Tune),
    Device("device", "Appareil", Icons.Default.PhoneAndroid),
}

/**
 * Picks between enrollment and the running app, and owns the service
 * lifecycle.
 *
 * The start/stop decision lives here and nowhere else: scattering it into a
 * screen is what previously stopped the service on first composition and
 * crashed the process.
 */
@Composable
fun RelaixApp(
    modifier: Modifier = Modifier,
    viewModel: EnrollmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Gated on identityLoaded: before the store answers, "no device id" only
    // means "not read yet", and stopping on that kills a service the system
    // may have just restarted, before it can call startForeground.
    LaunchedEffect(state.identityLoaded, state.enrolledDeviceId) {
        if (!state.identityLoaded) return@LaunchedEffect
        if (state.enrolledDeviceId != null) AgentService.start(context)
        else AgentService.stop(context)
    }

    if (state.enrolledDeviceId != null) {
        EnrolledApp(modifier = modifier)
    } else {
        EnrollmentScreen(modifier = modifier, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnrolledApp(
    modifier: Modifier = Modifier,
    statusViewModel: StatusViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // The connection light rides in the app bar so it is visible from every
    // tab: the question it answers is not specific to one screen.
    val connection by statusViewModel.connection.collectAsStateWithLifecycle()
    val status = LocalStatusColors.current
    val (dotColour, connectionLabel) = when (connection) {
        is ConnectionState.Connected -> status.ok to "En ligne"
        ConnectionState.Connecting -> status.waiting to "Connexion"
        is ConnectionState.Reconnecting -> status.waiting to "Reconnexion"
        is ConnectionState.Disabled -> status.waiting to "Désactivé"
        is ConnectionState.Refused -> status.bad to "Refusé"
        ConnectionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant to "Arrêté"
    }

    val outline = MaterialTheme.colorScheme.outline

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BrandMark(size = 28.dp)
                        Text("Relaix", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(soft(dotColour), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusDot(
                            color = dotColour,
                            pulsing = connection is ConnectionState.Connecting ||
                                connection is ConnectionState.Reconnecting,
                            size = 8,
                        )
                        Text(connectionLabel, style = MaterialTheme.typography.labelMedium, color = dotColour)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.drawBehind {
                    // A hairline rather than a tinted slab: the bar sits on the
                    // page instead of competing with the cards above it.
                    drawLine(outline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                },
            ) {
                Destination.entries.forEach { destination ->
                    val selected = currentRoute?.hierarchy?.any {
                        it.route == destination.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Single top plus popUpTo keeps the back stack
                                // from growing one entry per tab tap.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Overview.route,
            modifier = Modifier.padding(padding),
            // A short cross-fade rather than the default slide: tabs are peers,
            // and a directional slide would imply a hierarchy that is not there.
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
        ) {
            composable(Destination.Overview.route) { OverviewScreen() }
            composable(Destination.Jobs.route) { JobsScreen() }
            composable(Destination.Rules.route) { RulesScreen() }
            composable(Destination.Device.route) { DeviceScreen() }
        }
    }
}
