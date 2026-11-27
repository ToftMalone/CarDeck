package com.cardeck.app.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.LocalContentColor
import com.cardeck.app.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cardeck.app.AppViewModel
import com.cardeck.app.LocalOrigins
import com.cardeck.app.Perms
import com.cardeck.app.Screen
import com.cardeck.app.obd.ObdState
import com.cardeck.app.service.TripService
import com.cardeck.app.ui.AccentRoles
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Motion
import com.cardeck.app.ui.PulseRing
import com.cardeck.app.ui.Section
import com.cardeck.app.ui.SectionCard
import com.cardeck.app.ui.ShapeBadge
import com.cardeck.app.ui.StatusChip
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import com.cardeck.app.ui.accentRoles
import com.cardeck.app.ui.cloverShape
import com.cardeck.app.ui.cookieShape
import com.cardeck.app.ui.gs
import com.cardeck.app.ui.rememberSpin
import com.cardeck.app.ui.rf
import com.cardeck.app.ui.rise
import com.cardeck.app.ui.spinning
import kotlin.math.roundToInt

private data class Tile(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val section: Section,
    /** Écran ouvert, pour que la page « grandisse » depuis la carte. */
    val screen: Screen,
    val onClick: () -> Unit,
    val badge: String = "",
)

/**
 * Accueil, à la manière d'All Radio Tools : grand titre et message du jour, puis une grille de cartes colorées (une couleur
 * par rubrique) avec leur badge animé. Toucher une carte l'agrandit jusqu'à sa page.
 */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm)
    val scan = rememberLastScan(vm, v?.id)
    val (connLabel, connKind) = connStatus(vm, v)
    val codes = scan?.codes?.size ?: 0
    val tiles = listOf(
        Tile("dash", "Tableau de bord", "Vitesse, régime, températures et turbo en direct", Icons.Rounded.Dashboard, Section.Dash, Screen.Dash, { vm.go(Screen.Dash) }),
        Tile("obd", "Données OBD2", "Toutes les valeurs que le moteur sait donner", Icons.Rounded.Memory, Section.Obd, Screen.ObdData, { vm.go(Screen.ObdData) }),
        Tile("diag", "Diagnostic", "Codes défaut, effacement et contrôle technique", Icons.Rounded.Troubleshoot, Section.Diag, Screen.Diag, { vm.go(Screen.Diag) }, if (codes > 0) codes.toString() else ""),
        Tile("trips", "Trajets", "Tracés, consommation et coût de chaque trajet", Icons.Rounded.Route, Section.Trips, Screen.Trips, { vm.go(Screen.Trips) }),
        Tile("perf", "Performances", "0 à 100 km/h, reprise de 80 à 120 et 400 m chronométrés", Icons.Rounded.Timer, Section.Perf, Screen.Perf, { vm.go(Screen.Perf) }),
        Tile("contribution", "Contribution", "Explorez votre voiture et aidez à l'intégrer dans CarDeck", Icons.Rounded.VolunteerActivism, Section.Explorer, Screen.Explorer, { vm.go(Screen.Explorer) }),
    )
    Column(Modifier.fillMaxSize().background(c.sf)) {
        // Défilement gardé par le ViewModel : au retour d'une page, chaque carte est là où la page va rentrer.
        Column(Modifier.weight(1f).verticalScroll(vm.homeScroll)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("CarDeck", style = gs(32, 40, 500, -0.3f), color = c.on, maxLines = 1)
                    Text(vm.greeting, Modifier.padding(top = 4.dp), style = rf(14, 20), color = c.onv)
                }
                IconBtn(Icons.Rounded.Settings, { vm.go(Screen.Settings) }, c.onv, c.sf3)
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (v != null) {
                    SectionCard(Modifier.rise(0), shape = RoundedCornerShape(28.dp), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShapeBadge(Icons.Rounded.DirectionsCar, cookieShape(), 48.dp, c.p, c.op, 24.dp, spinMs = 30_000)
                            Column(Modifier.weight(1f)) {
                                Text(v.nickname, style = rf(16, 22, 600), color = c.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(v.template.fullName, v.plate).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = rf(13, 18), color = c.onv, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            StatusChip(
                                connLabel,
                                when (connKind) { ConnKind.Ok -> c.g; ConnKind.Busy -> c.w; ConnKind.Off -> c.ol },
                                blink = connKind != ConnKind.Off,
                            ) { if (v.adapter == null) vm.pairActive() else if (connKind == ConnKind.Off) vm.connectNow() }
                        }
                    }
                }
                TripPermissionCard()
                TileGrid(tiles)
            }
        }
        ConnectionStrip(vm)
    }
}

