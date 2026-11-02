package com.cardeck.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.BuildConfig
import com.cardeck.app.Screen
import com.cardeck.app.data.VehicleProfile
import com.cardeck.app.data.WakeConfig
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Field
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.SectionLabel
import com.cardeck.app.ui.Segmented
import com.cardeck.app.ui.T
import com.cardeck.app.ui.ThemeMode
import com.cardeck.app.ui.oklch

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    val vehicles by vm.repo.vehicles.collectAsState()
    val active = rememberActiveVehicle(vm)
    var toDelete by remember { mutableStateOf<VehicleProfile?>(null) }
    var toRename by remember { mutableStateOf<VehicleProfile?>(null) }
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Paramètres", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            T("Paramètres", 32, lineHeight = 40, modifier = Modifier.padding(top = 4.dp))

            SectionLabel("Garage")
            Group {
                vehicles.forEach { v ->
                    val sel = v.id == active?.id
                    Row(
                        Modifier.fillMaxWidth().background(c.sf1).clickable { vm.repo.select(v.id) }.heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (sel) c.p else c.onv, CircleShape), contentAlignment = Alignment.Center) {
                            if (sel) Box(Modifier.size(10.dp).clip(CircleShape).background(c.p))
                        }
                        Column(Modifier.weight(1f)) {
                            T(v.nickname, 16, maxLines = 1)
                            T(listOf(v.plate, v.template.model + " " + v.template.code, v.year).filter { it.isNotBlank() }.joinToString(" · "), 13, c.onv, maxLines = 1)
                            T(v.adapter?.let { "Boîtier : ${it.name} (${it.transport.label})" } ?: "Aucun boîtier associé", 12, c.onv, maxLines = 1)
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { newName = v.nickname; toRename = v }, contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Edit, 22, c.onv) }
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { toDelete = v }, contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DeleteOutline, 22, c.onv) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(c.sf1).clickable { vm.go(Screen.AddVehicle) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Add, 24, c.p) }
                    T("Ajouter un véhicule", 16, c.p, 500)
                }
            }
            T("Le véhicule sélectionné est utilisé partout : tableau de bord, diagnostic, trajets et connexion OBD2.", 12, c.onv, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), lineHeight = 16)

            WakeSection(vm, vehicles)

            SectionLabel("Apparence")
            Group {
                Column(Modifier.fillMaxWidth().background(c.sf1).padding(16.dp)) {
                    T("Thème", 16, modifier = Modifier.padding(bottom = 12.dp))
                    Segmented(
                        listOf(
                            Triple("Clair", Icons.Rounded.LightMode, vm.themeMode == ThemeMode.Light),
                            Triple("Sombre", Icons.Rounded.DarkMode, vm.themeMode == ThemeMode.Dark),
                            Triple("Système", Icons.Rounded.BrightnessAuto, vm.themeMode == ThemeMode.System),
                        ),
                    ) { vm.updateThemeMode(ThemeMode.entries[it]) }
                }
                Column(Modifier.fillMaxWidth().background(c.sf1).padding(16.dp)) {
                    T("Couleur dynamique", 16)
                    T("Toute la palette est dérivée d'une couleur source", 13, c.onv, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Palette.entries.forEach { p ->
                            val sel = p == vm.palette
                            Column(
                                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).then(if (sel) Modifier.border(3.dp, c.on, RoundedCornerShape(16.dp)) else Modifier).clickable { vm.updatePalette(p) },
                            ) {
                                Box(Modifier.fillMaxWidth().weight(1f).background(oklch(.62, .14, p.hue)))
                                Row(Modifier.fillMaxWidth().weight(1f)) {
                                    Box(Modifier.weight(1f).fillMaxSize().background(oklch(.8, .06, p.hue)))
                                    Box(Modifier.weight(1f).fillMaxSize().background(oklch(.72, .1, (p.hue + 60) % 360)))
                                }
                            }
                        }
                    }
                }
            }
            BackupSection(vm)
            AppSection(vm)
        }
    }

    toRename?.let { v ->
        AlertDialog(
            onDismissRequest = { toRename = null },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Renommer le véhicule", 22, lineHeight = 28) },
            text = { Field("Nom du profil", newName, { newName = it }) },
            confirmButton = {
                TextButton({ vm.repo.renameVehicle(v.id, newName.trim()); toRename = null }, enabled = newName.isNotBlank()) { T("Enregistrer", 14, c.p, 600) }
            },
            dismissButton = { TextButton({ toRename = null }) { T("Annuler", 14, c.p, 500) } },
        )
    }

    toDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Supprimer « ${v.nickname} » ?", 22, lineHeight = 28) },
            text = { T("Le profil et toutes ses données (trajets, diagnostics, boîtier associé) seront définitivement supprimés.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ toDelete = null; vm.deleteVehicle(v.id) }) { T("Supprimer", 14, c.e, 600) } },
            dismissButton = { TextButton({ toDelete = null }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

/** Sortie de veille par véhicule : Bluetooth (coché + appareil choisi) et / ou Android Auto (coché). */
@Composable
private fun WakeSection(vm: AppViewModel, vehicles: List<VehicleProfile>) {
    val c = Cd.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val changes by vm.repo.changes.collectAsState()
    var editing by remember { mutableStateOf<VehicleProfile?>(null) }
    var battery by remember { mutableStateOf(com.cardeck.app.service.Wake.batteryOk(ctx)) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { battery = com.cardeck.app.service.Wake.batteryOk(ctx) }
    fun label(w: WakeConfig) = listOfNotNull(
        if (w.btReady) "Bluetooth « ${w.btName} »" else if (w.bluetooth) "Bluetooth (appareil à choisir)" else null,
        if (w.androidAuto) "Android Auto" else null,
    ).joinToString(" ou ").ifEmpty { "Jamais : je lance CarDeck moi-même" }
    SectionLabel("Réveil automatique")
    Group {
        vehicles.forEach { v ->
            val w = remember(v.id, changes) { vm.repo.wake(v.id) }
            BackupRow(Icons.Rounded.Bluetooth, v.nickname, label(w), true) { editing = v }
        }
    }
    val anyAuto = remember(vehicles, changes) { vehicles.any { vm.repo.wake(it.id).any } }
    T(
        "Après un trajet, ou 5 minutes sans rouler (hors de l'application), CarDeck se met en veille, sans notification. " +
            "Elle en sort quand tu l'ouvres, ou à la connexion cochée ci-dessus." +
            if (anyAuto && !battery) " Pour cela, l'optimisation de batterie doit être désactivée pour CarDeck : touche ici pour l'autoriser." else "",
        12, if (anyAuto && !battery) c.e else c.onv, lineHeight = 16,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).then(
            if (anyAuto && !battery) Modifier.clickable { batteryLauncher.launch(batteryIntent(ctx)) } else Modifier,
        ),
    )

    editing?.let { v ->
        var w by remember(v.id) { mutableStateOf(vm.repo.wake(v.id)) }
        val devices = remember(v.id) { bondedDevices(ctx).filter { !it.first.equals(v.adapter?.address, ignoreCase = true) } }
        fun save(n: WakeConfig) {
            w = n
            vm.repo.setWake(v.id, n)
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Réveil de ${v.nickname}", 22, lineHeight = 28) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CheckRow("Android Auto", "La voiture a Android Auto : réveil à sa connexion", w.androidAuto) { save(w.copy(androidAuto = it)) }
                    CheckRow("Bluetooth", "Réveil à la connexion à l'autoradio de la voiture", w.bluetooth) { save(w.copy(bluetooth = it)) }
                    if (w.bluetooth) {
                        if (devices.isEmpty()) T("Aucun appareil Bluetooth appairé : appaire d'abord le téléphone à l'autoradio.", 12, c.e, lineHeight = 16, modifier = Modifier.padding(start = 36.dp, top = 4.dp))
                        devices.forEach { (addr, name) ->
                            val sel = w.btAddress.equals(addr, ignoreCase = true)
                            Row(
                                Modifier.fillMaxWidth().padding(start = 28.dp).clip(RoundedCornerShape(12.dp)).clickable { save(w.copy(btAddress = addr, btName = name)) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (sel) c.p else c.onv, CircleShape), contentAlignment = Alignment.Center) {
                                    if (sel) Box(Modifier.size(10.dp).clip(CircleShape).background(c.p))
                                }
                                T(name, 15, lineHeight = 20)
                            }
                        }
                    }
                    if (w.androidAuto) T(
                        "Android Auto : CarDeck vérifie qu'Android Auto démarre à chaque connexion Bluetooth du téléphone (pendant 3 minutes).",
                        12, c.onv, lineHeight = 16, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    editing = null
                    if (w.any && !com.cardeck.app.service.Wake.batteryOk(ctx)) batteryLauncher.launch(batteryIntent(ctx))
                }) { T("OK", 14, c.p, 600) }
            },
        )
    }
}

@Composable
private fun CheckRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Checkbox(checked, onChange, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = c.p, uncheckedColor = c.onv))
        Column(Modifier.weight(1f)) { T(title, 16); T(sub, 12, c.onv, lineHeight = 16) }
    }
}

