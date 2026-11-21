package com.gtarayici.browser.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Açılış ve sık kullanılan ekranların kod yollarını toplayıp Baseline Profile üretir. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    // Her yinelemede Gecko içeren büyük APK yeniden derlendiği için yineleme sayısı sınırlı tutulur (~7 dk/yineleme).
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        maxIterations = 3,
        stableIterations = 2,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        skipOnboarding()
        commonJourney()
    }
}
