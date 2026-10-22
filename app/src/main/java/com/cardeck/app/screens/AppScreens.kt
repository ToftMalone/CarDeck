package com.cardeck.app.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.cardeck.app.AppViewModel
import com.cardeck.app.BuildConfig
import com.cardeck.app.Screen
import com.cardeck.app.data.Release
import com.cardeck.app.data.Updater
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.SectionLabel
import com.cardeck.app.ui.Spinner
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun dateFr(t: Long) = if (t == 0L) "" else SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(t))

/** Notes d'une version : Markdown GitHub simplifié (titres et puces) en texte lisible. */
internal fun notesText(md: String): String = md.lines().joinToString("\n") { l ->
    val t = l.trim()
    when {
        t.startsWith("#") -> t.trimStart('#').trim()
        t.startsWith("- ") || t.startsWith("* ") -> "• " + t.drop(2)
        else -> t
    }.replace("**", "").replace("`", "")
}.trim()

/** Proposition de mise à jour au lancement (« Plus tard » : reste disponible dans Paramètres). */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val u = vm.update ?: return
    if (!vm.updatePrompt) return
    val c = Cd.c
    AlertDialog(
        onDismissRequest = { vm.updatePrompt = false },
        containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv, iconContentColor = c.p,
        icon = { Ico(Icons.Rounded.SystemUpdate, 24, c.p) },
        title = { T("Mise à jour disponible", 22, lineHeight = 28, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                T("CarDeck ${u.version} est disponible (version installée : ${BuildConfig.VERSION_NAME}).", 14, c.onv, lineHeight = 20)
                if (u.notes.isNotBlank()) T(notesText(u.notes), 13, c.onv, lineHeight = 18, modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton({ vm.installUpdate() }) { T("Installer", 14, c.p, 600) } },
        dismissButton = { TextButton({ vm.updatePrompt = false }) { T("Plus tard", 14, c.p, 500) } },
    )
}

/** Section « Application » des paramètres : mise à jour, journal des nouveautés, à propos. */
@Composable
fun AppSection(vm: AppViewModel) {
    val c = Cd.c
    LaunchedEffect(Unit) { if (vm.releases == null) vm.checkUpdate() }
    SectionLabel("Application")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Column(Modifier.fillMaxWidth().background(c.sf1).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Ico(Icons.Rounded.SystemUpdate, 22, c.p)
                Column(Modifier.weight(1f)) {
                    T("Mise à jour", 16)
                    val u = vm.update
                    T(
                        when {
                            vm.updateProgress != null -> "Téléchargement de la version ${u?.version}…"
                            u != null -> "Version ${u.version} disponible (installée : ${BuildConfig.VERSION_NAME})"
                            vm.updateChecking -> "Recherche de mises à jour…"
                            vm.updateError != null -> "Version ${BuildConfig.VERSION_NAME} installée · vérification impossible"
                            vm.releases != null -> "Version ${BuildConfig.VERSION_NAME} installée · à jour"
                            else -> "Version ${BuildConfig.VERSION_NAME} installée"
                        },
                        13, c.onv, lineHeight = 18,
                    )
                }
                if (vm.updateChecking) Spinner()
            }
            vm.updateProgress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = c.p, trackColor = c.sf4) }
            when {
                vm.updateProgress != null -> {}
                vm.update != null -> TextBtn("Installer la version ${vm.update?.version}", { vm.installUpdate() })
                !vm.updateChecking -> TextBtn("Rechercher une mise à jour", { vm.checkUpdate() })
            }
        }
        NavRow(Icons.Rounded.NewReleases, "Journal des nouveautés", "Les versions publiées et leurs nouveautés") { vm.go(Screen.Changelog) }
        NavRow(Icons.Rounded.Info, "À propos", "CarDeck ${BuildConfig.VERSION_NAME}") { vm.go(Screen.About) }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().background(c.sf1).clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ico(icon, 22, c.p)
        Column(Modifier.weight(1f)) { T(title, 16); T(sub, 13, c.onv, maxLines = 1) }
        Ico(Icons.AutoMirrored.Rounded.ArrowForward, 20, c.onv)
    }
}

/** Journal des nouveautés : toutes les versions publiées sur GitHub. */
@Composable
fun ChangelogScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { if (vm.releases == null) vm.checkUpdate() }
    Column(Modifier.fillMaxSize()) {
        BackBar("Journal des nouveautés", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ScreenTitle("Journal des nouveautés", "Version installée : ${BuildConfig.VERSION_NAME}")
            val list = vm.releases
            when {
                vm.updateChecking && list == null -> Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Spinner(); T("Chargement…", 14, c.onv) }
                list == null -> {
                    T(vm.updateError?.let { "Impossible de joindre GitHub : $it" } ?: "Journal non chargé.", 14, c.onv, lineHeight = 20)
                    TextBtn("Réessayer", { vm.checkUpdate() })
                }
                list.isEmpty() -> T("Aucune version publiée pour l'instant.", 14, c.onv)
                else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { list.forEach { ReleaseCard(it) } }
            }
        }
    }
}

@Composable
private fun ReleaseCard(r: Release) {
    val c = Cd.c
    val installed = Updater.compare(r.version, BuildConfig.VERSION_NAME) == 0
    val newer = Updater.compare(r.version, BuildConfig.VERSION_NAME) > 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T(r.name, 16, weight = 500, modifier = Modifier.weight(1f))
            if (installed || newer) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (newer) c.p else c.sf3).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    T(if (newer) "Nouvelle" else "Installée", 11, if (newer) c.op else c.onv, 600)
                }
            }
        }
        T("Version ${r.version}" + dateFr(r.date).let { if (it.isEmpty()) "" else " · $it" }, 12, c.onv, modifier = Modifier.padding(top = 2.dp))
        T(notesText(r.notes).ifBlank { "Pas de notes pour cette version." }, 14, c.onv, lineHeight = 20, modifier = Modifier.padding(top = 10.dp))
    }
}

/** À propos de CarDeck. */
@Composable
fun AboutScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxSize()) {
        BackBar("À propos", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.cardeck.app.R.drawable.ic_logo), contentDescription = "CarDeck", modifier = Modifier.padding(top = 16.dp).size(72.dp))
            T("CarDeck", 28, weight = 600, modifier = Modifier.padding(top = 12.dp))
            T("Version ${BuildConfig.VERSION_NAME}", 14, c.onv)
            T(
                "Tableau de bord, diagnostic, trajets et données moteur de ta voiture, par la prise OBD2 et un boîtier ELM327 (Bluetooth, BLE ou Wi-Fi). " +
                    "Les données restent sur ton téléphone.",
                14, c.onv, align = TextAlign.Center, lineHeight = 20, modifier = Modifier.padding(top = 16.dp),
            )
            Column(Modifier.fillMaxWidth().padding(top = 24.dp).clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NavRow(Icons.Rounded.Info, "Code source et versions", "github.com/${Updater.REPO}") { runCatching { uri.openUri(Updater.PAGE) } }
                NavRow(Icons.Rounded.NewReleases, "Journal des nouveautés", "Les versions publiées") { vm.go(Screen.Changelog) }
            }
            T(
                "Cartes © contributeurs OpenStreetMap (osmdroid). Codes défaut OBD2 génériques SAE J2012. " +
                    "CarDeck lit les données du véhicule ; utilise-la à l'arrêt ou via Android Auto, jamais en tenant le téléphone en conduisant.",
                12, c.onv, align = TextAlign.Center, lineHeight = 16, modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
