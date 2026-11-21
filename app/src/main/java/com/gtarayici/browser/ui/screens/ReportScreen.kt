package com.gtarayici.browser.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.diagnostics.DiagnosticsEnvironment
import com.gtarayici.browser.diagnostics.IssueCategory
import com.gtarayici.browser.diagnostics.IssueReport
import com.gtarayici.browser.diagnostics.PrivacyFilter
import com.gtarayici.browser.diagnostics.ReportActions
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.designsystem.AppShape
import com.gtarayici.browser.ui.designsystem.OverlayScaffold
import com.gtarayici.browser.ui.designsystem.SectionCard
import com.gtarayici.browser.ui.designsystem.SectionHeader
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.designsystem.SwitchRow
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/** Sorun bildirimi için toplanan bağlam: açık sayfa, o anki ekran görüntüsü ve bekleyen çökme raporu. */
class ReportRequest(val site: String?, val screenshot: Bitmap?, val crash: String?)

/** Kaç satırlık tanılama kaydının rapora ekleneceği (GitHub adresine sığması için sınırlı). */
private const val REPORT_LOG_LINES = 150

/**
 * "Sorun bildir": tür seçilir, isteğe bağlı açıklama yazılır, eklenecekler seçilir; önizlemede gönderilecek metnin
 * tamamı görülür. GitHub'da önceden doldurulmuş bildirim açılır, dosya olarak paylaşılır veya kopyalanır.
 */
