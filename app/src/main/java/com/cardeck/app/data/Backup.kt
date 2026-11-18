package com.cardeck.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.database.Cursor
import com.cardeck.app.BuildConfig
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Sauvegarde complète de l'application (véhicules, trajets, diagnostics, codages d'origine, préférences),
 * protégée au choix de l'utilisateur ([BackupLock]) : en clair, par mot de passe, ou liée à cet appareil.
 */
object Backup {
    private const val FORMAT = 1

    /** Tables dans l'ordre de restauration (parents avant enfants). */
    private val TABLES = listOf("vehicles", "trips", "trip_points", "trip_samples", "scans", "scan_codes", "coding_backups")

    /** [codingSkipped] : codages d'origine présents dans le fichier mais non restaurés (sauvegarde non liée à cet appareil). */
    class Summary(val vehicles: Int, val trips: Int, val codingSkipped: Int = 0)

    /**
     * Clé de l'application (sauvegardes de CarDeck 0.1 et mode « chiffrée par CarDeck ») : la clé officielle pour les versions
     * publiées, la clé de test pour les versions de développement. Une version publiée refuse la clé de test, publique dans le dépôt.
     */
    private fun legacyKeys(): Map<Int, ByteArray> =
        if (BuildConfig.BACKUP_KEY.isNotEmpty()) mapOf(BackupCrypto.KEY_OFFICIAL to BuildConfig.BACKUP_KEY.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        else mapOf(BackupCrypto.KEY_TEST to BackupCrypto.keyFromSecret("CarDeck test"))

    /** Protection « chiffrée par CarDeck » : clé officielle d'une version publiée, clé de test d'une version de développement. */
    fun appLock(): BackupLock.App = legacyKeys().let { k ->
        k[BackupCrypto.KEY_OFFICIAL]?.let { BackupLock.App(it, test = false) } ?: BackupLock.App(k.getValue(BackupCrypto.KEY_TEST), test = true)
    }

    /** Marque et modèle de l'appareil, tels qu'affichés à l'utilisateur. */
    fun deviceLabel(): String {
        val brand = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.titlecase() }
        val model = Build.MODEL.orEmpty().trim()
        return when {
            model.startsWith(brand, ignoreCase = true) -> model
            brand.isEmpty() -> model
            else -> "$brand $model"
        }.ifBlank { "cet appareil" }
    }

    /**
     * Secret propre à cet appareil, ou null s'il n'est pas disponible. Empreinte (SHA-256) de :
     * l'identifiant Android de l'appareil (ANDROID_ID : aléatoire, tiré à la première mise en route, propre à l'appareil et à
     * l'application, conservé à la désinstallation, renouvelé à la réinitialisation d'usine) et des caractéristiques matérielles
     * (fabricant, modèle, carte mère, processeur). Le secret ne quitte jamais l'appareil et n'est écrit nulle part.
     */
    @SuppressLint("HardwareIds")
    fun deviceSecret(ctx: Context): ByteArray? {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)?.trim().orEmpty()
        // Identifiant absent, ou valeur commune connue de certains appareils anciens ou bas de gamme : pas assez distinctif.
        if (id.length < 8 || id == "9774d56d682e549c" || id.all { it == '0' }) return null
        val parts = listOf("CarDeck appareil v1", id, Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.BOARD, Build.HARDWARE)
        return MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000") { it.orEmpty() }.toByteArray(Charsets.UTF_8))
    }

    /** Protection « liée à cet appareil », ou null si l'appareil ne fournit pas d'identifiant utilisable. */
    fun deviceLock(ctx: Context): BackupLock.Device? = deviceSecret(ctx)?.let { BackupLock.Device(it, deviceLabel()) }

    /** En-tête de la sauvegarde (mode de protection, appareil d'origine), sans rien déchiffrer. */
    fun header(input: InputStream): BackupHeader = input.use { BackupCrypto.header(it) }

    fun write(ctx: Context, repo: Repo, out: OutputStream, lock: BackupLock): Summary {
        val d = DataOutputStream(GZIPOutputStream(BackupCrypto.encrypt(lock, out), 64 * 1024).buffered(64 * 1024))
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

    /**
     * Remplace toutes les données par celles de la sauvegarde ; rien n'est modifié si elle est invalide, endommagée,
     * si le mot de passe est faux ou si elle est liée à un autre appareil.
     */
    fun read(ctx: Context, repo: Repo, input: InputStream, password: CharArray? = null): Summary {
        val (header, plain) = BackupCrypto.open(input, legacyKeys(), password, deviceSecret(ctx))
        // Codages d'origine : réécrits tels quels dans la voiture à la demande. Seule une sauvegarde liée à cet appareil
        // garantit qu'ils viennent de CarDeck ; un fichier en clair ou à mot de passe peut avoir été fabriqué par un tiers.
        val trustedCoding = header.mode == BackupCrypto.MODE_DEVICE
        // Un fichier en clair n'a pas d'authentification : un fichier qui n'est pas du gzip est signalé simplement.
        val gz = try { GZIPInputStream(plain, 64 * 1024) } catch (_: java.util.zip.ZipException) { throw BackupCrypto.BadBackup("Sauvegarde illisible") }
        val d = DataInputStream(gz.buffered(64 * 1024))
        var codingSkipped = 0
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
                    val known = if (table in TABLES && (table != "coding_backups" || trustedCoding)) db.rawQuery("PRAGMA table_info($table)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } } else emptySet()
                    val keep = cols.indices.filter { cols[it] in known }
                    val stmt = if (keep.isEmpty()) null else db.compileStatement(
                        "INSERT INTO $table(${keep.joinToString(",") { cols[it] }}) VALUES(${keep.joinToString(",") { "?" }})",
                    )
                    while (d.readBoolean()) {
                        val row = cols.map { readValue(d) }
                        if (stmt == null) {
                            if (table == "coding_backups") codingSkipped++
                            continue
                        }
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
                purgeRemovedPids(db)
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
        return Summary(repo.vehicles.value.size, count(repo, "trips"), codingSkipped)
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
