package com.gtarayici.browser.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.passwords.LoginMatching
import com.gtarayici.browser.passwords.SavedLogin
import com.gtarayici.browser.search.TextMatch
import com.gtarayici.browser.ui.components.Favicon
import com.gtarayici.browser.ui.designsystem.AddFab
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.ListRow
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.designsystem.SectionCard
import com.gtarayici.browser.ui.designsystem.SectionHeader
import com.gtarayici.browser.ui.designsystem.SwitchRow
import com.gtarayici.browser.ui.feedback.UiText
import kotlinx.coroutines.launch

/** Parolalar ekranının kilit anahtarı: ekran her açılışta (uygulama arka plana gidince) yeniden kilitlenir. */
const val PASSWORDS_LOCK_KEY = "passwords"

@Composable
fun PasswordsScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    val lock = components.appLock
    var unlocked by remember { mutableStateOf(lock.isUnlocked(PASSWORDS_LOCK_KEY)) }
    val title = stringResource(R.string.passwords_title)
    val authenticate = {
        (context as? FragmentActivity)?.let { activity ->
            lock.authenticate(activity, PASSWORDS_LOCK_KEY, title) { ok -> if (ok) unlocked = true }
        }
    }
    LaunchedEffect(Unit) { if (!unlocked) authenticate() }

    OverlayScaffold(title = title, onBack = onBack) { modifier ->
        if (unlocked) {
            PasswordList(onOpen, modifier)
        } else {
            LockedPasswords(
                modifier,
                hasPassword = lock.hasPassword,
                onUnlock = { authenticate() },
                onPassword = { pw -> lock.unlockWithPassword(PASSWORDS_LOCK_KEY, pw).also { if (it) unlocked = true } },
            )
        }
    }
}

