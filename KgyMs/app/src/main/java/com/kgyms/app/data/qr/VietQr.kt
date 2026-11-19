package com.kgyms.app.data.qr

/**
 * Tạo chuỗi VietQR (chuẩn EMVCo của Napas) để mọi app ngân hàng và MoMo quét được.
 * Chạy offline, không gọi dịch vụ ngoài.
 */
object VietQr {

    private const val NAPAS_GUID = "A000000727"
    private const val SERVICE_TO_ACCOUNT = "QRIBFTTA"
    private const val CURRENCY_VND = "704"
    private const val COUNTRY = "VN"

    /**
     * @param bankBin mã BIN Napas (Vietcombank = 970436).
     * @param amount số tiền VNĐ; mã có số tiền là mã "động" (point of initiation = 12).
     * @param content nội dung chuyển khoản, chỉ nên dùng chữ không dấu, số và khoảng trắng.
     */
    fun payload(bankBin: String, accountNo: String, amount: Long, content: String): String {
        require(amount > 0) { "Số tiền phải lớn hơn 0." }
        val beneficiary = tlv("00", bankBin) + tlv("01", accountNo)
        val merchant = tlv("00", NAPAS_GUID) + tlv("01", beneficiary) + tlv("02", SERVICE_TO_ACCOUNT)
        val body = tlv("00", "01") +
            tlv("01", "12") +
            tlv("38", merchant) +
            tlv("53", CURRENCY_VND) +
            tlv("54", amount.toString()) +
            tlv("58", COUNTRY) +
            tlv("62", tlv("08", content.take(MAX_CONTENT))) +
            "6304"
        return body + crc16(body)
    }

    private const val MAX_CONTENT = 50

    private fun tlv(id: String, value: String): String {
        require(value.length < 100) { "Trường $id quá dài." }
        return id + value.length.toString().padStart(2, '0') + value
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF), 4 ký tự hex in hoa. */
    internal fun crc16(data: String): String {
        var crc = 0xFFFF
        for (byte in data.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
