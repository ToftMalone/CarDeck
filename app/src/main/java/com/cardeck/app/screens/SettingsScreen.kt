package com.cardeck.app.screens

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
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LightMode
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
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (sel) c.p else c.onv, CircleShape), contentAlignment = Alignment.Center) {
                            if (sel) Box(Modifier.size(10.dp).clip(CircleShape).background(c.p))
                        }
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c.sc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 22, c.osc) }
                        Column(Modifier.weight(1f)) {
                            T(v.nickname, 16)
                            T(listOf(v.plate, v.template.model + " " + v.template.code, v.year).filter { it.isNotBlank() }.joinToString(" · "), 13, c.onv)
                            T(v.adapter?.let { "Boîtier : ${it.name} (${it.transport.label})" } ?: "Aucun boîtier associé", 12, c.onv)
                        }
                        Box(Modifier.size(48.dp).clip(CircleShape).clickable { newName = v.nickname; toRename = v }, contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Edit, 22, c.onv) }
                        Box(Modifier.size(48.dp).clip(CircleShape).clickable { toDelete = v }, contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DeleteOutline, 22, c.onv) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(c.sf1).clickable { vm.go(Screen.AddVehicle) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(20.dp))
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Add, 24, c.p) }
                    T("Ajouter un véhicule", 16, c.p, 500)
                }
            }
            T("Le véhicule sélectionné est utilisé partout : tableau de bord, diagnostic, trajets et connexion OBD2.", 12, c.onv, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), lineHeight = 16)

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
            T("CarDeck ${BuildConfig.VERSION_NAME}", 12, c.onv, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
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

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}
