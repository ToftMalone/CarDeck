package com.cardeck.app.data

import android.content.Context
import com.cardeck.app.ui.Palette
import com.cardeck.app.ui.ThemeMode

/** Préférences locales (SharedPreferences). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cardeck", Context.MODE_PRIVATE)

    /** Toutes les préférences, pour la sauvegarde et la restauration. */
    internal val raw get() = sp

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("themeMode", null)!!) }.getOrDefault(ThemeMode.System)
        set(v) = sp.edit().putString("themeMode", v.name).apply()

    var palette: Palette
        get() = runCatching { Palette.valueOf(sp.getString("palette", null)!!) }.getOrDefault(Palette.Rose)
        set(v) = sp.edit().putString("palette", v.name).apply()

    /** Couleur dynamique (Material You), Android 12 et plus. */
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamicColor", true)
        set(v) = sp.edit().putBoolean("dynamicColor", v).apply()

    /** Retours haptiques. */
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Unités : vitesse en mph, température en °F. */
    var imperialSpeed: Boolean
        get() = sp.getBoolean("unitMph", false)
        set(v) = sp.edit().putBoolean("unitMph", v).apply()

    var fahrenheit: Boolean
        get() = sp.getBoolean("unitF", false)
        set(v) = sp.edit().putBoolean("unitF", v).apply()

    /** Langue de l'application : "" = celle du système, sinon "en" ou "fr". */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) { sp.edit().putString("language", v).commit() }

    /** Outil favori d'un domaine (onglet « Favori » du dock). */
    fun favorite(domain: String): String? = sp.getString("fav_$domain", null)

    fun setFavorite(domain: String, tool: String) = sp.edit().putString("fav_$domain", tool).apply()

    /** Seuil d'alerte du compte-tours (tr/min). */
    var shiftRpm: Int
        get() = sp.getInt("shiftRpm", 6000)
        set(v) = sp.edit().putInt("shiftRpm", v).apply()

    /** Tableau de bord affiché : « standard » ou « sport ». */
    var dashMode: String
        get() = sp.getString("dashMode", "standard") ?: "standard"
        set(v) = sp.edit().putString("dashMode", v).apply()

    /** Index du dernier message d'accueil affiché (pour ne pas répéter le même). */
    var lastGreeting: Int
        get() = sp.getInt("lastGreeting", -1)
        set(v) = sp.edit().putInt("lastGreeting", v).apply()

    /** Carburant utilisé par un véhicule (coût des trajets), null = celui conseillé par le modèle. */
    fun fuel(vehicleId: Long): FuelType? = FuelType.of(sp.getString("fuel_$vehicleId", null))

    fun setFuel(vehicleId: Long, f: FuelType) = sp.edit().putString("fuel_$vehicleId", f.name).apply()

    /** Efface les anciens réglages qui n'existent plus (au lancement et après une restauration). */
    fun purgeObsolete() {
        val old = sp.all.keys.filter { it == "carSpeedAsked" || it == "homeOrder" || it.startsWith("wakeAa_") }
        if (old.isNotEmpty()) sp.edit().also { e -> old.forEach { e.remove(it) } }.apply()
    }

    /** Réveil d'un véhicule (Bluetooth coché + appareil « adresse|nom »). */
    fun wake(vehicleId: Long): WakeConfig {
        val dev = sp.getString("wakeBtDevice_$vehicleId", null)?.split('|', limit = 2)
        return WakeConfig(
            bluetooth = sp.getBoolean("wakeBt_$vehicleId", false),
            btAddress = dev?.getOrNull(0), btName = dev?.getOrNull(1),
        )
    }

    fun setWake(vehicleId: Long, w: WakeConfig) = sp.edit()
        .putBoolean("wakeBt_$vehicleId", w.bluetooth)
        .putString("wakeBtDevice_$vehicleId", w.btAddress?.let { "$it|${w.btName ?: it}" })
        .apply()

    /** Derniers PIDs déclarés par un véhicule (affichage des données hors connexion), null s'il n'a jamais été interrogé. */
    fun supported(vehicleId: Long): Set<Int>? =
        sp.getString("supported_$vehicleId", null)?.split(',')?.mapNotNull { it.toIntOrNull(16) }?.toSet()

    fun setSupported(vehicleId: Long, pids: Set<Int>) =
        sp.edit().putString("supported_$vehicleId", pids.sorted().joinToString(",") { "%02X".format(it) }).apply()

    var activeVehicleId: Long
        get() = sp.getLong("activeVehicle", -1L)
        set(v) = sp.edit().putLong("activeVehicle", v).apply()
}
