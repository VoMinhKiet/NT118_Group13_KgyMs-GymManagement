package com.kgyms.app.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso
import com.kgyms.app.ui.admin.navigation.AdminNavHost
import com.kgyms.app.ui.theme.KgyMsTheme
import org.junit.Rule
import org.junit.Test

class AdminNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun start(): StateRestorationTester = StateRestorationTester(compose).also {
        it.setContent { KgyMsTheme { AdminNavHost() } }
    }
    private fun route(name: String) { compose.onNodeWithTag("admin_route_$name").assertExists() }
    private fun back() { compose.onNodeWithTag("admin_back").performClick() }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true)
        val scrollable = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (scrollable) node.performScrollTo()
        node.performClick()
    }
    private fun more(text: String) { compose.onNodeWithTag("admin_more").performClick(); click(text) }

    @Test fun dashboardMetricsPrimaryListsAndDetailsReturnToTheirParents() {
        start(); route("dashboard")
        click("Doanh thu"); route("revenue"); back()
        click("Check-in"); route("traffic"); back()
        compose.onNodeWithTag("admin_tab_members").performClick(); route("members")
        compose.onNodeWithTag("admin_member_search").performTextReplacement("no-match")
        compose.onNodeWithText("Không tìm thấy hội viên").assertExists()
        compose.onNodeWithTag("admin_member_search").performTextReplacement("Nguyễn")
        click("Nguyễn Minh Anh"); route("member_detail")
        Espresso.pressBack(); route("members")
        compose.onNodeWithTag("admin_member_search").assertTextContains("Nguyễn")
        compose.onNodeWithTag("admin_tab_staff").performClick(); route("staff")
        // Nhân sự lấy từ Firebase nên chỉ kiểm tra nút thêm.
        compose.onNodeWithTag("staff_add_button").assertExists()
        compose.onNodeWithTag("admin_tab_assets").performClick(); route("assets")
        // Danh sách tài sản lấy từ Firebase nên không cố định tên máy để bấm vào.
        compose.onNodeWithTag("asset_add_button").assertExists()
        compose.onNodeWithTag("admin_tab_dashboard").performClick(); route("dashboard")
    }

    @Test fun everyMoreDestinationAndReceiptComplaintRecordsSurviveRecreation() {
        val restoration = start()
        more("Quản lý gói tập"); route("packages")
        // Gói tập lấy từ Firebase nên chỉ kiểm tra nút thêm.
        compose.onNodeWithTag("package_add_button").assertExists()
        back()
        more("Quản lý hóa đơn"); route("invoice")
        click("Lịch sử giao dịch"); route("transactions")
        click("Chờ")
        compose.onNodeWithText("Đã thanh toán").assertDoesNotExist()
        click("Chia sẻ"); route("receipt")
        restoration.emulateSavedInstanceStateRestore()
        route("receipt"); compose.onNodeWithText("#INV-1090").assertExists()
        click("Xuất PDF"); compose.onNodeWithTag("admin_feedback_ok").performClick()
        back(); route("transactions"); back(); route("invoice"); back()
        more("Quản lý khiếu nại"); route("complaints")
        click("#CS-208 • Thiết bị hỏng"); route("complaint_detail")
        compose.onNodeWithTag("admin_complaint_response").performTextReplacement("Kiểm tra thiết bị mẫu")
        restoration.emulateSavedInstanceStateRestore()
        route("complaint_detail")
        compose.onNodeWithText("#CS-208").assertExists()
        compose.onNodeWithTag("admin_complaint_response").assertTextContains("Kiểm tra thiết bị mẫu")
        back(); route("complaints"); back()
        more("Nhật ký hoạt động"); route("activity_logs")
        compose.onNodeWithTag("admin_log_search").performTextReplacement("PT")
        back(); route("dashboard")
    }
    @Test fun moreMenuBackReturnsToTheTabThatOpenedIt() {
        start()
        compose.onNodeWithTag("admin_tab_staff").performClick()
        more("Nhật ký hoạt động"); route("activity_logs"); back(); route("staff")
        more("Quản lý thông báo"); route("notifications"); back(); route("staff")
        more("Quản lý hóa đơn"); route("invoice")
        click("Lịch sử giao dịch"); route("transactions")
        back(); route("invoice"); back(); route("staff")
        Espresso.pressBack(); route("dashboard")
    }

}
