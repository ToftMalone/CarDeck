package com.cardeck.app.car

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
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.InputCallback
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.model.signin.InputSignInMethod
import androidx.car.app.model.signin.SignInTemplate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cardeck.app.BuildConfig
import com.cardeck.app.Perms
import com.cardeck.app.data.AdapterConfig
import com.cardeck.app.data.TEMPLATES
import com.cardeck.app.data.Transport
import com.cardeck.app.data.fr
import com.cardeck.app.service.TripService
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val THEME_LABELS = mapOf(ThemeMode.Light to "Clair", ThemeMode.Dark to "Sombre", ThemeMode.System to "Système")

/** Paramètres : garage (véhicule actif, renommer, supprimer, ajouter) et apparence de l'appli. */
internal class SettingsCarScreen(ctx: CarContext) : CarScreen(ctx) {
    override suspend fun snapshot(): Any? = listOf(app.repo.vehicles.value, app.repo.activeId.value, vm.themeMode, vm.palette)

    override fun onGetTemplate(): Template {
        val activeId = active?.id
        val garage = ItemList.Builder()
        app.repo.vehicles.value.forEach { v ->
            val sel = v.id == activeId
            garage.addItem(
                Row.Builder().setTitle(v.nickname)
                    .addText(listOf(v.plate, v.template.model + " " + v.template.code, v.year).filter { it.isNotBlank() }.joinToString(" · ") + if (sel) " · Véhicule actif" else "")
                    .addText(v.adapter?.let { "Boîtier : ${it.name} (${it.transport.label})" } ?: "Aucun boîtier associé")
                    .setImage(CarIcons.of(Icons.Rounded.DirectionsCar, if (sel) CarIcons.GREEN else CarIcons.GREY))
                    .setBrowsable(true).setOnClickListener { push(VehicleCarScreen(carContext, v.id)) }.build(),
            )
        }
        garage.addItem(Row.Builder().setTitle("Ajouter un véhicule").setBrowsable(true).setOnClickListener { push(AddVehicleCarScreen(carContext)) }.build())
        val look = ItemList.Builder()
            .addItem(
                Row.Builder().setTitle("Thème").addText(THEME_LABELS.getValue(vm.themeMode)).setBrowsable(true).setOnClickListener {
                    val modes = ThemeMode.entries
                    push(ChoiceCarScreen(carContext, "Thème", modes.map { THEME_LABELS.getValue(it) }, modes.indexOf(vm.themeMode)) { vm.updateThemeMode(modes[it]) })
                }.build(),
            )
            .addItem(
                Row.Builder().setTitle("Couleur dynamique").addText(vm.palette.label).setBrowsable(true).setOnClickListener {
                    val ps = Palette.entries
                    push(ChoiceCarScreen(carContext, "Couleur dynamique", ps.map { it.label }, ps.indexOf(vm.palette)) { vm.updatePalette(ps[it]) })
                }.build(),
            )
        val about = ItemList.Builder().addItem(
            Row.Builder().setTitle("CarDeck ${BuildConfig.VERSION_NAME}")
                .addText("Le véhicule sélectionné est utilisé partout : tableau de bord, diagnostic, trajets et connexion OBD2.").build(),
        )
        return ListTemplate.Builder()
            .setTitle("Paramètres")
            .setHeaderAction(Action.BACK)
            .addSectionedList(SectionedItemList.create(garage.build(), "Garage"))
            .addSectionedList(SectionedItemList.create(look.build(), "Apparence"))
            .addSectionedList(SectionedItemList.create(about.build(), "À propos"))
            .build()
    }
}

/** Fiche d'un véhicule du garage : choisir, renommer, associer un boîtier, supprimer. */
internal class VehicleCarScreen(ctx: CarContext, private val id: Long) : CarScreen(ctx) {
    override suspend fun snapshot(): Any? = listOf(app.repo.vehicles.value.find { it.id == id }, app.repo.activeId.value)

    override fun onGetTemplate(): Template {
        val v = app.repo.vehicles.value.find { it.id == id } ?: return message("Véhicule", "Ce véhicule n'existe plus.")
        val sel = v.id == active?.id
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle(v.template.fullName).addText(v.template.specs).build())
            .addRow(Row.Builder().setTitle("Boîtier OBD2").addText(v.adapter?.let { "${it.name} (${it.transport.label})" } ?: "Aucun boîtier associé").build())
            .addRow(
                Row.Builder().setTitle(if (sel) "Véhicule actif" else "Véhicule inactif")
                    .addText(listOfNotNull(v.plate.ifBlank { null }, v.year.ifBlank { null }, v.vin?.let { "VIN $it" }).joinToString(" · ").ifBlank { "Immatriculation et VIN non renseignés" })
                    .build(),
            )
            .apply {
                if (!sel) addAction(action("Utiliser ce véhicule", primary = true) { app.repo.select(id); invalidate() })
                else addAction(action("Boîtier OBD2") { push(PairCarScreen(carContext, id)) })
            }
            .addAction(
                action("Renommer") {
                    push(TextInputCarScreen(carContext, "Renommer le véhicule", "Nom du profil", v.nickname) { name -> app.repo.renameVehicle(id, name) })
                },
            )
            .build()
        val delete = action("Supprimer") {
            push(
                ConfirmCarScreen(
                    carContext, "Supprimer « ${v.nickname} » ?",
                    "Le profil et toutes ses données (trajets, diagnostics, boîtier associé) seront définitivement supprimés.", "Supprimer", danger = true,
                ) { c ->
                    vm.deleteVehicle(id)
                    c.screenManager.popToRoot()
                },
            )
        }
        return PaneTemplate.Builder(pane).setTitle(v.nickname).setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(delete).build()).build()
    }
}

