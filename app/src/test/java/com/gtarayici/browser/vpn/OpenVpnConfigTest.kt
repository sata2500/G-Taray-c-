package com.gtarayici.browser.vpn

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OpenVpnConfigTest {
    @Test
    fun detectsAndSanitizesProviderConfig() {
        val source = File("/opt/ovpn-test/client.ovpn")
        assumeTrue(source.exists())
        // Sağlayıcı dosyalarında sık görülen, masaüstüne özgü veya tehlikeli satırlar:
        val raw = source.readText().replace(
            "auth-user-pass",
            "auth-user-pass /etc/openvpn/pass.txt\nscript-security 2\nup /etc/openvpn/update-resolv-conf\n" +
                "down /etc/openvpn/update-resolv-conf\nlog /var/log/openvpn.log\nuser nobody\ngroup nogroup\n" +
                "redirect-gateway def1\nblock-outside-dns\n# yorum\ncomp-lzo",
        )
        assertTrue(OpenVpnConfig.looksLike(raw))
        assertTrue(OpenVpnConfig.needsCredentials(raw))
        assertEquals("127.0.0.1", OpenVpnConfig.displayName(raw))
        val clean = OpenVpnConfig.sanitize(raw)
        listOf("script-security", "\nup ", "\ndown ", "\nlog ", "\nuser ", "\ngroup ", "pass.txt", "redirect-gateway").forEach {
            assertFalse("Kaldırılmalıydı: $it", clean.contains(it))
        }
        assertTrue(clean.contains("\nauth-user-pass\n"))
        assertTrue(clean.contains("<tls-crypt>") && clean.contains("</tls-crypt>"))
        assertTrue(clean.contains("allow-compression asym"))
        File("/opt/ovpn-test/sanitized.ovpn").writeText(clean)
    }

    @Test(expected = OpenVpnConfig.UnsupportedException::class)
    fun rejectsTap() {
        OpenVpnConfig.sanitize("client\ndev tap\nremote a.com 1194\n")
    }
}
