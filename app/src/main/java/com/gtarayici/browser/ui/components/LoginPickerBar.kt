package com.gtarayici.browser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.passwords.LoginMatching
import mozilla.components.concept.storage.Login
import mozilla.components.feature.prompts.concept.AutocompletePrompt
import mozilla.components.feature.prompts.concept.SelectablePromptView
import mozilla.components.feature.prompts.concept.ToggleablePrompt

/**
 * Giriş formuna dokununca kayıtlı hesapları gösteren çubuğun durumu. İstek motor tarafından [showPrompt] ve
 * [populate] ile gelir; seçim [selectablePromptListener] üzerinden motora iletilir.
 */
@Stable
class LoginPickerState : AutocompletePrompt<Login> {
    var logins by mutableStateOf(emptyList<Login>())
        private set
    var visible by mutableStateOf(false)
        private set

    override var selectablePromptListener: SelectablePromptView.Listener<Login>? = null
    override var toggleablePromptListener: ToggleablePrompt.Listener? = null
    override val isPromptDisplayed: Boolean get() = visible

    override fun populate(options: List<Login>) {
        logins = options
    }

    override fun showPrompt() {
        visible = true
        toggleablePromptListener?.onShown()
    }

    override fun hidePrompt() {
        visible = false
        logins = emptyList()
        toggleablePromptListener?.onHidden()
    }
}

@Composable
fun LoginPickerBar(state: LoginPickerState, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = state.visible && state.logins.isNotEmpty(), modifier = modifier) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.passwords_pick_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { state.selectablePromptListener?.onManageOptions() }) {
                        Text(stringResource(R.string.passwords_manage))
                    }
                    IconButton(onClick = { state.hidePrompt() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    state.logins.forEach { login ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { state.selectablePromptListener?.onOptionSelect(login) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(
                                    login.username.ifEmpty { stringResource(R.string.passwords_no_username) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    LoginMatching.displayHost(login.origin),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
