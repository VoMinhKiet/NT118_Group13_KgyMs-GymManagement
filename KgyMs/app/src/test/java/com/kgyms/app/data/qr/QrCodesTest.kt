package com.kgyms.app.data.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodesTest {

    @Test fun crc16MatchesCcittFalseCheckValue() {
        // Giá trị kiểm tra chuẩn của CRC-16/CCITT-FALSE.
        assertEquals("29B1", VietQr.crc16("123456789"))
    }

    @Test fun vietQrPayloadHasExpectedFieldsAndValidCrc() {
        val payload = VietQr.payload("970436", "9793704700", 5000, "KGYMS KGABCD2345")
        assertTrue(payload.startsWith("000201010212"))
        assertTrue(payload.contains("0010A000000727"))
        assertTrue(payload.contains("0006970436" + "01109793704700"))
        assertTrue(payload.contains("0208QRIBFTTA"))
        assertTrue(payload.contains("54045000"))
        assertTrue(payload.contains("62200816KGYMS KGABCD2345"))
        val body = payload.dropLast(4)
        assertTrue(body.endsWith("6304"))
        assertEquals(VietQr.crc16(body), payload.takeLast(4))
    }

    @Test fun checkInSignatureMatchesToolHtml() {
        // Cùng giá trị với HMAC trong tool/src/qr.js (đã đối chiếu bằng Python hmac).
        val secret = "ee94784446ef71dda988423685e0cfb981d726a4"
        assertEquals("KGYMS-CHECKIN:59612345:bbbe4448", CheckInCode.build(secret, 59612345))
    }

    @Test fun checkInCodeAcceptsOneWindowDriftOnly() {
        val secret = "0123456789abcdef0123"
        val now = 1_800_000_000_000L
        val window = CheckInCode.window(now)
        assertTrue(CheckInCode.verify(CheckInCode.build(secret, window), secret, now))
        assertTrue(CheckInCode.verify(CheckInCode.build(secret, window - 1), secret, now))
        assertTrue(CheckInCode.verify(CheckInCode.build(secret, window + 1), secret, now))
        assertFalse(CheckInCode.verify(CheckInCode.build(secret, window - 2), secret, now))
        assertFalse(CheckInCode.verify(CheckInCode.build("another-secret-value", window), secret, now))
        assertFalse(CheckInCode.verify("KGYMS-CHECKIN:abc:1234", secret, now))
        assertFalse(CheckInCode.isCheckInCode("https://example.com"))
    }
}
