package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.gtarayici.browser.R
import com.gtarayici.browser.ui.BrowserController

/** "Uygulamayı yükle": gerçek uygulama (WebAPK) veya basit ana ekran kısayolu. */
@Composable
fun InstallAppDialog(controller: BrowserController, installable: Boolean) {
    AlertDialog(
        onDismissRequest = { controller.installDialogOpen = false },
        title = { Text(stringResource(R.string.menu_install_app)) },
        text = {
            Column {
                ListRow(
                    leading = { Icon(Icons.Filled.InstallMobile, null) },
                    headline = { Text(stringResource(R.string.webapk_option_app)) },
                    supporting = {
                        Text(stringResource(if (installable) R.string.webapk_option_app_summary else R.string.webapk_option_app_summary_site))
                    },
                    modifier = Modifier.clickable { controller.installWebApk() },
                )
                ListRow(
                    leading = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, null) },
                    headline = { Text(stringResource(R.string.menu_add_to_home)) },
                    supporting = { Text(stringResource(R.string.webapk_option_shortcut_summary)) },
                    modifier = Modifier.clickable { controller.addToHomescreen() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { controller.installDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
