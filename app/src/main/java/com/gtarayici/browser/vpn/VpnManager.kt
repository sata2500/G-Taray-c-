package com.gtarayici.browser.vpn

import com.gtarayici.browser.R

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
data class VpnServer(
    val id: String,
    val name: String,
    val protocol: String,
    val link: String,
    val username: String = "",
    val password: String = "",
) {
    val isOpenVpn: Boolean get() = protocol == OPENVPN
}

const val OPENVPN = "OpenVPN"

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
class VpnManager(private val context: Context, private val scope: CoroutineScope, private val core: VpnCore) {
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

    /**
     * Metni sunuculara çevirir ama kaydetmez (ekleme penceresindeki ön izleme için). OpenVPN yapılandırması
     * tek sunucudur; paylaşım bağlantıları ve abonelik içerikleri birden çok sunucu verebilir.
     */
    fun parse(input: String, username: String = "", password: String = ""): Result<List<VpnServer>> = runCatching {
        if (OpenVpnConfig.looksLike(input)) {
            OpenVpnConfig.sanitize(input) // Desteklenmeyen yapılandırmayı (ör. TAP) eklerken bildir.
            return@runCatching listOf(
                VpnServer(UUID.randomUUID().toString(), OpenVpnConfig.displayName(input), OPENVPN, input.trim(), username.trim(), password),
            )
        }
        val entries = splitInput(input)
        require(entries.isNotEmpty()) { "Bağlantı bulunamadı" }
        entries.map { raw ->
            val parsed = ShareLinkParser.parse(raw)
            VpnServer(UUID.randomUUID().toString(), parsed.name, parsed.protocol, raw)
        }
    }

    fun add(servers: List<VpnServer>) {
        if (servers.isEmpty()) return
        _servers.value = _servers.value + servers
        saveServers()
    }

    fun addServer(input: String, username: String = "", password: String = ""): Result<VpnServer> =
        parse(input, username, password).map { list -> add(list); list.first() }

    /** Sunucuya kullanıcının verdiği adı verir. */
    fun rename(server: VpnServer, name: String) {
        val clean = name.trim().ifBlank { return }
        _servers.value = _servers.value.map { if (it.id == server.id) it.copy(name = clean) else it }
        saveServers()
        (status.value as? VpnStatus.Connected)?.takeIf { it.server.id == server.id }?.let {
            _status.value = it.copy(server = it.server.copy(name = clean))
        }
    }

    /** "Geri al" ile silinen sunucuyu eski yerine koyar. */
    fun restore(server: VpnServer, index: Int) {
        if (_servers.value.any { it.id == server.id }) return
        _servers.value = _servers.value.toMutableList().apply { add(index.coerceIn(0, size), server) }
        saveServers()
    }

    /**
     * Abonelik adresindeki sunucu listesini indirir (sağlayıcıların verdiği https://… adresi). VPN açıksa
     * indirme VPN üzerinden yapılır.
     */
    suspend fun fetchSubscription(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://") || url.startsWith("http://")) { "Geçerli bir abonelik adresi değil" }
            val connection = URL(url).openConnection(javaProxy()) as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "G-Tarayici")
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Bağlı sunucuyu test eder ve sonucu o sunucu için hatırlar. */
    suspend fun testActive(): Result<VpnTestResult> {
        val server = (status.value as? VpnStatus.Connected)?.server
        return test().onSuccess { result -> server?.let { rememberTest(it.id, result) } }
    }

    /** Her sunucu için son bağlantı testinin sonucu (ülke, gecikme). */
    private val _lastTests = MutableStateFlow(loadTests())
    val lastTests: StateFlow<Map<String, VpnTestResult>> = _lastTests.asStateFlow()

    private fun rememberTest(serverId: String, result: VpnTestResult) {
        _lastTests.value = _lastTests.value + (serverId to result)
        val json = JSONObject()
        _lastTests.value.forEach { (id, r) ->
            json.put(id, JSONObject().put("ip", r.ip).put("country", r.country).put("latency", r.latencyMs))
        }
        prefs.edit { putString(KEY_TESTS, json.toString()) }
    }

    private fun loadTests(): Map<String, VpnTestResult> = runCatching {
        val json = JSONObject(prefs.getString(KEY_TESTS, "{}") ?: "{}")
        json.keys().asSequence().associateWith { id ->
            val o = json.getJSONObject(id)
            VpnTestResult(o.optString("ip").ifBlank { null }, o.optString("country").ifBlank { null }, o.optLong("latency"))
        }
    }.getOrDefault(emptyMap())

