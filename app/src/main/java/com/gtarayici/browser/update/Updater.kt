package com.gtarayici.browser.update

import com.gtarayici.browser.components
import com.gtarayici.browser.ui.feedback.UiText
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import com.gtarayici.browser.BuildConfig
import com.gtarayici.browser.R
import com.gtarayici.browser.settings.AppSettings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Release(
    val version: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Uygulama içi güncelleme. Yeni sürümler GitHub Releases'tan denetlenir; cihazın işlemci mimarisine
 * uygun APK indirilir, aynı anahtarla imzalandığı doğrulanır ve Android'in yükleyicisiyle kurulur.
 * Denetim günde en fazla bir kez yapılır ve ayarlardan kapatılabilir. Hiçbir kişisel veri gönderilmez.
 */
class Updater(private val context: Context, private val settings: AppSettings, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Yeni sürüm penceresinin gösterilip gösterilmeyeceği ("Sonra" ile kapatılır). */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private val prefs = context.getSharedPreferences("gtarayici_state", Context.MODE_PRIVATE)
    private var job: Job? = null

    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Açılışta çağrılır: otomatik denetim açıksa ve son denetimden bu yana bir gün geçtiyse denetler. */
    fun checkIfDue() {
        if (BuildConfig.DEBUG || !settings.autoUpdateCheck.value) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < TimeUnit.HOURS.toMillis(CHECK_INTERVAL_HOURS)) return
        check(userInitiated = false)
    }

    fun check(userInitiated: Boolean = true) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            prefs.edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
            result.fold(
                onSuccess = { release ->
                    if (release != null && isNewer(release.version, currentVersion)) {
                        _state.value = UpdateState.Available(release)
                        // Kullanıcının "Sonra" dediği sürüm kendiliğinden tekrar sorulmaz.
                        if (userInitiated || prefs.getString(KEY_SKIPPED, null) != release.version) _prompt.value = true
                    } else {
                        _state.value = UpdateState.UpToDate
                    }
                },
                onFailure = { error ->
                    _state.value = if (userInitiated) UpdateState.Failed(errorMessage(error)) else UpdateState.Idle
                },
            )
        }
    }

    fun later() {
        (state.value as? UpdateState.Available)?.let { prefs.edit { putString(KEY_SKIPPED, it.release.version) } }
        _prompt.value = false
        if (state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    fun dismissError() {
        if (state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** İndirir, doğrular ve kurar. */
    fun install(release: Release) {
        if (!canInstall()) {
            context.components.messenger.info(UiText.res(R.string.update_permission))
            requestInstallPermission()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _prompt.value = true
            _state.value = UpdateState.Downloading(release, 0f)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = download(release)
                    verify(file)
                    commit(file, release)
                }
            }
            _state.value = result.fold(
                onSuccess = { UpdateState.Installing },
                onFailure = { UpdateState.Failed(errorMessage(it)) },
            )
        }
    }

    private fun fetchLatest(): Release? {
        val json = Apks.fetchRelease(BuildConfig.UPDATE_REPO) ?: throw UpdateException(context.getString(R.string.update_no_release))
        return parseRelease(json, preferredAbi())
    }

    private fun download(release: Release): File {
        val dir = File(context.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "update.apk")
        Apks.download(release.apkUrl, file, release.apkSize) { progress ->
            _state.value = UpdateState.Downloading(release, progress)
        }
        return file
    }

    /** İndirilen APK bu uygulamanın daha yeni bir sürümü mü ve aynı (veya ondan devralınmış) anahtarla mı imzalı? */
    private fun verify(file: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateException(context.getString(R.string.update_invalid))
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.packageName != context.packageName || archive.longVersionCode <= installed.longVersionCode) {
            throw UpdateException(context.getString(R.string.update_invalid))
        }
        val archiveCerts = archive.signingInfo?.signingCertificateHistory.orEmpty().toSet()
        val ownCerts = installed.signingInfo?.signingCertificateHistory.orEmpty()
        if (ownCerts.none { it in archiveCerts }) {
            throw UpdateException(context.getString(R.string.update_signature))
        }
    }

    private fun commit(file: File, release: Release) {
        Apks.install(context, file, context.packageName, UpdateInstallReceiver::class.java) {
            putExtra(UpdateInstallReceiver.EXTRA_VERSION, release.version)
        }
    }

    /** Yükleme yarıda kaldığında arayüzü sıfırlar. */
    internal fun onInstallFinished(message: String?) {
        _state.value = if (message == null) UpdateState.Idle else UpdateState.Failed(message)
        if (message == null) _prompt.value = false
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is UpdateException -> error.message.orEmpty()
        is Apks.HttpException -> context.getString(R.string.update_server_error, error.code)
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            context.getString(R.string.update_offline)
        else -> com.gtarayici.browser.ui.feedback.HumanErrors.message(com.gtarayici.browser.ui.feedback.ErrorArea.Update, error).resolve(context)
    }

    private class UpdateException(message: String) : Exception(message)

    companion object {
        private const val KEY_LAST_CHECK = "update_last_check"
        private const val KEY_SKIPPED = "update_skipped_version"
        private const val CHECK_INTERVAL_HOURS = 24L
        private val ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

        fun preferredAbi(): String = Build.SUPPORTED_ABIS.firstOrNull { it in ABIS } ?: ABIS.first()

        /** GitHub sürüm yanıtından bu cihaza uygun APK'yı seçer. */
        internal fun parseRelease(json: JSONObject, abi: String): Release? {
            val version = json.optString("tag_name").removePrefix("v").ifBlank { return null }
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                // VPN bileşeni ayrı pakettir, uygulama güncellemesi değildir.
                if (name.endsWith("-$abi.apk") && !name.contains("-VPN-")) {
                    return Release(
                        version = version,
                        notes = json.optString("body").trim(),
                        apkName = name,
                        apkUrl = asset.getString("browser_download_url"),
                        apkSize = asset.optLong("size"),
                    )
                }
            }
            return null
        }

        /** "0.10.1" > "0.9.3" gibi sayısal karşılaştırma. */
        internal fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** Kurulum sonucu: onay gerekiyorsa sistem penceresini açar, hata olursa arayüze bildirir. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as com.gtarayici.browser.GTarayiciApplication).components.updater
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> updater.onInstallFinished(null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onInstallFinished(null)
            else -> {
                val detail = com.gtarayici.browser.ui.feedback.HumanErrors.installDetail(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                updater.onInstallFinished(com.gtarayici.browser.ui.feedback.HumanErrors.message(com.gtarayici.browser.ui.feedback.ErrorArea.Install, detail).resolve(context))
            }
        }
    }

    companion object {
        const val EXTRA_VERSION = "version"
    }
}
