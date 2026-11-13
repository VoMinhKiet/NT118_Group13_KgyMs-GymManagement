package com.kgyms.app.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kgyms.app.data.model.MemberDirectory
import com.kgyms.app.data.model.MemberProfile
import com.kgyms.app.data.model.Membership
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.data.model.PaymentStatus
import com.kgyms.app.data.repository.MemberRepository
import com.kgyms.app.data.util.AppDates
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/* ============================ UI MODEL ============================ */

/** Trạng thái tổng hợp của 1 hội viên (dùng cho lọc và màu). */
enum class MemberState { Active, Expiring, Pending, NoPlan, Locked }

/** Một dòng "nhãn — giá trị" trên màn chi tiết. */
data class DetailFieldUi(val label: String, val value: String, val tone: AdminTone? = null)

data class MemberRowUi(
    val uid: String,
    val name: String,
    val subtitle: String,
    val statusLabel: String,
    val tone: AdminTone
)

/** Một dòng gói/hoá đơn trong lịch sử của hội viên. */
data class MemberHistoryRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val tone: AdminTone
)

data class MemberDetailUi(
    val uid: String,
    val name: String,
    val codeLine: String,
    val hasCode: Boolean,
    val statusLabel: String,
    val statusTone: AdminTone,
    val inGym: Boolean,
    val isLocked: Boolean,
    val profile: List<DetailFieldUi>,
    /** null = chưa có gói đang chạy. */
    val currentPlan: List<DetailFieldUi>?,
    val checkIn: List<DetailFieldUi>,
    val plans: List<MemberHistoryRowUi>,
    val payments: List<MemberHistoryRowUi>
)

data class MemberLockConfirmUi(val uid: String, val name: String, val lock: Boolean)

data class MemberUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val totalCount: String = "–",
    val activeCount: String = "–",
    val inGymCount: String = "–",
    val query: String = "",
    val filter: String = MemberViewModel.FILTER_ALL,
    val filters: List<OptionUi> = emptyList(),
    val rows: List<MemberRowUi> = emptyList(),
    val details: Map<String, MemberDetailUi> = emptyMap(),
    val assigningCodeFor: String? = null,
    val confirmLock: MemberLockConfirmUi? = null,
    val message: String? = null
) {
    val emptyMessage: String?
        get() = if (!isLoading && errorMessage == null && rows.isEmpty()) "Không tìm thấy hội viên" else null
}

/* ============================ VIEWMODEL ============================ */

