package com.cardeck.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object Motion {
    /** Emphasized : cubic-bezier(0.2, 0, 0, 1). */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized accelerate : cubic-bezier(0.3, 0, 0.8, 0.15). */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> standard(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 500f)
}

/** Fondu + légère montée à l'apparition. */
fun Modifier.fadeUp(delayMs: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        p.animateTo(1f, tween(520, easing = Motion.Emphasized))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 24.dp.toPx()
    }
}

/** Apparition en cascade dans une liste ([index] = rang de l'élément). */
fun Modifier.rise(index: Int): Modifier = fadeUp(delayMs = 40 + index.coerceIn(0, 12) * 45)

/** Apparition « pop » des badges (ressort rebondissant). */
fun Modifier.pop(delayMs: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        p.animateTo(1f, Motion.bouncy())
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        scaleX = 0.6f + 0.4f * v
        scaleY = 0.6f + 0.4f * v
    }
}

/** Angle d'une rotation très lente et continue (un tour en [periodMs]). */
@Composable
fun slowRotation(periodMs: Int = 36_000): State<Float> {
    val inf = rememberInfiniteTransition(label = "slowSpin")
    return inf.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "angle")
}

/**
 * Valeur numérique animée au ressort : un nombre qui change glisse vers sa nouvelle valeur, jamais en saccade.
 * Renvoie null tant que [target] est null.
 */
@Composable
fun animatedNumber(target: Double?): Double? {
    val anim = remember { Animatable(target?.toFloat() ?: 0f) }
    LaunchedEffect(target) {
        if (target != null && !target.isNaN()) anim.animateTo(target.toFloat(), Motion.standard())
    }
    return if (target == null || target.isNaN()) null else anim.value.toDouble()
}
