package com.gtarayici.browser.vpn

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.edit
import com.gtarayici.browser.engine.GeckoPrefs
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.support.base.log.logger.Logger
import org.json.JSONArray
import org.json.JSONObject

/** Kullanıcının eklediği bir VPN sunucusu. [link] paylaşım bağlantısı, WireGuard metni veya JSON'dur. */
data class VpnServer(val id: String, val name: String, val protocol: String, val link: String)

sealed interface VpnStatus {
    data object Off : VpnStatus
    data object Connecting : VpnStatus
    data class Connected(val server: VpnServer, val port: Int) : VpnStatus
    data class Error(val message: String) : VpnStatus
}

data class VpnTestResult(val ip: String?, val country: String?, val latencyMs: Long)

/**
 * Tarayıcıya özel VPN. Cihaz genelinde VPN izni istemeden, uygulamanın içinde bir sing-box süreci
 * başlatır ve yalnızca tarayıcı trafiğini yerel bir SOCKS5 vekil sunucusu üzerinden geçirir.
 * Diğer uygulamalar ve telefondaki başka VPN'ler etkilenmez.
 *
 * Bağlantı koparsa trafik doğrudan internete düşmez (sızıntıya karşı "kapalı başarısızlık").
 */
class VpnManager(private val context: Context, private val scope: CoroutineScope) {
    private val logger = Logger("VpnManager")
    private val prefs = context.getSharedPreferences("gtarayici_vpn", Context.MODE_PRIVATE)
    private val workDir = File(context.filesDir, "vpn").apply { mkdirs() }

