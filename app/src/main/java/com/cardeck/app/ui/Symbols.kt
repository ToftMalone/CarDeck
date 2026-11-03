package com.cardeck.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardeck.app.R

/**
 * Icônes Material Symbols Rounded (police variable embarquée, axe FILL conservé).
 * Généré à partir de tools/fonts/symbols.txt : ajouter une icône là, relancer tools/fonts/subset.py puis l'ajouter ici.
 */
enum class Sym(val codepoint: Int) {
    Home(0xe9b2),
    Dashboard(0xe871),
    Speed(0xe9e4),
    Stethoscope(0xf805),
    Route(0xeacd),
    DirectionsCar(0xeff7),
    Settings(0xe8b8),
    Apps(0xe5c3),
    Star(0xf09a),
    GridView(0xe9b0),
    Bluetooth(0xe1a7),
    BluetoothDisabled(0xe1a9),
    LinkOff(0xe16f),
    Sensors(0xe51e),
    Memory(0xe322),
    Thermostat(0xf076),
    DeviceThermostat(0xe1ff),
    LocalGasStation(0xe546),
    Air(0xefd8),
    Compress(0xe94d),
    Bolt(0xea0b),
    Timeline(0xe922),
    ShowChart(0xe6e1),
    Monitoring(0xf190),
    Error(0xf8b6),
    Warning(0xf083),
    CheckCircle(0xf0be),
    Refresh(0xe5d5),
    PlayArrow(0xe037),
    Stop(0xe047),
    Add(0xe145),
    Remove(0xe15b),
    ArrowBack(0xe5c4),
    ChevronRight(0xe5cc),
    Lock(0xe899),
    Info(0xe88e),
    History(0xe8b3),
    Tune(0xe429),
    Palette(0xe40a),
    Vibration(0xf2cb),
    Straighten(0xe41c),
    Language(0xea07),
    DarkMode(0xe51c),
    LightMode(0xe518),
    BrightnessAuto(0xe1ab),
    ReceiptLong(0xef6e),
    Notifications(0xe7f5),
    Map(0xe55b),
    Euro(0xea15),
    Share(0xe80d),
    Delete(0xe92e),
    Search(0xef7a),
    HourglassEmpty(0xe88b),
    Sync(0xe627),
    PowerSettingsNew(0xf8c7),
    SettingsInputHdmi(0xe8c2),
    Build(0xf8cd),
    Handyman(0xf10b),
    AvgPace(0xf6bb),
    TrendingUp(0xe8e5),
    Wifi(0xe63e),
    LocationOn(0xf1db),
    Backup(0xe864),
    Update(0xe923),
    Favorite(0xe87e),
    CarRepair(0xea56),
    AutoAwesome(0xe65f),
    Colors(0xe997),
    Close(0xe5cd),
    Check(0xe668),
    ExpandMore(0xe5cf),
    Eco(0xea35),
    Timer(0xe425),
    Analytics(0xef3e);

    val char: String get() = String(Character.toChars(codepoint))
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun symbols(fill: Float) = FontFamily(
    Font(R.font.material_symbols_rounded, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", fill))),
)

private val Outlined = symbols(0f)
private val Filled = symbols(1f)

/** Icône Material Symbols : contour pour l'état inactif, pleine pour l'état actif. */
@Composable
fun Symbol(
    sym: Sym,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Box(
        modifier.size(size).then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            sym.char,
            style = TextStyle(
                fontFamily = if (filled) Filled else Outlined,
                fontSize = sp,
                lineHeight = sp,
                color = tint,
                textAlign = TextAlign.Center,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}
