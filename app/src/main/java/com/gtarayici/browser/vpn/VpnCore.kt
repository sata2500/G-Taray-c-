package com.gtarayici.browser.vpn

import com.gtarayici.browser.ui.feedback.UiText
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.gtarayici.browser.BuildConfig
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.update.Apks
import com.gtarayici.browser.update.Updater
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VpnCoreState {
    data object Missing : VpnCoreState
    data class Downloading(val progress: Float) : VpnCoreState
    data object Installing : VpnCoreState
    data object Ready : VpnCoreState
    data class Failed(val message: String) : VpnCoreState
}

/**
 * Tarayıcı VPN'inin çekirdekleri (sing-box, OpenVPN ve köprüsü) ayrı bir "VPN bileşeni" paketinde gelir;
 * VPN'i kullanmayanlar ~20 MB indirmez. Bileşen ilk kullanımda GitHub sürümünden bir kez indirilir.
 *
 * Android 10'dan beri uygulamalar indirdikleri programları çalıştıramaz; bu yüzden çekirdekler
 * sistemin kurduğu bir paketin yerel kütüphane klasöründen çalıştırılır. Bileşenin yalnızca
 * G-Tarayıcı'nın anahtarıyla imzalanmış olanı kabul edilir.
 */
class VpnCore(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(if (isReady()) VpnCoreState.Ready else VpnCoreState.Missing)
    val state: StateFlow<VpnCoreState> = _state.asStateFlow()
    private var job: Job? = null

    /** Çekirdek dosyası: önce uygulamanın kendisinde (geliştirme derlemeleri), sonra güvenilir bileşende aranır. */
    fun binary(name: String): File? {
        File(context.applicationInfo.nativeLibraryDir, name).takeIf { it.exists() }?.let { return it }
        val dir = trustedCoreLibraryDir() ?: return null
        return File(dir, name).takeIf { it.exists() }
    }

    fun isReady(): Boolean = binary(SING_BOX) != null

    fun refresh() {
        if (_state.value is VpnCoreState.Downloading || _state.value is VpnCoreState.Installing) return
        _state.value = if (isReady()) VpnCoreState.Ready else VpnCoreState.Missing
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Bileşeni indirir ve kurar (kullanıcıdan bir kez onay istenir). */
    fun install() {
        if (job?.isActive == true) return
        if (!canInstall()) {
            context.components.messenger.info(UiText.res(R.string.vpn_core_permission))
            context.components.updater.requestInstallPermission()
            return
        }
        job = scope.launch {
            _state.value = VpnCoreState.Downloading(0f)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val suffix = "-VPN-${Updater.preferredAbi()}-core.apk"
                    val version = BuildConfig.VERSION_NAME.substringBefore('-')
                    // Önce bu sürümün bileşeni; bulunamazsa (ör. henüz yayınlanmamış sürüm) en son yayınlanan.
                    val release = Apks.fetchRelease(BuildConfig.UPDATE_REPO, "v$version")?.takeIf { Apks.asset(it, suffix) != null }
                        ?: Apks.fetchRelease(BuildConfig.UPDATE_REPO)
                    val (url, size) = release?.let { Apks.asset(it, suffix) }
                        ?: error(context.getString(R.string.vpn_core_not_found))
                    val dir = File(context.cacheDir, "vpn-core").apply { deleteRecursively(); mkdirs() }
                    val file = File(dir, "vpn-core.apk")
                    Apks.download(url, file, size) { _state.value = VpnCoreState.Downloading(it) }
                    verify(file)
                    _state.value = VpnCoreState.Installing
                    Apks.install(context, file, PACKAGE, VpnCoreInstallReceiver::class.java)
                }
            }
            result.onFailure { _state.value = VpnCoreState.Failed(com.gtarayici.browser.ui.feedback.HumanErrors.detail(it)) }
        }
    }

    internal fun onInstallFinished(error: String?) {
        _state.value = when {
            error != null -> VpnCoreState.Failed(error)
            isReady() -> VpnCoreState.Ready
            else -> VpnCoreState.Missing
        }
    }

    /** İndirilen paket gerçekten G-Tarayıcı'nın VPN bileşeni mi? */
    private fun verify(file: File) {
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error(context.getString(R.string.vpn_core_invalid))
        check(info.packageName == PACKAGE) { context.getString(R.string.vpn_core_invalid) }
        check(Apks.certificateDigests(info).any { it in trustedDigests() }) { context.getString(R.string.update_signature) }
    }

    /** Kurulu bileşen güvenilir anahtarla imzalı ve yeterince yeniyse yerel kütüphane klasörü. */
    private fun trustedCoreLibraryDir(): String? = runCatching {
        val info = context.packageManager.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
        if (info.longVersionCode < REQUIRED_VERSION) return null
        if (Apks.certificateDigests(info).none { it in trustedDigests() }) return null
        info.applicationInfo?.nativeLibraryDir
    }.getOrNull()

    /** Kalıcı sürüm anahtarı ve bu uygulamayı imzalayan anahtar(lar) (geliştirme derlemeleri için). */
    private fun trustedDigests(): Set<String> {
        val own = runCatching {
            Apks.certificateDigests(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
        }.getOrDefault(emptySet())
        return own + RELEASE_CERT_SHA256
    }

    companion object {
        const val PACKAGE = "com.gtarayici.vpncore"
        /** Bu uygulamanın beklediği en düşük bileşen sürümü (vpn-core/build.gradle.kts versionCode). */
        const val REQUIRED_VERSION = 1L
        const val SING_BOX = "libsingbox.so"
        const val OPENVPN = "libopenvpn.so"
        const val OPENVPN_BRIDGE = "libovpnbridge.so"
        private const val RELEASE_CERT_SHA256 = "811599952D2EC394C24CF1D9836D365930735A81F359A917C25688F54072D1AC"
    }
}

/** Bileşen kurulumunun sonucu. */
class VpnCoreInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val core = context.components.vpnCore
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> core.onInstallFinished(null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> core.onInstallFinished(null)
            else -> core.onInstallFinished(
                com.gtarayici.browser.ui.feedback.HumanErrors.message(com.gtarayici.browser.ui.feedback.ErrorArea.Install, com.gtarayici.browser.ui.feedback.HumanErrors.installDetail(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))).resolve(context),
            )
        }
    }
}