    private val _servers = MutableStateFlow(loadServers())
    val servers: StateFlow<List<VpnServer>> = _servers.asStateFlow()

    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Off)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    var activeServerId: String?
        get() = prefs.getString(KEY_ACTIVE, null)
        private set(value) = prefs.edit { putString(KEY_ACTIVE, value) }

    val enabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)

    @Volatile private var process: Process? = null

    /** Uygulama açılışında, kullanıcı VPN'i açık bıraktıysa yeniden bağlanır. */
    fun restore() {
        val server = activeServerId?.let { id -> servers.value.firstOrNull { it.id == id } }
        if (enabled && server != null) {
            connect(server)
        } else {
            // Önceki oturumdan kalmış olabilecek vekil ayarını temizle.
            prefs.edit { putBoolean(KEY_ENABLED, false) }
            clearProxy()
        }
    }

    fun addServer(input: String): Result<VpnServer> = runCatching {
        val entries = splitInput(input)
        require(entries.isNotEmpty()) { "Bağlantı bulunamadı" }
        val added = entries.map { raw ->
            val parsed = ShareLinkParser.parse(raw)
            VpnServer(UUID.randomUUID().toString(), parsed.name, parsed.protocol, raw)
        }
        _servers.value = _servers.value + added
        saveServers()
        added.first()
    }

    fun removeServer(server: VpnServer) {
        if (activeServerId == server.id) disconnect()
        _servers.value = _servers.value.filterNot { it.id == server.id }
        saveServers()
    }

    fun connect(server: VpnServer) {
        stopProcess()
        activeServerId = server.id
        prefs.edit { putBoolean(KEY_ENABLED, true) }
        _status.value = VpnStatus.Connecting
        _log.value = emptyList()
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { startProcess(server) } }
            result.onSuccess { port ->
                applyProxy(port)
                _status.value = VpnStatus.Connected(server, port)
            }.onFailure { error ->
                logger.warn("VPN başlatılamadı", error)
                // Kapalı başarısızlık: vekil ayarı ölü bir porta yönlendirilir, trafik sızmaz.
                applyProxy(DEAD_PORT)
                _status.value = VpnStatus.Error(error.message ?: "Bilinmeyen hata")
            }
        }
    }

    fun disconnect() {
        prefs.edit { putBoolean(KEY_ENABLED, false) }
        stopProcess()
        clearProxy()
        _status.value = VpnStatus.Off
    }

    /** Java ağ istekleri (ör. YouTube indirici) için vekil; VPN kapalıysa doğrudan bağlantı. */
    fun javaProxy(): Proxy {
        val connected = status.value as? VpnStatus.Connected ?: return if (enabled) blackhole() else Proxy.NO_PROXY
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", connected.port))
    }

    /** VPN üzerinden çıkış IP'sini ve gecikmeyi ölçer. */
    suspend fun test(): Result<VpnTestResult> = withContext(Dispatchers.IO) {
        runCatching {
            val started = System.currentTimeMillis()
            val connection = URL("https://cloudflare.com/cdn-cgi/trace").openConnection(javaProxy()) as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val values = body.lines().filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
            VpnTestResult(values["ip"], values["loc"], System.currentTimeMillis() - started)
        }
    }

    // --- Süreç yönetimi ------------------------------------------------------------------

    private suspend fun startProcess(server: VpnServer): Int {
        val binary = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")
        check(binary.exists()) { "VPN çekirdeği bu cihaz mimarisi için bulunamadı" }
        val port = ServerSocket(0).use { it.localPort }
        val config = File(workDir, "config.json")
        config.writeText(buildConfig(server, port, systemDns()).toString(2))

        val started = ProcessBuilder(binary.absolutePath, "run", "-c", config.absolutePath, "-D", workDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        process = started
        Thread {
            started.inputStream.bufferedReader().forEachLine { line -> appendLog(line) }
            val code = runCatching { started.waitFor() }.getOrDefault(-1)
            if (process === started) {
                process = null
                scope.launch {
                    applyProxy(DEAD_PORT)
                    val reason = log.value.lastOrNull { it.contains("FATAL") || it.contains("ERROR") }
                    _status.value = VpnStatus.Error(reason ?: "VPN çekirdeği durdu (kod $code)")
                }
            }
        }.apply { isDaemon = true }.start()

        // Yerel port dinlemeye başlayana kadar bekle.
        repeat(50) {
            if (!started.isAlive) error(log.value.lastOrNull() ?: "VPN çekirdeği başlatılamadı")
            if (runCatching { Socket("127.0.0.1", port).close() }.isSuccess) return port
            delay(100)
        }
        error("VPN çekirdeği yanıt vermedi")
    }

    private fun stopProcess() {
        val current = process ?: return
        process = null
        current.destroy()
    }

    private fun appendLog(line: String) {
        _log.value = (_log.value + line).takeLast(MAX_LOG_LINES)
    }

    private fun applyProxy(port: Int) {
        GeckoPrefs.set("network.proxy.socks", "127.0.0.1")
        GeckoPrefs.set("network.proxy.socks_port", port)
        GeckoPrefs.set("network.proxy.socks_version", 5)
        GeckoPrefs.set("network.proxy.socks_remote_dns", true)
        GeckoPrefs.set("network.proxy.socks5_remote_dns", true)
        GeckoPrefs.set("network.proxy.no_proxies_on", "")
        GeckoPrefs.set("network.proxy.type", PROXY_MANUAL)
    }

    private fun clearProxy() {
        GeckoPrefs.set("network.proxy.type", PROXY_NONE)
    }

    private fun blackhole() = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", DEAD_PORT))

    private fun systemDns(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val servers = runCatching { cm?.getLinkProperties(cm.activeNetwork)?.dnsServers }.getOrNull().orEmpty()
        return servers.firstOrNull { it is Inet4Address }?.hostAddress ?: FALLBACK_DNS
    }

    // --- Kalıcılık -----------------------------------------------------------------------

    private fun splitInput(input: String): List<String> {
        val text = input.trim()
        if (text.startsWith("{") || text.contains("[Interface]", ignoreCase = true)) return listOf(text)
        // Abonelik içeriği gibi birden fazla satırlık bağlantı listeleri (düz veya base64).
        val decoded = if (!text.contains("://")) {
            runCatching { String(java.util.Base64.getMimeDecoder().decode(text)) }.getOrDefault(text)
        } else {
            text
        }
        return decoded.lines().map(String::trim).filter { it.contains("://") }
    }

    private fun loadServers(): List<VpnServer> = runCatching {
        val array = JSONArray(prefs.getString(KEY_SERVERS, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VpnServer(o.getString("id"), o.getString("name"), o.getString("protocol"), o.getString("link"))
        }
    }.getOrDefault(emptyList())

    private fun saveServers() {
        val array = JSONArray()
        _servers.value.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("protocol", it.protocol).put("link", it.link))
        }
        prefs.edit { putString(KEY_SERVERS, array.toString()) }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE = "active"
        private const val KEY_ENABLED = "enabled"
        private const val MAX_LOG_LINES = 200
        private const val PROXY_NONE = 0
        private const val PROXY_MANUAL = 1
        private const val DEAD_PORT = 9
        private const val FALLBACK_DNS = "1.1.1.1"

        /** sing-box yapılandırması: yerel "mixed" (SOCKS5/HTTP) giriş, seçilen sunucu çıkış. */
        fun buildConfig(server: VpnServer, port: Int, dnsServer: String): JSONObject {
            val parsed = ShareLinkParser.parse(server.link)
            val proxy = JSONObject(parsed.json.toString()).put("tag", "proxy")
            val config = JSONObject()
                .put("log", JSONObject().put("level", "warn").put("timestamp", false))
                .put(
                    "dns",
                    JSONObject().put(
                        "servers",
                        JSONArray().put(JSONObject().put("type", "udp").put("tag", "bootstrap").put("server", dnsServer)),
                    ),
                )
                .put(
                    "inbounds",
                    JSONArray().put(
                        JSONObject().put("type", "mixed").put("tag", "local-in").put("listen", "127.0.0.1").put("listen_port", port),
                    ),
                )
                .put(
                    "route",
                    JSONObject()
                        .put("final", "proxy")
                        .put("default_domain_resolver", JSONObject().put("server", "bootstrap")),
                )
            val direct = JSONObject().put("type", "direct").put("tag", "direct")
            if (parsed.isEndpoint) {
                config.put("endpoints", JSONArray().put(proxy))
                config.put("outbounds", JSONArray().put(direct))
            } else {
                config.put("outbounds", JSONArray().put(proxy).put(direct))
            }
            return config
        }
    }
}
