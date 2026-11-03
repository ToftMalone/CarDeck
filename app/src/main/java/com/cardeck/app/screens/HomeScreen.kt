package com.cardeck.app.screens

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cardeck.app.AppViewModel
import com.cardeck.app.BuildConfig
import com.cardeck.app.Perms
import com.cardeck.app.R
import com.cardeck.app.Screen
import com.cardeck.app.dashboard
import com.cardeck.app.data.Trip
import com.cardeck.app.service.TripService
import com.cardeck.app.title
import com.cardeck.app.ui.Accent
import com.cardeck.app.ui.ButtonStyle
import com.cardeck.app.ui.CookieBadge
import com.cardeck.app.ui.Corners
import com.cardeck.app.ui.Domain
import com.cardeck.app.ui.LiveNumber
import com.cardeck.app.ui.LocalHaptics
import com.cardeck.app.ui.MetricStyle
import com.cardeck.app.ui.ProvideAccent
import com.cardeck.app.ui.SecondaryButton
import com.cardeck.app.ui.StatusChip
import com.cardeck.app.ui.StatusKind
import com.cardeck.app.ui.Sym
import com.cardeck.app.ui.SymButton
import com.cardeck.app.ui.Symbol
import com.cardeck.app.ui.cs
import com.cardeck.app.ui.fadeUp
import com.cardeck.app.ui.pop
import com.cardeck.app.ui.rise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(vm: AppViewModel) {
    val v = rememberActiveVehicle(vm)
    val scan = rememberLastScan(vm, v?.id)
    val conn = rememberConnection(vm, v)
    val live by vm.obd.live.collectAsState()
    val changes by vm.repo.changes.collectAsState()
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    LaunchedEffect(v?.id, changes) { trips = withContext(Dispatchers.IO) { v?.let { vm.repo.trips(it.id) } ?: emptyList() } }
    val hap = LocalHaptics.current
    fun open(d: Domain) { hap.tick(); if (d == Domain.Garage && v?.adapter == null) { vm.home(); vm.pairActive() } else vm.openFromHome(d.dashboard) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(32.dp).pop())
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = cs.onSurface, modifier = Modifier.padding(start = 12.dp).weight(1f))
            SymButton(Sym.Settings, { hap.tick(); vm.openFromHome(Screen.Settings) }, stringResource(R.string.settings), tint = cs.onSurface)
        }
        if (vm.greeting.isNotEmpty()) {
            Text(
                vm.greeting, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 20.dp).fadeUp(),
            )
        }
        TripPermissionCard()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Conduite : vitesse en direct.
            DomainCard(Domain.Drive, 0, { open(Domain.Drive) }, chip = { ConnectionChip(conn) }) {
                val speed = live["std0D"]?.let { if (vm.mph) it / 1.609344 else it }
                Row(verticalAlignment = Alignment.Bottom) {
                    LiveNumber(if (conn == Conn.Live) speed else null, 0, MetricStyle.large, Accent.current.accent)
                    Text(
                        stringResource(if (vm.mph) R.string.unit_mph else R.string.unit_kmh), style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    LiveNumber(if (conn == Conn.Live) live["std0C"] else null, 0, MetricStyle.small, cs.onSurfaceVariant, Modifier.padding(bottom = 8.dp))
                    Text(stringResource(R.string.unit_rpm), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                }
            }
            // Diagnostic : codes du dernier scan.
            DomainCard(
                Domain.Diagnostic, 1, { open(Domain.Diagnostic) },
                chip = {
                    when {
                        vm.scanning -> StatusChip(stringResource(R.string.tool_scan), StatusKind.Busy)
                        scan == null -> StatusChip(stringResource(R.string.home_no_scan), StatusKind.Idle)
                        scan.codes.isEmpty() -> StatusChip(stringResource(R.string.home_no_fault), StatusKind.Good)
                        else -> StatusChip(pluralStringResource(R.plurals.codes_count, scan.codes.size, scan.codes.size), StatusKind.Poor)
                    }
                },
            ) {
                Text(
                    if (scan == null) stringResource(R.string.reason_no_scan)
                    else stringResource(R.string.home_last_scan, DateUtils.getRelativeTimeSpanString(scan.t).toString()),
                    style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                )
            }
            // Trajets : dernier trajet.
            DomainCard(
                Domain.Trips, 2, { open(Domain.Trips) },
                chip = { StatusChip(pluralStringResource(R.plurals.trips_count, trips.size, trips.size), StatusKind.Idle) },
            ) {
                val last = trips.firstOrNull()
                if (last == null) {
                    Text(stringResource(R.string.home_trip_none), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        LiveNumber(if (vm.mph) last.distanceKm / 1.609344 else last.distanceKm, 1, MetricStyle.large, Accent.current.accent)
                        Text(
                            if (vm.mph) "mi" else stringResource(R.string.unit_km), style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.home_last_trip, DateUtils.getRelativeTimeSpanString(last.start).toString()),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
            // Garage : véhicule actif, boîtier, version.
            DomainCard(Domain.Garage, 3, { open(Domain.Garage) }, chip = { ConnectionChip(conn) }) {
                if (v != null) {
                    Text(v.nickname, style = MaterialTheme.typography.headlineSmall, color = Accent.current.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(v.template.fullName, v.plate).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val up = vm.update
                Text(
                    if (up != null) stringResource(R.string.home_update, up.version) else stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium, color = if (up != null) Accent.current.accent else cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Grande carte d'un domaine : badge cookie, titre, métrique clé et état ; un toucher ouvre le domaine. */
@Composable
private fun DomainCard(d: Domain, index: Int, onClick: () -> Unit, chip: @Composable () -> Unit, body: @Composable () -> Unit) {
    ProvideAccent(d) {
        Column(
            Modifier.fillMaxWidth().rise(index).clip(Corners.section).background(cs.surfaceContainerLow)
                .clickable(role = Role.Button, onClick = onClick).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CookieBadge(d.sym, Modifier.pop(80 + index * 60), size = 56.dp)
                Text(stringResource(d.title), style = MaterialTheme.typography.titleLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                Symbol(Sym.ChevronRight, tint = cs.onSurfaceVariant)
            }
            Column { body() }
            chip()
        }
    }
}

enum class Conn { NoAdapter, Offline, Connecting, Connected, Live }

/** État de la liaison : boîtier, puis calculateur moteur qui répond (« en direct »). */
@Composable
fun rememberConnection(vm: AppViewModel, v: com.cardeck.app.data.VehicleProfile?): Conn {
    val (_, kind) = connStatus(vm, v)
    val ecu by vm.obd.ecuOnline.collectAsState()
    return when {
        v?.adapter == null -> Conn.NoAdapter
        kind == ConnKind.Busy -> Conn.Connecting
        kind == ConnKind.Ok && ecu -> Conn.Live
        kind == ConnKind.Ok -> Conn.Connected
        else -> Conn.Offline
    }
}

@Composable
fun ConnectionChip(c: Conn, onHero: Boolean = false) = when (c) {
    Conn.Live -> StatusChip(stringResource(R.string.conn_live), StatusKind.Live, onHero = onHero)
    Conn.Connected -> StatusChip(stringResource(R.string.conn_ecu_off), StatusKind.Fair, onHero = onHero)
    Conn.Connecting -> StatusChip(stringResource(R.string.conn_connecting), StatusKind.Busy, onHero = onHero)
    Conn.Offline -> StatusChip(stringResource(R.string.conn_offline), StatusKind.Idle, onHero = onHero)
    Conn.NoAdapter -> StatusChip(stringResource(R.string.conn_no_adapter), StatusKind.Idle, onHero = onHero)
}

/** Invite à accorder localisation (« toujours ») + notifications pour l'enregistrement automatique des trajets. */
@Composable
private fun TripPermissionCard() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val ok = remember(tick) { Perms.tripsOk(ctx) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; TripService.start(ctx) }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (Perms.locationOk(ctx) && !Perms.backgroundLocationOk(ctx) && Build.VERSION.SDK_INT >= 29) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        TripService.start(ctx)
    }
    if (ok) return
    ProvideAccent(Domain.Trips) {
        val a = Accent.current
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).fadeUp().clip(Corners.section).background(a.container).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CookieBadge(Sym.LocationOn, Modifier.pop(), size = 56.dp, container = a.accent, content = a.onAccent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.perm_trips_title), style = MaterialTheme.typography.titleMedium, color = a.onContainer)
                Text(stringResource(R.string.perm_trips_body), style = MaterialTheme.typography.bodyMedium, color = a.onContainer.copy(alpha = 0.85f))
                Box(Modifier.padding(top = 8.dp)) {
                    SecondaryButton(stringResource(R.string.perm_allow), {
                        if (!Perms.locationOk(ctx) || !Perms.notificationsOk(ctx)) {
                            val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                            fgLauncher.launch(list.toTypedArray())
                        } else if (Build.VERSION.SDK_INT >= 29) {
                            bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }, style = ButtonStyle.Filled)
                }
            }
        }
    }
}
