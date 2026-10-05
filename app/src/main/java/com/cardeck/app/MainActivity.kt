package com.cardeck.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cardeck.app.screens.AddVehicleScreen
import com.cardeck.app.screens.DashboardScreen
import com.cardeck.app.screens.DiagScreen
import com.cardeck.app.screens.DtcDetailScreen
import com.cardeck.app.screens.HomeScreen
import com.cardeck.app.screens.PairScreen
import com.cardeck.app.screens.SettingsScreen
import com.cardeck.app.screens.TripDetailScreen
import com.cardeck.app.screens.TripsScreen
import com.cardeck.app.screens.WelcomeScreen
import com.cardeck.app.ui.CarDeckTheme
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.T
import com.cardeck.app.ui.ThemeMode
import com.cardeck.app.service.TripService

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = when (vm.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CarDeckTheme(vm.palette, vm.themeMode) { CarDeckRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Suivi automatique des trajets du véhicule actif.
        if (vm.onboarded) TripService.start(this)
    }
}

@Composable
private fun CarDeckRoot(vm: AppViewModel) {
    val c = Cd.c
    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
    Box(Modifier.fillMaxSize().background(c.sf).windowInsetsPadding(WindowInsets.systemBars)) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = { fadeIn(tween(250, delayMillis = 60)) togetherWith fadeOut(tween(150)) },
            modifier = Modifier.fillMaxSize(),
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.Welcome -> WelcomeScreen(vm)
                Screen.Pair -> PairScreen(vm)
                Screen.AddVehicle -> AddVehicleScreen(vm)
                Screen.Home -> HomeScreen(vm)
                Screen.Dash -> DashboardScreen(vm)
                Screen.Diag -> DiagScreen(vm)
                is Screen.Dtc -> DtcDetailScreen(vm, screen.code)
                Screen.Trips -> TripsScreen(vm)
                is Screen.Trip -> TripDetailScreen(vm, screen.id)
                Screen.Settings -> SettingsScreen(vm)
            }
        }
        Snack(vm.snack, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Snack(message: String?, modifier: Modifier) {
    val c = Cd.c
    var last by remember { mutableStateOf("") }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null, modifier = modifier,
        enter = slideInVertically(tween(400)) { it * 2 } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it * 2 } + fadeOut(tween(200)),
    ) {
        Box(
            Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(c.isf).padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) { T(last, 14, c.ion) }
    }
}
