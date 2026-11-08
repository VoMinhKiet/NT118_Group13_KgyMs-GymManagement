package com.kgyms.app.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ngôn ngữ hiện tại của phần auth/onboarding và hàm đổi ngôn ngữ.
 * KgyMsApp cung cấp giá trị lấy từ LanguageViewModel; mặc định là tiếng Anh.
 */
data class AppLanguageController(
    val language: String = "en",
    val onLanguageChange: ((String) -> Unit)? = null,
) {
    val isVietnamese: Boolean get() = language == "vi"
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguageController() }

/** Chọn chuỗi theo ngôn ngữ (dùng được ngoài Composable, ví dụ trong LaunchedEffect). */
fun pickText(language: String, vi: String, en: String): String = if (language == "vi") vi else en

/** Chọn chuỗi theo ngôn ngữ hiện tại của app. */
@Composable
fun appText(vi: String, en: String): String = pickText(LocalAppLanguage.current.language, vi, en)
