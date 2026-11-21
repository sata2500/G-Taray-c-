// Baseline Profile üreticisi ve açılış ölçümü (macrobenchmark). Bağlı bir cihazda çalışır:
//   ./gradlew :app:generateBaselineProfile        → app/src/release/generated/baselineProfiles/ altına profil yazar
//   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  → profilli/profilsiz açılış süresi
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.gtarayici.browser.baselineprofile"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(21)
}

baselineProfile {
    // Öykünücü yerine bağlı gerçek cihaz kullanılır.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}

// Uygulamayla aynı kural: GeckoView glean-native'i kendi içinde barındırır; çakışmada GeckoView seçilir.
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("org.mozilla.telemetry:glean-native") {
        val gecko = candidates.firstOrNull {
            (it.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)?.module?.contains("geckoview") == true
        }
        if (gecko != null) select(gecko)
        because("GeckoView glean-native'i zaten içerir")
    }
}
