package com.cardeck.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.content.Context
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.cardeck.app.screens.ToolsScreen
import com.cardeck.app.ui.Corners
import com.cardeck.app.ui.Dock
import com.cardeck.app.ui.DockTab
import com.cardeck.app.ui.Domain
import com.cardeck.app.ui.Motion
import com.cardeck.app.ui.ProvideAccent
import com.cardeck.app.ui.Sym
import com.cardeck.app.ui.isDark
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cardeck.app.screens.AboutScreen
import com.cardeck.app.screens.AddVehicleScreen
import com.cardeck.app.screens.ChangelogScreen
import com.cardeck.app.screens.UpdateDialog
import com.cardeck.app.screens.CodingScreen
import com.cardeck.app.screens.ObdDataScreen
import com.cardeck.app.screens.DashboardScreen
import com.cardeck.app.screens.DiagScreen
import com.cardeck.app.screens.DtcDetailScreen
import com.cardeck.app.screens.HomeScreen
import com.cardeck.app.screens.PairScreen
import com.cardeck.app.screens.SettingsScreen
import com.cardeck.app.screens.TripDetailScreen
import com.cardeck.app.screens.TripsScreen
import com.cardeck.app.screens.WelcomeScreen
import com.cardeck.app.ui.CarDeckTheme
import com.cardeck.app.service.TripService

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel get() = cardeck.vm

    // Langue choisie dans les réglages (sinon celle du système).
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Locales.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nouveau message d'accueil à chaque lancement (pas à la rotation de l'écran).
        if (savedInstanceState == null || vm.greeting.isEmpty()) vm.newGreeting()
        // Recherche d'une mise à jour sur GitHub à chaque lancement : proposée si une version plus récente existe.
        if (savedInstanceState == null) vm.checkUpdate(prompt = true)
        setContent {
            val dark = isDark(vm.themeMode)
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CarDeckTheme(vm.themeMode, vm.dynamicColor, vm.palette, vm.haptics) { CarDeckRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Suivi automatique des trajets du véhicule actif.
        if (vm.onboarded) TripService.start(this)
    }
}

@Composable
private fun CarDeckRoot(vm: AppViewModel) {
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    val screen = vm.screen
    val domain = if (vm.onboarded) screen.domain() else null
    // Le dernier domaine reste affiché pendant que le dock disparaît.
    var lastDomain by remember { mutableStateOf(domain ?: Domain.Drive) }
    if (domain != null) lastDomain = domain
    ProvideAccent(domain) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(300, delayMillis = 60, easing = Motion.Emphasized)) +
                            scaleIn(tween(400, easing = Motion.Emphasized), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(150, easing = Motion.EmphasizedAccelerate))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Welcome -> WelcomeScreen(vm)
                        Screen.Pair -> PairScreen(vm)
                        Screen.AddVehicle -> AddVehicleScreen(vm)
                        Screen.Home -> HomeScreen(vm)
                        Screen.Dash -> DashboardScreen(vm)
                        Screen.Diag -> DiagScreen(vm)
                        is Screen.Dtc -> DtcDetailScreen(vm, s.code)
                        Screen.Trips -> TripsScreen(vm)
                        is Screen.Trip -> TripDetailScreen(vm, s.id)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.Coding -> CodingScreen(vm)
                        Screen.ObdData -> ObdDataScreen(vm)
                        Screen.Changelog -> ChangelogScreen(vm)
                        Screen.About -> AboutScreen(vm)
                        is Screen.Tools -> ToolsScreen(vm, s.domain)
                    }
                }
                Snack(vm.snack, Modifier.align(Alignment.BottomCenter))
            }
            AnimatedVisibility(
                visible = domain != null,
                enter = slideInVertically(Motion.standard()) { it } + fadeIn(tween(250)),
                exit = slideOutVertically(tween(250, easing = Motion.EmphasizedAccelerate)) { it } + fadeOut(tween(200)),
            ) {
                DomainDock(vm, lastDomain, screen)
            }
        }
        UpdateDialog(vm)
    }
}

/** Dock du domaine : Accueil, Tableau de bord, Favori, Tous les outils. */
@Composable
private fun DomainDock(vm: AppViewModel, d: Domain, screen: Screen) {
    val tools = rememberTools(vm, d).flatMap { it.tools }
    val fav = tools.find { it.id == (vm.favorite(d) ?: d.defaultFavorite) } ?: tools.first()
    val dashName = stringResource(R.string.dock_dashboard)
    val tabs = listOf(
        DockTab(d.sym, dashName),
        DockTab(fav.sym, stringResource(fav.name)),
        DockTab(Sym.Apps, stringResource(R.string.dock_tools)),
    )
    val selected = when {
        screen == d.dashboard -> 0
        fav.screen != null && screen == fav.screen -> 1
        screen is Screen.Tools -> 2
        else -> null
    }
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Dock(
            homeLabel = stringResource(R.string.dock_home),
            tabs = tabs,
            selected = selected,
            onHome = { vm.home() },
            onTab = { i ->
                when (i) {
                    0 -> vm.openFromHome(d.dashboard)
                    1 -> vm.launch(fav)
                    else -> vm.openFromHome(Screen.Tools(d))
                }
            },
        )
    }
}

@Composable
private fun Snack(message: String?, modifier: Modifier) {
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null, modifier = modifier,
        enter = slideInVertically(Motion.bouncy()) { it * 2 } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it * 2 } + fadeOut(tween(200)),
    ) {
        Box(
            Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 48.dp).clip(Corners.small).background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { Text(last, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface) }
    }
}
