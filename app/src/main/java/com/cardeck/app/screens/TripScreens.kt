package com.cardeck.app.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.data.EventType
import com.cardeck.app.data.Trip
import com.cardeck.app.data.TripEvent
import com.cardeck.app.data.TripPoint
import com.cardeck.app.data.dayLabel
import com.cardeck.app.data.durationLabel
import com.cardeck.app.data.fr
import com.cardeck.app.data.timeHm
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.CdColors
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.Skeleton
import com.cardeck.app.ui.T
import com.cardeck.app.ui.scoreColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.abs

private val TRIP_FILTERS = listOf("all" to "Tous", "today" to "Aujourd'hui", "7" to "7 jours", "30" to "30 jours")

private fun evColors(t: EventType, c: CdColors): Triple<Color, Color, Color> = when (t) {
    EventType.Brake -> Triple(c.ec, c.oec, c.e)
    EventType.Accel -> Triple(c.wc, c.owc, c.w)
    EventType.Turn -> Triple(c.tc, c.otc, c.t)
}

@Composable
fun TripsScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val changes by vm.repo.changes.collectAsState()
    var all by remember(v.id) { mutableStateOf<List<Trip>?>(null) }
    LaunchedEffect(v.id, changes) { all = withContext(Dispatchers.IO) { vm.repo.trips(v.id) } }
    val scroll = rememberScrollState()
    val tf = vm.tripFilter
    val now = System.currentTimeMillis()
    val list = all.orEmpty().filter {
        when (tf) {
            "today" -> dayLabel(it.start) == "Aujourd'hui"
            "7" -> it.start >= now - 7 * 86_400_000L
            "30" -> it.start >= now - 30 * 86_400_000L
            else -> true
        }
    }
    val km = list.sumOf { it.distanceKm }
    Column(Modifier.fillMaxSize()) {
        BackBar("Trajets", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ScreenTitle("Trajets", "${v.nickname} · ${list.size} trajet${if (list.size > 1) "s" else ""} · ${fr(km, 1)} km")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TRIP_FILTERS.forEach { (k, l) -> CdChip(l, tf == k) { vm.tripFilter = k } }
            }
            val loaded = all
            when {
                loaded == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Skeleton(Modifier.width(110.dp).height(20.dp)); Skeleton(Modifier.width(52.dp).height(28.dp), 8.dp) }
                            Skeleton(Modifier.fillMaxWidth(.6f).height(14.dp)); Skeleton(Modifier.fillMaxWidth(.45f).height(14.dp))
                        }
                    }
                }
                list.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Icons.Rounded.Route, 40, c.onv)
                    T(if (loaded.isEmpty()) "Aucun trajet enregistré pour l'instant" else "Aucun trajet sur cette période", 14, c.onv, modifier = Modifier.padding(top = 8.dp))
                    if (loaded.isEmpty()) T("Les trajets sont enregistrés automatiquement dès que le moteur démarre avec le boîtier branché.", 13, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), lineHeight = 18)
                }
                else -> list.groupBy { dayLabel(it.start) }.forEach { (date, trips) ->
                    T(date, 14, c.onv, 500, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp))
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trips.forEach { t -> TripCard(t) { vm.go(Screen.Trip(t.id)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripCard(t: Trip, onClick: () -> Unit) {
    val c = Cd.c
    val (sbg, sfg) = scoreColors(t.score)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).clickable(onClick = onClick).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            T("${timeHm(t.start)} – ${timeHm(t.end)}", 16, weight = 500)
            ScoreChip(t.score, sbg, sfg, 13, 28, 16)
        }
        Row(Modifier.padding(top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, c.g, CircleShape))
                Box(Modifier.width(2.dp).height(18.dp).background(c.olv))
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.p))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { T(t.from, 14, lineHeight = 20, maxLines = 1); T(t.to, 14, lineHeight = 20, maxLines = 1) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.olv))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "Durée" to durationLabel(t.end - t.start), "Distance" to "${fr(t.distanceKm, 1)} km",
                "Vit. moy." to "${fr(t.avgKmh)} km/h", "Vit. max" to "${fr(t.maxKmh)} km/h",
            ).forEach { (l, v) -> Column(Modifier.weight(1f)) { T(l, 11, c.onv); T(v, 14, weight = 500) } }
        }
        if (t.events.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.events.take(8).forEach { e ->
                    val (bg, fg, _) = evColors(e.type, c)
                    Box(Modifier.size(22.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { Ico(e.type.icon, 14, fg) }
                }
                T(t.events.size.toString() + if (t.events.size > 1) " événements" else " événement", 12, c.onv)
            }
        }
    }
}

