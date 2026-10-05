package com.cardeck.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

enum class Palette(val label: String, val hue: Double) {
    Bleu("Bleu", 250.0),
    Turquoise("Turquoise", 190.0),
    Violet("Violet", 300.0),
    Orange("Orange", 50.0),
}

enum class ThemeMode { Light, Dark, System }

/** Convertit une couleur OKLCH en sRGB (avec réduction de chroma si hors gamut, comme les navigateurs). */
fun oklch(l: Double, c: Double, h: Double): Color {
    fun toRgb(chroma: Double): DoubleArray {
        val hr = Math.toRadians(h)
        val a = chroma * cos(hr)
        val b = chroma * sin(hr)
        val l_ = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m_ = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s_ = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        return doubleArrayOf(
            4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
            -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
            -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_,
        )
    }
    fun inGamut(v: DoubleArray) = v.all { it in -0.0005..1.0005 }
    var rgb = toRgb(c)
    if (!inGamut(rgb)) {
        var lo = 0.0
        var hi = c
        repeat(18) {
            val mid = (lo + hi) / 2
            if (inGamut(toRgb(mid))) lo = mid else hi = mid
        }
        rgb = toRgb(lo)
    }
    fun enc(x: Double): Float {
        val v = x.coerceIn(0.0, 1.0)
        return (if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055).toFloat()
    }
    return Color(enc(rgb[0]), enc(rgb[1]), enc(rgb[2]))
}

/** Jetons de couleur de l'application (équivalent des variables CSS de la maquette). */
data class CdColors(
    val p: Color, val op: Color, val pc: Color, val opc: Color,
    val s: Color, val os: Color, val sc: Color, val osc: Color,
    val t: Color, val ot: Color, val tc: Color, val otc: Color,
    val sf: Color, val sfl: Color, val sf1: Color, val sf2: Color, val sf3: Color, val sf4: Color,
    val on: Color, val onv: Color, val ol: Color, val olv: Color,
    val isf: Color, val ion: Color,
    val e: Color, val oe: Color, val ec: Color, val oec: Color,
    val w: Color, val wc: Color, val owc: Color,
    val g: Color, val gc: Color, val ogc: Color,
    val dark: Boolean,
)

fun cdColors(h: Double, dark: Boolean): CdColors {
    val t = (h + 60) % 360
    return if (dark) CdColors(
        p = oklch(.82, .11, h), op = oklch(.3, .09, h), pc = oklch(.4, .11, h), opc = oklch(.93, .05, h),
        s = oklch(.82, .04, h), os = oklch(.3, .03, h), sc = oklch(.38, .045, h), osc = oklch(.92, .03, h),
        t = oklch(.82, .09, t), ot = oklch(.3, .07, t), tc = oklch(.4, .08, t), otc = oklch(.93, .04, t),
        sf = oklch(.165, .012, h), sfl = oklch(.135, .01, h), sf1 = oklch(.195, .013, h), sf2 = oklch(.215, .014, h),
        sf3 = oklch(.25, .015, h), sf4 = oklch(.285, .016, h),
        on = oklch(.92, .01, h), onv = oklch(.8, .02, h), ol = oklch(.62, .02, h), olv = oklch(.4, .02, h),
        isf = oklch(.92, .01, h), ion = oklch(.27, .012, h),
        e = oklch(.8, .11, 25.0), oe = oklch(.33, .1, 25.0), ec = oklch(.42, .13, 25.0), oec = oklch(.93, .04, 25.0),
        w = oklch(.85, .12, 80.0), wc = oklch(.44, .09, 70.0), owc = oklch(.94, .06, 85.0),
        g = oklch(.83, .13, 150.0), gc = oklch(.42, .09, 150.0), ogc = oklch(.93, .06, 150.0),
        dark = true,
    ) else CdColors(
        p = oklch(.5, .13, h), op = Color.White, pc = oklch(.92, .05, h), opc = oklch(.28, .09, h),
        s = oklch(.5, .04, h), os = Color.White, sc = oklch(.91, .035, h), osc = oklch(.28, .04, h),
        t = oklch(.5, .1, t), ot = Color.White, tc = oklch(.92, .05, t), otc = oklch(.28, .08, t),
        sf = oklch(.985, .006, h), sfl = Color.White, sf1 = oklch(.965, .008, h), sf2 = oklch(.95, .009, h),
        sf3 = oklch(.935, .01, h), sf4 = oklch(.905, .011, h),
        on = oklch(.22, .012, h), onv = oklch(.43, .02, h), ol = oklch(.58, .02, h), olv = oklch(.84, .015, h),
        isf = oklch(.3, .012, h), ion = oklch(.95, .008, h),
        e = oklch(.53, .19, 27.0), oe = Color.White, ec = oklch(.93, .045, 20.0), oec = oklch(.36, .12, 25.0),
        w = oklch(.58, .13, 65.0), wc = oklch(.93, .07, 85.0), owc = oklch(.38, .08, 60.0),
        g = oklch(.52, .13, 150.0), gc = oklch(.93, .06, 150.0), ogc = oklch(.32, .08, 150.0),
        dark = false,
    )
}

val LocalCd = staticCompositionLocalOf { cdColors(250.0, true) }

object Cd {
    val c: CdColors
        @Composable @ReadOnlyComposable get() = LocalCd.current
}

@Composable
fun CarDeckTheme(palette: Palette, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val c = cdColors(palette.hue, dark)
    val scheme = if (dark) darkColorScheme(
        primary = c.p, onPrimary = c.op, primaryContainer = c.pc, onPrimaryContainer = c.opc,
        secondary = c.s, onSecondary = c.os, secondaryContainer = c.sc, onSecondaryContainer = c.osc,
        tertiary = c.t, onTertiary = c.ot, tertiaryContainer = c.tc, onTertiaryContainer = c.otc,
        background = c.sf, onBackground = c.on, surface = c.sf, onSurface = c.on, onSurfaceVariant = c.onv,
        surfaceVariant = c.sf3, outline = c.ol, outlineVariant = c.olv,
        error = c.e, onError = c.oe, errorContainer = c.ec, onErrorContainer = c.oec,
        inverseSurface = c.isf, inverseOnSurface = c.ion,
    ) else lightColorScheme(
        primary = c.p, onPrimary = c.op, primaryContainer = c.pc, onPrimaryContainer = c.opc,
        secondary = c.s, onSecondary = c.os, secondaryContainer = c.sc, onSecondaryContainer = c.osc,
        tertiary = c.t, onTertiary = c.ot, tertiaryContainer = c.tc, onTertiaryContainer = c.otc,
        background = c.sf, onBackground = c.on, surface = c.sf, onSurface = c.on, onSurfaceVariant = c.onv,
        surfaceVariant = c.sf3, outline = c.ol, outlineVariant = c.olv,
        error = c.e, onError = c.oe, errorContainer = c.ec, onErrorContainer = c.oec,
        inverseSurface = c.isf, inverseOnSurface = c.ion,
    )
    CompositionLocalProvider(LocalCd provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
