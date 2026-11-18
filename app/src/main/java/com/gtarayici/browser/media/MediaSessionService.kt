package com.gtarayici.browser.media

import com.gtarayici.browser.components
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate

/** Medya oynarken bildirim ve kilit ekranı kontrollerini gösterir, arka planda oynatmayı sürdürür. */
class MediaSessionService : AbstractMediaSessionService() {
    override val store: BrowserStore by lazy { components.store }
    override val crashReporter: CrashReporting? = null
    override val notificationsDelegate: NotificationsDelegate by lazy { components.notificationsDelegate }
}
