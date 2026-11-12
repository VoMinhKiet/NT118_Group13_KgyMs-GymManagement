package com.kgyms.app.ui.member.progress

import com.kgyms.app.data.model.WorkoutRecord
import com.kgyms.app.data.model.WorkoutRecordStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class MemberProgressDataTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val now = 1_762_260_000_000L

    @Test fun unavailableHistoryIsDifferentFromConfirmedZero() {
        val unavailable = memberProgressData(3, null, now, zone)
        val empty = memberProgressData(3, emptyList(), now, zone)
        assertEquals(3, unavailable.week.target)
        assertNull(unavailable.completed)
        assertNull(unavailable.history)
        assertEquals(0, empty.completed ?: -1)
        assertTrue(empty.history.orEmpty().isEmpty())
    }

    @Test fun weeklyCountUsesConfirmedUniqueSessionsWhileHistoryIncludesOlderSessions() {
        val start = memberProgressData(4, null, now, zone).week.weekStartMillis
        val records = listOf(
            record("older", start - 1),
            record("first", start),
            record("first", start + 1),
            record("second", now),
            record("unconfirmed", now, confirmed = false),
            record("cancelled", now).copy(status = WorkoutRecordStatus.Cancelled),
            record("scheduled", now).copy(status = WorkoutRecordStatus.Scheduled),
            record("future", now + 1),
            record("", now),
        )
        val progress = memberProgressData(4, records, now, zone)
        assertEquals(2, progress.completed ?: -1)
        assertEquals(0.5f, progress.week.progress, 0f)
        assertEquals(listOf("second", "first", "older"), progress.history?.map { it.sessionId })
    }

    private fun record(id: String, time: Long, confirmed: Boolean = true) =
        WorkoutRecord(id, time, WorkoutRecordStatus.Completed, confirmed)
}
