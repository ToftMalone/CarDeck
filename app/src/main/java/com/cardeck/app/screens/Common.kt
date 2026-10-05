package com.cardeck.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.DtcScan
import com.cardeck.app.data.VehicleProfile
import com.cardeck.app.obd.ObdState
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.ConnDot
import com.cardeck.app.ui.Spinner
import com.cardeck.app.ui.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberActiveVehicle(vm: AppViewModel): VehicleProfile? {
    val vs by vm.repo.vehicles.collectAsState()
    val id by vm.repo.activeId.collectAsState()
    return vs.find { it.id == id } ?: vs.firstOrNull()
}

@Composable
fun rememberLastScan(vm: AppViewModel, vehicleId: Long?): DtcScan? {
    val changes by vm.repo.changes.collectAsState()
    var scan by remember { mutableStateOf<DtcScan?>(null) }
    LaunchedEffect(vehicleId, changes) { scan = withContext(Dispatchers.IO) { vehicleId?.let { vm.repo.lastScan(it) } } }
    return scan
}

enum class ConnKind { Ok, Busy, Off }

/** Libellé + nature de l'état de connexion du boîtier du véhicule. */
@Composable
fun connStatus(vm: AppViewModel, v: VehicleProfile?): Pair<String, ConnKind> {
    val st by vm.obd.state.collectAsState()
    val a = v?.adapter ?: return "Aucun boîtier" to ConnKind.Off
    return when (val s = st) {
        is ObdState.Connected -> if (s.adapter.address == a.address) "Connecté" to ConnKind.Ok else "Hors ligne" to ConnKind.Off
        is ObdState.Connecting -> if (s.adapter.address == a.address) "Connexion…" to ConnKind.Busy else "Hors ligne" to ConnKind.Off
        else -> "Hors ligne" to ConnKind.Off
    }
}

@Composable
fun ConnChip(label: String, kind: ConnKind, height: Int = 32, onClick: (() -> Unit)? = null) {
    val c = Cd.c
    val (bg, fg) = when (kind) { ConnKind.Ok -> c.gc to c.ogc; ConnKind.Busy -> c.sc to c.osc; ConnKind.Off -> c.sf3 to c.onv }
    Row(
        Modifier.height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (kind) {
            ConnKind.Ok -> ConnDot()
            ConnKind.Busy -> Spinner(12.dp, 2.dp, fg)
            ConnKind.Off -> Box(Modifier.size(8.dp).clip(CircleShape).background(fg.copy(alpha = .6f)))
        }
        T(label, 13, fg, 600)
    }
}
