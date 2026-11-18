package com.gtarayici.browser.vpn

/**
 * OpenVPN (.ovpn) yapılandırmalarını tanır ve tarayıcıya özel çalışma için hazırlar.
 *
 * Sağlayıcı dosyalarındaki betik çalıştırma, günlük dosyası, kullanıcı değiştirme gibi masaüstüne özgü
 * yönergeler çıkarılır; kullanıcı adı/parola dosya yerine yönetim kanalından (köprü) sağlanır.
 * Satır içi bloklar (<ca>, <cert>, <key>, <tls-auth>, <tls-crypt> …) olduğu gibi korunur.
 */
object OpenVpnConfig {
    class UnsupportedException(message: String) : Exception(message)

    /** Kaldırılan yönergeler: güvenlik (betik), masaüstü işletim sistemi veya yönetim kanalıyla çakışanlar. */
    private val DROPPED = setOf(
        "management", "management-client", "management-hold", "management-query-passwords",
        "management-signal", "management-forget-disconnect", "management-external-key",
        "daemon", "log", "log-append", "syslog", "writepid", "status", "cd", "chroot", "user", "group",
        "script-security", "up", "down", "route-up", "route-pre-down", "ipchange", "tls-verify",
        "learn-address", "client-connect", "client-disconnect", "plugin", "dev-node", "iproute",
        "redirect-gateway", "block-outside-dns", "register-dns", "service", "win-sys", "ip-win32",
        "route-method", "mute-replay-warnings", "askpass", "auth-user-pass", "allow-compression",
    )

    fun looksLike(text: String): Boolean {
        val lines = directives(text).map { it.first }.toSet()
        return "remote" in lines && ("client" in lines || "tls-client" in lines || text.contains("<ca>"))
    }

    fun needsCredentials(text: String): Boolean = directives(text).any { it.first == "auth-user-pass" }

    fun needsKeyPassword(text: String): Boolean = text.contains("ENCRYPTED PRIVATE KEY") || text.contains("Proc-Type: 4,ENCRYPTED")

    /** Görünen ad: ilk "remote" sunucusu. */
    fun displayName(text: String): String =
        directives(text).firstOrNull { it.first == "remote" }?.second?.split(Regex("\\s+"))?.firstOrNull()
            ?: "OpenVPN"

    fun sanitize(text: String): String {
        val out = StringBuilder()
        var inBlock: String? = null
        var hasAuth = false
        text.replace("\r", "").lines().forEach { raw ->
            val line = raw.trim()
            if (inBlock != null) {
                out.append(raw).append('\n')
                if (line.equals("</$inBlock>", ignoreCase = true)) inBlock = null
                return@forEach
            }
            val open = Regex("^<([a-zA-Z0-9-]+)>$").find(line)
            if (open != null) {
                inBlock = open.groupValues[1]
                out.append(raw).append('\n')
                return@forEach
            }
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
            val name = line.split(Regex("\\s+"), limit = 2).first().lowercase()
            if (name == "dev" && line.contains("tap", ignoreCase = true)) {
                throw UnsupportedException("TAP (dev tap) bağlantıları desteklenmiyor; yalnızca TUN")
            }
            if (name == "auth-user-pass") hasAuth = true
            if (name in DROPPED) return@forEach
            out.append(line).append('\n')
        }
        if (hasAuth) out.append("auth-user-pass\n")
        // Sunucu sıkıştırma isterse (eski yapılandırmalar) bağlantı kopmasın; gönderilen veri sıkıştırılmaz.
        out.append("allow-compression asym\n")
        // Erişilemeyen sunucuda sonsuza dek beklenmesin.
        out.append("connect-retry-max 3\n")
        out.append("resolv-retry 15\n")
        return out.toString()
    }

    /** Satır içi bloklar hariç (yönerge, değer) çiftleri. */
    private fun directives(text: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var inBlock = false
        text.replace("\r", "").lines().forEach { raw ->
            val line = raw.trim()
            when {
                inBlock -> if (line.startsWith("</")) inBlock = false
                line.matches(Regex("^<[a-zA-Z0-9-]+>$")) -> inBlock = true
                line.isEmpty() || line.startsWith("#") || line.startsWith(";") -> Unit
                else -> {
                    val parts = line.split(Regex("\\s+"), limit = 2)
                    result += parts[0].lowercase() to parts.getOrElse(1) { "" }
                }
            }
        }
        return result
    }
}