    fun removeServer(server: VpnServer) {
        if (activeServerId == server.id) disconnect()
        _servers.value = _servers.value.filterNot { it.id == server.id }
        saveServers()
        if (server.id in _lastTests.value) _lastTests.value = _lastTests.value - server.id
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
                // Bağlanınca çıkış ülkesi ve gecikme kendiliğinden ölçülür; kullanıcı ayrıca test etmek zorunda kalmaz.
                test().onSuccess { rememberTest(server.id, it) }
            }.onFailure { error ->
                logger.warn("VPN başlatılamadı", error)
                // Kapalı başarısızlık: vekil ayarı ölü bir porta yönlendirilir, trafik sızmaz.
                applyProxy(DEAD_PORT)
                _status.value = VpnStatus.Error(com.gtarayici.browser.ui.feedback.HumanErrors.detail(error))
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

    private suspend fun startProcess(server: VpnServer): Int =
        if (server.isOpenVpn) startOpenVpn(server) else startSingBox(server)

    /**
     * OpenVPN: gerçek OpenVPN istemcisi + kullanıcı alanı ağ yığını köprüsü. Köprü, bağlantı kurulunca
     * "GTOVPN READY" yazar; kimlik hatası "GTOVPN AUTH_FAILED" olarak bildirilir.
     */
    private suspend fun startOpenVpn(server: VpnServer): Int {
        val bridge = core.binary(VpnCore.OPENVPN_BRIDGE)
        val openvpn = core.binary(VpnCore.OPENVPN)
        check(bridge != null && openvpn != null) { context.getString(R.string.vpn_core_missing_error) }
        val dir = File(workDir, "openvpn").apply { deleteRecursively(); mkdirs() }
        val config = File(dir, "config.ovpn").apply { writeText(OpenVpnConfig.sanitize(server.link)) }
        val auth = File(dir, "auth.txt")
        if (OpenVpnConfig.needsCredentials(server.link)) {
            check(server.username.isNotBlank()) { "Bu OpenVPN sunucusu kullanıcı adı ve parola istiyor" }
            auth.writeText(server.username + "\n" + server.password + "\n")
            auth.setReadable(false, false)
            auth.setReadable(true, true)
        }
        val port = ServerSocket(0).use { it.localPort }
        val ready = kotlinx.coroutines.CompletableDeferred<Result<Int>>()
        val command = buildList {
            add(bridge.absolutePath)
            add("-openvpn"); add(openvpn.absolutePath)
            add("-config"); add(config.absolutePath)
            add("-workdir"); add(dir.absolutePath)
            add("-socks"); add("127.0.0.1:$port")
            if (auth.exists()) { add("-auth"); add(auth.absolutePath) }
        }
        launch(command) { line ->
            when {
                line.startsWith("GTOVPN READY") -> ready.complete(Result.success(port))
                line.startsWith("GTOVPN AUTH_FAILED") -> ready.complete(Result.failure(IllegalStateException("Kullanıcı adı veya parola hatalı")))
                line.startsWith("GTOVPN FATAL") -> ready.complete(Result.failure(IllegalStateException(line.removePrefix("GTOVPN FATAL").trim())))
                line.startsWith("GTOVPN EXIT") -> ready.complete(Result.failure(IllegalStateException(lastError() ?: "OpenVPN durdu")))
            }
        }
        return kotlinx.coroutines.withTimeoutOrNull(OPENVPN_TIMEOUT_MS) { ready.await() }?.getOrThrow()
            ?: error("OpenVPN sunucusuna bağlanılamadı (zaman aşımı)")
    }

    private fun lastError(): String? =
        log.value.lastOrNull { it.contains("AUTH_FAILED") || it.contains("FATAL") || it.contains("ERROR") || it.contains("error", true) }

    /** Süreci başlatır, çıktısını günlüğe yazar ve beklenmedik şekilde durursa sızıntıyı engeller. */
    private fun launch(command: List<String>, onLine: (String) -> Unit = {}): Process {
        val started = ProcessBuilder(command).redirectErrorStream(true).start()
        process = started
        Thread {
            // Bağlantı kesilirken süreç kapatılınca okuma "read interrupted by close()" ile biter; bu beklenen bir
            // durumdur. Yakalanmazsa bu iş parçacığı uygulamayı çökertiyordu ("Bağlantıyı kes").
            runCatching {
                started.inputStream.bufferedReader().forEachLine { line ->
                    appendLog(line)
                    onLine(line)
                }
            }
            val code = runCatching { started.waitFor() }.getOrDefault(-1)
            onLine("GTOVPN EXIT")
            if (process === started) {
                process = null
                scope.launch {
                    applyProxy(DEAD_PORT)
                    _status.value = VpnStatus.Error(lastError() ?: "VPN çekirdeği durdu (kod $code)")
                }
            }
        }.apply { isDaemon = true }.start()
        return started
    }

    private suspend fun startSingBox(server: VpnServer): Int {
        val binary = core.binary(VpnCore.SING_BOX)
        checkNotNull(binary) { context.getString(R.string.vpn_core_missing_error) }
        val port = ServerSocket(0).use { it.localPort }
        val config = File(workDir, "config.json")
        config.writeText(buildConfig(server, port, systemDns()).toString(2))

        val started = launch(listOf(binary.absolutePath, "run", "-c", config.absolutePath, "-D", workDir.absolutePath))

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
        File(workDir, "openvpn/auth.txt").delete()
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

    private fun splitInput(input: String): List<String> = splitVpnInput(input)

    private fun loadServers(): List<VpnServer> = runCatching {
        val array = JSONArray(prefs.getString(KEY_SERVERS, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VpnServer(
                o.getString("id"),
                o.getString("name"),
                o.getString("protocol"),
                o.getString("link"),
                o.optString("username"),
                o.optString("password"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveServers() {
        val array = JSONArray()
        _servers.value.forEach {
            array.put(
                JSONObject().put("id", it.id).put("name", it.name).put("protocol", it.protocol).put("link", it.link)
                    .put("username", it.username).put("password", it.password),
            )
        }
        prefs.edit { putString(KEY_SERVERS, array.toString()) }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE = "active"
        private const val KEY_TESTS = "last_tests"
        private const val KEY_ENABLED = "enabled"
        private const val MAX_LOG_LINES = 200
        private const val PROXY_NONE = 0
        private const val PROXY_MANUAL = 1
        private const val DEAD_PORT = 9
        private const val FALLBACK_DNS = "1.1.1.1"
        private const val OPENVPN_TIMEOUT_MS = 60_000L

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
