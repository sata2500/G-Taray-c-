plugins {
    alias(libs.plugins.android.application)
}

// G-Tarayıcı VPN bileşeni: yalnızca VPN çekirdeklerini (sing-box, OpenVPN ve köprüsü) taşıyan, kodsuz paket.
// Ana uygulama VPN'i ilk kullanımda bu paketi indirip kurar ve çekirdekleri onun yerel kütüphane
// klasöründen çalıştırır (Android 10+ indirilen programların çalıştırılmasına izin vermez).
android {
    namespace = "com.gtarayici.vpncore"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.gtarayici.vpncore"
        minSdk = 28
        targetSdk = 37
        // Çekirdek sürümü değişince artırılır; ana uygulamadaki VpnCore.REQUIRED_VERSION ile birlikte.
        versionCode = 1
        versionName = "1 (sing-box 1.14.4, OpenVPN 2.7.8)"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sürüm derlemesinde scripts/sign-release.sh kalıcı anahtarla yeniden imzalar.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        // Çekirdekler çalıştırılabilmeleri için kurulumda diske açılmalıdır.
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}
