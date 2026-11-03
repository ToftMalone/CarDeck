package com.cardeck.app.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.R
import com.cardeck.app.ui.LocalHaptics
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.SectionCard
import com.cardeck.app.ui.SegmentedGroup
import com.cardeck.app.ui.SwitchRow
import com.cardeck.app.ui.Sym
import com.cardeck.app.ui.Symbol
import com.cardeck.app.ui.ThemeMode
import com.cardeck.app.ui.cs
import com.cardeck.app.ui.dynamicColorAvailable
import com.cardeck.app.ui.fadeUp
import com.cardeck.app.ui.lightBlue
import com.cardeck.app.ui.lightRose
import com.cardeck.app.ui.pop

/** Réglages d'apparence : thème, couleur dynamique, palette, haptique, unités et langue. */
@Composable
fun AppearanceSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    val hap = LocalHaptics.current
    Column(Modifier.padding(top = 24.dp).fadeUp(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.set_appearance), sym = Sym.Palette) {
            Label(R.string.set_theme)
            val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
            SegmentedGroup(
                listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                modes.indexOf(vm.themeMode), { vm.updateThemeMode(modes[it]) },
                syms = listOf(Sym.BrightnessAuto, Sym.LightMode, Sym.DarkMode),
            )
            val dynOk = dynamicColorAvailable()
            SwitchRow(
                stringResource(R.string.set_dynamic), vm.dynamicColor && dynOk, { vm.updateDynamicColor(it) },
                sub = stringResource(if (dynOk) R.string.set_dynamic_sub else R.string.set_dynamic_unavailable),
                sym = Sym.Colors, enabled = dynOk,
            )
            val paletteOn = !(vm.dynamicColor && dynOk)
            Column(Modifier.alpha(if (paletteOn) 1f else 0.38f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(R.string.set_palette)
                Text(stringResource(R.string.set_palette_sub), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Palette.entries.forEach { p ->
                        val sel = p == vm.palette
                        val scheme = if (p == Palette.Rose) lightRose else lightBlue
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape)
                                    .then(if (sel) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                                    .clickable(enabled = paletteOn, role = Role.RadioButton) { hap.tick(); vm.updatePalette(p) }
                                    .padding(if (sel) 6.dp else 0.dp).clip(CircleShape),
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth().weight(1f).background(scheme.primary))
                                    Row(Modifier.weight(1f)) {
                                        Box(Modifier.weight(1f).fillMaxWidth().background(scheme.secondaryContainer))
                                        Box(Modifier.weight(1f).fillMaxWidth().background(scheme.tertiary))
                                    }
                                }
                                if (sel) Symbol(Sym.Check, Modifier.align(Alignment.Center).pop(), size = 20.dp, tint = scheme.onPrimary)
                            }
                            Text(
                                stringResource(if (p == Palette.Rose) R.string.palette_rose else R.string.palette_blue),
                                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            SwitchRow(
                stringResource(R.string.set_haptics), vm.haptics, { vm.updateHaptics(it) },
                sub = stringResource(R.string.set_haptics_sub), sym = Sym.Vibration,
            )
        }
        SectionCard(stringResource(R.string.set_units), sym = Sym.Straighten) {
            Label(R.string.set_speed)
            SegmentedGroup(listOf(stringResource(R.string.unit_kmh), stringResource(R.string.unit_mph)), if (vm.mph) 1 else 0, { vm.updateMph(it == 1) })
            Label(R.string.set_temp)
            SegmentedGroup(listOf(stringResource(R.string.unit_c), stringResource(R.string.unit_f)), if (vm.fahrenheit) 1 else 0, { vm.updateFahrenheit(it == 1) })
        }
        SectionCard(stringResource(R.string.set_language), sym = Sym.Language) {
            val langs = listOf("", "en", "fr")
            SegmentedGroup(
                listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_en), stringResource(R.string.lang_fr)),
                langs.indexOf(vm.language).coerceAtLeast(0),
                { vm.updateLanguage(langs[it]); (ctx as? Activity)?.recreate() },
            )
        }
    }
}

@Composable
private fun Label(res: Int) =
    Text(stringResource(res), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)

