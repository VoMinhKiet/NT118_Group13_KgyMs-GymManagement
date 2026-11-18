package com.kgyms.app.data.model

import java.math.BigDecimal
import java.math.RoundingMode

internal data class MemberBodyMeasurements(val weightKg: String = "", val heightCm: String = "") {
    companion object {
        fun parse(weight: String, height: String): MemberBodyMeasurements? {
            return MemberBodyMeasurements(
                normalize(weight, 20, 500) ?: return null,
            normalize(height, 70, 275) ?: return null,
            )
        }

        private fun normalize(input: String, min: Int, max: Int): String? {
            val value = input.trim()
            if (value.isEmpty()) return ""
            if (!Regex("[0-9]{1,3}([.,][0-9]{1,2})?").matches(value)) return null
            val decimal = BigDecimal(value.replace(',', '.'))
            return decimal.takeIf { it >= BigDecimal(min) && it <= BigDecimal(max) }
                ?.stripTrailingZeros()?.toPlainString()
        }

        fun fromOnboarding(weight: Int, unit: String, height: Int): MemberBodyMeasurements? {
            if (unit !in setOf("KG", "LB")) return null
            val kg = if (unit == "LB") BigDecimal(weight).multiply(BigDecimal("0.45359237"))
                .setScale(2, RoundingMode.HALF_UP).toPlainString() else weight.toString()
            return parse(kg, height.toString())
        }
    }
}
