package com.cardeck.app.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.background
import com.cardeck.app.ui.Spinner
import com.cardeck.app.export.TripExport
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.Share
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
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
import com.cardeck.app.data.Sample
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.pidById
import com.cardeck.app.data.Trip
import com.cardeck.app.data.TripPoint
import com.cardeck.app.data.dayLabel
import com.cardeck.app.data.durationLabel
import com.cardeck.app.data.fr
import com.cardeck.app.data.timeHm
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.Skeleton
import com.cardeck.app.ui.T
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

internal val TRIP_FILTERS = listOf("all" to "Tous", "today" to "Aujourd'hui", "7" to "7 jours", "30" to "30 jours")

/** Le trajet fait-il partie de la période choisie (filtre de l'historique) ? */
internal fun tripInPeriod(t: Trip, filter: String, now: Long): Boolean = when (filter) {
    "today" -> dayLabel(t.start) == "Aujourd'hui"
    "7" -> t.start >= now - 7 * 86_400_000L
    "30" -> t.start >= now - 30 * 86_400_000L
    else -> true
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
    val list = all.orEmpty().filter { tripInPeriod(it, tf, now) }
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
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).clickable(onClick = onClick).padding(16.dp)) {
        T("${timeHm(t.start)} – ${timeHm(t.end)}", 16, weight = 500)
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
    }
}


