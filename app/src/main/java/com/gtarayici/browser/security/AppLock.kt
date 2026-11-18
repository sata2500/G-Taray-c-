package com.gtarayici.browser.security

import android.app.KeyguardManager
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.R

/**
 * Biyometrik kilit. Kilitli profiller ve (isteğe bağlı) gizli sekmeler, uygulama her arka plana
 * gittiğinde yeniden kilitlenir ve parmak izi, yüz tanıma veya ekran kilidi ile açılır.
 */
class AppLock {
    /** Bu oturumda kilidi açılmış alanlar ("profile:<id>" veya "private"). */
    private val unlocked = mutableStateMapOf<String, Boolean>()

    /** Kimlik doğrulama ekranı açıkken uygulama arka plana geçmiş sayılmaz. */
    @Volatile var authenticating = false
        private set

    fun isUnlocked(key: String): Boolean = unlocked[key] == true

    fun lockAll() = unlocked.clear()

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.from(activity).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }

    /**
     * Kullanıcıdan kimlik doğrulaması ister. Cihazda ekran kilidi yoksa kilit uygulanamaz;
     * kullanıcı bilgilendirilir ve alan açılır (kullanıcının verisine erişimi engellenmez).
     */
    fun authenticate(activity: FragmentActivity, key: String, title: String, onResult: (Boolean) -> Unit) {
        if (isUnlocked(key)) return onResult(true)
        if (!canAuthenticate(activity)) {
            Toast.makeText(activity, R.string.lock_no_screen_lock, Toast.LENGTH_LONG).show()
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
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.lock_subtitle))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setAllowedAuthenticators(authenticators())
                } else {
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()
        authenticating = true
        prompt.authenticate(info)
    }

    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    companion object {
        const val PRIVATE = "private"

        fun profileKey(id: String) = "profile:$id"
    }
}
