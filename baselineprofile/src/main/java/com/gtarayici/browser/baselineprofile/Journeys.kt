package com.gtarayici.browser.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Ölçülen uygulama: release yapılandırmasının ayrı paket adlı kopyası (gerçek kurulu sürüme dokunulmaz). */
const val TARGET_PACKAGE = "com.gtarayici.browser.benchmark"

private const val WAIT_MS = 5_000L

/** İlk açılış tanıtımı çıkarsa geçilir (yeni kurulumda bir kez gösterilir). */
fun MacrobenchmarkScope.skipOnboarding() {
    device.wait(Until.findObject(By.text("Atla")), 2_000L)?.click()
    device.waitForIdle()
}

/** En sık kullanılan yollar: menü, sekmeler ve ayarlar açılıp kapanır. Her adım bulunamazsa atlanır. */
fun MacrobenchmarkScope.commonJourney() {
    device.wait(Until.findObject(By.desc("Menü")), WAIT_MS)?.let { menu ->
        menu.click()
        device.wait(Until.findObject(By.text("Ayarlar")), WAIT_MS)?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
    device.wait(Until.findObject(By.descContains("sekme")), WAIT_MS)?.let { tabs ->
        tabs.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }
}