@Composable
fun TripDetailScreen(vm: AppViewModel, id: Long) {
    val c = Cd.c
    var trip by remember(id) { mutableStateOf<Trip?>(null) }
    LaunchedEffect(id) { trip = withContext(Dispatchers.IO) { vm.repo.trip(id) } }
    var points by remember(id) { mutableStateOf<List<TripPoint>?>(null) }
    LaunchedEffect(id) { points = withContext(Dispatchers.IO) { vm.repo.points(id) } }
    var samples by remember(id) { mutableStateOf<Map<String, List<Sample>>>(emptyMap()) }
    LaunchedEffect(id) { samples = withContext(Dispatchers.IO) { vm.repo.samples(id) } }
    var sheetUp by remember { mutableStateOf(false) }
    // Replay : instant affiché (null = pas de replay), lecture en cours, vitesse de lecture.
    var replayT by remember(id) { mutableStateOf<Long?>(null) }
    var playing by remember(id) { mutableStateOf(false) }
    var speedX by remember { mutableIntStateOf(10) }
    LaunchedEffect(playing, speedX, trip) {
        val tr = trip ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (playing) {
            val now = withFrameMillis { it }
            val cur = (replayT ?: tr.start) + (now - last) * speedX
            last = now
            if (cur >= tr.end) { replayT = tr.end; playing = false } else replayT = cur
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val sheetFrac by animateFloatAsState(if (sheetUp) .8f else .46f, tween(450), label = "sheet")
    val t = trip

    BoxWithConstraints(Modifier.fillMaxSize().background(c.sf1)) {
        val mapH = maxHeight * .6f
        Box(Modifier.fillMaxWidth().height(mapH).background(c.sf2)) {
            val pts = points
            if (pts != null && pts.size >= 2 && t != null) {
                TripMap(t.id, pts, Modifier.fillMaxSize(), replayT?.let { positionAt(pts, it) }, playing)
            } else if (pts != null) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Icons.Rounded.LocationOff, 36, c.onv)
                    T("Aucun tracé GPS pour ce trajet", 14, c.onv, modifier = Modifier.padding(top = 8.dp))
                    T("La localisation était désactivée ou non autorisée.", 12, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() }, c.on, c.sf3, Modifier.padding(12.dp).shadow(4.dp, CircleShape))
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (exporting) Box(Modifier.size(48.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { Spinner() }
            else IconBtn(Icons.Rounded.Share, {
                val tr = trip ?: return@IconBtn
                exporting = true
                scope.launch {
                    val file = runCatching {
                        withContext(Dispatchers.IO) {
                            val vehicle = vm.repo.vehicles.value.find { it.id == tr.vehicleId }
                            TripExport.writeZip(java.io.File(ctx.cacheDir, "exports"), tr, vehicle, vm.repo.points(tr.id), vm.repo.samples(tr.id))
                        }
                    }.getOrNull()
                    exporting = false
                    if (file == null) vm.showSnack("Échec de l'export du trajet") else shareFile(ctx, file)
                }
            }, c.on, c.sf3, Modifier.shadow(4.dp, CircleShape))
            IconBtn(Icons.Rounded.Delete, { confirmDelete = true }, c.on, c.sf3, Modifier.shadow(4.dp, CircleShape))
        }

        if (t != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(sheetFrac)
                    .shadow(8.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.sf1),
            ) {
                Box(Modifier.fillMaxWidth().clickable { sheetUp = !sheetUp }.padding(top = 16.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.onv.copy(alpha = .5f)))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    T("${t.from} → ${t.to}", 22, lineHeight = 28)
                    T("${dayLabel(t.start)} · ${timeHm(t.start)} – ${timeHm(t.end)}", 14, c.onv, modifier = Modifier.padding(top = 4.dp))
                    ReplayBar(
                        t, replayT, playing, speedX,
                        onPlay = { if (replayT == null || replayT!! >= t.end) replayT = t.start; playing = !playing; sheetUp = false },
                        onSeek = { f -> replayT = t.start + ((t.end - t.start) * f).toLong() },
                        onSpeed = { speedX = REPLAY_SPEEDS[(REPLAY_SPEEDS.indexOf(speedX) + 1) % REPLAY_SPEEDS.size] },
                        onStop = { playing = false; replayT = null },
                    )
                    val stats = listOf(
                        "Durée" to durationLabel(t.end - t.start), "Distance" to "${fr(t.distanceKm, 1)} km", "Vit. moyenne" to "${fr(t.avgKmh)} km/h",
                        "Vit. max" to "${fr(t.maxKmh)} km/h",
                    )
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (l, v) ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.sf3).padding(12.dp)) { T(l, 12, c.onv); T(v, 18, weight = 500, modifier = Modifier.padding(top = 2.dp), lineHeight = 24) }
                                }
                                repeat(2 - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                    EngineHistory(t, samples, replayT)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Supprimer ce trajet ?", 22, lineHeight = 28) },
            text = { T("Le tracé et les données de ce trajet seront définitivement supprimés.", 14, c.onv, lineHeight = 20) },
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

/** Carte OpenStreetMap avec le tracé GPS réel, le départ et l'arrivée. */
@Composable
private fun TripMap(
    tripId: Long, points: List<TripPoint>, modifier: Modifier,
    cursor: GeoPoint? = null, follow: Boolean = false,
) {
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
    // Signature des calques fixes : on ne les reconstruit que si elle change (pas à chaque image du replay).
    val staticSig = remember { arrayOf("") }
    val cursorMarker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); infoWindow = null } }

    AndroidView(factory = { map }, modifier = modifier, update = { m ->
        val density = ctx.resources.displayMetrics.density
        val sig = "$tripId|${c.dark}|${c.p.toArgb()}"
        if (sig != staticSig[0]) {
        staticSig[0] = sig
        m.overlayManager.tilesOverlay.setColorFilter(if (c.dark) TilesOverlay.INVERT_COLORS else null)
        m.overlays.clear()
        cursorMarker.setIcon(dot(ctx, 26, c.t.toArgb(), android.graphics.Color.WHITE))
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
        }
        // Marqueur du replay
        if (cursor != null) {
            cursorMarker.position = cursor
            if (cursorMarker !in m.overlays) m.overlays.add(cursorMarker)
            if (follow) m.controller.setCenter(cursor)
        } else {
            m.overlays.remove(cursorMarker)
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

// ---------------------------------------------------------------- Replay et historique moteur

private val REPLAY_SPEEDS = listOf(1, 5, 10, 30, 60)

/** Position interpolée sur le tracé GPS à l'instant [t]. */
private fun positionAt(points: List<TripPoint>, t: Long): GeoPoint {
    if (t <= points.first().t) return GeoPoint(points.first().lat, points.first().lon)
    if (t >= points.last().t) return GeoPoint(points.last().lat, points.last().lon)
    var i = points.binarySearchBy(t) { it.t }
    if (i >= 0) return GeoPoint(points[i].lat, points[i].lon)
    i = -i - 1
    val a = points[i - 1]
    val b = points[i]
    val f = (t - a.t).toDouble() / (b.t - a.t).coerceAtLeast(1)
    return GeoPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
}

/** Dernière valeur connue à l'instant [t]. */
private fun valueAt(s: List<Sample>, t: Long): Double? {
    var i = s.binarySearchBy(t) { it.t }
    if (i >= 0) return s[i].v
    i = -i - 2
    return s.getOrNull(i)?.v
}

private fun clock(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
}

@Composable
private fun ReplayBar(t: Trip, replayT: Long?, playing: Boolean, speedX: Int, onPlay: () -> Unit, onSeek: (Float) -> Unit, onSpeed: () -> Unit, onStop: () -> Unit) {
    val c = Cd.c
    if (replayT == null) {
        Row(
            Modifier.padding(top = 14.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.p).clickable(onClick = onPlay).padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { Ico(Icons.Rounded.PlayArrow, 22, c.op); T("Rejouer le trajet", 14, c.op, 500) }
        return
    }
    val total = (t.end - t.start).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(20.dp)).background(c.sf3).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, onPlay, c.p)
            Slider(
                value = ((replayT - t.start).toFloat() / total).coerceIn(0f, 1f), onValueChange = onSeek, modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = c.p, activeTrackColor = c.p, inactiveTrackColor = c.sf4),
            )
            Box(Modifier.padding(start = 6.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).background(c.sc).clickable(onClick = onSpeed).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                T("×$speedX", 13, c.osc, 600)
            }
            IconBtn(Icons.Rounded.Close, onStop, c.onv)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            T("${timeHm(replayT)} · ${clock(replayT - t.start)}", 12, c.onv)
            T(clock(total), 12, c.onv)
        }
    }
}

