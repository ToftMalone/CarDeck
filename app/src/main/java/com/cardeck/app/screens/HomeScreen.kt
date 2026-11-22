package com.cardeck.app.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
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
 * par rubrique) avec leur badge animé. Toucher une carte l'agrandit jusqu'à sa page ; appui long puis glisser pour les ranger.
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
        Tile("pair", "Boîtier OBD2", "Associer, changer ou reconnecter le boîtier", Icons.Rounded.SettingsInputHdmi, Section.Adapter, Screen.Pair, { vm.pairActive() }),
        Tile("explorer", "Explorateur", "Tout ce que les calculateurs acceptent de dire", Icons.Rounded.TravelExplore, Section.Explorer, Screen.Explorer, { vm.go(Screen.Explorer) }),
    )
    Column(Modifier.fillMaxSize().background(c.sf)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                ReorderableTiles(tiles, vm.homeOrder) { vm.updateHomeOrder(it) }
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

/** Grille de cartes sur 2 colonnes, réorganisable : appui long puis glisser pour déplacer une carte. */
@Composable
private fun ReorderableTiles(tiles: List<Tile>, order: List<String>, onReorder: (List<String>) -> Unit) {
    val haptics = Cd.haptics
    val density = LocalDensity.current
    val origins = LocalOrigins.current
    val byId = tiles.associateBy { it.id }
    fun normalize(o: List<String>) = o.filter { it in byId } + tiles.map { it.id }.filter { it !in o }
    // Un seul état pour toute la vie de l'écran : le geste d'appui long garde toujours la liste affichée.
    var current by remember { mutableStateOf(normalize(order)) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val save by rememberUpdatedState(onReorder)
    var dragEndedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(order) { if (dragId == null) current = normalize(order) }
    val cols = 2
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 12.dp
        val cellH = 176.dp
        val cellW = (maxWidth - gap * (cols - 1)) / cols
        val rows = (current.size + cols - 1) / cols
        val gapPx = with(density) { gap.toPx() }
        val cellWPx = with(density) { cellW.toPx() }
        val cellHPx = with(density) { cellH.toPx() }
        fun pos(i: Int) = Offset((i % cols) * (cellWPx + gapPx), (i / cols) * (cellHPx + gapPx))
        Box(Modifier.fillMaxWidth().height(cellH * rows + gap * (rows - 1))) {
            current.forEachIndexed { i, id ->
                val t = byId[id] ?: return@forEachIndexed
                key(id) {
                    val dragging = dragId == id
                    val anim by animateOffsetAsState(pos(i), Motion.standard(), label = "tile")
                    val p = if (dragging) pos(i) + dragOffset else anim
                    // Pas d'ouverture de la carte à la fin d'un appui long / déplacement.
                    val open = { if (dragId == null && System.currentTimeMillis() - dragEndedAt > 400) t.onClick() }
                    TileCard(
                        t, dragging, open,
                        Modifier
                            .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                            .zIndex(if (dragging) 1f else 0f)
                            .width(cellW)
                            .height(cellH)
                            .rise(i + 2)
                            .onGloballyPositioned { origins[t.screen] = it.boundsInRoot() }
                            .pointerInput(id, cellWPx, cellHPx) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.longPress()
                                        dragId = id
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val from = current.indexOf(id)
                                        val center = pos(from) + dragOffset + Offset(cellWPx / 2, cellHPx / 2)
                                        val col = (center.x / (cellWPx + gapPx)).toInt().coerceIn(0, cols - 1)
                                        val rowCount = (current.size + cols - 1) / cols
                                        val row = (center.y / (cellHPx + gapPx)).toInt().coerceIn(0, rowCount - 1)
                                        val to = (row * cols + col).coerceIn(0, current.size - 1)
                                        if (to != from) {
                                            haptics.segment()
                                            current = current.toMutableList().apply { add(to, removeAt(from)) }
                                            dragOffset -= pos(to) - pos(from)
                                        }
                                    },
                                    onDragEnd = { dragEndedAt = System.currentTimeMillis(); dragId = null; dragOffset = Offset.Zero; save(current) },
                                    onDragCancel = { dragEndedAt = System.currentTimeMillis(); dragId = null; dragOffset = Offset.Zero; save(current) },
                                )
                            },
                    )
                }
            }
        }
    }
}

/** Carte d'une rubrique : conteneur de son accent, badge animé, nom et description ; légère pression au toucher. */
@Composable
private fun TileCard(t: Tile, dragging: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = Cd.c
    val roles = accentRoles(t.section, c.dark)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (dragging) 1.05f else if (pressed) 0.97f else 1f, Motion.standard(), label = "cardScale")
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (dragging) 10.dp else 0.dp, shape, ambientColor = c.shadow, spotColor = c.shadow)
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
        Section.Explorer -> Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            PulseRing(roles.accent, 44.dp, periodMs = 1600)
            Box(Modifier.size(46.dp).spinning(rememberSpin(20_000)).clip(cloverShape()).background(roles.accent))
            Ico(t.icon, 26, roles.onAccent)
        }
        Section.Settings -> ShapeBadge(t.icon, cookieShape(), 56.dp, roles.accent, roles.onAccent, 28.dp)
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (on) PulseRing(c.g, 28.dp, periodMs = 2400)
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) c.gc else c.sf3), contentAlignment = Alignment.Center) { Ico(icon, 20, if (on) c.ogc else c.onv) }
        }
        T(label, 11, c.onv, 600)
    }
}

@Composable
private fun StripLink(modifier: Modifier, on: Boolean) {
    val c = Cd.c
    Canvas(modifier.height(20.dp)) {
        val y = size.height / 2 - 9.dp.toPx()
        drawLine(if (on) c.g.copy(alpha = .9f) else c.olv, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
    }
}
