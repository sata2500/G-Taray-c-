package com.gtarayici.browser.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.ui.components.observe
import java.util.Locale
import mozilla.components.browser.state.action.TranslationsAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.translate.Language
import mozilla.components.concept.engine.translate.TranslationOperation
import mozilla.components.concept.engine.translate.TranslationOptions

/**
 * Sayfa çevirisi. Firefox'un çeviri sistemi kullanılır: dil modelleri bir kez indirilir,
 * çeviri tamamen cihazda yapılır ve sayfa metni hiçbir sunucuya gönderilmez.
 */
object Translation {
    fun translate(store: BrowserStore, tabId: String, from: String, to: String) {
        store.dispatch(TranslationsAction.TranslateAction(tabId, from, to, TranslationOptions(downloadModel = true)))
    }

    fun restore(store: BrowserStore, tabId: String) {
        store.dispatch(TranslationsAction.TranslateRestoreAction(tabId))
    }

    /** Çeviri hedefi: sayfanın bildirdiği kullanıcı dili, yoksa uygulama dili. */
    fun defaultTarget(preferred: String?): String = preferred ?: Locale.getDefault().language

    fun displayName(code: String, languages: List<Language>?): String =
        languages?.firstOrNull { it.code == code }?.localizedDisplayName
            ?: Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase() }
}

/** Sayfa başka bir dildeyse adres çubuğunun üstünde beliren öneri çubuğu. */
@Composable
fun TranslateOfferBar(tabId: String, onOptions: () -> Unit, onDismiss: () -> Unit) {
    val store = LocalContext.current.components.store
    val tab by store.observe { it.findTab(tabId)?.translationsState }
    val languages by store.observe { it.translationEngine.supportedLanguages?.toLanguages }
    val detected = tab?.translationEngineState?.detectedLanguages ?: return
    val from = detected.documentLangTag ?: return
    val to = Translation.defaultTarget(detected.userPreferredLangTag)

    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Translate, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.translate_offer, Translation.displayName(from, languages)),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (tab?.isTranslateProcessing == true) {
                CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { Translation.translate(store, tabId, from, to) }) {
                    Text(stringResource(R.string.translate_action))
                }
                TextButton(onClick = onOptions) { Text(stringResource(R.string.translate_options)) }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
        }
    }
}

/** Kaynak ve hedef dil seçimiyle çeviri penceresi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSheet(tabId: String, onDismiss: () -> Unit) {
    val store = LocalContext.current.components.store
    val engine by store.observe { it.translationEngine }
    val tab by store.observe { it.findTab(tabId)?.translationsState }

    LaunchedEffect(Unit) {
        if (engine.supportedLanguages == null) {
            store.dispatch(TranslationsAction.OperationRequestedAction(tabId, TranslationOperation.FETCH_SUPPORTED_LANGUAGES))
        }
    }

    val fromLanguages = engine.supportedLanguages?.fromLanguages.orEmpty()
    val toLanguages = engine.supportedLanguages?.toLanguages.orEmpty()
    val detected = tab?.translationEngineState?.detectedLanguages
    val requested = tab?.translationEngineState?.requestedTranslationPair
    var from by remember(detected, requested) {
        mutableStateOf(requested?.fromLanguage ?: detected?.documentLangTag)
    }
    var to by remember(detected, requested) {
        mutableStateOf(requested?.toLanguage ?: Translation.defaultTarget(detected?.userPreferredLangTag))
    }
    val translated = tab?.isTranslated == true
    val busy = tab?.isTranslateProcessing == true || tab?.isRestoreProcessing == true

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = com.gtarayici.browser.ui.designsystem.rememberFullSheetState()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(stringResource(R.string.translate_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.translate_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            when {
                engine.isEngineSupported == false -> Text(stringResource(R.string.translate_unsupported))
                fromLanguages.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanguagePicker(
                            label = stringResource(R.string.translate_from),
                            selected = from,
                            languages = fromLanguages,
                            onSelect = { from = it },
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.padding(horizontal = 8.dp))
                        LanguagePicker(
                            label = stringResource(R.string.translate_to),
                            selected = to,
                            languages = toLanguages,
                            onSelect = { to = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    tab?.translationError?.takeIf { it.displayError }?.let {
                        Text(
                            stringResource(R.string.translate_error, it.errorName),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        if (translated) {
                            OutlinedButton(enabled = !busy, onClick = { Translation.restore(store, tabId) }) {
                                Text(stringResource(R.string.translate_show_original))
                            }
                        }
                        val source = from
                        Button(
                            enabled = !busy && source != null && source != to,
                            onClick = { if (source != null) Translation.translate(store, tabId, source, to) },
                        ) { Text(stringResource(R.string.translate_action)) }
                    }
                    Text(
                        stringResource(R.string.translate_first_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: String?,
    languages: List<Language>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    selected?.let { Translation.displayName(it, languages) } ?: stringResource(R.string.translate_choose),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                languages.sortedBy { it.localizedDisplayName }.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.localizedDisplayName ?: language.code) },
                        onClick = {
                            open = false
                            onSelect(language.code)
                        },
                    )
                }
            }
        }
    }
}
