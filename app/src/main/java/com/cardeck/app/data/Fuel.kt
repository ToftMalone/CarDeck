package com.cardeck.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Carburants vendus en France : [field] = nom du champ dans les prix publiés par l'État (prix-carburants.gouv.fr),
 * [afr] = rapport air/carburant stœchiométrique, [density] = masse volumique (g/L), pour estimer la consommation à partir du débit d'air.
 */
enum class FuelType(val label: String, val field: String, val afr: Double, val density: Double) {
    E10("SP95-E10", "e10", 14.1, 750.0),
    SP95("SP95", "sp95", 14.7, 745.0),
    SP98("SP98", "sp98", 14.7, 745.0),
    E85("E85", "e85", 9.8, 785.0),
    Gazole("Gazole", "gazole", 14.5, 832.0),
    GPLc("GPLc", "gplc", 15.5, 540.0),
    ;

    companion object {
        fun of(name: String?): FuelType? = entries.find { it.name == name }
    }
}

/** Consommation d'un trajet, calculée à partir des données moteur enregistrées. */
object TripFuel {
    /** Au-delà, l'écart entre deux mesures est un trou (liaison perdue) : il est comblé au débit moyen. */
    private const val MAX_GAP_MS = 10_000L

    /**
     * Litres consommés : débit de carburant (PID 5E, L/h) si le véhicule le fournit, sinon débit d'air (MAF, g/s) ÷ rapport
     * air/carburant ÷ masse volumique. Intégration par trapèzes ; null sans données exploitables.
     */
    fun liters(samples: Map<String, List<Sample>>, fuel: FuelType): Double? {
        val direct = samples["std5E"].orEmpty()
        val maf = samples["std10"].orEmpty()
        val (s, perSecond) = when {
            direct.size >= 2 -> direct to { v: Double -> v / 3600 }
            maf.size >= 2 -> maf to { v: Double -> v / fuel.afr / fuel.density }
            else -> return null
        }
        var liters = 0.0
        var covered = 0L
        for (i in 1 until s.size) {
            val dt = s[i].t - s[i - 1].t
            if (dt <= 0 || dt > MAX_GAP_MS) continue
            liters += (perSecond(s[i - 1].v) + perSecond(s[i].v)) / 2 * dt / 1000.0
            covered += dt
        }
        if (covered == 0L) return null
        return liters * (s.last().t - s.first().t) / covered
    }
}

/** Prix d'un carburant : [perLiter] en €/L, [source] = d'où vient le prix (stations proches ou moyenne nationale). */
data class FuelPrice(val perLiter: Double, val source: String)

/** Prix des carburants en temps réel : données ouvertes du ministère de l'Économie (mises à jour toutes les 10 minutes). */
object FuelPrices {
    private const val API = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records"

    /** Prix médian des stations à moins de 10 km (puis 30 km) de la position, sinon moyenne nationale. */
    fun fetch(fuel: FuelType, lat: Double?, lon: Double?): FuelPrice {
        val f = "${fuel.field}_prix"
        if (lat != null && lon != null) {
            for (km in listOf(10, 30)) {
                val where = "within_distance(geom, geom'POINT(${"%.5f".format(java.util.Locale.US, lon)} ${"%.5f".format(java.util.Locale.US, lat)})', ${km}km) AND $f IS NOT NULL"
                val res = get("select=$f&where=${enc(where)}&limit=100").optJSONArray("results") ?: continue
                val prices = (0 until res.length()).mapNotNull { res.getJSONObject(it).optDouble(f).takeIf { p -> p.isFinite() && p > 0 } }.sorted()
                if (prices.isEmpty()) continue
                val median = if (prices.size % 2 == 1) prices[prices.size / 2] else (prices[prices.size / 2 - 1] + prices[prices.size / 2]) / 2
                val n = prices.size
                return FuelPrice(median, "prix médian de $n station${if (n > 1) "s" else ""} à moins de $km km de l'arrivée")
            }
        }
        val avg = get("select=${enc("avg($f) as prix")}&where=${enc("$f IS NOT NULL")}&limit=1").optJSONArray("results")?.optJSONObject(0)?.optDouble("prix")
        if (avg == null || !avg.isFinite() || avg <= 0) throw java.io.IOException("Prix du ${fuel.label} indisponible")
        return FuelPrice(avg, "moyenne nationale")
    }

    /** Fixe le prix d'un trajet au prix actuel, près de son point d'arrivée (requête réseau). */
    fun priceTrip(repo: Repo, trip: Trip, fuel: FuelType): Trip {
        val end = repo.points(trip.id).lastOrNull()
        val p = fetch(fuel, end?.lat, end?.lon)
        repo.setTripFuel(trip.id, fuel, p)
        return trip.copy(fuelType = fuel, fuelPrice = p.perLiter, fuelPriceInfo = p.source)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun get(query: String): JSONObject {
        val c = URL("$API?$query").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "CarDeck")
        try {
            if (c.responseCode != 200) throw java.io.IOException("Service des prix indisponible (${c.responseCode})")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }
}