/** Liste à choix unique (thème, couleur…). */
internal class ChoiceCarScreen(
    ctx: CarContext,
    private val title: String,
    private val labels: List<String>,
    private val selected: Int,
    private val onSelect: (Int) -> Unit,
) : CarScreen(ctx) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        labels.forEach { items.addItem(Row.Builder().setTitle(it).build()) }
        items.setSelectedIndex(selected.coerceAtLeast(0))
        items.setOnSelectedListener { i -> onSelect(i); screenManager.pop() }
        return ListTemplate.Builder().setTitle(title).setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

/** Saisie d'un texte au clavier de la voiture (disponible à l'arrêt). */
internal class TextInputCarScreen(
    ctx: CarContext,
    private val title: String,
    private val hint: String,
    private val value: String,
    private val onSubmit: (String) -> Unit,
) : CarScreen(ctx) {
    private var error: String? = null

    override fun onGetTemplate(): Template {
        val input = InputSignInMethod.Builder(object : InputCallback {
            override fun onInputSubmitted(text: String) {
                if (text.isBlank()) { error = "Saisissez une valeur"; invalidate(); return }
                onSubmit(text.trim())
                screenManager.pop()
            }
        })
            .setHint(hint)
            .setDefaultValue(value)
            .setShowKeyboardByDefault(true)
            .apply { error?.let { setErrorMessage(it) } }
            .build()
        return SignInTemplate.Builder(input).setTitle(title).setHeaderAction(Action.BACK).build()
    }
}

/** Ajout d'un véhicule à partir d'un modèle (nom par défaut, renommable ensuite dans Paramètres). */
internal class AddVehicleCarScreen(ctx: CarContext) : CarScreen(ctx) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        TEMPLATES.forEach { t ->
            if (t.comingSoon) {
                items.addItem(Row.Builder().setTitle(t.fullName).addText("Arrive bientôt").build())
                return@forEach
            }
            items.addItem(
                Row.Builder().setTitle(t.fullName).addText(t.specs)
                    .setBrowsable(true)
                    .setOnClickListener {
                        val id = vm.createVehicleQuick(t.id)
                        toast("« ${app.repo.vehicles.value.find { it.id == id }?.nickname ?: t.model} » ajouté")
                        screenManager.pop()
                        push(PairCarScreen(carContext, id))
                    }.build(),
            )
        }
        return ListTemplate.Builder().setTitle("Ajouter un véhicule").setHeaderAction(Action.BACK).setSingleList(items.build()).build()
    }
}

/**
 * Association d'un boîtier OBD2 : appareils Bluetooth appairés et à proximité (classique et BLE), ou boîtier Wi-Fi.
 * La recherche dure 12 s (écran d'attente), puis la liste s'affiche.
 */
@SuppressLint("MissingPermission")
internal class PairCarScreen(ctx: CarContext, private val vehicleId: Long) : CarScreen(ctx) {
    private class Found(val address: String, val name: String, val sub: String, val transport: Transport)

