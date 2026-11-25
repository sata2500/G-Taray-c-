package com.gtarayici.browser.security

import com.gtarayici.browser.components
import com.gtarayici.browser.ui.feedback.UiText
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.R
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Uygulama kilidi. Kilitli profiller ve (isteğe bağlı) gizli sekmeler, uygulama her arka plana
 * gittiğinde yeniden kilitlenir.
 *
 * Açma yöntemleri:
 * - Uygulama parolası/PIN'i ayarlıysa: parola veya (varsa) parmak izi / yüz tanıma.
 * - Ayarlı değilse: parmak izi / yüz tanıma veya cihazın ekran kilidi (PIN, desen, şifre).
 *
 * Parola hiçbir zaman düz metin saklanmaz; PBKDF2 (SHA-256, 210.000 tur, rastgele tuz) özeti tutulur.
 */
class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("gtarayici_lock", Context.MODE_PRIVATE)

    /** Bu oturumda kilidi açılmış alanlar ("profile:<id>" veya "private"). */
    private val unlocked = mutableStateMapOf<String, Boolean>()

    /** Kimlik doğrulama ekranı açıkken uygulama arka plana geçmiş sayılmaz. */
    @Volatile var authenticating = false
        private set

    var hasPassword by mutableStateOf(prefs.contains(KEY_HASH))
        private set

    private var failedAttempts = 0
    private var lockedOutUntil = 0L

    fun isUnlocked(key: String): Boolean = unlocked[key] == true

    fun lockAll() = unlocked.clear()

    /** Tek bir alanı yeniden kilitler (ör. gizli sekmelerden çıkınca gizli sekmeler). */
    fun lock(key: String) {
        unlocked.remove(key)
    }

    fun hasBiometrics(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Herhangi bir kilit yöntemi kullanılabilir mi (parola, biyometri veya ekran kilidi)? */
    fun canLock(context: Context): Boolean = hasPassword || hasBiometrics(context) || deviceSecure(context)

    // --- Parola -------------------------------------------------------------------------

    fun setPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_SALT, salt.b64())
            putString(KEY_HASH, hash(password, salt).b64())
        }
        hasPassword = true
    }

    fun removePassword() {
        prefs.edit { remove(KEY_HASH).remove(KEY_SALT) }
        hasPassword = false
    }

    fun verifyPassword(password: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.unb64() ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.unb64() ?: return false
        return MessageDigest.isEqual(hash(password, salt), expected)
    }

    /** Kalan bekleme süresi (sn). Art arda 5 hatalı denemeden sonra 30 sn beklenir. */
    fun lockoutSeconds(): Int = ((lockedOutUntil - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0)

    fun unlockWithPassword(key: String, password: String): Boolean {
        if (lockoutSeconds() > 0) return false
        return if (verifyPassword(password)) {
            failedAttempts = 0
            unlocked[key] = true
            true
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                failedAttempts = 0
                lockedOutUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS
            }
            false
        }
    }

    // --- Biyometri ----------------------------------------------------------------------

    /**
     * Biyometrik doğrulama ister. Parola ayarlıysa yalnızca biyometri sorulur (iptal edilirse parola
     * alanı kullanılır). Hiçbir kilit yöntemi yoksa kullanıcı bilgilendirilir ve alan açılır.
     */
    fun authenticate(activity: FragmentActivity, key: String, title: String, onResult: (Boolean) -> Unit) {
        if (isUnlocked(key)) return onResult(true)
        val biometrics = hasBiometrics(activity)
        if (hasPassword && !biometrics) return onResult(false)
        if (!hasPassword && !biometrics && !deviceSecure(activity)) {
            activity.components.messenger.warning(UiText.res(R.string.lock_no_screen_lock))
            unlocked[key] = true
            return onResult(true)
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    unlocked[key] = true
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    onResult(false)
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.lock_subtitle))
        when {
            hasPassword -> builder
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText(activity.getString(R.string.lock_use_password))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            biometrics -> builder
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText(activity.getString(R.string.action_cancel))
            else -> @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
        }
        authenticating = true
        prompt.authenticate(builder.build())
    }

    private fun deviceSecure(context: Context) =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    private fun hash(password: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256))
            .encoded

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val PRIVATE = "private"
        private const val KEY_HASH = "password_hash"
        private const val KEY_SALT = "password_salt"
        private const val ITERATIONS = 210_000
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L
        const val MIN_PASSWORD_LENGTH = 4

        fun profileKey(id: String) = "profile:$id"
    }
}
