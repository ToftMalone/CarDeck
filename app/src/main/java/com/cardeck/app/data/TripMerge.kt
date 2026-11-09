package com.cardeck.app.data

/** Fusion de plusieurs trajets en un seul trajet continu. */
object TripMerge {
    /** Trajet fusionné ; [shifts] = décalage (ms) à appliquer aux horodatages de chaque trajet source, dans l'ordre de [sources]. */
    class Plan(val sources: List<Trip>, val merged: Trip, val shifts: List<Long>)

    /**
     * Les trajets, du plus ancien au plus récent, mis bout à bout : les pauses entre eux disparaissent, la durée est
     * la somme des durées de conduite et la vitesse moyenne se calcule dessus. Départ du premier, arrivée du dernier.
     * Le prix du carburant est la moyenne des prix des trajets, pondérée par leur distance.
     * Null s'il y a moins de deux trajets ou s'ils ne sont pas tous du même véhicule.
     */
    fun plan(trips: List<Trip>): Plan? {
        val s = trips.sortedBy { it.start }
        if (s.size < 2 || s.map { it.vehicleId }.distinct().size != 1) return null
        val driving = s.sumOf { it.end - it.start }
        val km = s.sumOf { it.distanceKm }
        val hours = driving / 3_600_000.0
        val priced = s.filter { it.fuelPrice != null && it.distanceKm > 0 }
        val price = priced.takeIf { it.isNotEmpty() }?.let { p -> p.sumOf { it.fuelPrice!! * it.distanceKm } / p.sumOf { it.distanceKm } }
        val info = when {
            price == null -> null
            priced.map { it.fuelPrice }.distinct().size == 1 -> priced.first().fuelPriceInfo
            else -> "moyenne des prix des trajets fusionnés"
        }
        var cursor = s.first().start
        val shifts = s.map { t -> (cursor - t.start).also { cursor += t.end - t.start } }
        val merged = Trip(
            id = 0, vehicleId = s.first().vehicleId, start = s.first().start, end = s.first().start + driving,
            distanceKm = km, avgKmh = if (hours > 0) km / hours else 0.0, maxKmh = s.maxOf { it.maxKmh },
            from = s.first().from, to = s.last().to,
            fuelType = s.firstNotNullOfOrNull { it.fuelType }, fuelPrice = price, fuelPriceInfo = info,
        )
        return Plan(s, merged, shifts)
    }
}
