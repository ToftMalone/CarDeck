package com.cardeck.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.cardeck.app.screens.AboutScreen
import com.cardeck.app.screens.AddVehicleScreen
import com.cardeck.app.screens.ChangelogScreen
import com.cardeck.app.screens.CodingScreen
import com.cardeck.app.screens.DashboardScreen
import com.cardeck.app.screens.DiagScreen
import com.cardeck.app.screens.DtcDetailScreen
import com.cardeck.app.screens.HomeScreen
import com.cardeck.app.screens.ObdDataScreen
import com.cardeck.app.screens.PairScreen
import com.cardeck.app.screens.SettingsScreen
import com.cardeck.app.screens.TripDetailScreen
import com.cardeck.app.screens.TripsScreen
import com.cardeck.app.screens.UpdateDialog
import com.cardeck.app.screens.WelcomeScreen
import com.cardeck.app.service.TripService
import com.cardeck.app.ui.CarDeckTheme
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Motion
import com.cardeck.app.ui.LocalEntrance
import com.cardeck.app.ui.ProvideAccent
import com.cardeck.app.ui.Section
import com.cardeck.app.ui.accentRoles
import com.cardeck.app.ui.isDark
import com.cardeck.app.ui.pop
import com.cardeck.app.ui.rf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel get() = cardeck.vm

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
            CarDeckTheme(vm.palette, vm.themeMode) { CarDeckRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Suivi automatique des trajets du véhicule actif.
        if (vm.onboarded) TripService.start(this)
    }
}

/** Rubrique (couleur d'accent) d'un écran ; null = couleur principale. */
fun sectionOf(s: Screen): Section? = when (s) {
    Screen.Dash -> Section.Dash
    Screen.ObdData -> Section.Obd
    Screen.Explorer -> Section.Explorer
    Screen.Diag, is Screen.Dtc, Screen.Coding -> Section.Diag
    Screen.Trips, is Screen.Trip -> Section.Trips
    Screen.Settings, Screen.Changelog, Screen.About -> Section.Settings
    Screen.Pair, Screen.AddVehicle -> Section.Adapter
    Screen.Home, Screen.Welcome -> null
}

/** Cartes de l'accueil d'où une page « grandit » (position à l'écran), par écran. */
val LocalOrigins = staticCompositionLocalOf { HashMap<Screen, Rect>() }

/** Place à garder sous le contenu des rubriques pour le dock flottant. */
val DockScreens = listOf(Screen.Dash, Screen.ObdData, Screen.Diag, Screen.Trips)

private enum class Move { None, Forward, Back, Through }

@Composable
private fun CarDeckRoot(vm: AppViewModel) {
    val c = Cd.c
    val origins = remember { HashMap<Screen, Rect>() }
    CompositionLocalProvider(LocalOrigins provides origins) {
        Box(Modifier.fillMaxSize().background(c.sf).windowInsetsPadding(WindowInsets.systemBars)) {
            ScreenHost(vm, origins)
            DockLayer(vm, Modifier.align(Alignment.BottomCenter))
            Snack(vm.snack, vm.screen in DockScreens, Modifier.align(Alignment.BottomCenter))
            UpdateDialog(vm)
        }
    }
}

/**
 * Hôte des écrans, avec les transitions d'All Radio Tools :
 *  - une carte de l'accueil s'agrandit jusqu'à devenir sa page (et y rentre au retour) ;
 *  - les autres pages glissent depuis la droite en fondu ;
 *  - entre deux rubriques du dock, fondu avec une légère montée ;
 *  - retour prédictif : la page rétrécit sous le doigt et laisse voir la précédente.
 */
