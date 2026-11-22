package com.gtarayici.browser.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Yedek dosyası okunamadı: biçim yanlış, parola gerekli ya da parola yanlış. */
class BackupException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION, PASSWORD_REQUIRED, WRONG_PASSWORD }
}

/**
 * Yedek dosyasının zarfı. İçerik bir JSON nesnesidir; parola verilirse PBKDF2-SHA256 (210.000 tur) ile türetilen
 * anahtarla AES-256-GCM şifrelenir. GCM, parola yanlışsa ya da dosya bozulmuşsa çözmeyi reddeder.
 */
object BackupCodec {
    const val FORMAT = "g-tarayici-yedek"
    const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    fun encode(content: JSONObject, password: String?, random: SecureRandom = SecureRandom()): String {
        val envelope = JSONObject().put("format", FORMAT).put("version", VERSION)
        if (password.isNullOrEmpty()) {
            return envelope.put("encrypted", false).put("data", content).toString()
        }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(FORMAT.toByteArray())
        val sealed = cipher.doFinal(content.toString().toByteArray(Charsets.UTF_8))
        return envelope
            .put("encrypted", true)
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", ITERATIONS)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(sealed))
            .toString()
    }

    /** Dosya şifreli mi (parola sorulmalı mı)? */
    fun isEncrypted(text: String): Boolean = envelope(text).optBoolean("encrypted", false)

    fun decode(text: String, password: String?): JSONObject {
        val envelope = envelope(text)
        if (!envelope.optBoolean("encrypted", false)) {
            return envelope.optJSONObject("data") ?: throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        }
        if (password.isNullOrEmpty()) throw BackupException(BackupException.Reason.PASSWORD_REQUIRED)
        return try {
            val salt = unb64(envelope.getString("salt"))
            val iv = unb64(envelope.getString("iv"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, envelope.optInt("iterations", ITERATIONS)), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(FORMAT.toByteArray())
            JSONObject(String(cipher.doFinal(unb64(envelope.getString("data"))), Charsets.UTF_8))
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupException.Reason.NOT_A_BACKUP, e)
        }
    }

    private fun envelope(text: String): JSONObject {
        val json = runCatching { JSONObject(text) }.getOrNull()
        if (json == null || json.optString("format") != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        if (json.optInt("version") > VERSION) throw BackupException(BackupException.Reason.NEWER_VERSION)
        return json
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String) = Base64.getDecoder().decode(text)
}
