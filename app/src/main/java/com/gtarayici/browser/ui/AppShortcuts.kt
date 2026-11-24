package com.gtarayici.browser.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R

/** Uygulama simgesine uzun basınca çıkan kısayollar: yeni sekme, gizli sekme, QR kod, VPN. */
object AppShortcuts {
    fun publish(context: Context) {
        fun shortcut(id: String, action: String, label: Int, icon: Int) =
            ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(context.getString(label))
                .setIcon(IconCompat.createWithResource(context, icon))
                .setIntent(Intent(context, MainActivity::class.java).setAction(action))
                .build()
        val list = listOf(
            shortcut("new_tab", MainActivity.ACTION_NEW_TAB, R.string.menu_new_tab, R.drawable.ic_shortcut_add),
            shortcut("private_tab", MainActivity.ACTION_NEW_PRIVATE_TAB, R.string.menu_new_private_tab, R.drawable.ic_shortcut_private),
            shortcut("scan_qr", MainActivity.ACTION_SCAN_QR, R.string.menu_scan_qr, R.drawable.ic_shortcut_qr),
            shortcut("vpn", MainActivity.ACTION_OPEN_VPN, R.string.menu_vpn, R.drawable.ic_stat_vpn),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}