@android.annotation.SuppressLint("BatteryLife")
private fun batteryIntent(ctx: android.content.Context) =
    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}"))

/** Appareils Bluetooth appairés au téléphone (adresse, nom). */
@android.annotation.SuppressLint("MissingPermission")
private fun bondedDevices(ctx: android.content.Context): List<Pair<String, String>> {
    if (!com.cardeck.app.Perms.bluetoothOk(ctx)) return emptyList()
    val bt = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching { bt.bondedDevices.map { it.address to (it.name ?: it.address) }.sortedBy { it.second.lowercase() } }.getOrDefault(emptyList())
}

/** Sauvegarde chiffrée de toutes les données, et restauration (remplace les données actuelles). */
@Composable
private fun BackupSection(vm: AppViewModel) {
    val c = Cd.c
    var toRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.backupTo(it) } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> toRestore = uri }
    val busy = vm.backupBusy
    SectionLabel("Sauvegarde")
    Group {
        BackupRow(Icons.Rounded.Lock, "Sauvegarder les données", "Véhicules, trajets, diagnostics, codages d'origine et réglages, dans un fichier chiffré", busy == null) {
            save.launch(vm.backupName())
        }
        BackupRow(Icons.Rounded.Restore, "Restaurer une sauvegarde", "Remplace toutes les données actuelles", busy == null) {
            open.launch(arrayOf("*/*"))
        }
    }
    T(
        busy ?: ("Le fichier est chiffré (AES-256) : seule CarDeck peut le relire." +
            if (com.cardeck.app.data.Backup.official) "" else " Version de développement : clé de test, réservée aux essais."),
        12, if (busy != null) c.p else c.onv, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), lineHeight = 16,
    )

    toRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { toRestore = null },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Restaurer cette sauvegarde ?", 22, lineHeight = 28) },
            text = { T("Toutes les données actuelles (véhicules, trajets, diagnostics, réglages) seront remplacées par celles de la sauvegarde.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ toRestore = null; vm.restoreFrom(uri) }) { T("Restaurer", 14, c.e, 600) } },
            dismissButton = { TextButton({ toRestore = null }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

@Composable
private fun BackupRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().background(c.sf1).clickable(enabled = enabled, onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ico(icon, 22, if (enabled) c.p else c.onv)
        Column(Modifier.weight(1f)) { T(title, 16, if (enabled) c.on else c.onv); T(sub, 13, c.onv, lineHeight = 18) }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}
