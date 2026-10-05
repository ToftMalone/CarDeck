package com.cardeck.app.data

import android.content.Context
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

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

    var widgets: Set<String>
        get() = sp.getStringSet("widgets", null) ?: DEFAULT_WIDGETS
        set(v) = sp.edit().putStringSet("widgets", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", true)
        set(v) = sp.edit().putBoolean("autoStart", v).apply()

    var autoStop: Boolean
        get() = sp.getBoolean("autoStop", true)
        set(v) = sp.edit().putBoolean("autoStop", v).apply()

    var blackbox: Boolean
        get() = sp.getBoolean("blackbox", false)
        set(v) = sp.edit().putBoolean("blackbox", v).apply()

    var hudMirror: Boolean
        get() = sp.getBoolean("hudMirror", true)
        set(v) = sp.edit().putBoolean("hudMirror", v).apply()

    var vehicleId: String
        get() = sp.getString("vehicleId", "308") ?: "308"
        set(v) = sp.edit().putString("vehicleId", v).apply()

    var deviceName: String
        get() = sp.getString("deviceName", "OBDLink MX+") ?: "OBDLink MX+"
        set(v) = sp.edit().putString("deviceName", v).apply()

    var vehicles: List<Vehicle>
        get() = runCatching {
            val arr = JSONArray(sp.getString("vehicles", null)!!)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Vehicle(o.getString("id"), o.getString("name"), o.getString("plate"), o.getString("year"), o.getString("fuel"))
            }
        }.getOrDefault(DEFAULT_VEHICLES)
        set(v) {
            val arr = JSONArray()
            v.forEach {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("plate", it.plate).put("year", it.year).put("fuel", it.fuel))
            }
            sp.edit().putString("vehicles", arr.toString()).apply()
        }
}