@Composable
private fun LockedPasswords(modifier: Modifier, hasPassword: Boolean, onUnlock: () -> Unit, onPassword: (String) -> Boolean) {
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.passwords_locked), style = MaterialTheme.typography.bodyLarge)
        if (hasPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; wrong = false },
                singleLine = true,
                isError = wrong,
                label = { Text(stringResource(R.string.lock_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { wrong = !onPassword(password) }, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
        TextButton(onClick = onUnlock) { Text(stringResource(R.string.passwords_unlock_biometric)) }
    }
}

@Composable
private fun PasswordList(onOpen: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val components = context.components
    val store = components.passwords
    val settings = components.settings
    val profile by components.profiles.active.collectAsState()
    val logins by remember(profile.id) { store.logins(profile.id) }.collectAsState(initial = null)
    val saveOn by settings.savePasswords.collectAsState()
    val autofillOn by settings.autofillPasswords.collectAsState()
    val neverVersion by store.neverSaveVersion.collectAsState()
    val never = remember(neverVersion, profile.id) { store.neverSave().sorted() }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<SavedLogin?>(null) }
    var editing by remember { mutableStateOf<SavedLogin?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val delete = { login: SavedLogin ->
        selected = null
        scope.launch {
            val removed = store.delete(login.guid) ?: return@launch
            components.messenger.undoable(UiText.res(R.string.passwords_deleted), "parola-sil-${login.guid}") {
                scope.launch { store.restore(removed) }
            }
        }
    }

    OverlayScaffoldContent(modifier, fab = { AddFab(stringResource(R.string.passwords_add)) { adding = true } }) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                SectionCard(
                    listOf(
                        {
                            SwitchRow(stringResource(R.string.passwords_save), stringResource(R.string.passwords_save_summary), saveOn, Icons.Rounded.Password) {
                                settings.setSavePasswords(it)
                            }
                        },
                        {
                            SwitchRow(stringResource(R.string.passwords_autofill), stringResource(R.string.passwords_autofill_summary), autofillOn, Icons.Rounded.AutoAwesome) {
                                settings.setAutofillPasswords(it)
                            }
                        },
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val list = logins
            if (list != null && list.isNotEmpty()) {
                item { FilterField(query) { query = it } }
                val shown = list.filter { query.isBlank() || TextMatch.containsAny(query, it.host, it.username) }
                item { SectionHeader(pluralStringResource(R.plurals.passwords_count, list.size, list.size)) }
                items(shown, key = { it.guid }) { login ->
                    ListRow(
                        headline = { Text(login.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supporting = {
                            Text(
                                login.username.ifEmpty { stringResource(R.string.passwords_no_username) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leading = { Favicon(login.origin) },
                        modifier = Modifier.clickable { selected = login },
                    )
                }
            } else if (list != null) {
                item {
                    EmptyState(
                        stringResource(R.string.passwords_empty),
                        icon = Icons.Rounded.Key,
                        modifier = Modifier.height(320.dp),
                    )
                }
            }
            if (never.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.passwords_never_title)) }
                items(never, key = { "never-$it" }) { origin ->
                    ListRow(
                        headline = { Text(LoginMatching.displayHost(origin), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leading = { Icon(Icons.Rounded.Block, contentDescription = null) },
                        trailing = {
                            IconButton(onClick = { store.removeNeverSave(origin) }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.passwords_never_remove))
                            }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    selected?.let { login ->
        LoginDetailDialog(
            login,
            onOpen = { selected = null; onOpen(login.origin) },
            onEdit = { selected = null; editing = login },
            onDelete = { delete(login) },
            onDismiss = { selected = null },
        )
    }
    editing?.let { login ->
        LoginEditDialog(
            initialSite = login.origin,
            initialUser = login.username,
            initialPassword = login.password,
            siteEditable = false,
            onSave = { _, user, pw -> scope.launch { store.edit(login.guid, user, pw) }; editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        LoginEditDialog(
            initialSite = "",
            initialUser = "",
            initialPassword = "",
            siteEditable = true,
            onSave = { site, user, pw ->
                scope.launch { store.add(normalizeSite(site), user, pw) }
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

/** OverlayScaffold içeriği + ekleme düğmesi (iskelet tek bir yerde kalsın diye küçük yardımcı). */
@Composable
private fun OverlayScaffoldContent(modifier: Modifier, fab: @Composable () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier) {
        content()
        androidx.compose.foundation.layout.Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { fab() }
    }
}

@Composable
private fun LoginDetailDialog(
    login: SavedLogin,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var reveal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Favicon(login.origin, size = 32.dp) },
        title = { Text(login.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailLine(
                    label = stringResource(R.string.passwords_username),
                    value = login.username.ifEmpty { stringResource(R.string.passwords_no_username) },
                ) { copy(context, login.username, sensitive = false) }
                DetailLine(
                    label = stringResource(R.string.passwords_password),
                    value = if (reveal) login.password else "•".repeat(login.password.length.coerceIn(6, 16)),
                    extra = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(if (reveal) R.string.passwords_hide else R.string.passwords_show),
                            )
                        }
                    },
                ) { copy(context, login.password, sensitive = true) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Rounded.OpenInBrowser, null)
                        Text(stringResource(R.string.passwords_open_site), Modifier.padding(start = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, null)
                Text(stringResource(R.string.action_edit), Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.action_delete), Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String, extra: @Composable () -> Unit = {}, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        extra()
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy)) }
    }
}

@Composable
private fun LoginEditDialog(
    initialSite: String,
    initialUser: String,
    initialPassword: String,
    siteEditable: Boolean,
    onSave: (site: String, user: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var site by remember { mutableStateOf(initialSite) }
    var user by remember { mutableStateOf(initialUser) }
    var password by remember { mutableStateOf(initialPassword) }
    var reveal by remember { mutableStateOf(false) }
    val siteValid = !siteEditable || LoginMatching.hostOf(normalizeSite(site)).contains('.')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (siteEditable) R.string.passwords_add else R.string.passwords_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (siteEditable) {
                    OutlinedTextField(
                        value = site,
                        onValueChange = { site = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.passwords_site)) },
                        placeholder = { Text("example.com") },
                        isError = site.isNotBlank() && !siteValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.passwords_username)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.passwords_password)) },
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null)
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(site, user, password) }, enabled = password.isNotEmpty() && siteValid && site.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "example.com" → "https://example.com" (elle eklenen kayıtların kökeni). */
internal fun normalizeSite(input: String): String {
    val trimmed = input.trim()
    val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return LoginMatching.normalizeOrigin(withScheme)
}

/** Parola panoya hassas olarak kopyalanır: Android 13+ önizlemede göstermez. */
private fun copy(context: Context, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(if (sensitive) "Parola" else "Kullanıcı adı", text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    // Android 13+ kopyalamayı kendisi bildirir; eskilerde kısa bir mesaj gösterilir.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        context.components.messenger.info(UiText.res(R.string.passwords_copied))
    }
}
