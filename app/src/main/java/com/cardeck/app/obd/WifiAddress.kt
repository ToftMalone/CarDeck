package com.cardeck.app.obd

/**
 * Adresse d'un boîtier OBD2 Wi-Fi : seulement une adresse IPv4 de réseau local (le boîtier crée son propre Wi-Fi,
 * en général 192.168.0.10). Un nom de domaine ou une adresse publique est refusé : sinon une configuration piégée
 * (sauvegarde partagée, par exemple) ferait se connecter CarDeck à un serveur sur Internet à chaque utilisation.
 */
object WifiAddress {
    /**
     * Octets d'une adresse IPv4 écrite en décimal (« 192.168.0.10 »), ou null. Pas de zéro en tête (« 172.016.0.1 ») :
     * certains analyseurs le lisent en octal (172.14.0.1, une adresse publique) ou le résolvent par le DNS.
     */
    internal fun ipv4(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        return parts.map { p ->
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' } || (p.length > 1 && p[0] == '0')) return null
            p.toInt().takeIf { it <= 255 } ?: return null
        }
    }

    /** Adresse réseau construite directement à partir des octets : aucune analyse ni résolution DNS par le système. */
    fun inet(host: String): java.net.InetAddress? =
        ipv4(host.trim())?.takeIf { isLocal(host) }?.let { b -> java.net.InetAddress.getByAddress(ByteArray(4) { b[it].toByte() }) }

    /** Vrai si [host] est une adresse IPv4 privée (10/8, 172.16/12, 192.168/16) ou locale de lien (169.254/16). */
    fun isLocal(host: String): Boolean {
        val b = ipv4(host.trim()) ?: return false
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254)
    }

    /** Hôte et port d'une adresse « hôte:port » (port 35000 par défaut), ou null si elle n'est pas valable. */
    fun parse(address: String): Pair<String, Int>? {
        val host = address.substringBefore(':').trim()
        val port = address.substringAfter(':', "35000").trim().toIntOrNull() ?: return null
        if (port !in 1..65535 || !isLocal(host)) return null
        return host to port
    }

    const val REFUSED = "Adresse refusée : un boîtier Wi-Fi a une adresse de réseau local (ex. 192.168.0.10)."
}
