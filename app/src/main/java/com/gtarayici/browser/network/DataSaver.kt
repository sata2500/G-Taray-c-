package com.gtarayici.browser.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.gtarayici.browser.engine.GeckoPrefs
import com.gtarayici.browser.settings.AppSettings
import com.gtarayici.browser.settings.CacheSize
import com.gtarayici.browser.settings.DataSaverMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Varsayılan ağın veri tasarrufunu ilgilendiren özellikleri. */
data class LinkInfo(
    /** Mobil veri, paylaşılan erişim noktası gibi kotalı bağlantı. */
    val metered: Boolean,
    /** Sistemin tahmini indirme hızı (kbit/sn); 0 bilinmiyor demektir. */
    val downstreamKbps: Int,
    /** Android'in kendi "Veri Tasarrufu" ayarı bu uygulama için açık. */
    val systemSaver: Boolean,
)

/** Veri tasarrufunun ne zaman açılacağı ve önbelleğin ne kadar büyük olacağı (test edilebilir saf kurallar). */
object DataSaverPolicy {
    /** Bu hızın altındaki bağlantı "yavaş" sayılır (kbit/sn, ~2G/zayıf 3G). */
    const val SLOW_KBPS = 1_500

    fun active(mode: DataSaverMode, link: LinkInfo?): Boolean = when (mode) {
        DataSaverMode.OFF -> false
        DataSaverMode.ON -> true
        DataSaverMode.AUTO -> link != null &&
            (link.systemSaver || link.metered || link.downstreamKbps in 1 until SLOW_KBPS)
    }

    /**
     * Önbellek boyutu (KB). Seçilen boyut boş alanın onda birini geçmez; telefon dolarsa önbellek de küçülür.
     * [CacheSize.AUTO] için null döner (Gecko'nun kendi hesabı).
     */
    fun cacheCapacityKb(size: CacheSize, freeBytes: Long): Int? {
        if (size == CacheSize.AUTO) return null
        val wanted = size.megabytes.toLong() * 1024
        val allowed = (freeBytes / 10 / 1024).coerceAtLeast(MIN_CACHE_KB)
        return minOf(wanted, allowed).toInt()
    }

    private const val MIN_CACHE_KB = 50L * 1024
}

/**
 * Yavaş veya kotalı bağlantıda veriden ve pilden tasarruf eder; sayfa önbelleğinin boyutunu ayarlar.
 *
 * Veri tasarrufu açıkken:
 *  - Sessiz videolar da kendiliğinden oynamaz (en çok veri harcayan şey budur); izin verilen siteler hariç.
 *  - Videolar dokunulana kadar yalnızca bilgi kısmını indirir.
 *  - Tarayıcı, tıklanacağını tahmin ettiği bağlantıları önceden indirmez ve önceden bağlanmaz.
 *  - Sitelere `Save-Data: on` başlığı gönderilir; destekleyen siteler hafif sürüm sunar.
 *  - İsteğe bağlı olarak resimler yüklenmez.
 */
class DataSaver(
    private val context: Context,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private val _link = MutableStateFlow<LinkInfo?>(null)
    val link: StateFlow<LinkInfo?> = _link.asStateFlow()

    private val _active = MutableStateFlow(false)

    /** Veri tasarrufu şu an uygulanıyor mu (ayar + bağlantı). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun start() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        if (connectivity != null) {
            runCatching {
                connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        _link.value = LinkInfo(
                            metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                            downstreamKbps = capabilities.linkDownstreamBandwidthKbps,
                            systemSaver = connectivity.restrictBackgroundStatus ==
                                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
                        )
                    }

                    override fun onLost(network: Network) {
                        _link.value = null
                    }
                }, Handler(Looper.getMainLooper()))
            }
        }
        scope.launch {
            combine(settings.dataSaver, settings.dataSaverBlockImages, _link) { mode, images, link ->
                DataSaverPolicy.active(mode, link) to images
            }.distinctUntilChanged().collect { (on, images) ->
                // Bağlantı kısa süre koparsa (link = null) son durum korunur; ağ değişince yeniden hesaplanır.
                if (_link.value == null && settings.dataSaver.value == DataSaverMode.AUTO) return@collect
                apply(on, images)
            }
        }
        scope.launch { settings.cacheSize.collect { applyCache(it) } }
    }

    private fun apply(on: Boolean, blockImages: Boolean) {
        _active.value = on
        if (on) {
            // 0 hiçbiri, 1 yalnızca bilgi (süre, boyut), 2 tamamı.
            GeckoPrefs.set("media.preload.default", 1)
            GeckoPrefs.set("media.preload.auto", 1)
            GeckoPrefs.set("network.prefetch-next", false)
            GeckoPrefs.set("network.dns.disablePrefetch", true)
            GeckoPrefs.set("network.predictor.enabled", false)
            GeckoPrefs.set("network.http.speculative-parallel-limit", 0)
        } else {
            PREFS.forEach(GeckoPrefs::clear)
        }
        if (on && blockImages) GeckoPrefs.set("permissions.default.image", IMAGES_BLOCKED) else GeckoPrefs.clear("permissions.default.image")
    }

    private fun applyCache(size: CacheSize) {
        scope.launch {
            val free = withContext(Dispatchers.IO) { runCatching { context.cacheDir.usableSpace }.getOrDefault(0L) }
            val capacity = DataSaverPolicy.cacheCapacityKb(size, free)
            if (capacity == null) {
                GeckoPrefs.clear("browser.cache.disk.smart_size.enabled")
                GeckoPrefs.clear("browser.cache.disk.capacity")
            } else {
                GeckoPrefs.set("browser.cache.disk.smart_size.enabled", false)
                GeckoPrefs.set("browser.cache.disk.capacity", capacity)
            }
        }
    }

    /** Sayfa önbelleğinin diskte kapladığı yer (bayt). */
    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) {
        cacheDirs().sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    // Gecko disk önbelleği profilin önbellek klasöründeki "cache2" dizinlerindedir.
    private fun cacheDirs(): List<File> =
        context.cacheDir.walkTopDown().maxDepth(5).filter { it.isDirectory && it.name == "cache2" }.toList()

    private companion object {
        const val IMAGES_BLOCKED = 2
        val PREFS = listOf(
            "media.preload.default",
            "media.preload.auto",
            "network.prefetch-next",
            "network.dns.disablePrefetch",
            "network.predictor.enabled",
            "network.http.speculative-parallel-limit",
        )
    }
}
