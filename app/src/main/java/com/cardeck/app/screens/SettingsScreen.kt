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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import com.cardeck.app.data.BackupCrypto
import com.cardeck.app.data.BackupLock
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

/** Sortie de veille par véhicule : à la connexion Bluetooth à l'appareil choisi (autoradio de la voiture). */
@Composable
private fun WakeSection(vm: AppViewModel, vehicles: List<VehicleProfile>) {
    val c = Cd.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val changes by vm.repo.changes.collectAsState()
    var editing by remember { mutableStateOf<VehicleProfile?>(null) }
    var battery by remember { mutableStateOf(com.cardeck.app.service.Wake.batteryOk(ctx)) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { battery = com.cardeck.app.service.Wake.batteryOk(ctx) }
    fun label(w: WakeConfig) =
        if (w.btReady) "Bluetooth « ${w.btName} »" else if (w.bluetooth) "Bluetooth (appareil à choisir)" else "Jamais : je lance CarDeck moi-même"
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
            "Elle en sort quand tu l'ouvres, ou à la connexion Bluetooth choisie ci-dessus." +
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

/** Sauvegarde de toutes les données (protection au choix), et restauration (remplace les données actuelles). */
@Composable
private fun BackupSection(vm: AppViewModel) {
    val c = Cd.c
    var choosing by remember { mutableStateOf(false) }
    var pendingLock by remember { mutableStateOf<BackupLock?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val lock = pendingLock
        pendingLock = null
        if (uri != null && lock != null) vm.backupTo(uri, lock) else (lock as? BackupLock.Password)?.password?.fill('\u0000')
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.prepareRestore(it) } }
    val busy = vm.backupBusy
    SectionLabel("Sauvegarde")
    Group {
        BackupRow(Icons.Rounded.Lock, "Sauvegarder les données", "Véhicules, trajets, diagnostics, codages d'origine et réglages, dans un fichier", busy == null) {
            choosing = true
        }
        BackupRow(Icons.Rounded.Restore, "Restaurer une sauvegarde", "Remplace toutes les données actuelles", busy == null) {
            open.launch(arrayOf("*/*"))
        }
    }
    T(
        busy ?: "Au moment de sauvegarder, tu choisis la protection : aucune, chiffrée par CarDeck, mot de passe, ou liée à cet appareil.",
        12, if (busy != null) c.p else c.onv, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), lineHeight = 16,
    )

    if (choosing) BackupProtectionDialog(vm, onDismiss = { choosing = false }) { lock ->
        choosing = false
        pendingLock = lock
        save.launch(vm.backupName())
    }

    vm.restoreRequest?.let { req -> RestoreDialog(vm, req) }
}

/** Choix de la protection d'une nouvelle sauvegarde. */
@Composable
private fun BackupProtectionDialog(vm: AppViewModel, onDismiss: () -> Unit, onConfirm: (BackupLock) -> Unit) {
    val c = Cd.c
    val device = vm.deviceBackupLock
    var mode by remember { mutableStateOf(BackupCrypto.MODE_PASSWORD) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val pwOk = pw.isNotEmpty() && pw == pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
        title = { T("Protéger la sauvegarde", 22, lineHeight = 28) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtectionOption(
                    Icons.Rounded.LockOpen, "Sans protection", "Fichier en clair, aucun mot de passe. ⚠️ Toute personne qui récupère ce fichier " +
                        "peut lire toutes tes données : véhicules, trajets avec positions GPS, diagnostics, réglages.",
                    mode == BackupCrypto.MODE_PLAIN, true,
                ) { mode = BackupCrypto.MODE_PLAIN }
                ProtectionOption(
                    Icons.Rounded.Lock, "Chiffrée par CarDeck", "Chiffrée (AES-256) avec la clé de l'application, sans mot de passe. " +
                        "Restaurable sur n'importe quel appareil avec CarDeck." +
                        if (BuildConfig.BACKUP_KEY.isEmpty()) " Version de développement : clé de test, réservée aux essais." else "",
                    mode == BackupCrypto.MODE_APP, true,
                ) { mode = BackupCrypto.MODE_APP }
                ProtectionOption(
                    Icons.Rounded.Password, "Mot de passe", "Chiffré avec le mot de passe de ton choix, restaurable sur n'importe quel appareil. " +
                        "Sans lui, la sauvegarde est perdue : CarDeck ne peut pas le retrouver.",
                    mode == BackupCrypto.MODE_PASSWORD, true,
                ) { mode = BackupCrypto.MODE_PASSWORD }
                if (mode == BackupCrypto.MODE_PASSWORD) {
                    Column(Modifier.padding(start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Field("Mot de passe", pw, { pw = it }, password = true)
                        StrengthMeter(pw)
                        Field("Confirmer le mot de passe", pw2, { pw2 = it }, password = true)
                        if (pw2.isNotEmpty() && pw != pw2) T("Les deux mots de passe sont différents.", 12, c.e)
                    }
                }
                ProtectionOption(
                    Icons.Rounded.PhonelinkLock, "Liée à cet appareil",
                    if (device != null) "Chiffré avec l'empreinte de ${device.label}, sans mot de passe. Restaurable uniquement sur cet appareil, " +
                        "tant qu'il n'est pas réinitialisé : sur un autre téléphone, ou après une réinitialisation, le fichier est illisible. " +
                        "Idéal pour un autoradio Android qui reste dans la voiture : une copie du fichier ne sert à rien ailleurs."
                    else "Indisponible : cet appareil ne fournit pas d'identifiant assez fiable pour lier une sauvegarde.",
                    mode == BackupCrypto.MODE_DEVICE, device != null,
                ) { mode = BackupCrypto.MODE_DEVICE }
            }
        },
        confirmButton = {
            val enabled = mode != BackupCrypto.MODE_PASSWORD || pwOk
            TextButton(
                {
                    when (mode) {
                        BackupCrypto.MODE_PLAIN -> onConfirm(BackupLock.None)
                        BackupCrypto.MODE_APP -> onConfirm(com.cardeck.app.data.Backup.appLock())
                        BackupCrypto.MODE_PASSWORD -> onConfirm(BackupLock.Password(pw.toCharArray()))
                        else -> device?.let(onConfirm)
                    }
                },
                enabled = enabled,
            ) { T("Choisir l'emplacement", 14, if (enabled) c.p else c.onv, 600) }
        },
        dismissButton = { TextButton(onDismiss) { T("Annuler", 14, c.p, 500) } },
    )
}