@Composable
fun ReportScreen(request: ReportRequest, onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val components = context.components
    var category by rememberSaveable { mutableStateOf(if (request.crash != null) IssueCategory.Crash else IssueCategory.Page) }
    var description by rememberSaveable { mutableStateOf("") }
    var includeSite by rememberSaveable { mutableStateOf(request.site != null) }
    var includeLog by rememberSaveable { mutableStateOf(true) }
    var includeScreenshot by rememberSaveable { mutableStateOf(false) }
    var includeCrash by rememberSaveable { mutableStateOf(request.crash != null) }
    var previewing by rememberSaveable { mutableStateOf(false) }
    val logLines = remember { Diagnostics.lineCount().coerceAtMost(REPORT_LOG_LINES) }

    val screenshot = request.screenshot.takeIf { includeScreenshot }
    val report = IssueReport(
        category = category,
        description = description,
        environment = remember { DiagnosticsEnvironment.describe(context) },
        site = request.site.takeIf { includeSite },
        log = if (includeLog) remember { Diagnostics.dump(REPORT_LOG_LINES) } else "",
        crash = request.crash.takeIf { includeCrash },
        hasScreenshot = screenshot != null,
    )
    val finished = {
        if (report.crash != null) components.crashReporter.dismiss()
        onBack()
    }

    OverlayScaffold(
        title = stringResource(if (previewing) R.string.report_preview_title else R.string.report_title),
        onBack = { if (previewing) previewing = false else onBack() },
    ) { modifier ->
        if (!previewing) {
            ReportForm(
                modifier = modifier,
                category = category,
                onCategory = { category = it },
                description = description,
                onDescription = { description = it },
                site = request.site,
                includeSite = includeSite,
                onIncludeSite = { includeSite = it },
                logLines = logLines,
                includeLog = includeLog,
                onIncludeLog = { includeLog = it },
                hasScreenshot = request.screenshot != null,
                includeScreenshot = includeScreenshot,
                onIncludeScreenshot = { includeScreenshot = it },
                hasCrash = request.crash != null,
                includeCrash = includeCrash,
                onIncludeCrash = { includeCrash = it },
                onPreview = { previewing = true },
            )
        } else {
            ReportPreview(
                modifier = modifier,
                text = report.title + "\n\n" + report.body(),
                screenshot = screenshot,
                onGitHub = {
                    val link = report.gitHubLink()
                    link.clipboard?.let {
                        ReportActions.copy(context, "G-Tarayıcı sorun bildirimi", it)
                        components.messenger.info(UiText.res(R.string.report_log_in_clipboard))
                    }
                    if (screenshot != null && ReportActions.saveScreenshot(context, screenshot)) {
                        components.messenger.info(UiText.res(R.string.report_screenshot_saved))
                    }
                    Diagnostics.log("sorun bildirimi GitHub'da açıldı (${category.tag})", "bildirim")
                    onOpenUrl(link.url)
                    finished()
                },
                onShare = {
                    ReportActions.share(context, report, screenshot)
                    finished()
                },
                onCopy = {
                    ReportActions.copy(context, "G-Tarayıcı sorun bildirimi", report.title + "\n\n" + report.body())
                    components.messenger.copied(UiText.res(R.string.report_copied))
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportForm(
    modifier: Modifier,
    category: IssueCategory,
    onCategory: (IssueCategory) -> Unit,
    description: String,
    onDescription: (String) -> Unit,
    site: String?,
    includeSite: Boolean,
    onIncludeSite: (Boolean) -> Unit,
    logLines: Int,
    includeLog: Boolean,
    onIncludeLog: (Boolean) -> Unit,
    hasScreenshot: Boolean,
    includeScreenshot: Boolean,
    onIncludeScreenshot: (Boolean) -> Unit,
    hasCrash: Boolean,
    includeCrash: Boolean,
    onIncludeCrash: (Boolean) -> Unit,
    onPreview: () -> Unit,
) {
    Column(modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        SectionHeader(stringResource(R.string.report_category))
        FlowRow(
            Modifier.padding(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IssueCategory.entries.forEach { option ->
                val selected = option == category
                FilterChip(
                    selected = selected,
                    onClick = { onCategory(option) },
                    label = { Text(stringResource(option.label)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, null, Modifier.padding(0.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        OutlinedTextField(
            value = description,
            onValueChange = onDescription,
            label = { Text(stringResource(R.string.report_description)) },
            placeholder = { Text(stringResource(R.string.report_description_hint)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        )
        SectionCard(
            listOf(
                if (site != null) {
                    {
                        SwitchRow(
                            stringResource(R.string.report_include_site),
                            PrivacyFilter.maskUrl(site),
                            includeSite,
                            Icons.Rounded.Language,
                            onCheckedChange = onIncludeSite,
                        )
                    }
                } else null,
                if (logLines > 0) {
                    {
                        SwitchRow(
                            stringResource(R.string.report_include_log),
                            stringResource(R.string.report_include_log_summary, logLines),
                            includeLog,
                            Icons.AutoMirrored.Rounded.ReceiptLong,
                            onCheckedChange = onIncludeLog,
                        )
                    }
                } else null,
                if (hasScreenshot) {
                    {
                        SwitchRow(
                            stringResource(R.string.report_include_screenshot),
                            stringResource(R.string.report_include_screenshot_summary),
                            includeScreenshot,
                            Icons.Rounded.Screenshot,
                            onCheckedChange = onIncludeScreenshot,
                        )
                    }
                } else null,
                if (hasCrash) {
                    {
                        SwitchRow(
                            stringResource(R.string.report_include_crash),
                            null,
                            includeCrash,
                            Icons.Rounded.BugReport,
                            onCheckedChange = onIncludeCrash,
                        )
                    }
                } else null,
            ),
        )
        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().padding(Spacing.screen).heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.report_preview)) }
    }
}

@Composable
private fun ReportPreview(
    modifier: Modifier,
    text: String,
    screenshot: Bitmap?,
    onGitHub: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(modifier.navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            Text(
                stringResource(R.string.report_preview_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.md),
            )
            if (screenshot != null) {
                Image(
                    screenshot.asImageBitmap(),
                    contentDescription = stringResource(R.string.report_include_screenshot),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(0.5f).clip(AppShape.md).padding(bottom = Spacing.md),
                )
            }
            Surface(shape = AppShape.md, color = MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onGitHub, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
                    Spacer(Modifier.padding(start = Spacing.sm))
                    Text(stringResource(R.string.report_open_github))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Share, null)
                        Spacer(Modifier.padding(start = Spacing.xs))
                        Text(stringResource(R.string.report_share_file))
                    }
                    TextButton(onClick = onCopy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.ContentCopy, null)
                        Spacer(Modifier.padding(start = Spacing.xs))
                        Text(stringResource(R.string.report_copy))
                    }
                }
            }
        }
    }
}

@Preview(name = "Sorun bildir – önizleme", showBackground = true, heightDp = 640)
@Preview(name = "Sorun bildir – büyük yazı", showBackground = true, heightDp = 640, fontScale = 2f)
@Composable
private fun ReportPreviewPreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        ReportPreview(
            modifier = Modifier,
            text = "[Video] Tam ekranda ses kesiliyor\n\n### Ne oldu?\n…\n\n### Ortam\n- G-Tarayıcı 0.8.0",
            screenshot = null,
            onGitHub = {},
            onShare = {},
            onCopy = {},
        )
    }
}
