package net.cubevpn.app

import org.junit.Test
import org.junit.Assert.assertEquals

/** Regression check: a VLESS remark with emoji + parens + a middle dot must decode intact. */
class ConfigParserVlessNameTest {
    @Test
    fun decodesMultiByteEmojiRemark() {
        val link = "vless://f19d89d8-c1cb-a59b-7782-824c0b33b29f@sub.novexcloud.ir:1151" +
            "?encryption=none&security=none&type=tcp&headerType=http&path=%2F" +
            "#%F0%9F%91%A4%EF%B8%8F%20%28v3.0.0%29%20%C2%B7%20cubesan"
        val cfg = ConfigParser.parse(link)
        assertEquals("👤️ (v3.0.0) · cubesan", cfg?.name)
        assertEquals("sub.novexcloud.ir", cfg?.address)
        assertEquals(1151, cfg?.port)
    }
}
