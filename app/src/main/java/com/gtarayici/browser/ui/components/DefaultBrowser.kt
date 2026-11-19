package com.gtarayici.browser.ui.components

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri

/** G-Tarayıcı şu an varsayılan tarayıcı mı? */
fun isDefaultBrowser(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService<RoleManager>()?.let { return it.isRoleHeld(RoleManager.ROLE_BROWSER) }
    }
    val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}

/**
 * Varsayılan tarayıcı yapma isteği. Rol isteği penceresi startActivityForResult ile açılmazsa Android onu
 * sessizce kapatır; zaten varsayılansa veya rol isteği desteklenmiyorsa sistem ayarı açılır.
 * [onResult] sonucu (varsayılan mı) bildirir.
 */
@Composable
fun rememberDefaultBrowserRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult(isDefaultBrowser(context))
    }
    return {
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.getSystemService<RoleManager>() else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) && !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
        ) {
            launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
        } else {
            runCatching { launcher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                .onFailure { runCatching { launcher.launch(Intent(Settings.ACTION_SETTINGS)) } }
        }
    }
}
