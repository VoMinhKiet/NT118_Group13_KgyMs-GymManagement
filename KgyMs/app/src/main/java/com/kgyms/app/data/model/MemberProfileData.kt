package com.kgyms.app.data.model

import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** Only these fields are editable here. Auth email, role and status are never included. */
data class MemberProfileEdit(val fullName: String, val phone: String, val birthday: String) {
    fun updates(): Map<String, Any> {
        require(fullName.trim().isNotEmpty()) { "Full name is required" }
        require(phone.isBlank() || (phone.filter(Char::isDigit).length in 9..15 &&
            phone.all { it.isDigit() || it in " +()-" })) { "Invalid phone" }
        require(birthday.isBlank() || validBirthday(birthday)) { "Invalid birthday" }
        return mapOf("fullName" to fullName.trim(), "phone" to phone.trim(), "birthday" to birthday.trim())
    }
}

private fun validBirthday(value: String): Boolean {
    if (!Regex("\\d{2}/\\d{2}/\\d{4}").matches(value)) return false
    val parser = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    return parser.parse(value, position) != null && position.index == value.length
}

/** Stable account namespace; never use email or the legacy sample avatar filename. */
internal fun memberAccountKey(uid: String): String {
    require(uid.isNotBlank())
    return MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal fun User.memberProfile(language: String): ProfileUiData {
    val vi = language == "vi"
    val missing = if (vi) "Chưa cập nhật" else "Not updated"
    val genderText = when (gender.lowercase(Locale.ROOT)) {
        "male", "nam" -> if (vi) "Nam" else "Male"
        "female", "nữ" -> if (vi) "Nữ" else "Female"
        else -> gender.ifBlank { missing }
    }
    return ProfileUiData(
        name = fullName.ifBlank { missing }, email = email.ifBlank { missing },
        initials = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .takeLast(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT).ifBlank { "?" },
        membership = missing, workouts = missing, daysActive = missing, averageRating = missing,
        phone = phone.ifBlank { missing }, birthday = birthday.ifBlank { missing },
        gender = genderText, age = if (age > 0) "$age" else missing,
        weight = if (weight > 0) "$weight $weightUnit" else missing,
        height = if (height > 0) "$height cm" else missing, isRealAccount = true,
    )
}
