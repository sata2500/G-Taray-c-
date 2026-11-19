import java.util.Properties
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gtarayici.browser"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.gtarayici.browser"
        // GeckoView, Android 8.0 (API 26) ve üzerini destekler.
        minSdk = 26
        targetSdk = 37
        versionCode = 10
        versionName = "0.4.5"
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
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Mozilla Android Components (GeckoView motoru dahil)
    implementation(libs.mozilla.browser.engine.gecko)
    implementation(libs.mozilla.browser.state)
    implementation(libs.mozilla.browser.session.storage)
    implementation(libs.mozilla.browser.icons)
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