@Composable
private fun ScoreChip(score: Int, bg: Color, fg: Color, size: Int, height: Int, icon: Int) {
    Row(
        Modifier.height(height.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { Ico(Icons.Rounded.Speed, icon, fg); T(score.toString(), size, fg, 600) }
}

@Composable
fun TripDetailScreen(vm: AppViewModel, id: Long) {
    val c = Cd.c
    var trip by remember(id) { mutableStateOf<Trip?>(null) }
    LaunchedEffect(id) { trip = withContext(Dispatchers.IO) { vm.repo.trip(id) } }
    var points by remember(id) { mutableStateOf<List<TripPoint>?>(null) }
    LaunchedEffect(id) { points = withContext(Dispatchers.IO) { vm.repo.points(id) } }
    var sheetUp by remember { mutableStateOf(false) }
    var evSel by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetFrac by animateFloatAsState(if (sheetUp) .8f else .46f, tween(450), label = "sheet")
    val t = trip

    BoxWithConstraints(Modifier.fillMaxSize().background(c.sf1)) {
        val mapH = maxHeight * .6f
        Box(Modifier.fillMaxWidth().height(mapH).background(c.sf2)) {
            val pts = points
            if (pts != null && pts.size >= 2 && t != null) {
                TripMap(t.id, pts, t.events, evSel, { evSel = if (evSel == it) null else it }, Modifier.fillMaxSize())
            } else if (pts != null) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Icons.Rounded.LocationOff, 36, c.onv)
                    T("Aucun tracé GPS pour ce trajet", 14, c.onv, modifier = Modifier.padding(top = 8.dp))
                    T("La localisation était désactivée ou non autorisée.", 12, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() }, c.on, c.sf3, Modifier.padding(12.dp).shadow(4.dp, CircleShape))
        IconBtn(Icons.Rounded.Delete, { confirmDelete = true }, c.on, c.sf3, Modifier.align(Alignment.TopEnd).padding(12.dp).shadow(4.dp, CircleShape))

        if (t != null) {
            val (sbg, sfg) = scoreColors(t.score)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(sheetFrac)
                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.sf1),
            ) {
                Box(Modifier.fillMaxWidth().clickable { sheetUp = !sheetUp }.padding(top = 16.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.onv.copy(alpha = .5f)))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            T("${t.from} → ${t.to}", 22, lineHeight = 28)
                            T("${dayLabel(t.start)} · ${timeHm(t.start)} – ${timeHm(t.end)}", 14, c.onv, modifier = Modifier.padding(top = 4.dp))
                        }
                        ScoreChip(t.score, sbg, sfg, 14, 32, 18)
                    }
                    val stats = listOf(
                        "Durée" to durationLabel(t.end - t.start), "Distance" to "${fr(t.distanceKm, 1)} km", "Vit. moyenne" to "${fr(t.avgKmh)} km/h",
                        "Vit. max" to "${fr(t.maxKmh)} km/h", "Événements" to t.events.size.toString(), "Score" to "${t.score}/100",
                    )
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (l, v) ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.sf3).padding(12.dp)) { T(l, 12, c.onv); T(v, 18, weight = 500, modifier = Modifier.padding(top = 2.dp), lineHeight = 24) }
                                }
                            }
                        }
                    }
                    T("Événements signalés · ${t.events.size}", 16, weight = 500, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        t.events.forEachIndexed { i, e ->
                            val (bg, fg, _) = evColors(e.type, c)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (evSel == i) c.sf3 else Color.Transparent)
                                    .clickable { evSel = if (evSel == i) null else i }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(e.type.icon, 20, fg) }
                                Column(Modifier.weight(1f)) { T(e.type.label, 14, weight = 500); T(timeHm(e.t), 12, c.onv) }
                                val sign = when (e.type) { EventType.Brake -> "−"; EventType.Accel -> "+"; EventType.Turn -> "" }
                                T(sign + fr(abs(e.g), 2) + " g", 13, c.onv, 500)
                            }
                        }
                        if (t.events.isEmpty()) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.gc).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Ico(Icons.Rounded.ThumbUp, 22, c.ogc); T("Aucun événement signalé sur ce trajet", 14, c.ogc)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Supprimer ce trajet ?", 22, lineHeight = 28) },
            text = { T("Le tracé et les événements de ce trajet seront définitivement supprimés.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ confirmDelete = false; vm.repo.deleteTrip(id); vm.back() }) { T("Supprimer", 14, c.e, 600) } },
            dismissButton = { TextButton({ confirmDelete = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

private fun dot(ctx: Context, sizeDp: Int, fill: Int, border: Int): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val px = (sizeDp * d).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = border
    cv.drawCircle(px / 2f, px / 2f, px / 2f, p)
    p.color = fill
    cv.drawCircle(px / 2f, px / 2f, px / 2f - 3 * d, p)
    return BitmapDrawable(ctx.resources, bmp)
}

/** Carte OpenStreetMap avec le tracé GPS réel, le départ, l'arrivée et les événements. */
@Composable
private fun TripMap(tripId: Long, points: List<TripPoint>, events: List<TripEvent>, selected: Int?, onEvent: (Int) -> Unit, modifier: Modifier) {
    val c = Cd.c
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }
    val geo = remember(tripId, points) { points.map { GeoPoint(it.lat, it.lon) } }
    var fitted by remember(tripId) { mutableStateOf(false) }

    LaunchedEffect(selected) {
        val e = selected?.let { events.getOrNull(it) } ?: return@LaunchedEffect
        map.controller.animateTo(GeoPoint(e.lat, e.lon), 16.5, 600L)
    }

    AndroidView(factory = { map }, modifier = modifier, update = { m ->
        m.overlayManager.tilesOverlay.setColorFilter(if (c.dark) TilesOverlay.INVERT_COLORS else null)
        m.overlays.clear()
        val density = ctx.resources.displayMetrics.density
        m.overlays.add(Polyline(m).apply {
            setPoints(geo)
            outlinePaint.color = c.p.toArgb()
            outlinePaint.strokeWidth = 6 * density
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            infoWindow = null
        })
        val white = android.graphics.Color.WHITE
        fun marker(p: GeoPoint, icon: BitmapDrawable, onClick: (() -> Unit)? = null) = Marker(m).apply {
            position = p
            setIcon(icon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> onClick?.invoke(); true }
        }
        m.overlays.add(marker(geo.first(), dot(ctx, 18, c.g.toArgb(), white)))
        m.overlays.add(marker(geo.last(), dot(ctx, 22, c.p.toArgb(), white)))
        events.forEachIndexed { i, e ->
            val (_, _, mk) = evColors(e.type, c)
            m.overlays.add(marker(GeoPoint(e.lat, e.lon), dot(ctx, if (selected == i) 30 else 22, mk.toArgb(), white)) { onEvent(i) })
        }
        if (!fitted) {
            fitted = true
            val box = BoundingBox.fromGeoPointsSafe(geo)
            val pad = (48 * density).toInt()
            if (m.width > 0) m.zoomToBoundingBox(box, false, pad) else m.addOnFirstLayoutListener { _, _, _, _, _ -> m.zoomToBoundingBox(box, false, pad) }
        }
        m.invalidate()
    })
}
