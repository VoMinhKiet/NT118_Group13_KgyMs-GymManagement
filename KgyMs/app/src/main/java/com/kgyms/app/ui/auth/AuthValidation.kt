package com.kgyms.app.ui.auth

/** Local mock validation only. No real account or session is created. */
internal fun registrationError(name: String, email: String, password: String, confirmation: String): String? = when {
    name.isBlank() || email.isBlank() || password.isBlank() || confirmation.isBlank() -> "Vui lòng điền đầy đủ thông tin"
    !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> "Email không hợp lệ"
    password.length < 6 -> "Mật khẩu cần ít nhất 6 ký tự"
    password != confirmation -> "Mật khẩu xác nhận không khớp"
    else -> null
}

internal fun convertWeight(value: Int, from: String, to: String): Int {
    val converted = when {
        from == to -> value.toDouble()
        to == "LB" -> value * 2.2046226218
        else -> value / 2.2046226218
    }
    return kotlin.math.round(converted).toInt().coerceIn(if (to == "KG") 30..200 else 66..440)
}
