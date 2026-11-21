package com.gtarayici.browser.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnLabelsTest {
    @Test
    fun readableProtocolsAndFlags() {
        assertEquals("VLESS", VpnLabels.protocol("vless"))
        assertEquals("Hysteria 2", VpnLabels.protocol("hysteria2"))
        assertEquals("SOCKS5 vekil sunucu", VpnLabels.protocol("socks"))
        assertEquals("OpenVPN", VpnLabels.protocol(OPENVPN))
        assertEquals("🇩🇪", VpnLabels.flag("de"))
        assertEquals("", VpnLabels.flag("T1"))
        assertEquals("Almanya", VpnLabels.country("DE"))
    }
}
