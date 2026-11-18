package com.gtarayici.browser.vpn

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkParserTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    private val samples = mapOf(
        "vless-reality" to "vless://$uuid@example.com:443?encryption=none&flow=xtls-rprx-vision&security=reality" +
            "&sni=www.microsoft.com&fp=chrome&pbk=jNXHt1yRo0vDuchQlIP6Z0ZvjT3KtzVI-T4E7RoLJS0&sid=0123abcd&type=tcp#Reality%20TR",
        "vless-ws" to "vless://$uuid@example.com:443?security=tls&sni=example.com&type=ws&path=%2Fws&host=example.com#WS",
        "vmess" to "vmess://" + Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"VMess","add":"1.2.3.4","port":"443","id":"$uuid","aid":"0","net":"grpc","path":"grpc","tls":"tls","sni":"a.com"}"""
                .toByteArray(),
        ),
        "trojan" to "trojan://secret@a.com:443?sni=a.com&allowInsecure=1#Trojan",
        "ss-sip002" to "ss://" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("chacha20-ietf-poly1305:pw".toByteArray()) + "@1.2.3.4:8388#SS",
        "ss-legacy" to "ss://" + Base64.getEncoder().encodeToString("aes-256-gcm:pw@5.6.7.8:8388".toByteArray()) + "#Old",
        "hy2" to "hy2://pw@a.com:443?sni=a.com&obfs=salamander&obfs-password=x#HY2",
        "tuic" to "tuic://$uuid:pw@a.com:443?sni=a.com&congestion_control=bbr&alpn=h3#TUIC",
        "socks5" to "socks5://user:p%40ss@10.0.0.1:1080#Ev",
        "http" to "http://proxy.example.com:3128#HTTP",
        "https" to "https://u:p@secure.example.com:443#HTTPS",
        "wireguard" to """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.0.0.2/32
            [Peer]
            PublicKey = xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=
            Endpoint = 1.2.3.4:51820
        """.trimIndent(),
    )

    @Test
    fun parsesAllProtocolsIntoValidSingBoxConfigs() {
        val outDir = File(System.getProperty("java.io.tmpdir"), "gtarayici-singbox-configs").apply { mkdirs() }
        samples.forEach { (name, link) ->
            val parsed = ShareLinkParser.parse(link)
            val config = VpnManager.buildConfig(VpnServer("id", parsed.name, parsed.protocol, link), 10808, "1.1.1.1")
            File(outDir, "$name.json").writeText(config.toString(2))
            assertTrue("$name: sunucu adı boş", parsed.name.isNotBlank())
        }
        assertEquals("Reality TR", ShareLinkParser.parse(samples.getValue("vless-reality")).name)
        assertEquals("pw", ShareLinkParser.parse(samples.getValue("ss-legacy")).json.getString("password"))
        assertEquals("5.6.7.8", ShareLinkParser.parse(samples.getValue("ss-legacy")).json.getString("server"))
        assertEquals("/ws", ShareLinkParser.parse(samples.getValue("vless-ws")).json.getJSONObject("transport").getString("path"))
    }
}