    private val bt: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
    private val found = LinkedHashMap<String, Found>()
    private val scanKey = MutableStateFlow(0)
    private var scanning = false
    private var connecting = false
    private var wifi = "192.168.0.10:35000"

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) { app.obd.pairing = true }
            override fun onDestroy(owner: LifecycleOwner) { app.obd.pairing = false }
        })
    }

    private var scannedKey = -1

    // Une recherche à l'ouverture puis à chaque demande (pas à chaque retour sur l'écran).
    override suspend fun whileVisible() = scanKey.collectLatest { k ->
        if (k != scannedKey) {
            scannedKey = k
            scan()
        }
    }

    override suspend fun snapshot(): Any? = listOf(scanning, connecting, found.keys.toList(), Perms.bluetoothOk(carContext), bt?.isEnabled)

    private suspend fun scan() {
        val adapter = bt ?: return
        if (!Perms.bluetoothOk(carContext) || !adapter.isEnabled) return
        val appCtx = carContext.applicationContext
        found.clear()
        scanning = true
        invalidate()
        runCatching { adapter.bondedDevices }.getOrNull()?.forEach { d ->
            if (d.type != BluetoothDevice.DEVICE_TYPE_LE) found[d.address] = Found(d.address, d.name ?: d.address, "Bluetooth · Appairé", Transport.Classic)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                if (d.type == BluetoothDevice.DEVICE_TYPE_LE) return
                val name = d.name ?: return
                val bonded = d.bondState == BluetoothDevice.BOND_BONDED
                found[d.address] = Found(d.address, name, "Bluetooth · " + if (bonded) "Appairé" else "Non appairé", Transport.Classic)
            }
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: return
                if (found[r.device.address]?.transport != Transport.Classic) found[r.device.address] = Found(r.device.address, name, "Bluetooth LE · ${r.rssi} dBm", Transport.Ble)
            }
        }
        val scanner = adapter.bluetoothLeScanner
        ContextCompat.registerReceiver(appCtx, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
        runCatching { adapter.startDiscovery() }
        runCatching { scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb) }
        try {
            delay(12_000)
        } finally {
            runCatching { adapter.cancelDiscovery() }
            runCatching { scanner?.stopScan(cb) }
            runCatching { appCtx.unregisterReceiver(receiver) }
            scanning = false
            invalidate()
        }
    }

    private fun connect(a: AdapterConfig) {
        if (connecting) return
        connecting = true
        invalidate()
        lifecycleScope.launch {
            runCatching { bt?.cancelDiscovery() }
            val r = withContext(Dispatchers.IO) { app.obd.connect(a) }
            if (r.isSuccess) {
                val details = withContext(Dispatchers.IO) {
                    app.obd.use { e ->
                        val ecu = e.probe()
                        Triple(if (ecu) e.vin() else null, e.voltage(), ecu)
                    }
                }
                val parts = mutableListOf(r.getOrNull() ?: "ELM327")
                details?.second?.let { parts += fr(it, 1) + " V" }
                if (details?.third == false) parts += "contact coupé"
                val name = app.repo.vehicles.value.find { it.id == vehicleId }?.nickname ?: "ce véhicule"
                push(
                    ConfirmCarScreen(carContext, "Boîtier connecté", "${a.name} · ${parts.joinToString(" · ")}\n\nAssocier ce boîtier à « $name » ?", "Associer") { c ->
                        app.repo.setAdapter(vehicleId, a, details?.first)
                        app.obd.pairing = false
                        TripService.start(carContext)
                        c.toast("Boîtier associé à « $name »")
                        c.screenManager.popToRoot()
                    },
                )
            } else {
                toast(r.exceptionOrNull()?.message ?: "Connexion impossible")
            }
            connecting = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle("Boîtier OBD2").setHeaderAction(Action.BACK)
        if (scanning || connecting) return b.setLoading(true).build()
        val items = ItemList.Builder()
        val adapter = bt
        when {
            adapter == null -> items.addItem(Row.Builder().setTitle("Bluetooth indisponible").addText("Ce téléphone n'a pas de Bluetooth : utilisez un boîtier Wi-Fi.").build())
            !Perms.bluetoothOk(carContext) -> items.addItem(
                Row.Builder().setTitle("Autoriser le Bluetooth").addText("Nécessaire pour trouver le boîtier (réponse sur le téléphone)").setBrowsable(true)
                    .setOnClickListener { carContext.requestPermissions(Perms.bluetooth.toList()) { _, _ -> scanKey.value++ } }.build(),
            )
            !adapter.isEnabled -> items.addItem(Row.Builder().setTitle("Bluetooth désactivé").addText("Activez le Bluetooth du téléphone puis relancez la recherche.").build())
        }
        found.values.forEach { f ->
            items.addItem(
                Row.Builder().setTitle(f.name).addText(f.sub).setBrowsable(true)
                    .setOnClickListener { connect(AdapterConfig(f.transport, f.address, f.name)) }.build(),
            )
        }
        items.addItem(
            Row.Builder().setTitle("Boîtier Wi-Fi").addText("$wifi · téléphone connecté au Wi-Fi du boîtier (ex. « WiFi_OBDII »)").setBrowsable(true)
                .setOnClickListener { connect(AdapterConfig(Transport.Wifi, wifi, "Boîtier Wi-Fi")) }.build(),
        )
        items.addItem(
            Row.Builder().setTitle("Autre adresse Wi-Fi").addText("Adresse IP et port du boîtier").setBrowsable(true).setOnClickListener {
                push(TextInputCarScreen(carContext, "Adresse du boîtier Wi-Fi", "Adresse IP:port", wifi) { addr ->
                    wifi = if (':' in addr) addr else "$addr:35000"
                    invalidate()
                })
            }.build(),
        )
        return b.setActionStrip(ActionStrip.Builder().addAction(action("Rechercher") { scanKey.value++ }).build())
            .setSingleList(items.build())
            .build()
    }
}
