# Báo cáo Profile, Personal Plan và thẻ hội viên Home

Ngày 19/11/2026. Thực hiện trong `C:\Users\ADMIN\Desktop\KgyMs-next`, nhánh `feature/nutrition-optimizer-local`, nền `82c14ed`.

## Worktree trước khi sửa

`git status --short -uall` ghi nhận toàn bộ công việc Membership đang ở dạng local. Các file tracked đã sửa gồm `PackageService.kt`, `PaymentService.kt`, `QrModels.kt`, `MemberApp.kt`, `ProfileFlow.kt`, `ProfileScreen.kt`, `ProfileSettingsScreens.kt`, `MembershipViewModel.kt`. Các file mới gồm `MemberMembershipRepository.kt`, `MembershipState.kt`, `MemberPackagesMembershipScreen.kt`, `MembershipViewModelTest.kt`, `MemberPackagesMembershipUiTest.kt`, `MEMBER_PACKAGES_MEMBERSHIP_REPORT.md`, `firebase-tests/scripts/run-membership-tests.mjs` và `firebase-tests/test/memberships.test.mjs`. Lần này mở rộng trên cùng module và giữ các test Membership đã có.

## Profile

- Profile có palette riêng dù Home và thanh điều hướng đã dùng MemberDashboardAppearance. Profile và cả hai lối vào Membership giờ dùng màu tím/xám của Member Dashboard ở giao diện sáng và tối. Profile sử dụng cùng màu cho avatar, chữ, biểu tượng, thẻ, menu và điều khiển. QR và Nutrition vẫn nhận theme gốc từ shell.
- Mapper Profile trước đây gán giá trị thiếu cho ba chỉ số. Profile giờ theo dõi `LocalWorkoutHistory` của đúng UID. Một buổi hợp lệ có thời gian kết thúc không ở tương lai, thứ tự thời gian hợp lệ và ít nhất một bài được đánh dấu đã thực hiện. Buổi được tính theo `logicalSessionId` nên tập lại không làm tăng tổng số buổi. Ngày hoạt động lấy các ngày kết thúc khác nhau theo múi giờ thiết bị. Tập lại vào ngày khác vẫn là hoạt động của ngày đó.
- Không tìm thấy nguồn đánh giá trung bình của Member phù hợp với WorkoutHistory. Thay ô này bằng số buổi hoàn thành lần đầu trong tuần Thứ 2 đến Chủ nhật, cùng ranh giới tuần với tiến độ tập. Cả ba chỉ số có thể bao gồm lượt tự ghi nhận, kể cả lượt đang chờ đồng bộ. UI có ghi chú về lượt tự ghi nhận. WorkoutHistory tiếp tục giữ trạng thái PT xác nhận riêng. Khi lịch sử chưa tải được, ô hiển thị dấu gạch ngang. Lịch sử đã tải và rỗng hiển thị 0.
- Onboarding có các giá trị cân nặng và chiều cao trong phiên nhưng trước đây chỉ gửi giới tính/tuổi. Bây giờ Profile đọc và chỉnh hai số đo tại mục **Cập nhật cân nặng & chiều cao**. Onboarding lưu lựa chọn tại máy trước khi hoàn tất onboarding hiện có. Cân nặng LB được quy đổi sang kg. Form Profile nhận kg/cm, chuẩn hóa dấu thập phân chấm hoặc phẩy và tối đa hai chữ số thập phân. Khoảng chấp nhận là 20–500 kg và 70–275 cm. Có thể để trống từng trường để xóa giá trị đã lưu.
- Dữ liệu số đo được ghi qua `AtomicFile` vào `Context.noBackupFilesDir`, tên file chứa SHA-256 của Firebase UID. Đây là vùng app-private loại khỏi Android backup/chuyển thiết bị. Lưu thành công cập nhật Profile ngay. Đổi UID dùng file và state khác. Dữ liệu không còn lưu được hiển thị “Chưa cập nhật”. Số đo không được ghi lên Firebase hoặc ghi vào activity log.
- Tuổi cũ lấy từ trường onboarding `age`. Profile giờ tính số năm tròn từ `birthday` đúng `dd/MM/yyyy` theo ngày thiết bị. Sinh 13/10/2006 vào 19/11/2026 hiển thị 20. Ngày sinh thiếu, sai hoặc trong tương lai cho trạng thái chưa cập nhật. Với 29/02, tuổi tăng vào 01/03 trong năm không nhuận. Giá trị onboarding cũ tiếp tục được dùng bởi luồng xác định đã hoàn tất onboarding hiện có.

