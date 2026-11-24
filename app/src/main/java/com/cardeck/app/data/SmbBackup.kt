package com.cardeck.app.data

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.auth.NtlmAuthenticator
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Serveur SMB (NAS, PC Windows, Samba…) où CarDeck envoie ses sauvegardes : [host] et [port], [share] (partage), [folder]
 * (dossier dans le partage, vide = la racine), compte de l'utilisateur et, au besoin, [domain].
 */
data class SmbServer(
    val host: String,
    val port: Int = 445,
    val share: String,
    val folder: String = "CarDeck",
    val user: String,
    val password: String = "",
    val domain: String = "",
) {
    /** Chemin du dossier dans le partage, séparé par des « \ » comme l'attend SMB. */
    val folderPath: String get() = folder.split('/', '\\').filter { it.isNotEmpty() }.joinToString("\\")

    /** Chemin d'un fichier de ce dossier. */
    fun pathOf(name: String): String = if (folderPath.isEmpty()) name else "$folderPath\\$name"

    /** Texte affiché pour désigner le serveur (sans le mot de passe). */
    val label: String get() = "\\\\$host" + (if (port != 445) ":$port" else "") + "\\$share" + (if (folderPath.isEmpty()) "" else "\\$folderPath")

    override fun toString() = "SmbServer($label, user=$user)" // jamais le mot de passe dans un journal

    companion object {
        private val HOST = Regex("^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$")
        private val BAD_NAME = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

        /**
         * Découpe une adresse collée telle quelle : « nas », « nas:4455 », « smb://nas/Sauvegardes/CarDeck », « \\nas\Sauvegardes ».
         * Renvoie le serveur, le port, et le partage et le dossier s'ils figuraient dans l'adresse.
         */
        class Address(val host: String, val port: Int?, val share: String?, val folder: String?)

        fun parseAddress(raw: String): Address {
            var s = raw.trim()
            s = s.removePrefix("smb://").removePrefix("SMB://")
            val parts = s.split('/', '\\').filter { it.isNotEmpty() }
            val first = parts.firstOrNull().orEmpty()
            val colon = first.lastIndexOf(':')
            val port = if (colon > 0) first.substring(colon + 1).toIntOrNull() else null
            val host = if (colon > 0 && port != null) first.substring(0, colon) else first
            return Address(host, port, parts.getOrNull(1), parts.drop(2).joinToString("/").ifEmpty { null })
        }

        /** Résultat de [build] : le serveur, ou la première erreur de saisie. */
        class Built(val server: SmbServer?, val error: String?)

        /**
         * Transforme la saisie en serveur. Le serveur peut être collé avec son partage et son dossier (« smb://nas/Sauvegardes/CarDeck »).
         * Mot de passe laissé vide : celui du serveur déjà enregistré [saved] est gardé s'il s'agit du même serveur et du même compte.
         */
        fun build(hostText: String, shareText: String, folderText: String, user: String, password: String, domain: String, saved: SmbServer?): Built {
            val a = parseAddress(hostText)
            // Le champ du partage accepte un chemin collé (« //Stockage/Documents/Backup ») : le premier élément est le partage,
            // le reste est le dossier, qui l'emporte alors sur le champ du dossier.
            val shareParts = shareText.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }
            val share = shareParts.firstOrNull() ?: a.share.orEmpty()
            val folder = shareParts.drop(1).joinToString("/").ifEmpty {
                if (shareText.isBlank() && a.folder != null) a.folder else folderText.trim()
            }
            problem(a.host, a.port, share, folder, user.trim())?.let { return Built(null, it) }
            val keep = saved?.takeIf { password.isEmpty() && it.host.equals(a.host, true) && it.user == user.trim() }?.password.orEmpty()
            return Built(SmbServer(a.host, a.port ?: 445, share, folder, user.trim(), password.ifEmpty { keep }, domain.trim()), null)
        }

        /** Dossier proposé par défaut. */
        const val DEFAULT_FOLDER = "CarDeck"

        /** Première erreur de saisie, en français, ou null si tout est correct. */
        fun problem(host: String, port: Int?, share: String, folder: String, user: String): String? {
            if (host.isBlank()) return "Indiquez l'adresse du serveur (ex. 192.168.1.20 ou nas)"
            if (!HOST.matches(host)) return "Adresse du serveur incorrecte : seulement le nom ou l'adresse IP (ex. 192.168.1.20)"
            if (port != null && port !in 1..65535) return "Port incorrect (1 à 65535)"
            if (share.isBlank()) return "Indiquez le nom du partage"
            if (BAD_NAME.containsMatchIn(share) || share == "." || share == "..") return "Nom du partage incorrect"
            val segs = folder.split('/', '\\').filter { it.isNotEmpty() }
            if (segs.size > 8 || segs.any { it == "." || it == ".." || BAD_NAME.containsMatchIn(it) || it.length > 80 }) return "Dossier incorrect"
            if (user.isBlank()) return "Indiquez le nom d'utilisateur"
            return null
        }
    }
}

