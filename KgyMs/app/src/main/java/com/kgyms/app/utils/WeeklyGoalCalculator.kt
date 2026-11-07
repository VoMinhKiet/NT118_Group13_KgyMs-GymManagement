package com.kgyms.app.utils

import java.util.Calendar
import java.util.TimeZone
import com.kgyms.app.data.model.HomeWeeklyGoal
import com.kgyms.app.data.model.WorkoutRecord

/** Calendar days rather than 7 * 24h: preserves Monday/Sunday boundaries across DST. */
fun calculateWeeklyGoal(
    target: Int,
    records: List<WorkoutRecord>,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): HomeWeeklyGoal {
    val monday = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        this.set(Calendar.HOUR_OF_DAY, 0); this.set(Calendar.MINUTE, 0)
        this.set(Calendar.SECOND, 0); this.set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
    }
    val nextMonday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
    return HomeWeeklyGoal(target, monday.timeInMillis, nextMonday.timeInMillis, records.toList(), nowMillis)
}