/**
 * Données du moment : les valeurs relevées à l'instant du replay (au départ du trajet tant que le replay n'est pas lancé),
 * par catégorie. Chaque valeur est la dernière mesure connue à cet instant.
 */
@Composable
private fun EngineHistory(t: Trip, samples: Map<String, List<Sample>>, cursor: Long?) {
    val c = Cd.c
    val at = cursor ?: t.start
    T("Données du moment", 16, weight = 500, modifier = Modifier.padding(top = 20.dp))
    T(
        if (cursor == null) "Au départ du trajet. Lance le replay pour voir les données évoluer." else "${timeHm(at)} · ${clock(at - t.start)} depuis le départ",
        12, c.onv, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
    val series = samples.mapNotNull { (id, list) -> pidById(id)?.let { it to list } }.filter { it.second.isNotEmpty() }
    if (series.isEmpty()) {
        T("Aucune donnée moteur enregistrée pour ce trajet.", 13, c.onv, lineHeight = 18)
        return
    }
    val order = StdPids.CATEGORIES
    series.groupBy { it.first.category }.toList().sortedBy { (cat, _) -> order.indexOf(cat).let { if (it < 0) order.size else it } }.forEach { (cat, list) ->
        T(cat, 14, c.p, 500, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (p, s) -> MomentCard(p, valueAt(s, at), Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MomentCard(p: PidDef, value: Double?, modifier: Modifier) {
    val c = Cd.c
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.sf2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(p.icon, 16, c.p)
            T(p.label, 12, c.onv, lineHeight = 16, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            T(value?.let { p.text(it) } ?: "—", 20, weight = 500, lineHeight = 26)
            if (p.states == null) T(p.unit, 12, c.onv, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** Ouvre le menu de partage Android pour l'archive exportée. */
private fun shareFile(ctx: Context, file: java.io.File) {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(android.content.Intent.createChooser(send, "Exporter le trajet"))
}
