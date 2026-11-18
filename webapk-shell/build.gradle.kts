plugins {
    alias(libs.plugins.android.application)
}

// Ana uygulamaya gömülen küçük WebAPK şablonu. Kotlin kullanılmaz (boyut ~20 KB).
android {
    namespace = "com.gtarayici.webapkshell"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        // Yer tutucu paket adı; her web uygulaması için cihazda benzersiz bir adla değiştirilir.
        applicationId = "com.gtarayici.webapk.p0000000000000000000000000000000000000000000000000000000000"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Şablon cihazda yeniden imzalanır; buradaki imza yalnızca derleme içindir.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Yer tutucu metinler birebir korunmalı.
        noCompress += listOf("arsc")
    }
}

// Şablon saf Java'dır; AGP'nin varsayılan olarak eklediği Kotlin kütüphanesi gereksizdir.
configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}
