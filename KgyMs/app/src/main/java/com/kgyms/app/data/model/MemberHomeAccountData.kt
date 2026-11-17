package com.kgyms.app.data.model

import com.kgyms.app.data.mock.createMockMemberHome
import com.kgyms.app.utils.calculateWeeklyGoal

/** Keep reference recommendations, replace every personal fixture with this account's identity. */
internal fun User.memberHome(language: String, target: Int, now: Long): MemberHomeUiData {
    val profile = memberProfile(language)
    return createMockMemberHome(language, target, now).copy(
        memberName = profile.name, initials = profile.initials, avatarUri = avatarUrl.ifBlank { null },
        weeklyGoal = calculateWeeklyGoal(target, emptyList(), now),
        membership = HomeMembership(profile.membership, profile.membership, profile.membership, 0, available = false),
        notifications = emptyList(), activity = emptyList(), trainer = null,
    )
}
