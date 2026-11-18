package com.kgyms.app.ui.member.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.kgyms.app.data.model.GymPackage
import com.kgyms.app.data.model.MembershipStatus
import com.kgyms.app.data.model.PackageType
import com.kgyms.app.viewmodel.membership.*
import java.text.NumberFormat
import java.util.Locale

/** onCheckout is a handoff only. The default route has no purchase or payment operation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemberPackagesMembershipScreen(
    state: MembershipUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCheckout: ((uid: String, pkg: GymPackage) -> Unit)? = null,
    openCatalog: Boolean = false,
) {
    var tab by rememberSaveable(state.uid, openCatalog) { mutableIntStateOf(if (openCatalog) 1 else 0) }
    var packageId by rememberSaveable(state.uid) { mutableStateOf<String?>(null) }
    var membershipId by rememberSaveable(state.uid) { mutableStateOf<String?>(null) }
    val p = LocalProfilePalette.current
    val owned = state.owned
    Scaffold(containerColor = p.background, contentColor = p.text) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("packages_back")) {
                    Text(profileText("‹ Quay lại", "‹ Back"))
                }
            }
            Text(profileText("Gói tập & hội viên", "Packages & Membership"),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            PrimaryTabRow(selectedTabIndex = tab, containerColor = p.background, contentColor = p.accent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.testTag("packages_tab_owned"),
                    text = { Text(profileText("Gói của tôi", "My memberships")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.testTag("packages_tab_catalog"),
                    text = { Text(profileText("Khám phá gói", "Explore packages")) })
            }
            LazyColumn(Modifier.fillMaxSize().testTag("packages_list"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.uid == null) {
                    item { Text(profileText("Vui lòng đăng nhập để xem gói tập và hội viên.", "Sign in to view packages and memberships.")) }
                } else if (tab == 0) {
                    item { Text(membershipSummaryText(state), style = MaterialTheme.typography.bodyMedium) }
                    if (state.packagesError != null) item { LoadError(state.packagesError, onRetry) }
                    when {
                        state.membershipsLoading -> item { LoadingPackages() }
                        state.membershipsError != null -> item { LoadError(state.membershipsError, onRetry) }
                        owned.isEmpty() -> item { Text(profileText("Bạn chưa đăng ký gói tập nào.", "You have no memberships yet."), Modifier.testTag("memberships_empty")) }
                        else -> items(owned, key = { it.membership.id }) { row ->
                            PackageCard(onClick = { membershipId = row.membership.id }, tag = "membership_${row.membership.id}") {
                                OwnedContent(row, state, details = false)
                                Text(profileText("Xem chi tiết ›", "View details ›"), color = p.accent)
                            }
                        }
                    }
                } else {
                    item { Text(profileText("Gói đang bán từ phòng tập", "Packages offered by your gym")) }
                    when {
                        state.packagesLoading -> item { LoadingPackages() }
                        state.packagesError != null -> item { LoadError(state.packagesError, onRetry) }
                        state.catalog.isEmpty() -> item { Text(profileText("Hiện chưa có gói đang bán.", "No packages are currently on sale."), Modifier.testTag("packages_empty")) }
                        else -> items(state.catalog, key = { it.id }) { pkg ->
                            PackageCard(onClick = { packageId = pkg.id }, tag = "catalog_${pkg.id}") {
                                PackageContent(pkg, state.trainerNames[pkg.trainerUid], details = false)
                                Text(profileText("Xem chi tiết ›", "View details ›"), color = p.accent)
                            }
                        }
                    }
                }
            }
        }
    }
    val selectedMembership = owned.firstOrNull { it.membership.id == membershipId }
    val selectedPackage = state.catalog.firstOrNull { it.id == packageId }
    if (state.uid != null && (selectedMembership != null || selectedPackage != null)) {
        ModalBottomSheet(onDismissRequest = { packageId = null; membershipId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = p.card, contentColor = p.text) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedMembership != null) {
                    OwnedContent(selectedMembership, state, details = true)
                } else if (selectedPackage != null) {
                    PackageContent(selectedPackage, state.trainerNames[selectedPackage.trainerUid], details = true)
                    Text(profileText("Đăng ký và thanh toán sẽ được xử lý qua checkout của phòng tập.",
                        "Registration and payment will be handled by the gym checkout."))
                    Button(
                        enabled = onCheckout != null,
                        onClick = { onCheckout?.invoke(state.uid, selectedPackage) },
                        modifier = Modifier.fillMaxWidth().testTag("package_checkout"),
                    ) { Text(if (onCheckout == null) profileText("Đăng ký — chưa tích hợp", "Register — not integrated") else profileText("Tiếp tục đăng ký", "Continue to checkout")) }
                }
                TextButton(onClick = { packageId = null; membershipId = null }, Modifier.fillMaxWidth().testTag("package_detail_close")) {
                    Text(profileText("Đóng", "Close"))
                }
            }
        }
    }
}

@Composable
private fun PackageCard(onClick: () -> Unit, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalProfilePalette.current
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), color = p.card, contentColor = p.text,
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, p.border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun PackageContent(pkg: GymPackage, trainerName: String?, details: Boolean) {
    Text(pkg.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(packageTypeText(pkg.type), color = LocalProfilePalette.current.accent)
    Text(NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(pkg.price) + " VNĐ", style = MaterialTheme.typography.titleMedium)
    Text(profileText("Thời hạn: ${pkg.durationDays} ngày", "Duration: ${pkg.durationDays} days"))
    if (pkg.type == PackageType.PT) {
        Text(profileText("Tổng buổi PT: ${pkg.sessions}", "Total PT sessions: ${pkg.sessions}"))
        TrainerText(pkg.trainerUid, trainerName)
    }
    if (details) PackageDescription(pkg)
}

@Composable
private fun OwnedContent(row: OwnedMembershipUi, state: MembershipUiState, details: Boolean) {
    val membership = row.membership
    Text(row.pkg?.name ?: profileText("Thiếu thông tin gói", "Package information unavailable"),
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(validityText(row.validity), color = if (row.validity in listOf(MembershipValidity.PastEnd, MembershipValidity.InvalidDates))
        LocalProfilePalette.current.danger else LocalProfilePalette.current.accent,
        modifier = Modifier.testTag("membership_validity_${membership.id}"))
    Text(profileText("Trạng thái Firebase: ", "Firebase status: ") + storedStatusText(membership.status))
    Text(profileText("Bắt đầu: ", "Starts: ") + membership.startDate.ifBlank { profileText("Chưa có", "Not available") })
    Text(profileText("Hết hạn: ", "Ends: ") + membership.endDate.ifBlank { profileText("Chưa có", "Not available") })
    if (row.pkg == null) {
        Text(when {
            state.packagesLoading -> profileText("Đang tải thông tin gói…", "Loading package information…")
            state.packagesError != null -> profileText("Không tải được catalog. Chưa xác định được thông tin gói.", "Catalog unavailable. Package information cannot be determined.")
            else -> profileText("Gói không còn trong catalog. Bản ghi sở hữu vẫn được giữ; chưa có tên, loại hoặc quyền lợi để hiển thị.",
                "This package is missing from the catalog. Your membership is retained; its name, type and benefits are unavailable.")
        })
    } else {
        Text(packageTypeText(row.pkg.type))
        if (!row.pkg.isActive) Text(profileText("Gói đã ngừng bán • không thay đổi hiệu lực hội viên", "No longer on sale • membership validity is unchanged"))
    }
    if (row.pkg?.type == PackageType.PT || membership.trainerUid.isNotBlank()) {
        TrainerText(membership.trainerUid, row.trainerName)
        val unknown = profileText("Chưa có dữ liệu", "Unavailable")
        Text(profileText("Tổng buổi: ", "Total sessions: ") + (row.totalSessions?.toString() ?: unknown))
        Text(profileText("Đã sử dụng: ", "Used: ") + (row.usedSessions?.toString() ?: unknown))
        Text(profileText("Còn lại: ", "Remaining: ") + (row.remainingSessions?.toString() ?: unknown))
        if (row.totalSessions != null && (row.usedSessions ?: 0) > row.totalSessions!!) {
            Text(profileText("Dữ liệu buổi đã dùng vượt tổng buổi. Vui lòng liên hệ phòng tập.", "Used sessions exceed the total. Please contact the gym."), color = LocalProfilePalette.current.danger)
        }
    }
    if (details) {
        Text(profileText("Mã Membership: ", "Membership ID: ") + membership.id)
        Text(profileText("Mã gói: ", "Package ID: ") + membership.packageId)
        if (membership.createdAt.isNotBlank()) Text(profileText("Đăng ký: ", "Registered: ") + membership.createdAt)
        row.pkg?.let {
            PackageDescription(it)
            Text(profileText("Thông tin mô tả và quyền lợi theo catalog hiện tại của phòng tập.", "Description and benefits reflect the gym's current catalog."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TrainerText(uid: String, name: String?) {
    Text(profileText("PT phụ trách: ", "Assigned PT: ") + (name ?: if (uid.isBlank()) profileText("Chưa phân công", "Not assigned")
        else profileText("Đã phân công • chưa có tên được phép đọc", "Assigned • readable name unavailable")))
}

@Composable
private fun PackageDescription(pkg: GymPackage) {
    Text(profileText("Mô tả", "Description"), fontWeight = FontWeight.Bold)
    Text(pkg.description.ifBlank { profileText("Chưa có mô tả.", "No description available.") })
    Text(profileText("Quyền lợi", "Benefits"), fontWeight = FontWeight.Bold)
    if (pkg.benefits.isEmpty()) Text(profileText("Chưa có thông tin quyền lợi.", "No benefits available."))
    pkg.benefits.forEach { Text("• $it") }
}

@Composable
private fun LoadingPackages() { CircularProgressIndicator(Modifier.testTag("packages_loading")) }

@Composable
private fun LoadError(error: MembershipLoadError, onRetry: () -> Unit) {
    Column {
        Text(if (error == MembershipLoadError.Permission) profileText("Không có quyền đọc dữ liệu này. Vui lòng kiểm tra phiên đăng nhập hoặc liên hệ phòng tập.",
            "Permission denied. Check your session or contact the gym.") else profileText("Không tải được dữ liệu. Vui lòng thử lại.", "Could not load data. Please retry."), color = LocalProfilePalette.current.danger)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("packages_retry")) { Text(profileText("Thử lại", "Retry")) }
    }
}

@Composable
internal fun membershipSummaryText(state: MembershipUiState): String = when {
    state.uid == null -> profileText("Chưa đăng nhập", "Not signed in")
    state.membershipsLoading -> profileText("Đang tải hội viên…", "Loading memberships…")
    state.membershipsError != null -> profileText("Không tải được hội viên", "Memberships unavailable")
    state.owned.isEmpty() -> profileText("Chưa đăng ký gói", "No memberships")
    else -> {
        val labels = mutableListOf<String>()
        for ((validity, count) in state.owned.groupingBy { it.validity }.eachCount()) {
            labels.add("$count ${validityText(validity)}")
        }
        labels.joinToString(" • ")
    }
}

@Composable
private fun packageTypeText(type: String): String = when (type) {
    PackageType.MEMBERSHIP -> profileText("Gói Membership", "Membership package")
    PackageType.PT -> profileText("Gói PT", "PT package")
    else -> profileText("Chưa rõ loại gói", "Unknown package type")
}

@Composable
internal fun validityText(validity: MembershipValidity): String = when (validity) {
    MembershipValidity.Active -> profileText("Đang hoạt động", "Active")
    MembershipValidity.Pending -> profileText("Đang chờ kích hoạt", "Pending activation")
    MembershipValidity.Frozen -> profileText("Bị đóng băng", "Frozen")
    MembershipValidity.Expired -> profileText("Hết hạn", "Expired")
    MembershipValidity.Cancelled -> profileText("Đã hủy", "Cancelled")
    MembershipValidity.NotStarted -> profileText("Active trên Firebase • chưa đến ngày bắt đầu", "Active in Firebase • starts in the future")
    MembershipValidity.PastEnd -> profileText("Active trên Firebase • đã quá ngày hết hạn", "Active in Firebase • past end date")
    MembershipValidity.InvalidDates -> profileText("Active trên Firebase • chưa xác định hiệu lực do ngày thiếu hoặc không hợp lệ", "Active in Firebase • validity unknown due to missing or invalid dates")
    MembershipValidity.Unknown -> profileText("Trạng thái chưa hỗ trợ", "Unsupported status")
}

@Composable
private fun storedStatusText(status: String): String = when (status) {
    MembershipStatus.ACTIVE -> profileText("Đang hoạt động (active)", "Active (active)")
    MembershipStatus.PENDING -> profileText("Đang chờ (pending)", "Pending (pending)")
    MembershipStatus.FROZEN -> profileText("Đóng băng (frozen)", "Frozen (frozen)")
    MembershipStatus.EXPIRED -> profileText("Hết hạn (expired)", "Expired (expired)")
    MembershipStatus.CANCELLED -> profileText("Đã hủy (cancelled)", "Cancelled (cancelled)")
    else -> status
}

@Preview(widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun PackagesCompactLightPreview() {
    ProfileAppearance(false, "en") { MemberPackagesMembershipScreen(MembershipUiState(uid = "preview"), {}, {}) }
}
