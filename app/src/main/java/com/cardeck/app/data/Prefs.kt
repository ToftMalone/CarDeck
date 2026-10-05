package com.cardeck.app.data

import android.content.Context
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ThemeMode

/** Préférences locales (SharedPreferences). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cardeck", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("themeMode", null)!!) }.getOrDefault(ThemeMode.Dark)
        set(v) = sp.edit().putString("themeMode", v.name).apply()

    var palette: Palette
        get() = runCatching { Palette.valueOf(sp.getString("palette", null)!!) }.getOrDefault(Palette.Bleu)
        set(v) = sp.edit().putString("palette", v.name).apply()

    var activeVehicleId: Long
        get() = sp.getLong("activeVehicle", -1L)
        set(v) = sp.edit().putLong("activeVehicle", v).apply()
}
