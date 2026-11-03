package com.cardeck.app

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cardeck.app.screens.rememberActiveVehicle
import com.cardeck.app.screens.shareFile
import com.cardeck.app.ui.Domain
import com.cardeck.app.ui.Sym
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@get:StringRes
val Domain.title: Int
    get() = when (this) {
        Domain.Drive -> R.string.domain_drive
        Domain.Diagnostic -> R.string.domain_diagnostic
        Domain.Trips -> R.string.domain_trips
        Domain.Garage -> R.string.domain_garage
    }

/** Domaine d'un écran (accent et dock) ; null pour l'accueil et l'accueil de première utilisation. */
fun Screen.domain(): Domain? = when (this) {
    Screen.Dash, Screen.ObdData -> Domain.Drive
    Screen.Diag, is Screen.Dtc -> Domain.Diagnostic
    Screen.Trips, is Screen.Trip -> Domain.Trips
    Screen.Settings, Screen.Pair, Screen.AddVehicle, Screen.Changelog, Screen.About, Screen.Coding -> Domain.Garage
    is Screen.Tools -> domain
    Screen.Home, Screen.Welcome -> null
}

/** Écran « Tableau de bord » (premier onglet du dock) de chaque domaine. */
val Domain.dashboard: Screen
    get() = when (this) {
        Domain.Drive -> Screen.Dash
        Domain.Diagnostic -> Screen.Diag
        Domain.Trips -> Screen.Trips
        Domain.Garage -> Screen.Settings
    }

/** Favori par défaut de chaque domaine. */
val Domain.defaultFavorite: String
    get() = when (this) {
        Domain.Drive -> "obd"
        Domain.Diagnostic -> "scanlog"
        Domain.Trips -> "lasttrip"
        Domain.Garage -> "adapter"
    }

/** Un outil d'un domaine : il ouvre un écran ou lance une action ; [reason] explique pourquoi il est indisponible. */
data class Tool(
    val id: String,
    val sym: Sym,
    @StringRes val name: Int,
    val screen: Screen? = null,
    @StringRes val reason: Int? = null,
    val action: (() -> Unit)? = null,
) {
    val enabled get() = reason == null
}

data class ToolGroup(@StringRes val title: Int, val tools: List<Tool>)

/** Outils d'un domaine, avec leur disponibilité actuelle. */
@Composable
fun rememberTools(vm: AppViewModel, d: Domain): List<ToolGroup> {
    val ctx = LocalContext.current
    val v = rememberActiveVehicle(vm)
    val changes by vm.repo.changes.collectAsState()
    var lastTrip by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(v?.id, changes) {
        lastTrip = withContext(Dispatchers.IO) { v?.let { vm.repo.trips(it.id).firstOrNull()?.id } }
    }
    val noAdapter = if (v?.adapter == null) R.string.reason_no_adapter else null
    return when (d) {
        Domain.Drive -> listOf(
            ToolGroup(
                R.string.group_live,
                listOf(
                    Tool("dash", Sym.Speed, R.string.tool_dashboard, Screen.Dash),
                    Tool("obd", Sym.Memory, R.string.tool_obd, Screen.ObdData),
                ),
            ),
        )
        Domain.Diagnostic -> {
            val log = remember(changes, vm.scanning) { vm.scanLog() }
            val logTitle = androidx.compose.ui.res.stringResource(R.string.tool_scan_log)
            listOf(
                ToolGroup(
                    R.string.group_codes,
                    listOf(
                        Tool("scan", Sym.Stethoscope, R.string.tool_scan, Screen.Diag, noAdapter),
                        Tool(
                            "scanlog", Sym.ReceiptLong, R.string.tool_scan_log,
                            reason = if (log == null) R.string.reason_no_scan else null,
                            action = { log?.let { shareFile(ctx, it, "text/plain", logTitle) } },
                        ),
                    ),
                ),
            )
        }
        Domain.Trips -> listOf(
            ToolGroup(
                R.string.group_history,
                listOf(
                    Tool("trips", Sym.Route, R.string.tool_trips, Screen.Trips),
                    Tool(
                        "lasttrip", Sym.History, R.string.tool_last_trip, lastTrip?.let { Screen.Trip(it) },
                        if (lastTrip == null) R.string.reason_no_trip else null,
                    ),
                ),
            ),
        )
        Domain.Garage -> listOf(
            ToolGroup(
                R.string.group_vehicle,
                listOf(
                    Tool("settings", Sym.DirectionsCar, R.string.tool_settings, Screen.Settings),
                    Tool("adapter", Sym.Bluetooth, R.string.tool_adapter, Screen.Pair, action = { vm.home(); vm.pairActive() }),
                ),
            ),
            ToolGroup(
                R.string.group_app,
                listOf(
                    Tool("changelog", Sym.AutoAwesome, R.string.tool_changelog, Screen.Changelog),
                    Tool("about", Sym.Info, R.string.tool_about, Screen.About),
                ),
            ),
        )
    }
}

/** Lance un outil : action dédiée, sinon ouverture de son écran depuis l'accueil. */
fun AppViewModel.launch(t: Tool) {
    if (!t.enabled) return
    val a = t.action
    when {
        a != null -> a()
        t.screen != null -> openFromHome(t.screen)
    }
}
