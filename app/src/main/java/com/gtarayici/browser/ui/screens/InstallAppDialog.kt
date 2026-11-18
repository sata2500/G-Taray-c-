package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
                ListItem(
                    leadingContent = { Icon(Icons.Filled.InstallMobile, null) },
                    headlineContent = { Text(stringResource(R.string.webapk_option_app)) },
                    supportingContent = {
                        Text(stringResource(if (installable) R.string.webapk_option_app_summary else R.string.webapk_option_app_summary_site))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { controller.installWebApk() },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Filled.AddToHomeScreen, null) },
                    headlineContent = { Text(stringResource(R.string.menu_add_to_home)) },
                    supportingContent = { Text(stringResource(R.string.webapk_option_shortcut_summary)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { controller.addToHomescreen() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { controller.installDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
