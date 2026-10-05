package com.cardeck.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.BuildConfig
import com.cardeck.app.Screen
import com.cardeck.app.data.WIDGETS
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdSwitch
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.SectionLabel
import com.cardeck.app.ui.Segmented
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import com.cardeck.app.ui.ThemeMode
import com.cardeck.app.ui.oklch

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        BackBar("Paramètres", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            T("Paramètres", 32, lineHeight = 40, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf2).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(c.tc), contentAlignment = Alignment.Center) { T("CL", 20, c.otc, 500) }
                Column(Modifier.weight(1f)) { T("Camille Laurent", 18, weight = 500); T("camille.laurent@mail.fr", 14, c.onv) }
                Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 24, c.onv)
            }

            SectionLabel("Garage")
            Group {
                vm.vehicles.forEach { v ->
                    Row(
                        Modifier.fillMaxWidth().background(c.sf1).clickable { vm.selectVehicle(v.id) }.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(c.sc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 22, c.osc) }
                        Column(Modifier.weight(1f)) { T(v.name, 16); T(v.sub, 13, c.onv) }
                        val sel = v.id == vm.vehicle.id
                        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (sel) c.p else c.onv, CircleShape), contentAlignment = Alignment.Center) {
                            if (sel) Box(Modifier.size(10.dp).clip(CircleShape).background(c.p))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(c.sf1).clickable { vm.go(Screen.AddVehicle) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Add, 24, c.p) }
                    T("Ajouter un véhicule", 16, c.p, 500)
                }
            }

            SectionLabel("Tableau de bord")
            Group {
                WIDGETS.forEach { w ->
                    Box(Modifier.fillMaxWidth().background(c.sf1)) { WidgetToggleRow(w.icon, w.label, w.id in vm.widgets, 16) { vm.toggleWidget(w.id) } }
                }
            }

            SectionLabel("Boîtier OBD2")
            Group {
                Row(Modifier.fillMaxWidth().background(c.sf1).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.gc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.BluetoothConnected, 22, c.ogc) }
                    Column(Modifier.weight(1f)) { T(vm.deviceName, 16); T("Connecté · firmware 5.4.2", 13, c.onv) }
                    TextBtn("Changer", { vm.go(Screen.Pair) })
                }
                BoxOpt(Icons.Rounded.PlayCircle, "Démarrage automatique", "Se connecte dès que le moteur démarre", vm.autoStart) { vm.toggleAutoStart() }
                BoxOpt(Icons.Rounded.StopCircle, "Arrêt automatique", "Coupe la connexion 2 min après l'arrêt du moteur", vm.autoStop) { vm.toggleAutoStop() }
                BoxOpt(Icons.Rounded.Videocam, "Mode boîte noire", "Enregistre en continu les 30 s précédant un choc", vm.blackbox) { vm.toggleBlackbox() }
            }

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

            SectionLabel("Compte")
            Group {
                ActionRow(Icons.Rounded.Download, "Exporter mes données", c.onv, c.on, 400) {}
                ActionRow(Icons.Rounded.PrivacyTip, "Confidentialité", c.onv, c.on, 400) {}
                ActionRow(Icons.AutoMirrored.Rounded.Logout, "Se déconnecter", c.e, c.e, 500) { vm.resetToWelcome() }
            }
            T("CarDeck ${BuildConfig.VERSION_NAME}", 12, c.onv, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

@Composable
private fun BoxOpt(icon: ImageVector, label: String, sub: String, on: Boolean, onToggle: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().background(c.sf1).clickable(onClick = onToggle).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Ico(icon, 24, c.onv)
        Column(Modifier.weight(1f)) { T(label, 16); T(sub, 13, c.onv, lineHeight = 18) }
        CdSwitch(on, onToggle)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, iconColor: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color, weight: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Cd.c.sf1).clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Ico(icon, 24, iconColor)
        T(label, 16, textColor, weight)
    }
}