/** Invite à accorder localisation (« toujours ») + notifications pour l'enregistrement automatique des trajets. */
@Composable
private fun TripPermissionCard() {
    val c = Cd.c
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val ok = remember(tick) { Perms.tripsOk(ctx) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; TripService.start(ctx) }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (Perms.locationOk(ctx) && !Perms.backgroundLocationOk(ctx) && Build.VERSION.SDK_INT >= 29) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        TripService.start(ctx)
    }
    if (ok) return
    Row(
        Modifier.fillMaxWidth().rise(1).clip(RoundedCornerShape(24.dp)).background(c.tc).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ico(Icons.Rounded.LocationOn, 22, c.otc)
        Column(Modifier.weight(1f)) {
            Text("Enregistrement automatique des trajets", style = rf(15, 20, 600), color = c.otc)
            Text(
                "Autorisez la localisation « Toujours » et les notifications pour que CarDeck enregistre vos trajets dès que le moteur démarre.",
                Modifier.padding(top = 2.dp), style = rf(13, 18), color = c.otc,
            )
            Box(Modifier.padding(top = 4.dp)) {
                TextBtn("Autoriser", {
                    if (!Perms.locationOk(ctx) || !Perms.notificationsOk(ctx)) {
                        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                        fgLauncher.launch(list.toTypedArray())
                    } else if (Build.VERSION.SDK_INT >= 29) {
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }, color = c.otc)
            }
        }
    }
}

/** Grille de cartes sur 2 colonnes, dans un ordre fixe. */
@Composable
private fun TileGrid(tiles: List<Tile>) {
    val density = LocalDensity.current
    val origins = LocalOrigins.current
    val gap = 12.dp
    // Hauteur des cartes : 176 dp, plus grande si le texte est agrandi dans les réglages d'Android (nom et description lisibles).
    val cellH = 176.dp + 72.dp * (density.fontScale - 1f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.chunked(2).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { c, t ->
                    key(t.id) {
                        TileCard(
                            t, t.onClick,
                            Modifier.weight(1f).height(cellH).rise(r * 2 + c + 2)
                                // Position de la carte : la page s'ouvre en grandissant depuis elle, et y rentre au retour.
                                .onGloballyPositioned { origins[t.screen] = it.boundsInRoot() },
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Carte d'une rubrique : conteneur de son accent, badge animé, nom et description ; légère pression au toucher. */
@Composable
private fun TileCard(t: Tile, onClick: () -> Unit, modifier: Modifier) {
    val c = Cd.c
    val roles = accentRoles(t.section, c.dark)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.standard(), label = "cardScale")
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(roles.container)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
    ) {
        CompositionLocalProvider(LocalContentColor provides roles.onContainer) {
            Column(Modifier.fillMaxSize().padding(16.dp)) { TileContent(t, roles) }
        }
        if (t.badge.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(14.dp).size(28.dp).clip(CircleShape).background(c.e),
                contentAlignment = Alignment.Center,
            ) { Text(t.badge, style = rf(13, 16, 700, tnum = true), color = c.oe) }
        }
    }
}

@Composable
private fun ColumnScope.TileContent(t: Tile, roles: AccentRoles) {
    TileIcon(t, roles)
    Spacer(Modifier.weight(1f))
    Text(t.label, style = rf(18, 24, 600), color = roles.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
        t.description, Modifier.padding(top = 4.dp).graphicsLayer { alpha = 0.85f },
        style = rf(13, 18), color = roles.onContainer, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

/** Badge animé de chaque rubrique (formes qui tournent lentement, ondes), comme les cartes d'All Radio Tools. */
@Composable
private fun TileIcon(t: Tile, roles: AccentRoles) {
    when (t.section) {
        Section.Dash -> ShapeBadge(t.icon, cookieShape(), 56.dp, roles.accent, roles.onAccent, 28.dp, spinMs = 30_000)
        Section.Obd -> Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            PulseRing(roles.accent, 40.dp, periodMs = 1800)
            Box(Modifier.size(48.dp).spinning(rememberSpin(24_000, reverse = true)).clip(RoundedCornerShape(16.dp)).background(roles.accent))
            Ico(t.icon, 26, roles.onAccent)
        }
        Section.Diag -> ShapeBadge(t.icon, cloverShape(), 56.dp, roles.accent, roles.onAccent, 28.dp, spinMs = 40_000, reverse = true)
        Section.Trips -> {
            val spin = rememberSpin(8_000)
            val dash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(roles.accent))
                Canvas(Modifier.size(56.dp).spinning(spin)) {
                    drawCircle(roles.accent, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = dash))
                }
                Ico(t.icon, 26, roles.onAccent)
            }
        }
        Section.Adapter -> Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp).spinning(rememberSpin(30_000)).clip(RoundedCornerShape(18.dp)).background(roles.accent))
            PulseRing(roles.accent, 40.dp, periodMs = 2400)
            Ico(t.icon, 28, roles.onAccent)
        }
        Section.Perf -> {
            // Chrono : cadran en pointillés qui tourne vite autour du badge.
            val spin = rememberSpin(3_000)
            val dash = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 10f)) }
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                ShapeBadge(t.icon, cookieShape(), 46.dp, roles.accent, roles.onAccent, 26.dp, spinMs = 24_000, reverse = true)
                Canvas(Modifier.size(56.dp).spinning(spin)) {
                    drawCircle(roles.accent, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = dash))
                }
            }
        }
        Section.Explorer -> Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            PulseRing(roles.accent, 44.dp, periodMs = 1600)
            Box(Modifier.size(46.dp).spinning(rememberSpin(20_000)).clip(cloverShape()).background(roles.accent))
            Ico(t.icon, 26, roles.onAccent)
        }
        Section.Settings -> ShapeBadge(t.icon, cookieShape(), 56.dp, roles.accent, roles.onAccent, 28.dp, spinMs = 36_000, reverse = true)
    }
}

