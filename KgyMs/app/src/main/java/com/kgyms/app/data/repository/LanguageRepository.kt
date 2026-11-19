package com.kgyms.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Ngôn ngữ giao diện ("en" / "vi"), lưu trên máy để giữ nguyên sau khi thoát app
 * hoặc đăng xuất. Dùng chung một chỗ lưu với cài đặt của khu vực hội viên
 * (MemberPreferences) nên đổi ở đâu thì auth và member đều đồng bộ.
 */
class LanguageRepository(context: Context) {

    private val storage: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentLanguage(): String = normalize(storage.getString(KEY_LANGUAGE, null))

    fun setLanguage(language: String) {
        storage.edit().putString(KEY_LANGUAGE, normalize(language)).apply()
    }

    /** Phát giá trị hiện tại và mỗi lần ngôn ngữ thay đổi (kể cả khi đổi từ màn Hồ sơ). */
    fun languageFlow(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LANGUAGE || key == null) trySend(currentLanguage())
        }
        storage.registerOnSharedPreferenceChangeListener(listener)
        trySend(currentLanguage())
        awaitClose { storage.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val PREFS_NAME = "kgyms_member_ui"
        const val KEY_LANGUAGE = "language"
        const val DEFAULT_LANGUAGE = "en"

        fun normalize(language: String?): String = when (language) {
            "vi" -> "vi"
            "en" -> "en"
            else -> DEFAULT_LANGUAGE
        }
    }
}
