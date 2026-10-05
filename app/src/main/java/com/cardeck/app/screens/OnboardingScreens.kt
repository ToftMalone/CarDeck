package com.cardeck.app.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.cardeck.app.AppViewModel
import com.cardeck.app.Perms
import com.cardeck.app.Screen
import com.cardeck.app.data.AdapterConfig
import com.cardeck.app.data.SWIFT_SPORT_ZC33S
import com.cardeck.app.data.Transport
import com.cardeck.app.data.fr
import com.cardeck.app.ui.ArcGauge
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Field
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.Radar
import com.cardeck.app.ui.Segmented
import com.cardeck.app.ui.Spinner
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        }
        T("Comprenez enfin votre voiture", 36, lineHeight = 44, modifier = Modifier.padding(bottom = 12.dp))
        T(
            "Branchez un boîtier OBD2 sur la prise diagnostic, suivez le moteur en temps réel et détectez les pannes avant qu'elles ne coûtent cher.",
            16, c.onv, lineHeight = 24,
        )
        Spacer(Modifier.height(28.dp))
        FilledBtn("Commencer", { vm.go(Screen.AddVehicle) })
    }
}

@Composable
private fun StepBar(step: Int, onBack: () -> Unit, showSteps: Boolean) {
    val c = Cd.c
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, onBack)
        if (showSteps) {
            T("Étape $step sur 2", 14, c.onv, 500)
            Box(Modifier.weight(1f).padding(start = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                Box(Modifier.fillMaxWidth(step / 2f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.p))
            }
        }
    }
}

@Composable
fun AddVehicleScreen(vm: AppViewModel) {
    val c = Cd.c
    val tpl = SWIFT_SPORT_ZC33S
    val count = vm.repo.vehicles.collectAsState().value.count { it.templateId == tpl.id }
    var nickname by remember { mutableStateOf(if (count == 0) tpl.model else "${tpl.model} ${count + 1}") }
    var plate by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        StepBar(1, { vm.back() }, !vm.onboarded)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) {
            T("Votre véhicule", 28, lineHeight = 36, modifier = Modifier.padding(bottom = 8.dp))
            T("Choisissez le modèle : il apporte ses fonctionnalités dédiées (données moteur, diagnostic…).", 14, c.onv, lineHeight = 20, modifier = Modifier.padding(bottom = 20.dp))
            // Modèle disponible (un seul pour le moment)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sc).border(2.dp, c.p, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 26, c.op) }
                Column(Modifier.weight(1f)) {
                    T(tpl.fullName, 16, c.osc, 600)
                    T(tpl.specs, 13, c.osc)
                }
                Ico(Icons.Rounded.CheckCircle, 24, c.p)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp).clip(RoundedCornerShape(16.dp)).background(c.sf2).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ico(Icons.Rounded.Info, 20, c.onv)
                T("Plusieurs ${tpl.model} ? Créez un profil par voiture : chacune garde ses trajets, ses diagnostics et son boîtier OBD2.", 13, c.onv, lineHeight = 18)
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field("Nom du profil", nickname, { nickname = it })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Immatriculation", plate, { plate = it.uppercase() }, Modifier.weight(1.4f), mono = true)
                    Field("Année", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), number = true)
                }
            }
        }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)) {
            FilledBtn("Continuer", { vm.createVehicle(nickname.trim(), plate.trim(), year.trim()) }, enabled = nickname.isNotBlank())
        }
    }
}

private data class Found(val address: String, val name: String, val sub: String, val transport: Transport) {
    val obdLike get() = listOf("OBD", "ELM", "VGATE", "ICAR", "V-LINK", "VLINK", "VEEPEAK", "KONNWEI", "CARISTA", "VIECAR").any { name.uppercase().contains(it) }
}

