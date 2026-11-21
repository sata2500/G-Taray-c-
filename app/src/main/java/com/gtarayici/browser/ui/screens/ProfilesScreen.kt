package com.gtarayici.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.gtarayici.browser.ui.designsystem.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.data.DEFAULT_PROFILE_ID
import com.gtarayici.browser.data.Profile
import com.gtarayici.browser.profiles.PROFILE_COLORS
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.components.colorOption
import com.gtarayici.browser.ui.designsystem.OverlayScaffold

fun profileColor(profile: Profile) = Color(PROFILE_COLORS[profile.colorIndex.mod(PROFILE_COLORS.size)])

@Composable
fun ProfileAvatar(profile: Profile, size: Dp = 32.dp, selected: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(profileColor(profile))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

@Composable
fun ProfilesScreen(controller: BrowserController, onBack: () -> Unit) {
    val components = LocalContext.current.components
    val profiles by components.profiles.profiles.collectAsState()
    val active by components.profiles.active.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }

    OverlayScaffold(
        title = stringResource(R.string.profiles_title),
        onBack = onBack,
        floatingActionButton = {
            AddFab(stringResource(R.string.profile_add)) { creating = true }
        },
    ) { modifier ->
        LazyColumn(modifier) {
            item {
                Text(
                    stringResource(R.string.profiles_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(profiles, key = { it.id }) { profile ->
                ListRow(
                    leading = { ProfileAvatar(profile, 40.dp, selected = profile.id == active.id) },
                    headline = { Text(profile.name) },
                    supporting = {
                        val parts = listOfNotNull(
                            stringResource(R.string.profile_active).takeIf { profile.id == active.id },
                            stringResource(R.string.profile_locked).takeIf { profile.locked },
                        )
                        if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (profile.locked) Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp))
                            IconButton(onClick = { editing = profile }) {
                                Icon(Icons.Filled.Edit, stringResource(R.string.profile_edit))
                            }
                        }
                    },
                    modifier = Modifier.clickable { controller.switchProfile(profile) },
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (creating) {
        ProfileEditor(
            initial = null,
            onSave = { name, color, locked ->
                creating = false
                val created = components.profiles.create(name, color, locked)
                controller.switchProfile(created)
            },
            onDelete = null,
            onDismiss = { creating = false },
        )
    }
    editing?.let { profile ->
        ProfileEditor(
            initial = profile,
            onSave = { name, color, locked ->
                editing = null
                components.profiles.update(profile.copy(name = name.ifBlank { profile.name }, colorIndex = color, locked = locked))
            },
            onDelete = if (profile.id != DEFAULT_PROFILE_ID) {
                {
                    editing = null
                    controller.deleteProfile(profile)
                }
            } else {
                null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ProfileEditor(
    initial: Profile?,
    onSave: (String, Int, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: (1 until PROFILE_COLORS.size).random()) }
    var locked by remember { mutableStateOf(initial?.locked ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.profile_add else R.string.profile_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILE_COLORS.forEachIndexed { index, value ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(value))
                                .colorOption(index, PROFILE_COLORS.size, index == color) { color = index },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index == color) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ListRow(
                    leading = { Icon(Icons.Filled.Fingerprint, null) },
                    headline = { Text(stringResource(R.string.profile_lock)) },
                    supporting = { Text(stringResource(R.string.profile_lock_summary)) },
                    trailing = { Switch(checked = locked, onCheckedChange = { locked = it }) },
                    modifier = Modifier.clickable { locked = !locked },
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.profile_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color, locked) }) { Text(stringResource(R.string.profile_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (confirmDelete && initial != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.profile_delete_confirm, initial.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Araç çubuğundaki profil simgesine dokununca açılan hızlı profil değiştirici. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(controller: BrowserController) {
    val components = LocalContext.current.components
    val profiles by components.profiles.profiles.collectAsState()
    val active by components.profiles.active.collectAsState()
    ModalBottomSheet(
        onDismissRequest = { controller.profileSwitcherOpen = false },
        sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState(),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.profile_switch),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            profiles.forEach { profile ->
                ListRow(
                    leading = { ProfileAvatar(profile, 36.dp, selected = profile.id == active.id) },
                    headline = { Text(profile.name) },
                    trailing = {
                        when {
                            profile.id == active.id -> Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            profile.locked -> Icon(Icons.Filled.Lock, null)
                        }
                    },
                    modifier = Modifier.clickable { controller.switchProfile(profile) },
                )
            }
            ListRow(
                leading = { Icon(Icons.Filled.Edit, null) },
                headline = { Text(stringResource(R.string.menu_profiles)) },
                modifier = Modifier.clickable {
                    controller.profileSwitcherOpen = false
                    controller.open(com.gtarayici.browser.ui.Overlay.PROFILES)
                },
            )
        }
    }
}

/** Kilitli profil veya gizli sekmeler için içeriği tamamen örten kilit ekranı. */
@Composable
fun LockScreen(title: String, lockKey: String, onBiometric: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lock = context.components.appLock
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val submit = {
        if (lock.unlockWithPassword(lockKey, password)) {
            error = null
        } else {
            password = ""
            val wait = lock.lockoutSeconds()
            error = if (wait > 0) resources.getString(R.string.lock_wait, wait) else resources.getString(R.string.lock_wrong_password)
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (lock.hasPassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    singleLine = true,
                    label = { Text(stringResource(R.string.lock_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { submit() }, enabled = password.isNotEmpty()) {
                    Text(stringResource(R.string.lock_unlock))
                }
            }
            if (lock.hasBiometrics(context) || !lock.hasPassword) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBiometric) {
                    Icon(Icons.Filled.Fingerprint, null)
                    Text(
                        stringResource(if (lock.hasPassword) R.string.lock_use_biometric else R.string.lock_unlock),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
