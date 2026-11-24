import java.util.Properties
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.gtarayici.browser"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.gtarayici.browser"
        // Android 9+: sürümler yalnızca APK İmza Şeması v3 (anahtar döndürme) ile imzalanır.
        minSdk = 28
        targetSdk = 37
        versionCode = 32
        versionName = "0.16.3"
        // Uygulama içi güncellemelerin alındığı GitHub deposu (sürümler GitHub Releases'ta yayınlanır).
        buildConfigField("String", "UPDATE_REPO", "\"sata2500/G-Taray-c-\"")
        // Anonim sorun bildirimi aracısının adresi (tools/report-relay). GitHub'da "GT_REPORT_RELAY_URL" depo
        // değişkeninden gelir; tanımlı değilse uygulamada anonim gönderim seçeneği görünmez.
        val relay = (System.getenv("GT_REPORT_RELAY_URL") ?: providers.gradleProperty("gtReportRelayUrl").orNull ?: "")
            .trim().trimEnd('/').filter { it.isLetterOrDigit() || it in ":/.-_" }
        buildConfigField("String", "REPORT_RELAY_URL", "\"$relay\"")
    }

    // Kalıcı imza anahtarı keystore.properties (repoya girmez) veya ortam değişkenlerinden okunur.
    // Bulunamazsa release sürümü debug anahtarıyla imzalanır (yalnızca deneme amaçlı).
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
    val releaseStoreFile = signingValue("storeFile", "GT_KEYSTORE_FILE")

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "GT_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "GT_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "GT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        // Baseline Profile üretimi ve ölçüm için (baselineprofile eklentisi bu adlı yapı türlerini kullanır).
        // Ayrı paket adı: telefonda kurulu gerçek sürümün (com.gtarayici.browser) üzerine kurulmaz, verisine dokunulmaz.
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Her işlemci mimarisi için ayrı, küçük bir APK üretilir (GeckoView yerel kodu büyüktür).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Yerel kütüphaneleri sıkıştırarak APK indirme boyutunu yarıdan fazla küçültür.
        jniLibs.useLegacyPackaging = true
        // Mozilla app-services (megazord: nimbus, remotesettings, search…) ve çökme raporlama araçları uygulamada
        // kullanılmıyor; çıkarılınca paket ~10 MB küçülür. Cihazda doğrulandı (test planı 14. bölüm): YouTube,
        // indirme, eklentiler, çeviri, okuma görünümü ve dosya indirme çalışıyor, UnsatisfiedLinkError yok.
        jniLibs.excludes += setOf("**/libmegazord.so", "**/libcrashtools.so")
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Açılış ve sık kullanılan ekranlar için önceden derlenmiş kod profili (baselineprofile modülü üretir).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    // Mozilla Android Components (GeckoView motoru dahil)
    implementation(libs.mozilla.browser.engine.gecko)
    implementation(libs.mozilla.browser.state)
    implementation(libs.mozilla.browser.session.storage)
    implementation(libs.mozilla.browser.icons)
    implementation(libs.mozilla.browser.errorpages)
    implementation(libs.mozilla.browser.thumbnails)
    implementation(libs.mozilla.concept.engine)
    implementation(libs.mozilla.concept.fetch)
    implementation(libs.mozilla.feature.session)
    implementation(libs.mozilla.feature.tabs)
    implementation(libs.mozilla.feature.downloads)
    implementation(libs.mozilla.feature.prompts)
    implementation(libs.mozilla.feature.contextmenu)
    implementation(libs.mozilla.feature.sitepermissions)
    implementation(libs.mozilla.feature.media)
    implementation(libs.mozilla.feature.pwa)
    implementation(libs.mozilla.feature.intent)
    implementation(libs.mozilla.feature.privatemode)
    implementation(libs.mozilla.feature.readerview)
    implementation(libs.mozilla.support.webextensions)
    implementation(libs.mozilla.support.base)
    implementation(libs.mozilla.support.ktx)
    implementation(libs.mozilla.support.utils)

    // AndroidX ve Compose
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.material)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.androidx.swiperefreshlayout)
    // Cihazda üretilen web uygulaması APK'larını imzalamak için (Apache-2.0)
    implementation(libs.apksig)

    // YouTube video çözümleme (GPL-3.0)
    implementation(libs.newpipe.extractor)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// GeckoView, glean-native kütüphanesini kendi içinde barındırır; çakışmada GeckoView seçilir.
configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("org.mozilla.telemetry:glean-native") {
        val gecko = candidates.firstOrNull {
            (it.id as? ModuleComponentIdentifier)?.module?.contains("geckoview") == true
        }
        if (gecko != null) select(gecko)
        because("GeckoView glean-native'i zaten içerir")
    }
}

/** WebAPK şablonunu (webapk-shell modülü) uygulamanın assets/webapk/shell.apk dosyası olarak paketler. */
abstract class BundleWebApkShell : DefaultTask() {
    @get:InputFiles
    abstract val shellApks: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile.resolve("webapk")
        out.deleteRecursively()
        out.mkdirs()
        val apk = shellApks.asFileTree.matching { include("*.apk") }.singleFile
        apk.copyTo(out.resolve("shell.apk"), overwrite = true)
    }
}

val bundleWebApkShell = tasks.register<BundleWebApkShell>("bundleWebApkShell") {
    dependsOn(":webapk-shell:assembleRelease")
    shellApks.from(rootProject.layout.projectDirectory.dir("webapk-shell/build/outputs/apk/release"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleWebApkShell, BundleWebApkShell::outputDir)
    }
}
