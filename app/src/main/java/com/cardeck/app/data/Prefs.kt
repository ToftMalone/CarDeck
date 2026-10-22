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
        get() = runCatching { ThemeMode.valueOf(sp.getString("themeMode", null)!!) }.getOrDefault(ThemeMode.System)
        set(v) = sp.edit().putString("themeMode", v.name).apply()

    var palette: Palette
        get() = runCatching { Palette.valueOf(sp.getString("palette", null)!!) }.getOrDefault(Palette.Vert)
        set(v) = sp.edit().putString("palette", v.name).apply()

    /** Tableau de bord affiché : « standard » ou « sport ». */
    var dashMode: String
        get() = sp.getString("dashMode", "standard") ?: "standard"
        set(v) = sp.edit().putString("dashMode", v).apply()

    /** Ordre des cartes de l'écran d'accueil (identifiants séparés par des virgules). */
    var homeOrder: List<String>
        get() = sp.getString("homeOrder", null)?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("homeOrder", v.joinToString(",")).apply()

    /** Autorisation « vitesse du véhicule » déjà demandée par Android Auto. */
    var carSpeedAsked: Boolean
        get() = sp.getBoolean("carSpeedAsked", false)
        set(v) = sp.edit().putBoolean("carSpeedAsked", v).apply()

    /** Index du dernier message d'accueil affiché (pour ne pas répéter le même). */
    var lastGreeting: Int
        get() = sp.getInt("lastGreeting", -1)
        set(v) = sp.edit().putInt("lastGreeting", v).apply()

    var activeVehicleId: Long
        get() = sp.getLong("activeVehicle", -1L)
        set(v) = sp.edit().putLong("activeVehicle", v).apply()
}