@Composable
private fun ScreenHost(vm: AppViewModel, origins: HashMap<Screen, Rect>) {
    val c = Cd.c
    val haptics = Cd.haptics
    val density = LocalDensity.current
    var moving by remember { mutableStateOf(vm.screen) }
    var under by remember { mutableStateOf<Screen?>(null) }
    var move by remember { mutableStateOf(Move.None) }
    var origin by remember { mutableStateOf<Rect?>(null) }
    var depth by remember { mutableIntStateOf(vm.stack.size) }
    val p = remember { Animatable(1f) }
    val back = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var hostBounds by remember { mutableStateOf(Rect.Zero) }

    // Une transition à la fois : un nouvel écran demandé pendant une animation la termine d'abord (collectLatest attend
    // la fin de la précédente), sans jamais laisser d'écran fantôme ni d'état à moitié.
    LaunchedEffect(Unit) {
        snapshotFlow { vm.screen }.collectLatest { target ->
            if (target == moving && under == null) { depth = vm.stack.size; return@collectLatest }
            val d = vm.stack.size
            val from = moving
            try {
                when {
                    d > depth -> {
                        // Progression remise à zéro avant d'afficher la nouvelle page : jamais une image en pleine page.
                        p.snapTo(0f)
                        move = Move.Forward
                        origin = if (from == Screen.Home) origins[target]?.translate(-hostBounds.left, -hostBounds.top) else null
                        under = from
                        moving = target
                        haptics.confirm()
                        p.animateTo(1f, tween(if (origin != null) 520 else 400, easing = Motion.Emphasized))
                    }
                    d < depth -> {
                        move = Move.Back
                        origin = if (target == Screen.Home) origins[from]?.translate(-hostBounds.left, -hostBounds.top) else null
                        under = target
                        coroutineScope {
                            launch { back.animateTo(0f, tween(380, easing = Motion.EmphasizedAccelerate)) }
                            p.animateTo(0f, tween(380, easing = Motion.EmphasizedAccelerate))
                        }
                    }
                    else -> {
                        p.snapTo(0f)
                        move = Move.Through
                        origin = null
                        under = from
                        moving = target
                        p.animateTo(1f, tween(400, 60, Motion.Emphasized))
                    }
                }
            } finally {
                // Fin normale ou interrompue : l'écran demandé seul, au repos.
                withContext(NonCancellable) {
                    moving = vm.screen
                    under = null
                    move = Move.None
                    depth = vm.stack.size
                    p.snapTo(1f)
                    back.snapTo(0f)
                }
            }
        }
    }

    // Retour prédictif : la page suit le geste ; relâchée, elle part (retour), sinon elle revient.
    // Toujours actif dès qu'il y a un écran derrière : un retour pendant une animation ne doit jamais fermer l'appli.
    PredictiveBackHandler(enabled = vm.stack.size > 1) { events ->
        if (move != Move.None) {
            // Transition en cours : retour simple, sans suivre le geste.
            events.collect { }
            vm.back()
            return@PredictiveBackHandler
        }
        var buzzed = false
        try {
            under = vm.stack.getOrNull(vm.stack.size - 2)
            events.collect { e ->
                back.snapTo(e.progress)
                if (!buzzed && e.progress > 0.3f) { buzzed = true; haptics.threshold() }
            }
            vm.back()
        } catch (e: CancellationException) {
            scope.launch { back.animateTo(0f, Motion.standard()); if (move == Move.None) under = null }
            throw e
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInRoot() }) {
        val layers = listOfNotNull(under, moving)
        for (s in layers) key(s) {
            val isMoving = s == moving
            val pv = p.value
            val b = if (isMoving) back.value else 0f
            val o = origin
            val section = sectionOf(s)
            val startBg = section?.let { accentRoles(it, c.dark).container } ?: c.pc
            val radiusPx = with(density) { 28.dp.toPx() }
            val modifier = when {
                !isMoving -> Modifier.graphicsLayer {
                    // Fondu enchaîné entre deux rubriques : l'ancienne s'efface vite.
                    if (move == Move.Through) alpha = (1f - pv * 4f).coerceIn(0f, 1f)
                }
                o != null && move != Move.Through -> Modifier.graphicsLayer {
                    val full = Rect(0f, 0f, size.width, size.height)
                    val sc = 1f - 0.1f * b
                    scaleX = sc; scaleY = sc
                    clip = true
                    shape = RoundRectShape(lerpRect(o, full, pv), maxOf(radiusPx * (1f - pv), radiusPx * b))
                }
                move == Move.Through -> Modifier.graphicsLayer {
                    alpha = pv
                    translationY = (1f - pv) * 24.dp.toPx()
                }
                else -> Modifier.graphicsLayer {
                    val sc = 1f - 0.1f * b
                    scaleX = sc; scaleY = sc
                    translationX = (1f - pv) * size.width * 0.25f
                    alpha = pv
                    if (b > 0f) { clip = true; shape = RoundedCornerShape(28.dp * b) }
                }
            }
            val bg = if (isMoving && o != null && move != Move.Through) lerp(startBg, c.sf, pv) else c.sf
            val contentAlpha = if (isMoving && o != null && move != Move.Through) ((pv - 0.3f) / 0.7f).coerceIn(0f, 1f) else 1f
            ProvideAccent(section) {
                // Une page qui réapparaît dessous (retour, geste de retour) ne rejoue pas ses animations d'entrée.
                CompositionLocalProvider(LocalEntrance provides isMoving) {
                    Box(Modifier.fillMaxSize().then(modifier).background(bg)) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) { ScreenContent(vm, s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(vm: AppViewModel, screen: Screen) {
    when (screen) {
        Screen.Welcome -> WelcomeScreen(vm)
        Screen.Pair -> PairScreen(vm)
        Screen.AddVehicle -> AddVehicleScreen(vm)
        Screen.Home -> HomeScreen(vm)
        Screen.Dash -> DashboardScreen(vm)
        Screen.Diag -> DiagScreen(vm)
        is Screen.Dtc -> DtcDetailScreen(vm, screen.code)
        Screen.Trips -> TripsScreen(vm)
        is Screen.Trip -> TripDetailScreen(vm, screen.id)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Coding -> CodingScreen(vm)
        Screen.ObdData -> ObdDataScreen(vm)
        Screen.Changelog -> ChangelogScreen(vm)
        Screen.About -> AboutScreen(vm)
        Screen.Explorer -> com.cardeck.app.screens.ExplorerScreen(vm)
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/** Découpe selon un rectangle arrondi placé dans le calque. */
private class RoundRectShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

// ---------------------------------------------------------------- Dock flottant

private data class DockTab(val screen: Screen, val icon: ImageVector, val label: String)

private val Tabs = listOf(
    DockTab(Screen.Dash, Icons.Rounded.Dashboard, "Tableau"),
    DockTab(Screen.ObdData, Icons.Rounded.Memory, "Données"),
    DockTab(Screen.Diag, Icons.Rounded.Troubleshoot, "Diagnostic"),
    DockTab(Screen.Trips, Icons.Rounded.Route, "Trajets"),
)

/**
 * Dock flottant des rubriques (barre d'outils d'All Radio Tools) : bouton d'accueil puis les rubriques, l'active
 * s'élargit avec son nom. Il monte avec un ressort quand on ouvre une rubrique et se tasse à chaque changement.
 */
@Composable
private fun DockLayer(vm: AppViewModel, modifier: Modifier) {
    val screen = vm.screen
    val visible = screen in DockScreens && !vm.dockHidden
    var last by remember { mutableStateOf(if (visible) screen else Screen.Dash) }
    if (visible) last = screen
    val density = LocalDensity.current
    val hidden = with(density) { 180.dp.toPx() }
    val offset = remember { Animatable(if (visible) 0f else hidden) }
    LaunchedEffect(visible) {
        if (visible) offset.animateTo(0f, spring(0.6f, 500f))
        else offset.animateTo(hidden, tween(350, easing = Motion.EmphasizedAccelerate))
    }
    if (!visible && offset.value >= hidden) return
    ProvideAccent(sectionOf(last)) {
        val acc = Cd.acc
        val haptics = Cd.haptics
        val squash = remember { Animatable(0f) }
        LaunchedEffect(last) {
            squash.snapTo(1f)
            squash.animateTo(0f, spring(0.5f, 500f))
        }
        Row(
            modifier
                .padding(bottom = 20.dp)
                .graphicsLayer {
                    translationY = offset.value
                    scaleX = 1f + 0.06f * squash.value
                    scaleY = 1f - 0.1f * squash.value
                }
                .shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = Cd.c.shadow, spotColor = Cd.c.shadow)
                .clip(RoundedCornerShape(32.dp))
                .background(acc.container)
                .height(64.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable {
                    haptics.tick()
                    while (vm.screen != Screen.Home && vm.stack.size > 1) vm.stack.removeAt(vm.stack.lastIndex)
                },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.pop()) { Ico(Icons.Rounded.GridView, 24, acc.onContainer) } }
            Tabs.forEach { t ->
                DockButton(t, t.screen == last) {
                    if (t.screen != vm.screen) { haptics.segment(); vm.switchTo(t.screen) }
                }
            }
        }
    }
}

@Composable
private fun DockButton(t: DockTab, active: Boolean, onClick: () -> Unit) {
    val acc = Cd.acc
    val width by animateDpAsState(if (active) 128.dp else 48.dp, Motion.standard(), label = "tabW")
    val bg by animateColorAsState(if (active) acc.accent else Color.Transparent, tween(250, easing = Motion.Emphasized), label = "tabBg")
    val fg by animateColorAsState(if (active) acc.onAccent else acc.onContainer, tween(250, easing = Motion.Emphasized), label = "tabFg")
    val labelAlpha by animateFloatAsState(if (active) 1f else 0f, tween(200, easing = Motion.Emphasized), label = "tabLabel")
    Row(
        Modifier.width(width).height(48.dp).clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(t.icon, 24, fg)
        if (labelAlpha > 0.01f) {
            Text(t.label, Modifier.padding(start = 6.dp).graphicsLayer { alpha = labelAlpha }, style = rf(14, 20, 600), color = fg, maxLines = 1, softWrap = false)
        }
    }
}

// ---------------------------------------------------------------- Message (snackbar)

@Composable
private fun Snack(message: String?, dock: Boolean, modifier: Modifier) {
    val c = Cd.c
    val y = remember { Animatable(1f) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(message) {
        if (message != null) {
            text = message
            y.animateTo(0f, Motion.standard())
        } else {
            y.animateTo(1f, tween(200, easing = Motion.Emphasized))
        }
    }
    if (y.value >= 1f && message == null) return
    Row(
        modifier
            .padding(start = 16.dp, end = 16.dp, bottom = if (dock) 100.dp else 16.dp)
            .fillMaxWidth()
            .graphicsLayer {
                translationY = y.value * 20.dp.toPx()
                alpha = 1f - y.value
            }
            .clip(RoundedCornerShape(8.dp))
            .background(c.isf)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = rf(14, 20), color = c.ion)
    }
}

/** Hauteur à laisser sous le contenu d'une rubrique pour ne pas passer sous le dock. */
@Composable
fun DockSpace() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(104.dp))
}
