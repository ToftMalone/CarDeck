package com.cardeck.app.data

import android.content.Context
import android.database.Cursor
import com.cardeck.app.BuildConfig
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Sauvegarde complète de l'application (véhicules, trajets, diagnostics, codages d'origine, préférences),
 * chiffrée par [BackupCrypto] : seule CarDeck peut la relire.
 */
object Backup {
    private const val FORMAT = 1

    /** Tables dans l'ordre de restauration (parents avant enfants). */
    private val TABLES = listOf("vehicles", "trips", "trip_points", "trip_samples", "scans", "scan_codes", "coding_backups")

    class Summary(val vehicles: Int, val trips: Int)

    /** Clés connues : la clé officielle (versions publiées) et la clé de test (versions de développement). */
    private fun keys(): Map<Int, ByteArray> = buildMap {
        put(BackupCrypto.KEY_TEST, BackupCrypto.keyFromSecret("CarDeck test"))
        if (BuildConfig.BACKUP_KEY.isNotEmpty()) put(BackupCrypto.KEY_OFFICIAL, BuildConfig.BACKUP_KEY.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    }

    /** Les versions de développement chiffrent avec la clé de test (pas de clé officielle dans le dépôt public). */
    val official: Boolean get() = BuildConfig.BACKUP_KEY.isNotEmpty()

    fun write(ctx: Context, repo: Repo, out: OutputStream): Summary {
        val keyId = if (official) BackupCrypto.KEY_OFFICIAL else BackupCrypto.KEY_TEST
        val d = DataOutputStream(GZIPOutputStream(BackupCrypto.encrypt(keys().getValue(keyId), keyId, out), 64 * 1024).buffered(64 * 1024))
        d.use {
            d.writeUTF("CarDeck"); d.writeInt(FORMAT); d.writeInt(DB_VERSION); d.writeLong(System.currentTimeMillis()); d.writeUTF(BuildConfig.VERSION_NAME)
            val prefs = Prefs(ctx).raw.all.filterValues { it != null }
            d.writeInt(prefs.size)
            prefs.forEach { (k, v) -> d.writeUTF(k); writeValue(d, v) }
            val db = repo.sql
            d.writeInt(TABLES.size)
            for (t in TABLES) {
                d.writeUTF(t)
                db.rawQuery("SELECT * FROM $t", null).use { c ->
                    d.writeInt(c.columnCount)
                    c.columnNames.forEach { d.writeUTF(it) }
                    while (c.moveToNext()) {
                        d.writeBoolean(true)
                        for (i in 0 until c.columnCount) writeValue(d, cell(c, i))
                    }
                    d.writeBoolean(false)
                }
            }
        }
        return Summary(repo.vehicles.value.size, count(repo, "trips"))
    }

    /** Remplace toutes les données par celles de la sauvegarde ; rien n'est modifié si elle est invalide ou endommagée. */
    fun read(ctx: Context, repo: Repo, input: InputStream): Summary {
        val d = DataInputStream(GZIPInputStream(BackupCrypto.decrypt(keys(), input), 64 * 1024).buffered(64 * 1024))
        d.use {
            if (d.readUTF() != "CarDeck" || d.readInt() != FORMAT) throw BackupCrypto.BadBackup("Sauvegarde illisible")
            if (d.readInt() > DB_VERSION) throw BackupCrypto.BadBackup("Sauvegarde d'une version plus récente de CarDeck : mettez l'application à jour")
            d.readLong(); d.readUTF()
            val prefs = (0 until d.readInt()).associate { d.readUTF() to readValue(d) }
            val db = repo.sql
            db.beginTransaction()
            try {
                TABLES.asReversed().forEach { db.delete(it, null, null) }
                repeat(d.readInt()) {
                    val table = d.readUTF()
                    val cols = (0 until d.readInt()).map { d.readUTF() }
                    // Colonnes inconnues de ce schéma (sauvegarde plus ancienne ou table retirée) : ignorées.
                    val known = if (table in TABLES) db.rawQuery("PRAGMA table_info($table)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } } else emptySet()
                    val keep = cols.indices.filter { cols[it] in known }
                    val stmt = if (keep.isEmpty()) null else db.compileStatement(
                        "INSERT INTO $table(${keep.joinToString(",") { cols[it] }}) VALUES(${keep.joinToString(",") { "?" }})",
                    )
                    while (d.readBoolean()) {
                        val row = cols.map { readValue(d) }
                        stmt ?: continue
                        stmt.clearBindings()
                        keep.forEachIndexed { j, i ->
                            when (val v = row[i]) {
                                null -> stmt.bindNull(j + 1)
                                is Long -> stmt.bindLong(j + 1, v)
                                is Double -> stmt.bindDouble(j + 1, v)
                                is ByteArray -> stmt.bindBlob(j + 1, v)
                                else -> stmt.bindString(j + 1, v.toString())
                            }
                        }
                        stmt.executeInsert()
                    }
                    stmt?.close()
                }
                // Fin du fichier lue et authentifiée (dernier bloc chiffré, contrôle gzip) avant de valider quoi que ce soit.
                if (d.read() != -1) throw BackupCrypto.BadBackup("Sauvegarde illisible")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            val sp = Prefs(ctx).raw.edit().clear()
            prefs.forEach { (k, v) ->
                @Suppress("UNCHECKED_CAST")
                when (v) {
                    is Boolean -> sp.putBoolean(k, v)
                    is Int -> sp.putInt(k, v)
                    is Long -> sp.putLong(k, v)
                    is Float -> sp.putFloat(k, v)
                    is String -> sp.putString(k, v)
                    is Set<*> -> sp.putStringSet(k, v as Set<String>)
                }
            }
            sp.commit()
        }
        repo.afterRestore()
        return Summary(repo.vehicles.value.size, count(repo, "trips"))
    }

    private fun count(repo: Repo, table: String) = repo.sql.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun cell(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
        else -> c.getString(i)
    }

    // Valeurs typées : 0 nul, 1 entier long, 2 réel, 3 texte, 4 octets, 5 booléen, 6 entier, 7 flottant, 8 ensemble de textes.
    private fun writeValue(d: DataOutputStream, v: Any?) {
        when (v) {
            null -> d.writeByte(0)
            is Long -> { d.writeByte(1); d.writeLong(v) }
            is Double -> { d.writeByte(2); d.writeDouble(v) }
            is String -> { d.writeByte(3); bytes(d, v.toByteArray(Charsets.UTF_8)) }
            is ByteArray -> { d.writeByte(4); bytes(d, v) }
            is Boolean -> { d.writeByte(5); d.writeBoolean(v) }
            is Int -> { d.writeByte(6); d.writeInt(v) }
            is Float -> { d.writeByte(7); d.writeFloat(v) }
            is Set<*> -> { d.writeByte(8); d.writeInt(v.size); v.forEach { bytes(d, it.toString().toByteArray(Charsets.UTF_8)) } }
            else -> { d.writeByte(3); bytes(d, v.toString().toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun bytes(d: DataOutputStream, b: ByteArray) { d.writeInt(b.size); d.write(b) }

    private fun readBytes(d: DataInputStream): ByteArray {
        val n = d.readInt()
        if (n < 0 || n > 64 * 1024 * 1024) throw BackupCrypto.BadBackup("Sauvegarde illisible")
        return ByteArray(n).also { d.readFully(it) }
    }

    private fun readValue(d: DataInputStream): Any? = when (d.readByte().toInt()) {
        0 -> null
        1 -> d.readLong()
        2 -> d.readDouble()
        3 -> String(readBytes(d), Charsets.UTF_8)
        4 -> readBytes(d)
        5 -> d.readBoolean()
        6 -> d.readInt()
        7 -> d.readFloat()
        8 -> (0 until d.readInt()).map { String(readBytes(d), Charsets.UTF_8) }.toSet()
        else -> throw BackupCrypto.BadBackup("Sauvegarde illisible")
    }
}
