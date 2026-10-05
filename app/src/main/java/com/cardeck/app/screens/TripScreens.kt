package com.cardeck.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.data.EventType
import com.cardeck.app.data.TRIPS
import com.cardeck.app.data.TRIP_FILTERS
import com.cardeck.app.data.TRIP_PATHS
import com.cardeck.app.data.Trip
import com.cardeck.app.data.fr
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
import kotlinx.coroutines.delay

private fun evColors(t: EventType, c: CdColors): Triple<Color, Color, Color> = when (t) {
    EventType.Brake -> Triple(c.ec, c.oec, c.e)
    EventType.Accel -> Triple(c.wc, c.owc, c.w)
    EventType.Turn -> Triple(c.tc, c.otc, c.t)
}

@Composable
fun TripsScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    val tf = vm.tripFilter
    val list = TRIPS.filter { tf == "all" || (if (tf == "today") it.day == 0 else it.day < tf.toInt()) }
    val km = list.sumOf { it.dist.replace(',', '.').toDouble() }
    Column(Modifier.fillMaxSize()) {
        BackBar("Trajets", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ScreenTitle("Trajets", "${list.size} trajet${if (list.size > 1) "s" else ""} · ${fr(km, 1)} km")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TRIP_FILTERS.forEach { (k, l) -> CdChip(l, tf == k) { vm.updateTripFilter(k) } }
            }
            if (vm.tripsLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.padding(horizontal = 4.dp, vertical = 8.dp).width(90.dp).height(14.dp))
                    repeat(3) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Skeleton(Modifier.width(110.dp).height(20.dp)); Skeleton(Modifier.width(52.dp).height(28.dp), 8.dp) }
                            Skeleton(Modifier.fillMaxWidth(.6f).height(14.dp)); Skeleton(Modifier.fillMaxWidth(.45f).height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(4) { Skeleton(Modifier.weight(1f).height(28.dp)) } }
                        }
                    }
                }
            } else if (list.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Ico(Icons.Rounded.Route, 40, c.onv)
                    T("Aucun trajet sur cette période", 14, c.onv, modifier = Modifier.padding(top = 8.dp))
                }
            } else {
                list.groupBy { it.date }.forEach { (date, trips) ->
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
            T("${t.start} – ${t.end}", 16, weight = 500)
            ScoreChip(t.score, sbg, sfg, 13, 28, 16)
        }
        Row(Modifier.padding(top = 12.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(CircleShape).border(2.dp, c.g, CircleShape))
                Box(Modifier.width(2.dp).height(18.dp).background(c.olv))
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.p))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { T(t.from, 14, lineHeight = 20); T(t.to, 14, lineHeight = 20) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.olv))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Durée" to t.dur, "Distance" to "${t.dist} km", "Vit. moy." to "${t.avg} km/h", "Conso." to "${t.conso} L").forEach { (l, v) ->
                Column(Modifier.weight(1f)) { T(l, 11, c.onv); T(v, 14, weight = 500) }
            }
        }
        if (t.events.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.events.forEach { e ->
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
fun TripDetailScreen(vm: AppViewModel, id: Int) {
    val c = Cd.c
    val trip = TRIPS.find { it.id == id } ?: TRIPS[0]
    val path = TRIP_PATHS[trip.path]
    var sheetUp by remember { mutableStateOf(false) }
    var evSel by remember { mutableStateOf<Int?>(null) }
    val draw = remember(id) { Animatable(0f) }
    LaunchedEffect(id) {
        delay(350)
        draw.animateTo(1f, tween(1600, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)))
    }
    val drawn = draw.value > .01f
    val sheetFrac by animateFloatAsState(if (sheetUp) .8f else .46f, tween(450), label = "sheet")
    val (sbg, sfg) = scoreColors(trip.score)

    BoxWithConstraints(Modifier.fillMaxSize().background(c.sf1)) {
        val mapH = maxHeight * .6f
        val mapW = maxWidth
        Box(Modifier.fillMaxWidth().height(mapH).background(c.sf2)) {
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / 400f
                val sy = size.height / 480f
                fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
                // grille de fond
                val step = 46.dp.toPx()
                var gx = 0f
                while (gx < size.width) { drawLine(c.sf3, Offset(gx, 0f), Offset(gx, size.height), 2.dp.toPx()); gx += step }
                var gy = 0f
                while (gy < size.height) { drawLine(c.sf3, Offset(0f, gy), Offset(size.width, gy), 2.dp.toPx()); gy += step }
                // parc et rivière
                drawRoundRect(c.gc.copy(alpha = .5f), p(180f, 250f), androidx.compose.ui.geometry.Size(120f * sx, 70f * sy), androidx.compose.ui.geometry.CornerRadius(12f * sx))
                val river = Path().apply { moveTo(-10f * sx, 440f * sy); cubicTo(120f * sx, 400f * sy, 220f * sx, 470f * sy, 420f * sx, 380f * sy) }
                drawPath(river, c.pc.copy(alpha = .55f), style = Stroke(22f * sx))
                // routes
                val roadW = 10f * sx
                listOf(p(0f, 312f) to p(400f, 300f), p(56f, 0f) to p(56f, 480f), p(262f, 0f) to p(262f, 480f), p(0f, 126f) to p(400f, 126f), p(340f, 0f) to p(340f, 480f))
                    .forEach { (a, b) -> drawLine(c.sf4, a, b, roadW) }
                // trajet
                val route = Path().apply {
                    path.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * sx, y * sy) else lineTo(x * sx, y * sy) }
                }
                drawPath(route, c.sf.copy(alpha = .8f), style = Stroke(10f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val seg = Path()
                val pm = PathMeasure().apply { setPath(route, false) }
                pm.getSegment(0f, pm.length * draw.value, seg, true)
                drawPath(seg, c.p, style = Stroke(5f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // Marqueurs
            val w = mapW
            fun px(x: Float) = w * (x / 400f)
            fun py(y: Float) = mapH * (y / 480f)
            val start = path.first()
            val end = path.last()
            Box(Modifier.offset(px(start.first) - 9.dp, py(start.second) - 9.dp).size(18.dp).clip(CircleShape).background(c.g).border(3.dp, c.sf, CircleShape))
            if (drawn && draw.value >= .99f) {
                Box(
                    Modifier.offset(px(end.first) - 16.dp, py(end.second) - 16.dp).size(32.dp).clip(CircleShape).background(c.p).border(3.dp, c.sf, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Ico(Icons.Rounded.Flag, 16, c.op) }
            }
            trip.events.forEachIndexed { i, e ->
                val (_, _, mk) = evColors(e.type, c)
                val sel = evSel == i
                val sc by animateFloatAsState(if (draw.value < .99f) 0f else if (sel) 1.35f else 1f, spring(dampingRatio = .5f), label = "ev")
                val pt = path[e.pathIdx]
                Box(
                    Modifier.offset(px(pt.first) - 15.dp, py(pt.second) - 15.dp).size(30.dp).scale(sc).clip(CircleShape).background(mk).border(3.dp, c.sf, CircleShape)
                        .clickable { evSel = if (sel) null else i },
                    contentAlignment = Alignment.Center,
                ) { Ico(e.type.icon, 16, c.sf) }
            }
        }
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() }, c.on, c.sf3, Modifier.padding(12.dp).shadow(4.dp, CircleShape))

        // Feuille inférieure
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
                        T("${trip.from} → ${trip.to}", 22, lineHeight = 28)
                        T("${trip.date} · ${trip.start} – ${trip.end}", 14, c.onv, modifier = Modifier.padding(top = 4.dp))
                    }
                    ScoreChip(trip.score, sbg, sfg, 14, 32, 18)
                }
                val stats = listOf("Durée" to trip.dur, "Distance" to "${trip.dist} km", "Vit. moyenne" to "${trip.avg} km/h", "Vit. max" to trip.vmax, "Consommation" to "${trip.conso} L/100", "Score" to "${trip.score}/100")
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (l, v) ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.sf3).padding(12.dp)) { T(l, 12, c.onv); T(v, 18, weight = 500, modifier = Modifier.padding(top = 2.dp), lineHeight = 24) }
                            }
                        }
                    }
                }
                T("Événements signalés · ${trip.events.size}", 16, weight = 500, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    trip.events.forEachIndexed { i, e ->
                        val (bg, fg, _) = evColors(e.type, c)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (evSel == i) c.sf3 else Color.Transparent)
                                .clickable { evSel = if (evSel == i) null else i }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(e.type.icon, 20, fg) }
                            Column(Modifier.weight(1f)) { T(e.type.label, 14, weight = 500); T("${e.time} · ${e.where}", 12, c.onv) }
                            T(e.value, 13, c.onv, 500)
                        }
                    }
                    if (trip.events.isEmpty()) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.gc).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Ico(Icons.Rounded.ThumbUp, 22, c.ogc); T("Aucun événement signalé sur ce trajet", 14, c.ogc)
                        }
                    }
                }
            }
        }
    }
}