/** Une sauvegarde trouvée sur le serveur. */
class SmbBackupFile(val name: String, val size: Long, val modified: Long)

/** Erreur de CarDeck (et non du réseau) : message déjà lisible. */
class SmbProblem(message: String) : Exception(message)

/**
 * Envoi et relecture des sauvegardes sur un serveur SMB (SMB 2 et 3 seulement : le SMB 1, obsolète et dangereux, n'est pas géré).
 * La signature des échanges est exigée : un faux serveur ou un appareil du réseau ne peut ni altérer ni se faire passer pour le vrai.
 * À appeler hors du fil principal.
 */
object SmbBackup {
    private const val EXT = ".cdbak"

    /** Taille maximale d'une sauvegarde relue depuis le serveur (comme la limite de lecture d'une sauvegarde). */
    const val MAX_DOWNLOAD = 2L * 1024 * 1024 * 1024

    private fun config(): SmbConfig = SmbConfig.builder()
        .withTimeout(20, TimeUnit.SECONDS)
        .withSoTimeout(20, TimeUnit.SECONDS)
        .withSigningRequired(true)
        .withDfsEnabled(false)
        // NTLM seulement : Kerberos (GSS-API) n'existe pas sur Android, et un compte local ou de NAS s'identifie en NTLM.
        .withAuthenticators(NtlmAuthenticator.Factory())
        .build()

    private inline fun <T> withShare(s: SmbServer, crossinline block: (DiskShare) -> T): T =
        SMBClient(config()).use { client ->
            client.connect(s.host, s.port).use { connection ->
                val auth = AuthenticationContext(s.user, s.password.toCharArray(), s.domain)
                connection.authenticate(auth).use { session ->
                    val share = session.connectShare(s.share) as? DiskShare ?: throw SmbProblem("« ${s.share} » n'est pas un partage de fichiers")
                    share.use { block(it) }
                }
            }
        }