## Personal Plan

- Trước đây nút gợi ý áp dụng ngay danh sách ngày mặc định. Giờ người dùng xem các phương án ngay bên dưới số buổi và danh sách ngày hiện tại. Chạm một phương án mới áp dụng. Có thể giữ các ngày đang chọn hoặc chạm từng ngày. Không có ngày nào thì nút xem trước/gợi ý bị khóa.
- Một buổi có bảy lựa chọn ngày. Từ hai đến sáu buổi, UI đề xuất các phương án giữ đúng số ngày. Riêng ba buổi có T2–T4–T6, T3–T5–T7 và T2–T5–CN. Với các mức còn lại ngoài ba buổi, danh sách gồm mặc định cũ và các phương án có khoảng cách nghỉ đều khi xét cả ranh giới Chủ nhật → Thứ 2. Bảy buổi có một phương án chọn đủ tuần.
- Trước đây generator lần lượt quay vòng mẫu. Generator mới chọn mẫu có bài hợp trình độ, ưu tiên các nhóm đẩy/kéo/thân dưới/core còn thiếu trong tuần, giảm lặp mẫu và hạn chế dùng lại cùng nhóm có tải trong 1–2 ngày gần nhất. Quy tắc, thứ tự phân giải và kết quả có tính xác định. Trình độ cao vẫn có thể dùng bài cơ bản. Mỗi tuần có đúng số buổi đã chọn, mỗi ngày tối đa một buổi trong khung bốn tuần.
- Bổ sung 10 mẫu buổi tham khảo từ các bài cũ và mới. Xem trước cho biết nhóm cơ, bài tập, số hiệp/lần và dụng cụ. Lịch đã lưu xuất hiện trong **Kế hoạch → Lịch tập của tôi**. Có thể đổi ngày, thay buổi, thêm/đổi/bỏ bài trước khi lưu.
- Thư viện giữ tìm theo tên tiếng Việt/Anh và lọc nhóm cơ/trình độ. Thẻ hiển thị dụng cụ. Màn thêm/đổi bài dùng lại bộ lọc tìm kiếm và cho biết các bài đang chọn. Chọn bài tương thích trình độ lịch, danh sách sửa giữ các giới hạn hiện có.
- Giữ toàn bộ ID bài và mẫu cũ. Các mẫu/bài mới có ID chuỗi ổn định. Định dạng đồng bộ vẫn là `schemaVersion=1` và `generatorVersion=1`. Lịch đã lưu hiển thị từ dữ liệu phiên bản đã lưu, không được chạy qua generator mới. Tạo lịch mới với đầu vào và catalog giống nhau cho cùng kết quả. Không thay giao thức đồng bộ, quy tắc lưu, bước chọn lịch đang dùng hoặc lưu phiên bản.
- Các bài mới có mô tả dạng văn bản và ảnh minh họa nhóm. Khi chưa có video, màn chi tiết hiển thị trạng thái chưa có video. Các mô tả được viết riêng cho app. Thư viện ACE dùng để đối chiếu các động tác tham khảo. [ACE Exercise Library](https://www.acefitness.org/resources/everyone/exercise-library/)

| ID | Tiếng Việt | English |
| --- | --- | --- |
| `incline-push-up` | Chống đẩy tay cao | Incline push-up |
| `incline-db-press` | Đẩy ngực ghế dốc | Incline dumbbell press |
| `lat-pulldown` | Kéo xô máy cáp | Lat pulldown |
| `pull-up` | Kéo xà sấp tay | Pull-up |
| `lateral-raise` | Nâng tạ ngang vai | Dumbbell lateral raise |
| `db-shoulder-press` | Đẩy vai tạ đơn | Dumbbell shoulder press |
| `hammer-curl` | Cuốn tạ kiểu búa | Dumbbell hammer curl |
| `triceps-pressdown` | Duỗi tay sau với cáp | Cable triceps pressdown |
| `step-up` | Bước lên bục thấp | Low step-up |
| `split-squat` | Squat tách chân | Split squat |
| `seated-leg-curl` | Gập đùi sau trên máy | Seated leg curl |
| `db-romanian-deadlift` | Deadlift Romania tạ đơn | Dumbbell Romanian deadlift |
| `single-leg-rdl` | Deadlift Romania một chân | Single-leg Romanian deadlift |
| `side-lying-hip-abduction` | Nâng chân nằm nghiêng | Side-lying hip abduction |
| `standing-calf-raise` | Nhón gót đứng | Standing calf raise |
| `dead-bug` | Duỗi tay chân đối bên nằm ngửa | Dead bug |
| `bird-dog` | Duỗi tay chân đối bên chống gối | Bird dog |
| `suitcase-carry` | Đi bộ xách tạ một bên | Suitcase carry |
| `db-squat-to-press` | Squat kết hợp đẩy tạ | Dumbbell squat to press |

## Membership từ Home

Thẻ và nút gia hạn trước đây mở popup dữ liệu mẫu qua `previewKey`. Giờ Home dùng cùng `MembershipViewModel`, route và màn hai tab với Profile. Thẻ hội viên mở **Gói của tôi**, còn **Mở thẻ / Gia hạn** mở **Khám phá gói**. Back quay lại Home với vị trí cuộn trước đó hoặc về Profile nếu mở từ Profile. Tab đang chọn vẫn cho đổi trong màn hình.

Home và Profile lấy cùng bản ghi theo UID thời gian thực, dùng cùng logic hiệu lực và tóm tắt. Tài khoản chưa có gói hiện “Chưa đăng ký gói”. Gói pending, frozen, expired và active đã qua hạn giữ đúng trạng thái. Tên ưu tiên bản ghi đầu tiên theo thứ tự module Membership, còn dòng tóm tắt có thể phản ánh nhiều gói. Điểm nối checkout đã có được tái sử dụng từ cả hai lối vào.

## File triển khai

| Phần | Sửa hoặc bổ sung |
| --- | --- |
| Profile và số đo | `MemberProfileData.kt`, `MemberBodyMeasurements.kt` (mới), `MemberBodyMeasurementStore.kt` (mới), `KgyMsApp.kt`, `ProfileAppearance.kt`, `MemberDashboardAppearance.kt`, `ProfileFlow.kt`, `ProfileScreen.kt`, `ProfileSettingsScreens.kt`, `ProfileBodyMeasurements.kt` (mới), `ProfileWorkoutMetrics.kt` (mới) |
| Lịch tập | `PersonalPlanGenerator.kt`, `PersonalPlanScreens.kt`, `PersonalPlanCalendar.kt`, `PersonalExerciseEditor.kt`, `WorkoutTrainingFocus.kt` (mới) |
| Thư viện | `WorkoutModels.kt`, `WorkoutReferenceCatalog.kt`, `AdditionalWorkoutReferences.kt` (mới), `MemberWorkoutScreen.kt`, `WorkoutComponents.kt` |
| Home và Membership | `MemberApp.kt`, `MemberHomeAction.kt`, `MemberHomeComponents.kt`, `MemberHomeScreen.kt`, `MemberMembershipRoute.kt` (mới), `MemberPackagesMembershipScreen.kt`, `MemberMembershipRepository.kt` |
| Kiểm thử | `ProfileDataTest.kt` (mới), `ProfilePlanHomeUiTest.kt` (mới), `MemberAccountDataTest.kt`, `WorkoutCatalogTest.kt`, `PersonalPlanGeneratorTest.kt`, `PersonalPlanUiTest.kt`, `PersonalExerciseEditorUiTest.kt`, `ProfileFlowTest.kt`, `MemberDashboardUiTest.kt`, `MemberHomeIntegrationTest.kt` |

## Firebase và giới hạn triển khai

Membership giữ truy vấn lọc UID và kiểm tra checkout hiện có. Personal Plan tiếp tục dùng codec/lưu phiên bản/sync sẵn có. Mã mới không thêm node/cột, không sửa Rules, không viết số đo lên Firebase và không triển khai thanh toán. Các bài kiểm thử dùng dữ liệu fixture hoặc tài khoản demo cục bộ. Không ghi/xóa production. Dữ liệu số đo có phạm vi thiết bị, không được chuyển sang thiết bị khác. Gợi ý tập là quy tắc theo nhóm cơ và lịch, không tính tải tạ, chấn thương hoặc thiết bị sẵn có. Tần suất 6–7 buổi có thể cần ngày nhẹ tùy người dùng. Thiết bị dùng bản app cũ cần cập nhật thư viện để hiển thị nội dung của ID mới.

## Kiểm thử thực tế

- `:app:assembleDebug` và `:app:compileDebugAndroidTestKotlin` đã PASS.
- `:app:testDebugUnitTest` đã PASS 186 tests. Bao gồm 18 test Membership có sẵn và các test mới cho tuổi, số đo, workout metrics, phương án ngày, nhóm cơ, ID bài cũ và lịch xác định.
- AVD Android 9, API 28 ở 320 dp. Bộ `MemberPackagesMembershipUiTest` hiện có PASS 7/7 qua Gradle `:app:connectedPersonalPlanEmulatorAndroidTest`. Bộ tập trung bằng cùng APK cô lập PASS 7/7, gồm form số đo, lưu/đọc lại theo UID, Home → hai tab, Back, trạng thái Home–Profile, áp dụng gợi ý, lưu lịch bảy ngày, thêm/thay/bỏ bài và mở lại phiên bản cũ. Kết quả nằm trong `app/build/reports/androidTests/connected/personalPlanEmulator/index.html` và `app/build/member-packages-ui/profile-plan-targeted-final-ui.txt`.
- Chạy thêm 9 UI smoke tests ProfileFlow, HomeIntegration và MemberDashboard bằng APK Debug trên AVD offline, xác nhận thiết bị không có mạng mặc định. 7 bài PASS lần đầu. Hai kỳ vọng cũ về nhãn Premium mẫu và màn Nutrition dành cho khách đã được cập nhật trong test, sau đó chạy lại PASS 2/2. Tổng cộng 23 UI tests khác nhau đã PASS qua các lượt cuối. Log tại `app/build/member-packages-ui/profile-home-debug-offline-ui.txt` và `app/build/member-packages-ui/profile-home-debug-offline-retry-ui.txt`.
- Firebase Rules Emulator demo đã PASS 5/5 với Rules snapshot có sẵn. Chạy `node scripts/run-membership-tests.mjs` từ `firebase-tests`. Kiểm tra query theo UID, lỗi quyền Member, đọc catalog và thông báo, sự kiện pending → active và quyền ghi bị chặn.
- Lần thử UI đầu bằng compiler in-process gặp lỗi native JBR. Dùng Kotlin daemon mặc định và một Gradle worker để chạy lại. Lần thử UI tập trung đầu tiên có một assertion phụ thuộc ngôn ngữ AVD. Fixture đã được sửa để chọn ngôn ngữ rõ ràng.
- Không chạy kiểm thử checkout hoặc Firebase production. Không chạy toàn bộ ma trận UI suite, bản Debug đã biên dịch toàn bộ mã UI test.


Kiểm tra bàn giao đã xác nhận git diff --check PASS, index rỗng và HEAD vẫn là 82c14ed. Toàn bộ thay đổi ở local, chưa stage, commit hoặc push.
