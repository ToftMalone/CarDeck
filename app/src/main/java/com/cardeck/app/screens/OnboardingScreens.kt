package com.cardeck.app.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material.icons.rounded.SignalCellularAlt2Bar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.data.BRANDS
import com.cardeck.app.data.BT_DEVICES
import com.cardeck.app.data.ObdDevice
import com.cardeck.app.data.WIFI_DEVICES
import com.cardeck.app.ui.ArcGauge
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.Field
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.Radar
import com.cardeck.app.ui.Segmented
import com.cardeck.app.ui.Spinner
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val c = Cd.c
    var anim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120); anim = true }
    val progress by animateFloatAsState(if (anim) .7f else 0f, spring(dampingRatio = .6f, stiffness = 40f), label = "w")
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Speed, 20, c.op) }
            T("CarDeck", 20, weight = 600)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(272.dp).clip(CircleShape).background(c.sf2), contentAlignment = Alignment.Center) {
                ArcGauge(progress, c.p, 7f, Modifier.size(220.dp))
                Ico(Icons.Rounded.DirectionsCar, 88, c.on)
            }
            Row(
                Modifier.align(Alignment.TopEnd).offset(y = 40.dp).shadow(4.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.sf3).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { Ico(Icons.Rounded.Speed, 20, c.p); T("2 140 tr/min", 14, weight = 500) }
            Row(
                Modifier.align(Alignment.BottomStart).offset(y = (-30).dp).shadow(4.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(c.gc).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { Ico(Icons.Rounded.CheckCircle, 20, c.ogc); T("Aucun défaut", 14, c.ogc, 500) }
        }
        T("Comprenez enfin votre voiture", 36, lineHeight = 44, modifier = Modifier.padding(bottom = 12.dp))
        T(
            "Branchez un boîtier OBD2 sur la prise diagnostic, suivez le moteur en temps réel et détectez les pannes avant qu'elles ne coûtent cher.",
            16, c.onv, lineHeight = 24,
        )
        Spacer(Modifier.height(28.dp))
        FilledBtn("Commencer", { vm.go(Screen.Pair) })
    }
}

private fun signalIcon(level: Int, wifi: Boolean) = when {
    wifi -> Icons.Rounded.Wifi
    level >= 3 -> Icons.Rounded.SignalCellularAlt
    level == 2 -> Icons.Rounded.SignalCellularAlt2Bar
    else -> Icons.Rounded.SignalCellularAlt1Bar
}

@Composable
fun PairScreen(vm: AppViewModel) {
    val c = Cd.c
    var mode by remember { mutableIntStateOf(0) } // 0 = Bluetooth, 1 = Wi-Fi
    var scanKey by remember { mutableIntStateOf(0) }
    var found by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    var sel by remember { mutableStateOf<ObdDevice?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val devices = if (mode == 0) BT_DEVICES else WIFI_DEVICES

    LaunchedEffect(mode, scanKey) {
        found = 0; scanning = true; sel = null; connecting = false; done = false
        delay(900); found = 1
        delay(800); found = minOf(2, devices.size)
        delay(800); found = minOf(3, devices.size)
        delay(700); scanning = false
    }
    LaunchedEffect(sel) {
        val d = sel ?: return@LaunchedEffect
        connecting = true; scanning = false
        delay(1600)
        connecting = false; done = true
        vm.setDevice(d.name)
    }

    val inApp = vm.onboarded
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() })
            if (!inApp) {
                T("Étape 1 sur 2", 14, c.onv, 500)
                Box(Modifier.weight(1f).padding(start = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                    Box(Modifier.fillMaxWidth(.5f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.p))
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) {
            T("Appairer le boîtier OBD2", 28, lineHeight = 36, modifier = Modifier.padding(bottom = 8.dp))
            T("Branchez le boîtier sur la prise diagnostic, sous le volant. Mettez le contact, puis sélectionnez-le dans la liste.", 14, c.onv, modifier = Modifier.padding(bottom = 20.dp), lineHeight = 20)
            Segmented(listOf(Triple("Bluetooth", Icons.Rounded.Bluetooth, mode == 0), Triple("Wi-Fi", Icons.Rounded.Wifi, mode == 1))) { mode = it }
            Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                Radar(200.dp, scanning, if (mode == 0) Icons.Rounded.BluetoothSearching else Icons.Rounded.WifiFind)
            }
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                T(if (scanning) "Recherche en cours…" else if (done) "Boîtier connecté" else "$found appareils trouvés", 14, weight = 500)
                if (!scanning && !connecting) TextBtn("Relancer", { scanKey++ }, Icons.Rounded.Refresh)
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.take(found).forEach { d ->
                    val isSel = sel?.id == d.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (isSel && done) c.sc else c.sf1)
                            .clickable(enabled = !connecting) { sel = d }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.pc), contentAlignment = Alignment.Center) {
                            Ico(if (d.wifi) Icons.Rounded.Wifi else Icons.Rounded.Bluetooth, 22, c.opc)
                        }
                        Column(Modifier.weight(1f)) {
                            T(d.name, 16, weight = 500, lineHeight = 24)
                            T(d.sub, 12, c.onv, lineHeight = 16)
                        }
                        when {
                            isSel && connecting -> Spinner()
                            isSel && done -> Ico(Icons.Rounded.CheckCircle, 26, c.g)
                            else -> Ico(signalIcon(d.signal, d.wifi), 22, c.onv)
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)) {
            FilledBtn("Continuer", { if (inApp) vm.back() else vm.go(Screen.AddVehicle) }, enabled = done)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddVehicleScreen(vm: AppViewModel) {
    val c = Cd.c
    val inApp = vm.onboarded
    var brand by remember { mutableStateOf("Peugeot") }
    var model by remember { mutableStateOf("308 1.5 BlueHDi 130") }
    var year by remember { mutableStateOf("2019") }
    var fuel by remember { mutableStateOf("Diesel") }
    var plate by remember { mutableStateOf("GH-482-KT") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() })
            if (!inApp) {
                T("Étape 2 sur 2", 14, c.onv, 500)
                Box(Modifier.weight(1f).padding(start = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.p))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) {
            T("Votre véhicule", 28, lineHeight = 36, modifier = Modifier.padding(bottom = 20.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tc).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Ico(Icons.Rounded.QrCodeScanner, 24, c.otc)
                Column {
                    T("VIN lu depuis le boîtier", 14, c.otc, 500)
                    T("VF3LBYHZ6KS123456", 14, c.otc, mono = true, spacing = .5f, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    T("Peugeot 308 · 2019 · Diesel détecté", 12, c.otc)
                }
            }
            T("Marque", 14, weight = 500, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                BRANDS.forEach { b -> CdChip(b, brand == b) { brand = b } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field("Modèle", model, { model = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Année", year, { year = it.take(4) }, Modifier.weight(1f), number = true)
                    Field("Carburant", fuel, { fuel = it }, Modifier.weight(1f))
                }
                Field("Immatriculation", plate, { plate = it.uppercase() }, mono = true)
            }
        }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)) {
            FilledBtn(if (inApp) "Ajouter au garage" else "Terminer", {
                val existing = vm.vehicles.find { it.plate.equals(plate, true) }
                if (existing != null) vm.selectVehicle(existing.id)
                else vm.addVehicle(if (brand == "Autre") model.trim() else "$brand ${model.trim().substringBefore(' ')}", plate.trim(), year.trim(), fuel.trim())
                if (inApp) { vm.back(); vm.showSnack("Véhicule ajouté au garage") } else vm.finishOnboarding()
            })
        }
    }
}