@Composable
private fun ProtectionOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) c.pc else c.sf1)
            .clickable(enabled = enabled, onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ico(if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, 20, if (!enabled) c.onv else if (selected) c.opc else c.onv)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ico(icon, 18, if (!enabled) c.onv else if (selected) c.opc else c.on)
                T(title, 15, if (!enabled) c.onv else if (selected) c.opc else c.on, 500)
            }
            T(sub, 12, if (selected) c.opc else c.onv, lineHeight = 16)
        }
    }
}

/** Force du mot de passe, à titre indicatif : aucun mot de passe n'est refusé. */
@Composable
private fun StrengthMeter(pw: String) {
    val c = Cd.c
    if (pw.isEmpty()) return
    val score = BackupCrypto.passwordStrength(pw)
    val (label, color) = when (score) {
        0 -> "Très faible" to c.e
        1 -> "Faible" to c.e
        2 -> "Moyen" to c.w
        3 -> "Fort" to c.g
        else -> "Très fort" to c.g
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= score) color else c.sf4)) }
    }
    T(
        "Force : $label" + if (score < 3) " · conseil : 12 caractères ou plus, en mêlant lettres, chiffres et symboles (ce n'est pas obligatoire)." else "",
        12, color, lineHeight = 16,
    )
}

/** Confirmation de la restauration, avec le mot de passe si la sauvegarde en demande un. */
@Composable
private fun RestoreDialog(vm: AppViewModel, req: AppViewModel.RestoreRequest) {
    val c = Cd.c
    var pw by remember(req.uri) { mutableStateOf("") }
    val busy = vm.backupBusy != null
    val h = req.header
    AlertDialog(
        onDismissRequest = { if (!busy) vm.cancelRestore() },
        containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
        title = { T("Restaurer cette sauvegarde ?", 22, lineHeight = 28) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(
                    when (h.mode) {
                        BackupCrypto.MODE_PLAIN -> "🔓 Sauvegarde en clair (sans protection)."
                        BackupCrypto.MODE_APP, BackupCrypto.MODE_APP_TEST -> "🔐 Sauvegarde chiffrée par CarDeck."
                        BackupCrypto.MODE_PASSWORD -> "🔑 Sauvegarde protégée par mot de passe."
                        BackupCrypto.MODE_DEVICE -> "📱 Sauvegarde liée à " + h.label.ifBlank { "un appareil" } + " : restaurable seulement sur cet appareil."
                        else -> "🔐 Sauvegarde de CarDeck 0.1."
                    },
                    14, c.on, lineHeight = 20,
                )
                T("Toutes les données actuelles (véhicules, trajets, diagnostics, réglages) seront remplacées par celles de la sauvegarde.", 14, c.onv, lineHeight = 20)
                if (h.mode != BackupCrypto.MODE_DEVICE) {
                    T("Les codages d'origine ne sont repris que depuis une sauvegarde liée à cet appareil.", 12, c.onv, lineHeight = 16)
                }
                if (h.needsPassword) Field("Mot de passe", pw, { pw = it }, password = true)
                req.error?.let { T(it, 13, c.e, 500) }
                vm.backupBusy?.let { T(it, 13, c.p) }
            }
        },
        confirmButton = {
            val enabled = !busy && (!h.needsPassword || pw.isNotEmpty())
            TextButton({ vm.restoreFrom(if (h.needsPassword) pw.toCharArray() else null) }, enabled = enabled) {
                T("Restaurer", 14, if (enabled) c.e else c.onv, 600)
            }
        },
        dismissButton = { TextButton({ vm.cancelRestore() }, enabled = !busy) { T("Annuler", 14, c.p, 500) } },
    )
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