class MemberViewModel(
    private val repository: MemberRepository = MemberRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(MemberUiState())
    val state: StateFlow<MemberUiState> = _state.asStateFlow()

    private var directory = MemberDirectory()
    /** uid → (trạng thái, dòng danh sách, chuỗi tìm kiếm). */
    private var summaries: List<Summary> = emptyList()

    private data class Summary(val profile: MemberProfile, val state: MemberState, val row: MemberRowUi, val search: String)

    init {
        viewModelScope.launch {
            repository.observeDirectory()
                .catch { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toUserMessage()) } }
                .collect { dir ->
                    directory = dir
                    summaries = dir.members.map { summarize(it) }.sortedWith(
                        compareBy<Summary>({ it.state.ordinal }, { it.profile.displayName.lowercase() })
                    )
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            details = dir.members.associate { m -> m.uid to detailOf(m) }
                        ).recompute()
                    }
                }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query).recompute() }

    fun onFilterSelected(filter: String) = _state.update { it.copy(filter = filter).recompute() }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    /* ---------- Cấp mã hội viên ---------- */

    fun assignMemberCode(uid: String) {
        val member = directory.members.firstOrNull { it.uid == uid } ?: return
        if (member.memberCode.isNotBlank() || _state.value.assigningCodeFor != null) return
        _state.update { it.copy(assigningCodeFor = uid) }
        viewModelScope.launch {
            val result = runSafely { repository.assignMemberCode(member) }
            _state.update {
                it.copy(
                    assigningCodeFor = null,
                    message = result.fold({ code -> "Đã cấp mã $code cho ${member.displayName}" }, { e -> e.toUserMessage() })
                )
            }
        }
    }

    /* ---------- Khoá / mở khoá ---------- */

    fun requestLockToggle(uid: String) {
        val member = directory.members.firstOrNull { it.uid == uid } ?: return
        _state.update { it.copy(confirmLock = MemberLockConfirmUi(uid, member.displayName, lock = !member.isLocked)) }
    }

    fun cancelLockToggle() = _state.update { it.copy(confirmLock = null) }

    fun confirmLockToggle() {
        val confirm = _state.value.confirmLock ?: return
        val member = directory.members.firstOrNull { it.uid == confirm.uid } ?: return cancelLockToggle()
        _state.update { it.copy(confirmLock = null) }
        viewModelScope.launch {
            val result = runSafely { repository.setLocked(member, confirm.lock) }
            _state.update {
                it.copy(message = result.exceptionOrNull()?.toUserMessage()
                    ?: if (confirm.lock) "Đã khoá tài khoản ${member.displayName}" else "Đã mở khoá ${member.displayName}")
            }
        }
    }

    /* ============================ TÍNH TOÁN ============================ */

    private fun MemberUiState.recompute(): MemberUiState {
        val keyword = query.trim()
        val counts = summaries.groupingBy { it.state }.eachCount()
        val visible = summaries
            .filter { s ->
                when (filter) {
                    FILTER_ACTIVE -> s.state == MemberState.Active || s.state == MemberState.Expiring
                    FILTER_EXPIRING -> s.state == MemberState.Expiring
                    FILTER_PENDING -> s.state == MemberState.Pending
                    FILTER_NONE -> s.state == MemberState.NoPlan
                    FILTER_LOCKED -> s.state == MemberState.Locked
                    else -> true
                }
            }
            .filter { keyword.isEmpty() || it.search.contains(keyword, ignoreCase = true) }

        val active = (counts[MemberState.Active] ?: 0) + (counts[MemberState.Expiring] ?: 0)
        return copy(
            totalCount = summaries.size.toString(),
            activeCount = active.toString(),
            inGymCount = directory.members.count { isInGym(it.uid) }.toString(),
            filters = buildList {
                add(OptionUi(FILTER_ALL, "Tất cả (${summaries.size})"))
                add(OptionUi(FILTER_ACTIVE, "Có gói ($active)"))
                add(OptionUi(FILTER_EXPIRING, "Sắp hết hạn (${counts[MemberState.Expiring] ?: 0})"))
                add(OptionUi(FILTER_PENDING, "Chờ thanh toán (${counts[MemberState.Pending] ?: 0})"))
                add(OptionUi(FILTER_NONE, "Chưa có gói (${counts[MemberState.NoPlan] ?: 0})"))
                if ((counts[MemberState.Locked] ?: 0) > 0) add(OptionUi(FILTER_LOCKED, "Đã khoá (${counts[MemberState.Locked]})"))
            },
            rows = visible.map { it.row }
        )
    }

    private fun summarize(m: MemberProfile): Summary {
        val current = currentMembership(m.uid)
        val daysLeft = current?.let { daysLeft(it) }
        val hasPending = directory.payments.any { it.uid == m.uid && it.status == PaymentStatus.PENDING }
        val state = when {
            m.isLocked -> MemberState.Locked
            current != null && (daysLeft ?: 0) <= EXPIRING_DAYS -> MemberState.Expiring
            current != null -> MemberState.Active
            hasPending -> MemberState.Pending
            else -> MemberState.NoPlan
        }
        val planText = current?.let { "${packageName(it)} • còn ${daysLeft ?: 0} ngày" }
            ?: if (hasPending) "Có hoá đơn chờ thanh toán" else "Chưa mua gói"
        val subtitle = listOf(
            m.memberCode.ifBlank { "Chưa cấp mã" },
            planText,
            if (isInGym(m.uid)) "đang ở gym" else ""
        ).filter { it.isNotBlank() }.joinToString(" • ")

        return Summary(
            profile = m,
            state = state,
            row = MemberRowUi(m.uid, m.displayName, subtitle, stateLabel(state), stateTone(state)),
            search = "${m.displayName} ${m.email} ${m.phone} ${m.memberCode}"
        )
    }

    private fun detailOf(m: MemberProfile): MemberDetailUi {
        val summary = summaries.firstOrNull { it.profile.uid == m.uid } ?: summarize(m)
        val current = currentMembership(m.uid)
        val pkg = current?.let { directory.packages[it.packageId] }
        val memberships = directory.memberships.filter { it.uid == m.uid }
            .sortedByDescending { AppDates.parseTimestamp(it.createdAt) ?: 0L }
        val payments = directory.payments.filter { it.uid == m.uid }
            .sortedByDescending { AppDates.parseTimestamp(it.createdAt) ?: 0L }
        val debt = payments.filter { it.status == PaymentStatus.PENDING }.sumOf { it.amount }

        val checkIns = directory.checkIns.filter { it.uid == m.uid }.sortedByDescending { it.checkInAt }
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            timeInMillis = AppDates.startOfDay(timeInMillis)
        }.timeInMillis

        return MemberDetailUi(
            uid = m.uid,
            name = m.displayName,
            codeLine = m.memberCode.ifBlank { "Chưa cấp mã hội viên" },
            hasCode = m.memberCode.isNotBlank(),
            statusLabel = stateLabel(summary.state),
            statusTone = stateTone(summary.state),
            inGym = isInGym(m.uid),
            isLocked = m.isLocked,
            profile = listOf(
                DetailFieldUi("Email", m.email.ifBlank { "—" }),
                DetailFieldUi("Số điện thoại", m.phone.ifBlank { "—" }),
                DetailFieldUi("Giới tính", when (m.gender) { "Male" -> "Nam"; "Female" -> "Nữ"; else -> "—" }),
                DetailFieldUi("Tuổi", if (m.age > 0) m.age.toString() else "—"),
                DetailFieldUi("Ngày tạo tài khoản", m.createdAt.ifBlank { "—" }),
                DetailFieldUi(
                    "Công nợ",
                    AdminFormat.vnd(debt),
                    if (debt > 0) AdminTone.Warning else null
                ),
                DetailFieldUi(
                    "Tài khoản",
                    if (m.isLocked) "Đã khoá" else "Đang hoạt động",
                    if (m.isLocked) AdminTone.Critical else AdminTone.Positive
                )
            ),
            currentPlan = current?.let { ms ->
                buildList {
                    add(DetailFieldUi("Gói", packageName(ms)))
                    add(DetailFieldUi("Thời hạn", "${ms.startDate} – ${ms.endDate}"))
                    val left = daysLeft(ms)
                    add(DetailFieldUi("Còn lại", "$left ngày", if (left <= EXPIRING_DAYS) AdminTone.Warning else AdminTone.Positive))
                    if (pkg?.type == PackageType.PT) {
                        add(DetailFieldUi("Buổi đã tập", "${ms.sessionsUsed}/${pkg.sessions}"))
                        add(DetailFieldUi("PT phụ trách", directory.staffNames[ms.trainerUid] ?: "Chưa phân công"))
                    }
                }
            },
            checkIn = listOf(
                DetailFieldUi("Lượt tháng này", checkIns.count { it.checkInAt >= monthStart }.toString()),
                DetailFieldUi("Lần gần nhất", checkIns.firstOrNull()?.let { AppDates.timestamp(it.checkInAt) } ?: "—"),
                DetailFieldUi(
                    "Hiện tại",
                    if (isInGym(m.uid)) "Đang ở gym" else "Không ở gym",
                    if (isInGym(m.uid)) AdminTone.Positive else null
                )
            ),
            plans = memberships.map { ms ->
                MemberHistoryRowUi(
                    id = ms.id,
                    title = packageName(ms),
                    subtitle = if (ms.startDate.isNotBlank()) "${ms.startDate} – ${ms.endDate}" else "Đăng ký ${ms.createdAt}",
                    statusLabel = membershipLabel(ms),
                    tone = membershipTone(ms)
                )
            },
            payments = payments.map { p ->
                MemberHistoryRowUi(
                    id = p.id,
                    title = "${p.id} • ${AdminFormat.vnd(p.amount)}",
                    subtitle = p.createdAt,
                    statusLabel = when (p.status) {
                        PaymentStatus.PAID -> "Đã thanh toán"
                        PaymentStatus.CANCELLED -> "Đã huỷ"
                        else -> "Chờ thanh toán"
                    },
                    tone = when (p.status) {
                        PaymentStatus.PAID -> AdminTone.Positive
                        PaymentStatus.CANCELLED -> AdminTone.Neutral
                        else -> AdminTone.Warning
                    }
                )
            }
        )
    }

    /** Gói đang chạy hôm nay có ngày hết hạn xa nhất. */
    private fun currentMembership(uid: String): Membership? {
        val today = AppDates.startOfDay()
        return directory.memberships
            .filter { it.uid == uid && it.status == MembershipStatus.ACTIVE }
            .filter { ms ->
                val start = AppDates.parseDay(ms.startDate) ?: return@filter false
                val end = AppDates.parseDay(ms.endDate) ?: return@filter false
                start <= today && end >= today
            }
            .maxByOrNull { AppDates.parseDay(it.endDate) ?: 0L }
    }

    /** Số ngày còn lại, tính cả hôm nay (hết hạn hôm nay = 1). */
    private fun daysLeft(ms: Membership): Int {
        val end = AppDates.parseDay(ms.endDate) ?: return 0
        return (((end - AppDates.startOfDay()) / DAY_MS) + 1).toInt().coerceAtLeast(0)
    }

    private fun isInGym(uid: String): Boolean {
        val today = AppDates.startOfDay()
        return directory.checkIns.any { it.uid == uid && it.checkOutAt == 0L && it.checkInAt >= today }
    }

    private fun packageName(ms: Membership) = directory.packages[ms.packageId]?.name ?: ms.packageId.ifBlank { "Gói" }

    private fun membershipLabel(ms: Membership): String {
        if (ms.status == MembershipStatus.ACTIVE) {
            val end = AppDates.parseDay(ms.endDate)
            val start = AppDates.parseDay(ms.startDate)
            val today = AppDates.startOfDay()
            return when {
                end != null && end < today -> "Hết hạn"
                start != null && start > today -> "Chờ bắt đầu"
                else -> "Đang dùng"
            }
        }
        return when (ms.status) {
            MembershipStatus.PENDING -> "Chờ thanh toán"
            MembershipStatus.FROZEN -> "Bảo lưu"
            MembershipStatus.EXPIRED -> "Hết hạn"
            MembershipStatus.CANCELLED -> "Đã huỷ"
            else -> ms.status
        }
    }

    private fun membershipTone(ms: Membership) = when (membershipLabel(ms)) {
        "Đang dùng" -> AdminTone.Positive
        "Chờ bắt đầu", "Bảo lưu" -> AdminTone.Info
        "Chờ thanh toán" -> AdminTone.Warning
        else -> AdminTone.Neutral
    }

    private fun stateLabel(state: MemberState) = when (state) {
        MemberState.Active -> "Có gói"
        MemberState.Expiring -> "Sắp hết hạn"
        MemberState.Pending -> "Chờ TT"
        MemberState.NoPlan -> "Chưa có gói"
        MemberState.Locked -> "Đã khoá"
    }

    private fun stateTone(state: MemberState) = when (state) {
        MemberState.Active -> AdminTone.Positive
        MemberState.Expiring -> AdminTone.Warning
        MemberState.Pending -> AdminTone.Info
        MemberState.NoPlan -> AdminTone.Neutral
        MemberState.Locked -> AdminTone.Critical
    }

    private inline fun <T> runSafely(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun Throwable.toUserMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("permission", ignoreCase = true) -> "Bạn không có quyền xem hội viên (cần tài khoản admin)."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi, vui lòng thử lại."
        }
    }

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_ACTIVE = "active"
        const val FILTER_EXPIRING = "expiring"
        const val FILTER_PENDING = "pending"
        const val FILTER_NONE = "none"
        const val FILTER_LOCKED = "locked"

        /** Còn ≤ 7 ngày thì tính là sắp hết hạn. */
        private const val EXPIRING_DAYS = 7
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
