package com.kgyms.app.viewmodel.admin

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Một cột biểu đồ đã chuẩn hóa (0..1) để UI chỉ việc vẽ. */
data class BarUi(val label: String, val ratio: Float, val highlighted: Boolean = true)

/** Một lựa chọn chip: key gửi lại ViewModel, label để hiển thị. */
data class OptionUi(val key: String, val label: String)

/** Định dạng dùng chung cho mọi ViewModel Admin. */
object AdminFormat {

    private val vnd = DecimalFormat("#,###", DecimalFormatSymbols(Locale.ROOT).apply { groupingSeparator = '.' })

    /** 1990000 → "1.990.000đ". */
    fun vnd(amount: Long): String = vnd.format(amount) + "đ"

    /** 128_500_000 → "128.5M", 850_000 → "850K", 0 → "0đ". */
    fun compact(amount: Long): String = when {
        amount >= 1_000_000_000 -> trim(amount / 1_000_000_000.0) + "B"
        amount >= 1_000_000 -> trim(amount / 1_000_000.0) + "M"
        amount >= 1_000 -> "${amount / 1_000}K"
        else -> "${amount}đ"
    }

    /** Chuẩn hóa dãy số thành cột biểu đồ; ô 0 vẫn hiện vạch mờ. */
    fun bars(labels: List<String>, values: List<Long>): List<BarUi> {
        val max = values.maxOrNull() ?: 0L
        return labels.zip(values).map { (label, value) ->
            BarUi(
                label = label,
                ratio = if (max <= 0L) 0f else value.toFloat() / max,
                highlighted = value > 0
            )
        }
    }

    /** Nhãn 7 ngày gần nhất kết thúc hôm nay, vd T2..CN. */
    fun lastSevenDayLabels(): List<String> {
        val calendar = java.util.Calendar.getInstance()
        return (6 downTo 0).map { back ->
            val c = (calendar.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -back) }
            when (c.get(java.util.Calendar.DAY_OF_WEEK)) {
                java.util.Calendar.MONDAY -> "T2"
                java.util.Calendar.TUESDAY -> "T3"
                java.util.Calendar.WEDNESDAY -> "T4"
                java.util.Calendar.THURSDAY -> "T5"
                java.util.Calendar.FRIDAY -> "T6"
                java.util.Calendar.SATURDAY -> "T7"
                else -> "CN"
            }
        }
    }

    private fun trim(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
}
