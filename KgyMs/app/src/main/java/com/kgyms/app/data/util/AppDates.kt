package com.kgyms.app.data.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Định dạng ngày giờ dùng trên Firebase (xem FIREBASE_STRUCTURE.md mục 2):
 * - thời điểm: "HH:mm dd/MM/yyyy"
 * - ngày:      "dd/MM/yyyy"
 * minSdk 24 nên dùng Calendar thay cho java.time.
 */
object AppDates {

    private const val TIMESTAMP = "HH:mm dd/MM/yyyy"
    private const val DAY = "dd/MM/yyyy"

    fun timestamp(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat(TIMESTAMP, Locale.ROOT).format(Date(millis))

    fun day(millis: Long): String = SimpleDateFormat(DAY, Locale.ROOT).format(Date(millis))

    /** "dd/MM/yyyy" → millis lúc 00:00 của ngày đó; null nếu sai định dạng. */
    fun parseDay(text: String): Long? = runCatching {
        SimpleDateFormat(DAY, Locale.ROOT).apply { isLenient = false }.parse(text.trim())?.time
    }.getOrNull()

    /** "HH:mm dd/MM/yyyy" → millis; null nếu sai định dạng. */
    fun parseTimestamp(text: String): Long? = runCatching {
        SimpleDateFormat(TIMESTAMP, Locale.ROOT).apply { isLenient = false }.parse(text.trim())?.time
    }.getOrNull()

    fun startOfDay(millis: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 23:59:00 của ngày chứa [millis]. */
    fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(dayMillis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayMillis
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis
}
