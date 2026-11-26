package com.cardeck.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Mouvements des maquettes : courbes « emphasized » et ressorts. */
object Motion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Le ressort de base : spring(0.8, 380). */
    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Léger dépassement, pour les entrées (dock, cartes). */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 500f)

    fun <T> emphasized(ms: Int, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, Emphasized)
}

/** Retours haptiques des maquettes. */
class Haptics(private val view: View) {
    private fun perform(constant: Int) { view.performHapticFeedback(constant) }
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)
    fun segment() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)
    fun threshold() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK)
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("Haptics non fourni") }

// ---------------------------------------------------------------- Animations

/** Rotation linéaire continue, en degrés. */
@Composable
fun rememberSpin(periodMs: Int, reverse: Boolean = false): State<Float> {
    val t = rememberInfiniteTransition(label = "spin")
    return t.animateFloat(0f, if (reverse) -360f else 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "spin")
}

fun Modifier.spinning(angle: State<Float>) = graphicsLayer { rotationZ = angle.value }

/** Anneau qui s'élargit : échelle 0,6 → 2,2, opacité 0,7 → 0. */
@Composable
fun PulseRing(color: Color, size: Dp, periodMs: Int = 1600, filled: Boolean = false, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = Motion.Emphasized)), label = "pulse")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = 0.6f + 1.6f * p
                scaleX = s; scaleY = s; alpha = 0.7f * (1f - p)
            }
            .clip(CircleShape)
            .then(if (filled) Modifier.background(color) else Modifier.border(2.dp, color, CircleShape)),
    )
}

/** Point qui clignote (état « en cours »). */
@Composable
fun BlinkDot(color: Color, size: Dp, periodMs: Int = 1600) {
    val t = rememberInfiniteTransition(label = "blink")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(periodMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blink")
    Box(Modifier.size(size).graphicsLayer { alpha = a }.clip(CircleShape).background(color))
}

/**
 * Les animations d'entrée jouent-elles ? Non pour une page qui réapparaît sous une autre (retour) : elle est déjà là,
 * l'animation ferait bouger les cartes pendant que la page qui part y rentre.
 */
val LocalEntrance = staticCompositionLocalOf { true }

/** Entrée des listes : montée de 18 dp et fondu, ressort (0,8 ; 380), décalée de [index] × [stepMs]. */
@Composable
fun Modifier.rise(index: Int = 0, stepMs: Int = 45, distance: Dp = 18.dp): Modifier {
    val play = LocalEntrance.current
    val y = remember { Animatable(if (play) 1f else 0f) }
    val a = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!play) return@LaunchedEffect
        delay((index.coerceAtMost(12) * stepMs).toLong())
        launch { y.animateTo(0f, Motion.standard()) }
        a.animateTo(1f, tween(300, easing = Motion.Emphasized))
    }
    return graphicsLayer {
        translationY = y.value * distance.toPx()
        alpha = a.value
    }
}

/** Entrée « pop » : échelle 0,4 → 1 et rotation −40° → 0, ressort rebondissant. */
@Composable
fun Modifier.pop(delayMs: Int = 0, key: Any? = Unit): Modifier {
    val play = LocalEntrance.current
    val p = remember(key) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(key) {
        if (!play) return@LaunchedEffect
        delay(delayMs.toLong())
        p.animateTo(1f, spring(0.55f, 420f))
    }
    return graphicsLayer {
        val v = p.value
        val s = 0.4f + 0.6f * v
        scaleX = s; scaleY = s
        rotationZ = -40f * (1f - v)
        alpha = v.coerceIn(0f, 1f)
    }
}

/** Entrée d'un contenu de page : montée de 12 dp et fondu, 400 ms. */
@Composable
fun Modifier.fadeUp(delayMs: Int = 0): Modifier {
    val play = LocalEntrance.current
    val p = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) { if (play) p.animateTo(1f, tween(400, delayMs, Motion.Emphasized)) }
    return graphicsLayer {
        translationY = (1f - p.value) * 12.dp.toPx()
        alpha = p.value
    }
}

/**
 * Changement d'état d'une page (non connecté → lecture → données, bouton → scan → résultats…) : l'ancien contenu s'efface vite,
 * le nouveau monte légèrement en fondu, et la hauteur suit avec le ressort de base. [state] doit changer seulement quand
 * l'état change vraiment (pas à chaque nouvelle valeur).
 */
@Composable
fun <S> StateSwap(state: S, modifier: Modifier = Modifier, content: @Composable (S) -> Unit) {
    AnimatedContent(
        state, modifier,
        transitionSpec = {
            (fadeIn(tween(320, 70, Motion.Emphasized)) + slideInVertically(tween(320, 70, Motion.Emphasized)) { it / 40 }) togetherWith
                fadeOut(tween(90)) using SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 1f, stiffness = 380f) }
        },
        label = "state",
    ) { content(it) }
}

// ---------------------------------------------------------------- Formes expressives

/**
 * Forme « fleur » : rayon qui ondule [lobes] fois autour du centre ([depth] = creux relatif entre deux lobes).
 * Sert aux badges des cartes : biscuit à 9 lobes, trèfle à 4 feuilles.
 */
class LobedShape(private val lobes: Int, private val depth: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = minOf(cx, cy)
        val path = Path()
        val steps = 240
        for (i in 0..steps) {
            val a = 2 * PI * i / steps - PI / 2
            // Creux arrondis : cos² adoucit les pointes entre deux lobes.
            val k = (1 - cos(lobes * a)) / 2
            val rr = r * (1f - depth * (k * k).toFloat())
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Biscuit à 9 lobes. */
fun cookieShape(): Shape = LobedShape(9, 0.16f)

/** Trèfle à 4 feuilles. */
fun cloverShape(): Shape = LobedShape(4, 0.34f)

/** Une icône centrée sur une forme expressive, la forme peut tourner lentement pendant que l'icône reste droite. */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    shape: Shape,
    size: Dp,
    color: Color,
    iconColor: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    spinMs: Int = 0,
    reverse: Boolean = false,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val shapeMod = if (spinMs > 0) Modifier.spinning(rememberSpin(spinMs, reverse)) else Modifier
        Box(Modifier.matchParentSize().then(shapeMod).clip(shape).background(color))
        Ico(icon, iconSize.value.toInt(), iconColor)
    }
}

/** Liste groupée : coins extérieurs de 24 dp, [inner] entre les éléments. */
fun groupShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 8.dp): Shape = when {
    count == 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(outer, outer, inner, inner)
    index == count - 1 -> RoundedCornerShape(inner, inner, outer, outer)
    else -> RoundedCornerShape(inner)
}