@SuppressLint("MissingPermission")
@Composable
fun PairScreen(vm: AppViewModel) {
    val c = Cd.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicle = vm.repo.vehicles.collectAsState().value.find { it.id == vm.pairVehicleId }
    val bt: BluetoothAdapter? = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }

    var mode by remember { mutableIntStateOf(0) } // 0 = Bluetooth, 1 = BLE, 2 = Wi-Fi
    var btGranted by remember { mutableStateOf(Perms.bluetoothOk(ctx)) }
    var btOn by remember { mutableStateOf(bt?.isEnabled == true) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { btGranted = Perms.bluetoothOk(ctx) }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { btOn = bt?.isEnabled == true }

    val found = remember { mutableStateListOf<Found>() }
    var scanning by remember { mutableStateOf(false) }
    var scanKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf<AdapterConfig?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var vin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var host by remember { mutableStateOf("192.168.0.10") }
    var port by remember { mutableStateOf("35000") }

    fun add(f: Found) {
        val i = found.indexOfFirst { it.address == f.address }
        if (i >= 0) found[i] = f else found.add(f)
    }

    val goBack: () -> Unit = { if (!vm.onboarded) vm.cancelOnboardingPair() else { vm.obd.pairing = false; vm.back() } }
    BackHandler { goBack() }
    DisposableEffect(Unit) {
        vm.obd.pairing = true
        onDispose { vm.obd.pairing = false }
    }
    LaunchedEffect(mode) { if (mode < 2 && !btGranted) permLauncher.launch(Perms.bluetooth) }

    // Bluetooth classique : appareils appairés + découverte
    DisposableEffect(mode, btGranted, btOn, scanKey) {
        if (mode != 0 || !btGranted || !btOn || bt == null) return@DisposableEffect onDispose {}
        found.clear()
        runCatching { bt.bondedDevices }.getOrNull()?.forEach { d ->
            if (d.type != BluetoothDevice.DEVICE_TYPE_LE) add(Found(d.address, d.name ?: d.address, "Appairé", Transport.Classic))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                        if (d.type == BluetoothDevice.DEVICE_TYPE_LE) return
                        val name = d.name ?: return
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val bonded = d.bondState == BluetoothDevice.BOND_BONDED
                        add(Found(d.address, name, (if (bonded) "Appairé" else "Non appairé") + if (rssi != Short.MIN_VALUE) " · $rssi dBm" else "", Transport.Classic))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> scanning = false
                }
            }
        }
        ContextCompat.registerReceiver(
            ctx, receiver,
            IntentFilter().apply { addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) },
            ContextCompat.RECEIVER_EXPORTED,
        )
        scanning = runCatching { bt.startDiscovery() }.getOrDefault(false)
        onDispose {
            runCatching { bt.cancelDiscovery() }
            runCatching { ctx.unregisterReceiver(receiver) }
            scanning = false
        }
    }

    // Bluetooth LE : scan de 12 s
    DisposableEffect(mode, btGranted, btOn, scanKey) {
        val scanner = bt?.bluetoothLeScanner
        if (mode != 1 || !btGranted || !btOn || scanner == null) return@DisposableEffect onDispose {}
        found.clear()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: return
                add(Found(r.device.address, name, "Signal ${r.rssi} dBm", Transport.Ble))
            }
        }
        runCatching { scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb) }
        scanning = true
        val job = scope.launch { delay(12_000); runCatching { scanner.stopScan(cb) }; scanning = false }
        onDispose {
            job.cancel()
            runCatching { scanner.stopScan(cb) }
            scanning = false
        }
    }

    fun connect(a: AdapterConfig) {
        if (connecting) return
        selected = a.address
        connecting = true
        connected = null
        info = null
        error = null
        vin = null
        scope.launch {
            runCatching { bt?.cancelDiscovery() }
            scanning = false
            val r = withContext(Dispatchers.IO) { vm.obd.connect(a) }
            if (r.isSuccess) {
                val details = withContext(Dispatchers.IO) {
                    vm.obd.use { e ->
                        val ecu = e.probe()
                        Triple(if (ecu) e.vin() else null, e.voltage(), ecu)
                    }
                }
                connected = a
                vin = details?.first
                val parts = mutableListOf(r.getOrNull() ?: "ELM327")
                details?.second?.let { parts += fr(it, 1) + " V" }
                if (details?.third == false) parts += "contact coupé"
                info = parts.joinToString(" · ")
            } else {
                error = r.exceptionOrNull()?.message ?: "Connexion impossible"
            }
            connecting = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        StepBar(2, goBack, !vm.onboarded)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp)) {
            T("Associer un boîtier OBD2", 28, lineHeight = 36, modifier = Modifier.padding(bottom = 8.dp))
            T(
                (vehicle?.let { "Pour « ${it.nickname} ». " } ?: "") + "Branchez le boîtier sur la prise diagnostic (sous le volant), mettez le contact, puis sélectionnez-le.",
                14, c.onv, modifier = Modifier.padding(bottom = 20.dp), lineHeight = 20,
            )
            Segmented(
                listOf(
                    Triple("Bluetooth", Icons.Rounded.Bluetooth, mode == 0),
                    Triple("BLE", Icons.Rounded.BluetoothSearching, mode == 1),
                    Triple("Wi-Fi", Icons.Rounded.Wifi, mode == 2),
                ),
            ) { mode = it; found.clear(); selected = null }

            if (mode < 2) {
                when {
                    bt == null -> Notice(Icons.Rounded.BluetoothDisabled, "Ce téléphone n'a pas de Bluetooth.", null, null)
                    !btGranted -> Notice(Icons.Rounded.Bluetooth, "CarDeck a besoin de l'autorisation Bluetooth pour trouver le boîtier.", "Autoriser") { permLauncher.launch(Perms.bluetooth) }
                    !btOn -> Notice(Icons.Rounded.BluetoothDisabled, "Le Bluetooth est désactivé.", "Activer le Bluetooth") {
                        enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                    else -> {
                        Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp), contentAlignment = Alignment.Center) {
                            Radar(180.dp, scanning, Icons.Rounded.BluetoothSearching)
                        }
                        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            T(if (scanning) "Recherche en cours…" else "${found.size} appareil${if (found.size > 1) "s" else ""} trouvé${if (found.size > 1) "s" else ""}", 14, weight = 500)
                            if (!scanning && !connecting) TextBtn("Relancer", { scanKey++ }, Icons.Rounded.Refresh)
                        }
                        if (mode == 0) T("Boîtier absent ? Code PIN habituel : 1234 ou 0000.", 12, c.onv, modifier = Modifier.padding(bottom = 4.dp))
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            found.sortedWith(compareByDescending<Found> { it.obdLike }.thenBy { it.name }).forEach { d ->
                                DeviceRow(
                                    icon = if (d.transport == Transport.Ble) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                                    name = d.name, sub = d.sub,
                                    connecting = connecting && selected == d.address,
                                    done = connected?.address == d.address,
                                    onClick = { connect(AdapterConfig(d.transport, d.address, d.name)) },
                                )
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp).clip(RoundedCornerShape(16.dp)).background(c.sf2).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ico(Icons.Rounded.WifiFind, 20, c.onv)
                    T("Connectez d'abord le téléphone au réseau Wi-Fi du boîtier (ex. « WiFi_OBDII »), puis revenez ici.", 13, c.onv, lineHeight = 18)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Adresse IP", host, { host = it.trim() }, Modifier.weight(1.6f), mono = true)
                    Field("Port", port, { port = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), number = true)
                }
                Spacer(Modifier.height(16.dp))
                val addr = "$host:$port"
                DeviceRow(Icons.Rounded.Wifi, "Boîtier Wi-Fi", addr, connecting && selected == addr, connected?.address == addr) {
                    connect(AdapterConfig(Transport.Wifi, addr, "Boîtier Wi-Fi"))
                }
            }

            if (info != null) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(c.gc).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ico(Icons.Rounded.CheckCircle, 22, c.ogc)
                    Column {
                        T("Boîtier connecté", 14, c.ogc, 600)
                        T(info!!, 13, c.ogc)
                        vin?.let { T("VIN $it", 13, c.ogc, mono = true, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
            }
            if (error != null) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(c.ec).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ico(Icons.Rounded.ErrorOutline, 22, c.oec)
                    T(error!!, 13, c.oec, lineHeight = 18)
                }
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FilledBtn("Continuer", { vm.finishPairing(connected, vin) }, enabled = connected != null && !connecting)
            if (!vm.onboarded) TextBtn("Plus tard", { vm.finishPairing(null, null) })
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: String?, onAction: (() -> Unit)?) {
    val c = Cd.c
    Column(Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ico(icon, 36, c.onv)
        T(text, 14, c.onv, modifier = Modifier.padding(top = 10.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) Box(Modifier.padding(top = 8.dp)) { TextBtn(action, onAction) }
    }
}

@Composable
private fun DeviceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, sub: String, connecting: Boolean, done: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (done) c.sc else c.sf1).clickable(enabled = !connecting, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.pc), contentAlignment = Alignment.Center) { Ico(icon, 22, c.opc) }
        Column(Modifier.weight(1f)) {
            T(name, 16, weight = 500, lineHeight = 24, maxLines = 1)
            T(sub, 12, c.onv, lineHeight = 16)
        }
        when {
            connecting -> Spinner()
            done -> Ico(Icons.Rounded.CheckCircle, 26, c.g)
            else -> Ico(Icons.Rounded.SettingsInputHdmi, 22, c.onv)
        }
    }
}