    /** Nom d'une sauvegarde sur le serveur : sans chemin, avec l'extension de CarDeck. */
    internal fun safeName(name: String): Boolean =
        name.endsWith(EXT) && name.length > EXT.length && name.length <= 120 && name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 } && !name.startsWith(".")

    private fun ensureFolder(share: DiskShare, s: SmbServer) {
        var path = ""
        for (seg in s.folderPath.split('\\').filter { it.isNotEmpty() }) {
            path = if (path.isEmpty()) seg else "$path\\$seg"
            if (!share.folderExists(path)) share.mkdir(path)
        }
    }

    /**
     * Essai de connexion : s'identifie, ouvre le partage, crée le dossier au besoin et vérifie qu'on peut y écrire.
     * Renvoie le nombre de sauvegardes déjà présentes.
     */
    fun test(s: SmbServer): Int = withShare(s) { share ->
        ensureFolder(share, s)
        val probe = s.pathOf(".cardeck-test")
        share.openFile(probe, EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null).use { f ->
            f.outputStream.use { it.write("CarDeck".toByteArray()) }
        }
        share.rm(probe)
        list(share, s).size
    }

    private fun list(share: DiskShare, s: SmbServer): List<SmbBackupFile> {
        if (s.folderPath.isNotEmpty() && !share.folderExists(s.folderPath)) return emptyList()
        return share.list(s.folderPath, "*$EXT")
            .filter { it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value == 0L && safeName(it.fileName) }
            .map { SmbBackupFile(it.fileName, it.endOfFile, it.lastWriteTime.toEpochMillis()) }
            .sortedByDescending { it.modified }
            .take(100)
    }

    /** Sauvegardes présentes sur le serveur, la plus récente d'abord. */
    fun list(s: SmbServer): List<SmbBackupFile> = withShare(s) { list(it, s) }

    /** Envoie [local] sur le serveur sous le nom [name] ; vérifie la taille reçue. */
    fun upload(s: SmbServer, local: File, name: String) {
        if (!safeName(name)) throw SmbProblem("Nom de fichier incorrect")
        withShare(s) { share ->
            ensureFolder(share, s)
            val path = s.pathOf(name)
            try {
                share.openFile(path, EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null).use { f ->
                    f.outputStream.use { out -> local.inputStream().use { it.copyTo(out, 64 * 1024) } }
                }
                val sent = share.getFileInformation(path).standardInformation.endOfFile
                if (sent != local.length()) throw SmbProblem("Le serveur n'a pas reçu tout le fichier ($sent sur ${local.length()} octets)")
            } catch (e: Exception) {
                // Un fichier incomplet sur le serveur serait pris pour une vraie sauvegarde.
                runCatching { share.rm(path) }
                throw e
            }
        }
    }

    /** Télécharge la sauvegarde [name] du serveur dans [out] (refusée au-delà de [maxBytes]). */
    fun download(s: SmbServer, name: String, out: File, maxBytes: Long = MAX_DOWNLOAD) {
        if (!safeName(name)) throw SmbProblem("Nom de fichier incorrect")
        withShare(s) { share ->
            share.openFile(s.pathOf(name), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { f ->
                val size = f.fileInformation.standardInformation.endOfFile
                if (size > maxBytes) throw SmbProblem("Fichier anormalement volumineux : téléchargement refusé")
                var total = 0L
                f.inputStream.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw SmbProblem("Fichier anormalement volumineux : téléchargement refusé")
                            o.write(buf, 0, n)
                        }
                    }
                }
                if (total != size) throw SmbProblem("Téléchargement incomplet ($total sur $size octets)")
            }
        }
    }

    /** Message lisible pour une erreur du serveur ou du réseau. */
    fun describe(e: Throwable): String {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 8) {
            when (t) {
                is SmbProblem -> return t.message ?: "Erreur du serveur"
                is SMBApiException -> return when (t.status) {
                    NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED -> "Utilisateur ou mot de passe refusé par le serveur"
                    NtStatus.STATUS_ACCOUNT_DISABLED -> "Ce compte est désactivé sur le serveur"
                    NtStatus.STATUS_PASSWORD_EXPIRED -> "Le mot de passe de ce compte a expiré"
                    NtStatus.STATUS_BAD_NETWORK_NAME -> "Partage introuvable sur le serveur : vérifiez son nom"
                    NtStatus.STATUS_ACCESS_DENIED -> "Accès refusé : ce compte n'a pas le droit d'écrire dans ce dossier"
                    NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND -> "Fichier ou dossier introuvable sur le serveur"
                    NtStatus.STATUS_DISK_FULL -> "Le serveur n'a plus de place"
                    NtStatus.STATUS_SHARING_VIOLATION -> "Fichier utilisé par un autre programme sur le serveur"
                    else -> "Le serveur a refusé : ${t.status.name.removePrefix("STATUS_").lowercase().replace('_', ' ')}"
                }
                is UnknownHostException -> return "Serveur introuvable : vérifiez son nom ou son adresse"
                is ConnectException, is NoRouteToHostException -> return "Serveur injoignable : est-il allumé, et le téléphone est-il sur le même réseau (Wi-Fi ou VPN) ?"
                is SocketTimeoutException -> return "Le serveur ne répond pas (délai dépassé)"
            }
            t = t.cause
        }
        val msg = e.message.orEmpty()
        return when {
            msg.contains("sign", ignoreCase = true) -> "Le serveur ne gère pas la signature SMB, exigée par CarDeck pour protéger vos données"
            e is IOException || e is com.hierynomus.protocol.transport.TransportException -> "Échange avec le serveur impossible" + if (msg.isNotBlank()) " ($msg)" else ""
            else -> msg.ifBlank { "Erreur du serveur" }
        }
    }
}
