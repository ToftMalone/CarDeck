package com.cardeck.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "cardeck.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE vehicles(id INTEGER PRIMARY KEY AUTOINCREMENT, template TEXT NOT NULL, nickname TEXT NOT NULL,
               plate TEXT NOT NULL, year TEXT NOT NULL, vin TEXT, adapter_transport TEXT, adapter_address TEXT, adapter_name TEXT, created INTEGER NOT NULL)""",
        )
        db.execSQL(
            """CREATE TABLE trips(id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
               start INTEGER NOT NULL, end_t INTEGER, distance_km REAL NOT NULL DEFAULT 0, avg_kmh REAL NOT NULL DEFAULT 0, max_kmh REAL NOT NULL DEFAULT 0,
               score INTEGER NOT NULL DEFAULT 100, from_label TEXT NOT NULL DEFAULT '', to_label TEXT NOT NULL DEFAULT '')""",
        )
        db.execSQL("CREATE TABLE trip_points(trip_id INTEGER NOT NULL REFERENCES trips(id) ON DELETE CASCADE, t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, speed REAL NOT NULL)")
        db.execSQL("CREATE INDEX trip_points_trip ON trip_points(trip_id)")
        db.execSQL("CREATE TABLE trip_events(trip_id INTEGER NOT NULL REFERENCES trips(id) ON DELETE CASCADE, t INTEGER NOT NULL, type TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, g REAL NOT NULL)")
        db.execSQL(
            """CREATE TABLE scans(id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle_id INTEGER NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
               t INTEGER NOT NULL, ecus INTEGER NOT NULL, ff_code TEXT, ff_values TEXT)""",
        )
        db.execSQL("CREATE TABLE scan_codes(scan_id INTEGER NOT NULL REFERENCES scans(id) ON DELETE CASCADE, code TEXT NOT NULL, status TEXT NOT NULL, ecu TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

/** Accès aux données locales. Toutes les données sont rattachées à un profil de véhicule. */
class Repo(ctx: Context) {
    private val db = DbHelper(ctx).writableDatabase
    private val prefs = Prefs(ctx)

    private val _vehicles = MutableStateFlow(loadVehicles())
    val vehicles: StateFlow<List<VehicleProfile>> = _vehicles

    private val _activeId = MutableStateFlow(prefs.activeVehicleId)
    val activeId: StateFlow<Long> = _activeId

    /** Incrémenté à chaque modification des trajets / scans (pour rafraîchir l'interface). */
    val changes = MutableStateFlow(0L)

    val active: VehicleProfile?
        get() = _vehicles.value.find { it.id == _activeId.value } ?: _vehicles.value.firstOrNull()

    private fun bump() { changes.value = changes.value + 1 }

    // ---- Véhicules ----

    private fun loadVehicles(): List<VehicleProfile> = db.rawQuery("SELECT * FROM vehicles ORDER BY created", null).use { c ->
        buildList {
            while (c.moveToNext()) {
                val tr = c.str("adapter_transport")
                val adapter = if (tr != null) AdapterConfig(Transport.valueOf(tr), c.str("adapter_address") ?: "", c.str("adapter_name") ?: "") else null
                add(VehicleProfile(c.long("id"), c.str("template")!!, c.str("nickname")!!, c.str("plate")!!, c.str("year")!!, c.str("vin"), adapter))
            }
        }
    }

    private fun reloadVehicles() { _vehicles.value = loadVehicles() }

    fun addVehicle(templateId: String, nickname: String, plate: String, year: String): Long {
        val id = db.insertOrThrow("vehicles", null, ContentValues().apply {
            put("template", templateId); put("nickname", nickname); put("plate", plate); put("year", year); put("created", System.currentTimeMillis())
        })
        reloadVehicles()
        return id
    }

    fun setAdapter(vehicleId: Long, adapter: AdapterConfig, vin: String?) {
        db.update("vehicles", ContentValues().apply {
            put("adapter_transport", adapter.transport.name); put("adapter_address", adapter.address); put("adapter_name", adapter.name)
            if (vin != null) put("vin", vin)
        }, "id=?", arrayOf(vehicleId.toString()))
        reloadVehicles()
    }

    fun deleteVehicle(id: Long) {
        db.delete("vehicles", "id=?", arrayOf(id.toString()))
        reloadVehicles()
        if (_activeId.value == id) _vehicles.value.firstOrNull()?.let { select(it.id) }
        bump()
    }

    fun select(id: Long) {
        prefs.activeVehicleId = id
        _activeId.value = id
    }

    // ---- Trajets ----

    fun startTrip(vehicleId: Long, start: Long): Long =
        db.insertOrThrow("trips", null, ContentValues().apply { put("vehicle_id", vehicleId); put("start", start) })

    fun addPoints(tripId: Long, pts: List<TripPoint>) {
        db.beginTransaction()
        try {
            pts.forEach { p ->
                db.insert("trip_points", null, ContentValues().apply { put("trip_id", tripId); put("t", p.t); put("lat", p.lat); put("lon", p.lon); put("speed", p.speedKmh) })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addEvent(tripId: Long, e: TripEvent) {
        db.insert("trip_events", null, ContentValues().apply { put("trip_id", tripId); put("t", e.t); put("type", e.type.name); put("lat", e.lat); put("lon", e.lon); put("g", e.g) })
    }

    fun finishTrip(id: Long, end: Long, distanceKm: Double, avgKmh: Double, maxKmh: Double, score: Int, from: String, to: String) {
        db.update("trips", ContentValues().apply {
            put("end_t", end); put("distance_km", distanceKm); put("avg_kmh", avgKmh); put("max_kmh", maxKmh); put("score", score); put("from_label", from); put("to_label", to)
        }, "id=?", arrayOf(id.toString()))
        bump()
    }

    fun deleteTrip(id: Long) {
        db.delete("trips", "id=?", arrayOf(id.toString()))
        bump()
    }

    fun events(tripId: Long): List<TripEvent> = db.rawQuery("SELECT * FROM trip_events WHERE trip_id=? ORDER BY t", arrayOf(tripId.toString())).use { c ->
        buildList { while (c.moveToNext()) add(TripEvent(c.long("t"), EventType.valueOf(c.str("type")!!), c.dbl("lat"), c.dbl("lon"), c.dbl("g"))) }
    }

    fun points(tripId: Long): List<TripPoint> = db.rawQuery("SELECT * FROM trip_points WHERE trip_id=? ORDER BY t", arrayOf(tripId.toString())).use { c ->
        buildList { while (c.moveToNext()) add(TripPoint(c.long("t"), c.dbl("lat"), c.dbl("lon"), c.dbl("speed"))) }
    }

    private fun tripFrom(c: Cursor) = Trip(
        c.long("id"), c.long("vehicle_id"), c.long("start"), c.long("end_t"), c.dbl("distance_km"), c.dbl("avg_kmh"), c.dbl("max_kmh"),
        c.getInt(c.getColumnIndexOrThrow("score")), c.str("from_label") ?: "", c.str("to_label") ?: "", events(c.long("id")),
    )

    /** Trajets terminés du véhicule, du plus récent au plus ancien. */
    fun trips(vehicleId: Long): List<Trip> =
        db.rawQuery("SELECT * FROM trips WHERE vehicle_id=? AND end_t IS NOT NULL ORDER BY start DESC", arrayOf(vehicleId.toString())).use { c ->
            buildList { while (c.moveToNext()) add(tripFrom(c)) }
        }

    fun trip(id: Long): Trip? = db.rawQuery("SELECT * FROM trips WHERE id=? AND end_t IS NOT NULL", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) tripFrom(c) else null
    }

    /** Clôture les trajets restés ouverts (service arrêté brutalement). */
    fun recoverUnfinished() {
        val open = db.rawQuery("SELECT id, start FROM trips WHERE end_t IS NULL", null).use { c ->
            buildList { while (c.moveToNext()) add(c.long("id") to c.long("start")) }
        }
        open.forEach { (id, start) ->
            val pts = points(id)
            val dist = pathKm(pts)
            if (pts.size < 2 || dist < 0.2) { deleteTrip(id); return@forEach }
            val end = pts.last().t
            val hours = (end - start) / 3_600_000.0
            val evs = events(id)
            finishTrip(id, end, dist, if (hours > 0) dist / hours else 0.0, pts.maxOf { it.speedKmh }, scoreOf(evs), "Position inconnue", "Position inconnue")
        }
    }

    // ---- Diagnostics ----

    fun saveScan(vehicleId: Long, t: Long, ecus: Int, codes: List<FoundCode>, ffCode: String?, ff: List<String>?): Long {
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("scans", null, ContentValues().apply {
                put("vehicle_id", vehicleId); put("t", t); put("ecus", ecus); put("ff_code", ffCode); put("ff_values", ff?.joinToString("|"))
            })
            codes.forEach { fc ->
                db.insert("scan_codes", null, ContentValues().apply { put("scan_id", id); put("code", fc.code); put("status", fc.status.name); put("ecu", fc.ecu) })
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
            bump()
        }
    }

    fun lastScan(vehicleId: Long): DtcScan? = db.rawQuery("SELECT * FROM scans WHERE vehicle_id=? ORDER BY t DESC LIMIT 1", arrayOf(vehicleId.toString())).use { c ->
        if (!c.moveToFirst()) return null
        val id = c.long("id")
        val codes = db.rawQuery("SELECT * FROM scan_codes WHERE scan_id=?", arrayOf(id.toString())).use { k ->
            buildList { while (k.moveToNext()) add(FoundCode(k.str("code")!!, DtcStatus.valueOf(k.str("status")!!), k.str("ecu")!!)) }
        }
        DtcScan(id, vehicleId, c.long("t"), c.getInt(c.getColumnIndexOrThrow("ecus")), codes, c.str("ff_code"), c.str("ff_values")?.split("|"))
    }
}

private fun Cursor.str(n: String): String? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getString(it) }
private fun Cursor.long(n: String): Long = getLong(getColumnIndexOrThrow(n))
private fun Cursor.dbl(n: String): Double = getDouble(getColumnIndexOrThrow(n))

/** Distance (km) entre deux points GPS. */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

fun pathKm(pts: List<TripPoint>): Double = pts.zipWithNext { a, b -> haversineKm(a.lat, a.lon, b.lat, b.lon) }.sum()

/** Score de conduite : 100 moins une pénalité par événement détecté. */
fun scoreOf(events: List<TripEvent>): Int {
    val penalty = events.sumOf { e -> when (e.type) { EventType.Brake -> 6; EventType.Accel -> 4; EventType.Turn -> 3 }.toInt() }
    return (100 - penalty).coerceIn(0, 100)
}
