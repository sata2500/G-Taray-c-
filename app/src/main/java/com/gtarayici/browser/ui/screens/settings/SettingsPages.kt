package com.gtarayici.browser.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FrontHand
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gtarayici.browser.BuildConfig
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.search.SearchEngine
import com.gtarayici.browser.settings.DataSaverMode
import com.gtarayici.browser.settings.DnsProvider
import com.gtarayici.browser.ui.components.isDefaultBrowser
import com.gtarayici.browser.ui.components.rememberDefaultBrowserRequest
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.update.UpdateState
import mozilla.components.concept.engine.Engine

// Ayarların bütün sayfaları ve satırları; değerler ayarlardan canlı okunur.

/** Tüm sayfaların satırları; değerler ayarlardan canlı okunur. */
@Composable
internal fun settingsPages(
    open: (SettingsDialog) -> Unit,
    onReport: () -> Unit,
    onPasswords: () -> Unit,
    onGuide: () -> Unit,
    backup: BackupUi,
): Map<SettingsPage, List<SettingSection>> {
    val context = LocalContext.current
    val components = context.components
    val settings = components.settings
    val engine by settings.searchEngine.collectAsState()
    val theme by settings.theme.collectAsState()
    val strict by settings.strictTracking.collectAsState()
    val httpsOnly by settings.httpsOnly.collectAsState()
    val gpc by settings.globalPrivacyControl.collectAsState()
    val cookieBanner by settings.cookieBannerReject.collectAsState()
    val dnsProvider by settings.dnsProvider.collectAsState()
    val dnsCustom by settings.dnsCustomUrl.collectAsState()
    val dnsStrict by settings.dnsStrict.collectAsState()
    val lockPrivate by settings.lockPrivateTabs.collectAsState()
    val securePrivate by settings.securePrivateTabs.collectAsState()
    val backgroundPlay by settings.backgroundPlay.collectAsState()
    val sponsorBlock by settings.sponsorBlock.collectAsState()
    val autoPip by settings.autoPictureInPicture.collectAsState()
    val inAppPip by settings.inAppPictureInPicture.collectAsState()
    val autoUpdate by settings.autoUpdateCheck.collectAsState()
    val hideToolbar by settings.hideToolbarOnScroll.collectAsState()
    val unloadTabs by settings.unloadInactiveTabs.collectAsState()
    val videoDownloadButton by settings.videoDownloadButton.collectAsState()
    val pullToRefresh by settings.pullToRefresh.collectAsState()
    val toolbarSwipe by settings.toolbarSwipeTabs.collectAsState()
    val hintsOn by settings.contextualHints.collectAsState()
    val diagnosticsOn by settings.diagnosticsLog.collectAsState()
    val crashPrompt by settings.crashPrompt.collectAsState()
    val updateState by components.updater.state.collectAsState()
    val textZoom by settings.textZoom.collectAsState()
    val searchSuggest by settings.searchSuggestions.collectAsState()
    val vpnKeepAlive by settings.vpnKeepAlive.collectAsState()
    val darkSites by settings.darkSites.collectAsState()
    val customSites by components.siteSettings.sites.collectAsState()
    val dataSaverMode by settings.dataSaver.collectAsState()
    val dataSaverActive by components.dataSaver.active.collectAsState()
    val dataSaverImages by settings.dataSaverBlockImages.collectAsState()
    val cacheSize by settings.cacheSize.collectAsState()
    val link by components.dataSaver.link.collectAsState()
    // Önbellek boyutu temizlemeden sonra yeniden ölçülsün diye sürüm sayacı.
    var cacheVersion by remember { mutableIntStateOf(0) }
    val cacheBytes by produceState<Long?>(null, cacheVersion) { value = components.dataSaver.cacheBytes() }
    var isDefault by remember { mutableStateOf(isDefaultBrowser(context)) }
    val requestDefaultBrowser = rememberDefaultBrowserRequest { isDefault = it }
    // Kayıt satır sayısı: temizleme/geri alma sonrası yeniden okunsun diye sürüm sayacı.
    var logVersion by remember { mutableIntStateOf(0) }
    val logLines = remember(logVersion, diagnosticsOn) { Diagnostics.lineCount() }
    val applyPrivacy = { components.applyPrivacySettings() }
    val s = @Composable { id: Int -> stringResource(id) }

    return mapOf(
        SettingsPage.GENERAL to listOf(
            SettingSection(
                null,
                listOf(
                    SettingItem.Action(
                        s(R.string.settings_search_engine),
                        if (engine == SearchEngine.CUSTOM) com.gtarayici.browser.search.CustomSearch.label(settings.customSearchUrl.collectAsState().value) else engine.title,
                        Icons.Rounded.Search,
                    ) { open(SettingsDialog.ENGINE) },
                    SettingItem.Toggle(s(R.string.settings_search_suggestions), s(R.string.settings_search_suggestions_summary), Icons.Rounded.Lightbulb, searchSuggest) {
                        settings.setSearchSuggestions(it)
                    },
                    SettingItem.Info(s(R.string.settings_search_shortcuts), com.gtarayici.browser.search.SearchShortcuts.ALL.joinToString(" · ") { "${it.keyword} = ${it.title}" }, Icons.Rounded.Search),
                    SettingItem.Action(
                        s(R.string.settings_default_browser),
                        s(if (isDefault) R.string.settings_default_browser_active else R.string.settings_default_browser_summary),
                        Icons.Rounded.OpenInBrowser,
                    ) { requestDefaultBrowser() },
                    SettingItem.Toggle(s(R.string.settings_unload_tabs), s(R.string.settings_unload_tabs_summary), Icons.Rounded.Bedtime, unloadTabs) {
                        settings.setUnloadInactiveTabs(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_hints), s(R.string.settings_hints_summary), Icons.Rounded.Lightbulb, hintsOn) {
                        settings.setContextualHints(it)
                    },
                ),
            ),
            SettingSection(s(R.string.backup_section), backup.items),
        ),
        SettingsPage.APPEARANCE to listOf(
            SettingSection(
                null,
                listOf(
                    SettingItem.Action(s(R.string.settings_theme), themeLabel(theme), Icons.Rounded.Palette) { open(SettingsDialog.THEME) },
                    SettingItem.Toggle(s(R.string.settings_dark_sites), s(R.string.settings_dark_sites_summary), Icons.Rounded.DarkMode, darkSites) {
                        settings.setDarkSites(it)
                    },
                    SettingItem.Action(s(R.string.settings_text_zoom), "%$textZoom", Icons.Rounded.TextFields) { open(SettingsDialog.TEXT_ZOOM) },
                    SettingItem.Action(
                        s(R.string.settings_site_prefs),
                        if (customSites.isEmpty()) s(R.string.settings_site_prefs_none)
                        else pluralStringResource(R.plurals.settings_site_prefs_count, customSites.size, customSites.size),
                        Icons.Rounded.Language,
                    ) { open(SettingsDialog.SITES) },
                    SettingItem.Toggle(s(R.string.settings_pull_to_refresh), s(R.string.settings_pull_to_refresh_summary), Icons.Rounded.Refresh, pullToRefresh) {
                        settings.setPullToRefresh(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_toolbar_swipe), s(R.string.settings_toolbar_swipe_summary), Icons.Rounded.Swipe, toolbarSwipe) {
                        settings.setToolbarSwipeTabs(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_hide_toolbar), s(R.string.settings_hide_toolbar_summary), Icons.Rounded.Fullscreen, hideToolbar) {
                        settings.setHideToolbarOnScroll(it)
                    },
                ),
            ),
        ),
        SettingsPage.PRIVACY to listOf(
            SettingSection(
                s(R.string.settings_section_protection),
                listOf(
                    SettingItem.Toggle(s(R.string.settings_strict_tracking), s(R.string.settings_strict_tracking_summary), Icons.Rounded.Shield, strict) {
                        settings.setStrictTracking(it); applyPrivacy()
                    },
                    SettingItem.Toggle(s(R.string.settings_https_only), s(R.string.settings_https_only_summary), Icons.Rounded.Lock, httpsOnly) {
                        settings.setHttpsOnly(it); applyPrivacy()
                    },
                    SettingItem.Toggle(s(R.string.settings_gpc), s(R.string.settings_gpc_summary), Icons.Rounded.FrontHand, gpc) {
                        settings.setGlobalPrivacyControl(it); applyPrivacy()
                    },
                    SettingItem.Toggle(s(R.string.settings_cookie_banner), s(R.string.settings_cookie_banner_summary), Icons.Rounded.Cookie, cookieBanner) {
                        settings.setCookieBannerReject(it); applyPrivacy()
                    },
                ),
            ),
            SettingSection(
                s(R.string.settings_section_connection),
                listOfNotNull(
                    SettingItem.Toggle(s(R.string.settings_vpn_keep_alive), s(R.string.settings_vpn_keep_alive_summary), Icons.Rounded.VpnLock, vpnKeepAlive) {
                        settings.setVpnKeepAlive(it)
                        components.vpnNotifier.onKeepAliveChanged(it)
                    },
                    SettingItem.Action(
                        s(R.string.settings_dns),
                        if (dnsProvider == DnsProvider.CUSTOM) dnsCustom.ifBlank { dnsProvider.title } else dnsProvider.title,
                        Icons.Rounded.Dns,
                    ) { open(SettingsDialog.DNS) },
                    if (dnsProvider != DnsProvider.OFF) {
                        SettingItem.Toggle(s(R.string.settings_dns_strict), s(R.string.settings_dns_strict_summary), Icons.Rounded.GppGood, dnsStrict) {
                            settings.setDnsStrict(it); applyPrivacy()
                        }
                    } else null,
                ),
            ),
            SettingSection(
                s(R.string.settings_section_locks),
                listOf(
                    SettingItem.Action(s(R.string.passwords_title), s(R.string.passwords_summary), Icons.Rounded.Key, opensPage = true, onClick = onPasswords),
                    SettingItem.Action(
                        s(R.string.settings_app_password),
                        s(if (components.appLock.hasPassword) R.string.settings_app_password_on else R.string.settings_app_password_off),
                        Icons.Rounded.Password,
                    ) { open(SettingsDialog.PASSWORD) },
                    SettingItem.Toggle(s(R.string.settings_lock_private), s(R.string.settings_lock_private_summary), Icons.Rounded.LockPerson, lockPrivate) {
                        settings.setLockPrivateTabs(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_secure_private), s(R.string.settings_secure_private_summary), Icons.Rounded.ScreenLockPortrait, securePrivate) {
                        settings.setSecurePrivateTabs(it)
                    },
                ),
            ),
            SettingSection(
                s(R.string.settings_section_data),
                listOf(
                    SettingItem.Action(s(R.string.settings_clear_data), s(R.string.settings_clear_data_summary), Icons.Rounded.CleaningServices) {
                        open(SettingsDialog.CLEAR_DATA)
                    },
                ),
            ),
        ),
        SettingsPage.MEDIA to listOf(
            SettingSection(
                null,
                listOf(
                    SettingItem.Toggle(s(R.string.settings_background_play), s(R.string.settings_background_play_summary), Icons.Rounded.Headphones, backgroundPlay) {
                        settings.setBackgroundPlay(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_sponsorblock), s(R.string.settings_sponsorblock_summary), Icons.Rounded.SkipNext, sponsorBlock) {
                        settings.setSponsorBlock(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_auto_pip), s(R.string.settings_auto_pip_summary), Icons.Rounded.PictureInPictureAlt, autoPip) {
                        settings.setAutoPictureInPicture(it)
                    },
                    SettingItem.Toggle(s(R.string.settings_in_app_pip), s(R.string.settings_in_app_pip_summary), Icons.Rounded.PictureInPicture, inAppPip) {
                        settings.setInAppPictureInPicture(it)
                    },
                    SettingItem.Toggle(
                        s(R.string.settings_video_download_button),
                        s(R.string.settings_video_download_button_summary),
                        Icons.Rounded.Download,
                        videoDownloadButton,
                    ) { settings.setVideoDownloadButton(it) },
                ),
            ),
        ),
        SettingsPage.ADVANCED to listOf(
            SettingSection(
                s(R.string.settings_data_saver),
                listOfNotNull(
                    SettingItem.Action(
                        s(R.string.settings_data_saver),
                        "${dataSaverLabel(dataSaverMode)} · ${s(if (dataSaverActive) R.string.data_saver_now_on else R.string.data_saver_now_off)}",
                        Icons.Rounded.DataSaverOn,
                    ) { open(SettingsDialog.DATA_SAVER) },
                    if (dataSaverMode != DataSaverMode.OFF) {
                        SettingItem.Toggle(s(R.string.settings_data_saver_images), s(R.string.settings_data_saver_images_summary), Icons.Rounded.HideImage, dataSaverImages) {
                            settings.setDataSaverBlockImages(it)
                        }
                    } else null,
                    SettingItem.Info(s(R.string.data_saver_connection), connectionLabel(link), Icons.Rounded.NetworkCheck),
                ),
            ),
            SettingSection(
                s(R.string.settings_section_cache),
                listOf(
                    SettingItem.Action(s(R.string.settings_cache_size), cacheSizeLabel(cacheSize), Icons.Rounded.Storage) {
                        open(SettingsDialog.CACHE_SIZE)
                    },
                    SettingItem.Action(
                        s(R.string.settings_cache_clear),
                        cacheBytes?.let { stringResource(R.string.settings_cache_clear_summary, Formatter.formatShortFileSize(context, it)) },
                        Icons.Rounded.DeleteSweep,
                    ) {
                        components.engine.clearData(
                            Engine.BrowsingData.allCaches(),
                            onSuccess = {
                                cacheVersion++
                                components.messenger.success(UiText.res(R.string.settings_cache_cleared))
                            },
                        )
                    },
                ),
            ),
            SettingSection(
                s(R.string.diag_section_report),
                listOf(SettingItem.Action(s(R.string.report_title), s(R.string.report_summary), Icons.Rounded.ReportProblem, opensPage = true, onClick = onReport)),
            ),
            SettingSection(
                s(R.string.diag_section_log),
                listOfNotNull(
                    SettingItem.Toggle(s(R.string.diag_log_enabled), s(R.string.diag_log_enabled_summary), Icons.AutoMirrored.Rounded.ReceiptLong, diagnosticsOn) {
                        settings.setDiagnosticsLog(it)
                    },
                    SettingItem.Toggle(s(R.string.diag_crash_prompt), s(R.string.diag_crash_prompt_summary), Icons.Rounded.BugReport, crashPrompt) {
                        settings.setCrashPrompt(it)
                    },
                    SettingItem.Action(s(R.string.diag_view), stringResource(R.string.diag_view_summary, logLines), Icons.Rounded.Code) {
                        open(SettingsDialog.LOG)
                    },
                    if (logLines > 0) {
                        SettingItem.Action(s(R.string.diag_clear), null, Icons.Rounded.DeleteSweep) {
                            val removed = Diagnostics.clear()
                            logVersion++
                            components.messenger.undoable(UiText.res(R.string.diag_cleared), "tanilama-temizle") {
                                Diagnostics.restore(removed)
                                logVersion++
                            }
                        }
                    } else null,
                ),
            ),
        ),
        SettingsPage.ABOUT to listOf(
            SettingSection(
                s(R.string.settings_updates),
                listOf(
                    SettingItem.Action(
                        s(R.string.update_check),
                        when (val state = updateState) {
                            UpdateState.Checking -> s(R.string.update_checking)
                            UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, components.updater.currentVersion)
                            is UpdateState.Available -> stringResource(R.string.update_title, state.release.version)
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.release.version)
                            else -> stringResource(R.string.update_check_summary, components.updater.currentVersion)
                        },
                        Icons.Rounded.SystemUpdate,
                    ) { components.updater.check() },
                    SettingItem.Toggle(s(R.string.update_auto), s(R.string.update_auto_summary), Icons.Rounded.Autorenew, autoUpdate) {
                        settings.setAutoUpdateCheck(it)
                    },
                ),
            ),
            SettingSection(
                s(R.string.settings_about),
                listOf(
                    SettingItem.Action(s(R.string.guide_title), s(R.string.guide_summary), Icons.Rounded.Lightbulb, opensPage = true, onClick = onGuide),
                    SettingItem.Info(s(R.string.settings_version), BuildConfig.VERSION_NAME, Icons.Rounded.Info),
                    SettingItem.Info(s(R.string.settings_engine), "GeckoView ${org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION}", Icons.Rounded.Language),
                    SettingItem.Info(s(R.string.settings_license), s(R.string.settings_license_value), Icons.Rounded.Code),
                ),
            ),
        ),
    )
}
