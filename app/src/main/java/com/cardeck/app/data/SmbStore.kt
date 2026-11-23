package com.cardeck.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Chiffre et déchiffre un petit secret (le mot de passe du serveur). */
interface SecretBox {
    fun seal(plain: String): String
    /** Texte d'origine, ou null si le secret ne peut plus être lu (clé perdue). */
    fun open(sealed: String): String?
}

/**
 * Secret chiffré (AES-256-GCM) avec une clé de l'Android Keystore : la clé reste dans la puce de sécurité ou le système,
 * elle ne peut pas être extraite, et n'est utilisable que par CarDeck. Le fichier chiffré ne sert à rien sur un autre téléphone.
 */
class KeystoreBox(private val alias: String = "cardeck_smb") : SecretBox {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = runCatching {
        val (iv, ct) = sealed.split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    fun delete() { runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) } }
}

/**
 * Serveur SMB enregistré. Fichier à part (« cardeck_smb »), jamais celui des réglages : ni l'adresse du serveur ni le mot de passe
 * ne sont écrits dans les sauvegardes, et une restauration ne les touche pas.
 */
class SmbStore(ctx: Context, private val box: SecretBox = KeystoreBox()) {
    private val sp = ctx.getSharedPreferences("cardeck_smb", Context.MODE_PRIVATE)

    fun load(): SmbServer? {
        val host = sp.getString("host", null) ?: return null
        val share = sp.getString("share", null) ?: return null
        val user = sp.getString("user", null) ?: return null
        val password = sp.getString("password", null)?.let { box.open(it) }.orEmpty()
        return SmbServer(
            host = host, port = sp.getInt("port", 445), share = share, folder = sp.getString("folder", "") ?: "",
            user = user, password = password, domain = sp.getString("domain", "") ?: "",
        )
    }

    /** Le mot de passe enregistré n'est plus lisible (clé du Keystore perdue) : à ressaisir. */
    fun passwordLost(): Boolean = sp.getString("password", null)?.let { it.isNotEmpty() && box.open(it) == null } ?: false

    fun save(s: SmbServer) {
        sp.edit()
            .putString("host", s.host).putInt("port", s.port).putString("share", s.share).putString("folder", s.folder)
            .putString("user", s.user).putString("domain", s.domain)
            .putString("password", if (s.password.isEmpty()) "" else box.seal(s.password))
            .commit()
    }

    fun clear() {
        sp.edit().clear().commit()
        (box as? KeystoreBox)?.delete()
    }
}