/** Bandeau « Appli — OBD2 — ECU » reflétant l'état réel de la liaison. */
@Composable
private fun ConnectionStrip(vm: AppViewModel) {
    val c = Cd.c
    val st by vm.obd.state.collectAsState()
    val ecu by vm.obd.ecuOnline.collectAsState()
    val obdOk = st is ObdState.Connected
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).clip(RoundedCornerShape(28.dp)).background(c.sf1)
            .height(72.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StripNode(Icons.Rounded.Smartphone, "Appli", true)
        StripLink(Modifier.weight(1f), obdOk)
        StripNode(Icons.Rounded.SettingsInputHdmi, "OBD2", obdOk)
        StripLink(Modifier.weight(1f), obdOk && ecu)
        StripNode(Icons.Rounded.Memory, "ECU", obdOk && ecu)
    }
}

@Composable
private fun StripNode(icon: ImageVector, label: String, on: Boolean) {
    val c = Cd.c
    // Allumage du nœud en fondu (et petit rebond) quand la liaison s'établit.
    val bg by androidx.compose.animation.animateColorAsState(if (on) c.gc else c.sf3, tween(350, easing = Motion.Emphasized), label = "node")
    val fg by androidx.compose.animation.animateColorAsState(if (on) c.ogc else c.onv, tween(350, easing = Motion.Emphasized), label = "nodeFg")
    val scale by animateFloatAsState(if (on) 1f else 0.92f, Motion.bouncy(), label = "nodeScale")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (on) PulseRing(c.g, 28.dp, periodMs = 2400)
            Box(Modifier.size(36.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Ico(icon, 20, fg) }
        }
        T(label, 11, c.onv, 600)
    }
}

@Composable
private fun StripLink(modifier: Modifier, on: Boolean) {
    val c = Cd.c
    // La liaison se remplit de gauche à droite quand elle s'établit, et se vide dans l'autre sens quand elle tombe.
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(500, easing = Motion.Emphasized), label = "link")
    Canvas(modifier.height(20.dp)) {
        val y = size.height / 2 - 9.dp.toPx()
        val w = 2.dp.toPx()
        drawLine(c.olv, Offset(0f, y), Offset(size.width, y), strokeWidth = w)
        if (fill > 0.001f) drawLine(c.g.copy(alpha = .9f), Offset(0f, y), Offset(size.width * fill, y), strokeWidth = w)
    }
}
