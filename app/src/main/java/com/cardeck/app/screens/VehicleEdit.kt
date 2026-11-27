package com.cardeck.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Field
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.LeadingIcon
import com.cardeck.app.ui.SectionLabel
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import com.cardeck.app.ui.TonalBtn
import com.cardeck.app.ui.rise

/**
 * Modification d'un véhicule du garage : nom du profil, immatriculation et année, modèle (même sélecteur que pour un
 * nouveau véhicule) et boîtier OBD2 (en associer un autre, ou le dissocier). Trajets et diagnostics restent attachés au véhicule.
 */
@Composable
fun EditVehicleScreen(vm: AppViewModel, id: Long) {
    val c = Cd.c
    val acc = Cd.acc
    val vehicles by vm.repo.vehicles.collectAsState()
    val v = vehicles.find { it.id == id }
    val scroll = rememberScrollState()
    if (v == null) {
        // Véhicule supprimé entre-temps : rien à modifier.
        Column(Modifier.fillMaxSize()) { BackBar("Modifier le véhicule", false, { vm.back() }) }
        return
    }
    var nickname by rememberSaveable(id) { mutableStateOf(v.nickname) }
    var plate by rememberSaveable(id) { mutableStateOf(v.plate) }
    var year by rememberSaveable(id) { mutableStateOf(v.year) }
    var confirmUnpair by rememberSaveable { mutableStateOf(false) }
    val changed = nickname.trim() != v.nickname || plate.trim() != v.plate || year.trim() != v.year
    val t = v.template

    Column(Modifier.fillMaxSize()) {
        BackBar("Modifier le véhicule", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            T(v.nickname, 14, c.onv, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp))

            SectionLabel("Profil")
            Column(
                Modifier.fillMaxWidth().rise(0).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Field("Nom du profil", nickname, { nickname = it })
                Field("Immatriculation", plate, { plate = it.uppercase() })
                Field("Année", year, { year = it.filter(Char::isDigit).take(4) }, number = true)
                FilledBtn("Enregistrer", {
                    vm.repo.updateVehicle(id, nickname.trim(), plate.trim(), year.trim())
                    vm.showSnack("Véhicule enregistré")
                }, enabled = changed && nickname.isNotBlank())
            }

            SectionLabel("Modèle")
            Row(
                Modifier.fillMaxWidth().rise(1).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LeadingIcon(Icons.Rounded.DirectionsCar, acc.container, acc.onContainer)
                Column(Modifier.weight(1f)) {
                    T(t.fullName, 16, weight = 500)
                    if (t.specs.isNotBlank()) T(t.specs, 13, c.onv)
                }
            }
            Box(Modifier.padding(top = 8.dp)) {
                TonalBtn("Changer de modèle", { vm.go(Screen.ChangeModel(id)) }, Icons.Rounded.SwapHoriz)
            }
            T(
                "Les trajets, diagnostics et le boîtier du véhicule sont gardés. Les données propres au modèle suivent le nouveau choix.",
                12, c.onv, lineHeight = 16, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )

            SectionLabel("Boîtier OBD2")
            val a = v.adapter
            Row(
                Modifier.fillMaxWidth().rise(2).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LeadingIcon(Icons.Rounded.SettingsInputHdmi, acc.container, acc.onContainer)
                Column(Modifier.weight(1f)) {
                    T(a?.name?.ifBlank { null } ?: if (a != null) "Boîtier OBD2" else "Aucun boîtier associé", 16, weight = 500)
                    if (a != null) T("${a.transport.label} · ${a.address}", 13, c.onv, mono = true)
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TonalBtn(if (a == null) "Associer un boîtier" else "Changer de boîtier", { vm.pairFor(id) }, Icons.Rounded.SettingsInputHdmi)
                if (a != null) TextBtn("Dissocier", { confirmUnpair = true }, Icons.Rounded.LinkOff, c.e)
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Dissocier le boîtier ?", 22, lineHeight = 28) },
            text = { T("${v.nickname} n'aura plus de boîtier OBD2 : plus de connexion ni d'enregistrement automatique des trajets tant qu'un autre n'est pas associé.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ confirmUnpair = false; vm.unpair(id) }) { T("Dissocier", 14, c.e, 600) } },
            dismissButton = { TextButton({ confirmUnpair = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}
