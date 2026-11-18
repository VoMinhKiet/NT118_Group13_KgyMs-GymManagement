# Báo cáo Gói tập & hội viên — Member

Ngày: 18/11/2026. Workspace: `C:\Users\ADMIN\Desktop\KgyMs-next`.
Nền: `82c14ed` (main sau PR #9). Nhánh đang làm: `feature/nutrition-optimizer-local`.
Git sạch trước khi thực hiện. Giữ nguyên nhánh; tất cả thay đổi chưa stage/chưa commit.

## 1. Chức năng đã triển khai

- Điểm vào `Profile → Gói tập & hội viên / Packages & Membership`.
- Một màn hình, hai tab nội bộ: **Gói của tôi** và **Khám phá gói**.
- Catalog realtime từ dữ liệu Admin: Membership và PT, tên, giá VNĐ, thời hạn, số buổi PT, tên PT nếu có thể đọc, mô tả và quyền lợi trong bottom sheet.
- Chỉ bán mới các bản ghi có `isActive == true`, thuộc loại `membership` hoặc `pt`. Mapper cũng coi trường `isActive` thiếu/không đúng Boolean là không bán; không mặc định mở bán dữ liệu cũ không rõ cờ.
- Gói ngừng bán vẫn hiện trong lịch sử sở hữu, và không bị gán là hết hạn vì ngừng bán.
- Danh sách nhiều Membership theo UID thật, đủ trạng thái `pending`, `active`, `frozen`, `expired`, `cancelled`; có chi tiết ID, mã gói, ngày đăng ký, bắt đầu/kết thúc.
- Giữ bản ghi Membership khi gói bị xóa khỏi catalog. Hiển thị thiếu thông tin; không bịa tên, loại, quyền lợi hoặc số buổi còn lại.
- Tách trạng thái lưu trên Firebase và hiệu lực theo ngày. `active` quá endDate cảnh báo rõ; active chưa đến startDate hoặc thiếu/sai ngày không được tính là đang có hiệu lực.
- Ngày kết thúc được tính trọn ngày theo múi giờ thiết bị, dùng định dạng hiện có `dd/MM/yyyy`. Kiểm tra định dạng đầy đủ, ngày hợp lệ và startDate không sau endDate.
- PT: tên từ `staff`, tổng buổi theo catalog, đã dùng từ Membership và còn lại tính cục bộ. Thiếu dữ liệu sử dụng không giả định bằng 0. Nếu đã dùng vượt tổng, vẫn giữ số đã dùng, cảnh báo và hiển thị còn lại tối thiểu 0. Không sửa dữ liệu buổi.
- PT của Membership dùng `membership.trainerUid`; không thay lịch sử phân công bằng trainerUid hiện tại của catalog.
- Loading/error/empty/retry riêng cho catalog và Membership. Lỗi tra tên PT không làm mất danh sách; có thông báo tên không có dữ liệu được phép đọc.
- Profile dùng cùng state Membership thật để tóm tắt số gói theo từng trạng thái/hiệu lực, thay dữ liệu membership/Premium mẫu.
- Đổi UID/đăng xuất: hủy listener cũ, xóa state, kiểm tra UID khi nhận sự kiện và trước khi render; xóa lựa chọn chi tiết theo UID. Lọc UID thêm một lần ở ViewModel và UI state.
- Hiệu lực ngày được đánh giá lại mỗi phút và khi Profile resume, không ghi thay đổi trạng thái lên Firebase.
- Dark/Light và Việt/Anh theo ProfileAppearance hiện có. Danh sách lazy, chi tiết cuộn, tiêu đề/nội dung xuống dòng để hỗ trợ màn hình nhỏ.

## 2. File đã tạo và sửa

Đã đọc các file được yêu cầu: PackageModels, PackageService, PackageRepository, PackageViewModel, PackageScreen (Admin), QrModels, PaymentService, PaymentRepository, MembershipViewModel, ProfileFlow, ProfileScreen, ProfileSettingsScreens và MemberApp; đồng thời đối chiếu StaffService, AppDates, cơ chế account host và Rules snapshot.

### Tạo mới

| File | Vai trò |
| --- | --- |
| `app/src/main/java/com/kgyms/app/data/repository/MemberMembershipRepository.kt` | Ranh giới chỉ đọc; adapter tái sử dụng PackageRepository, PaymentRepository và StaffService; quan sát Firebase Auth UID. |
| `app/src/main/java/com/kgyms/app/viewmodel/membership/MembershipState.kt` | Ghép gói/sở hữu, lọc catalog, hiệu lực ngày, thống kê buổi PT. |
| `app/src/main/java/com/kgyms/app/ui/member/profile/MemberPackagesMembershipScreen.kt` | Hai tab, bottom sheet, trạng thái dữ liệu và điểm nối checkout. |
| `app/src/test/java/com/kgyms/app/viewmodel/membership/MembershipViewModelTest.kt` | 18 unit tests bằng fake repository và clock điều khiển được. |
| `app/src/androidTest/java/com/kgyms/app/ui/member/profile/MemberPackagesMembershipUiTest.kt` | 7 UI tests bằng fixture và fake repository, không dùng Firebase production. |
| `../firebase-tests/scripts/run-membership-tests.mjs` | Runner Emulator demo-only, cổng riêng, cấu hình/cache/log trong workspace. |
| `../firebase-tests/test/memberships.test.mjs` | 5 bài test Rules snapshot và sự kiện realtime trên Emulator. |
| `docs/MEMBER_PACKAGES_MEMBERSHIP_REPORT.md` | Báo cáo này. |

### Sửa

| File | Thay đổi |
| --- | --- |
| `app/src/main/java/com/kgyms/app/data/firebase/PackageService.kt` | Chỉ coi cờ Boolean true là đang bán. Không sửa Admin CRUD. |
| `app/src/main/java/com/kgyms/app/data/firebase/PaymentService.kt` | Bổ sung metadata đọc để phân biệt thiếu sessionsUsed với số 0. Giữ nguyên truy vấn UID. |
| `app/src/main/java/com/kgyms/app/data/model/QrModels.kt` | Thêm sessionsUsedAvailable ở cuối Membership, có Firebase Exclude; không thêm trường trên database. |
| `app/src/main/java/com/kgyms/app/viewmodel/membership/MembershipViewModel.kt` | Thay file trống bằng ViewModel thật, hủy listener theo UID, lỗi độc lập, retry, clock. |
| `app/src/main/java/com/kgyms/app/ui/member/profile/ProfileFlow.kt` | Dùng chung state cho Profile và màn Membership, lifecycle, navigation và chặn checkout khi UID/gói không còn phù hợp. |
| `app/src/main/java/com/kgyms/app/ui/member/profile/ProfileScreen.kt` | Tóm tắt Membership thật và tên điểm vào mới; bỏ suy diễn Premium. |
| `app/src/main/java/com/kgyms/app/ui/member/profile/ProfileSettingsScreens.kt` | Gỡ màn Membership placeholder/mẫu cũ. |
| `app/src/main/java/com/kgyms/app/ui/member/MemberApp.kt` | Truyền callback checkout tùy chọn xuống ProfileFlow. |

Không sửa Nutrition V1, payment gateway, Admin ViewModel/Screen/CRUD hoặc bất kỳ Rules file nào. Không thay đổi dependencies/Gradle source hoặc package-lock.

Cấu hình local bị Git ignore: đặt `KgyMs/local.properties` trỏ SDK đã cài; sao chép `google-services.json` có sẵn từ `Desktop\KgyMs\KgyMs\app` vào `KgyMs-next\KgyMs\app`. Chỉ đọc project gốc, không sửa project gốc hoặc KgyMs-merge. Không in nội dung cấu hình Firebase.

## 3. Luồng dữ liệu thực

```text
FirebaseAuth.AuthStateListener
  → UID + retry generation
  → collectLatest: hủy collection của UID cũ, reset state
      packages: PackageRepository → PackageService.observePackages()
      memberships: PaymentRepository.observeMyMemberships(uid)
                   → PaymentService.orderByChild("uid").equalTo(uid)
      trainer names: StaffService.observeStaff() → fullName theo UID
  → MembershipUiState, ghép bằng membership.packageId
  → Profile summary + màn hai tab + chi tiết realtime
```

Các callbackFlow có awaitClose gỡ Firebase listener. Scope ViewModel sở hữu listener và clock; onCleared hủy job. Repository dành cho màn Member không công khai API ghi, tạo hóa đơn hoặc kích hoạt Membership. Sự kiện tài khoản cũ bị bỏ nếu currentUid không còn khớp.

Catalog lỗi không bị diễn giải thành gói đã xóa: UI thông báo catalog chưa tải được. Chỉ khi đọc catalog thành công mà thiếu packageId mới thông báo gói không còn trong catalog. Lỗi Membership không bị diễn giải thành chưa đăng ký.

## 4. UI kết nối dữ liệu Admin và Rules

- Không hardcode gói Gold/Silver/Premium, giá hoặc quyền lợi; mọi dữ liệu sản phẩm đến từ node `packages` có sẵn.
- Giữ toàn bộ catalog đọc được để join lịch sử; chỉ lọc isActive cho tab Khám phá.
- Tên PT chỉ dùng `staff/{trainerUid}/fullName`, theo schema StaffService hiện có và quyền read auth của node staff. Không dùng Admin getTrainers()/users collection hoặc đọc users của PT.
- Đã đối chiếu `firebase-tests/rules.production.snapshot.json`: packages read auth, memberships UID query, index uid/trainerUid/status, các trạng thái, staff read auth, payments UID query, notifications theo UID.
- Không tải hoặc sửa Rules đang deploy. Kiểm thử dùng nguyên snapshot trong repository; không khẳng định snapshot chắc chắn trùng deployment nếu server đã được người khác thay đổi.
- Runner chỉ cho project `demo-kgyms-memberships`, bind loopback DB 9100/Auth 9199/hub 4411/logging 4511; từ chối cổng đã dùng. Không dùng project alias, Firebase token hoặc service account production.
- Fixture và các thử ghi bị từ chối chỉ xảy ra trong Emulator demo. Không ghi/thử ghi dữ liệu production, không tạo hóa đơn production, không deploy Rules.

## 5. Cách UI nhận Membership sau thanh toán

Checkout hoặc phía có thẩm quyền tạo/cập nhật Membership theo schema hiện có, gắn đúng `uid`, `packageId`, `trainerUid`, `status`, `startDate`, `endDate`, `sessionsUsed` nếu có.

Listener theo UID sẽ nhận cả Membership mới và thay đổi pending → active. ViewModel phát state mới; danh sách, bottom sheet đang mở và phần tóm tắt Profile cập nhật. UI không phụ thuộc thông báo hoặc kết quả thanh toán để tự gán active. Active còn phải có ngày hợp lệ và nằm trong thời hạn để được tính là đang có hiệu lực.

## 6. Điểm bàn giao cho checkout tự động

`MemberApp(onPackageCheckout = { uid, pkg -> ... })` truyền tiếp tới `ProfileFlow` và màn gói.

- Mặc định callback null: nút **Đăng ký — chưa tích hợp / Register — not integrated** bị vô hiệu hóa.
- Khi có callback: chỉ bàn giao UID + GymPackage đang bán. ProfileFlow đọc lại current UID/state và package hiện tại trước khi bàn giao.
- Người tích hợp chịu trách nhiệm mở checkout, kiểm tra lại xác thực/giá/trạng thái trên hệ thống có thẩm quyền và quản lý vòng đời giao dịch.
- Sau checkout, trở lại màn này và để listener nhận dữ liệu server; không gán active hoặc hiển thị thành công giả từ callback.
- Không gọi startPurchase(), confirmPaid(), tạo payment, phát notification hoặc ghi Membership trong module này. Không triển khai Cloud Functions hoặc xác nhận chuyển khoản.

## 7. Kết quả kiểm thử/build

### Gradle Debug và unit tests — PASS

Chạy thực tế từ `KgyMs/` với Android Studio JBR:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-configuration-cache --max-workers=1 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process'
```

Kết quả: BUILD SUCCESSFUL. **179 unit tests, 0 failures, 0 errors**, gồm **18 Membership tests**. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.

Bao phủ: hai loại gói, gói ngừng bán, lọc UID/foreign record, nhiều gói/năm trạng thái, PT/tên PT lịch sử/buổi đã dùng/còn lại/thiếu dữ liệu, gói xóa, empty/loading, permission/network errors/retry, lỗi staff/catalog độc lập, cập nhật realtime, hủy listener/đăng xuất, ngày bao gồm endDate/quá hạn/chưa bắt đầu/sai ngày và ranh giới không cho phép ghi/kích hoạt.

### Firebase Rules Emulator — PASS

```powershell
cd firebase-tests
npm.cmd ci --ignore-scripts --cache .npm-cache --no-audit --no-fund
node scripts/run-membership-tests.mjs
```

**5/5 PASS** trên demo project, Rules snapshot không sửa. Bao phủ query UID chính chủ, chặn query toàn bộ/UID khác/guest; hai loại gói và gói ngừng bán; đọc tên từ staff, chặn users; chặn Member sửa catalog/tự active/sửa buổi; authority update tới listener; lọc payments, chặn paid và ghi notification.

Log run PASS: `firebase-tests/artifacts/memberships/2026-11-17T22-10-09-009Z/` (timestamp UTC). Những cảnh báo permission_denied trong log là các phép thử chặn ghi được mong đợi.

### Android UI — PASS (7/7, 0 failures, 0 errors)

Dùng AVD riêng `MemberPackagesTest`, Android 9/API 28/x86, màn hình 960×1920 density 480 (~320×640 dp). AVD và dữ liệu tạo trong `app/build/member-packages-ui`, không sử dụng userdata của AVD gốc. APK cô lập `personalPlanEmulator` có applicationId riêng, gỡ FirebaseInitProvider và MainActivity production. Chỉ chạy lớp test mới; không mở launcher production hoặc launcher Personal Plan.

```powershell
$env:ANDROID_SERIAL='emulator-5580'
.\gradlew.bat :app:connectedPersonalPlanEmulatorAndroidTest -PpersonalPlanEmulatorTests=true '-Pandroid.testInstrumentationRunnerArguments.class=com.kgyms.app.ui.member.profile.MemberPackagesMembershipUiTest' --no-configuration-cache --max-workers=1 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process'
```

7 UI tests: 320dp/font 1.5 Light/English, catalog/detail/nút chưa tích hợp, Back đóng sheet, năm trạng thái/gói xóa, active quá hạn/buổi PT, đổi UID/đăng xuất đóng chi tiết, callback chỉ bàn giao, Profile tóm tắt thật/theme/ngôn ngữ, điều hướng Profile → gói và Back bằng nút/hệ thống.

Kết quả cuối: BUILD SUCCESSFUL (1m 4s), 7 tests PASS. Báo cáo UI: `app/build/reports/androidTests/connected/personalPlanEmulator/index.html`.

### Vấn đề đã gặp và xử lý

- Build đầu thiếu SDK local và google-services.json: bổ sung cấu hình local từ môi trường có sẵn. Thử bỏ qua Google Services không đủ vì app tham chiếu default_web_client_id; bản build PASS dùng cấu hình có sẵn, không dùng cấu hình giả.
- Npm cần chạy npm.cmd do ExecutionPolicy; network/JDK/SDK cần chạy ngoài sandbox. Dependency đã khóa cài thành công. Node 26 có cảnh báo engine từ dependency CLI, nhưng test Rules chạy thành công.
- Hai lần test Rules đầu lỗi mã test (cú pháp, rồi lỗi công cụ cập nhật file tạo null bytes); đã viết lại file, kiểm tra node --check và chạy 5/5 PASS. Không sửa Rules để làm test qua.
- UI lần đầu 4/6 PASS, hai assertion tìm thấy hai bản cùng nội dung ở card và sheet; đã sửa selector. Thêm test navigation Profile để kiểm tra luồng thực.
- Kotlin daemon bị mất kết nối và build incremental sau đó lỗi symbol: build tuần tự, tắt incremental và dùng compiler in-process đã PASS. Không clean/reset build hoặc nguồn.
- Không chạy debug unit tests trong cùng cấu hình `-PpersonalPlanEmulatorTests=true`, vì project chuyển testBuildType và không tạo task testDebugUnitTest; chạy riêng đúng variant.

## 8. Hạn chế và phần còn lại

- Checkout thực do người khác tích hợp qua callback. Nút mặc định chưa tích hợp là chủ đích; không có luồng thanh toán tự động trong thay đổi này.
- Schema Membership hiện không lưu snapshot tên/loại/giá/quyền lợi/tổng buổi lúc mua. UI dùng catalog hiện tại và ghi rõ mô tả/quyền lợi theo catalog. Tổng buổi và phép tính còn lại cũng phụ thuộc catalog hiện tại; không thể khôi phục dữ liệu gói đã xóa hoặc tổng buổi gốc khi Admin đã sửa. Không thêm schema hoặc tự tạo dữ liệu lịch sử.
- Nếu staff không có tên hoặc bị từ chối đọc, UI hiển thị chưa có tên được phép đọc; không thử mở quyền hoặc đọc users thay thế.
- Hiệu lực ngày là diễn giải hiển thị theo đồng hồ/múi giờ thiết bị; không thay thế kiểm soát quyền truy cập/check-in phía có thẩm quyền.
- Chưa thực hiện giao dịch/checkout production hoặc kiểm chứng triển khai Rules live; các kiểm thử quyền chỉ dùng snapshot trên Emulator. Chưa chạy ma trận nhiều thiết bị/API, TalkBack, xoay màn hình và screenshot so sánh màu/pixel.
- Không sửa Nutrition, không đồng bộ Nutrition Firebase, không thêm AI, không thay dữ liệu production bằng mock. Fixture chỉ nằm trong test/Preview.
- Không Git add/commit/push/pull/merge/reset/clean hoặc tạo PR. Không sửa main, Desktop\KgyMs hoặc Desktop\KgyMs-merge. Trạng thái bàn giao: local changes chưa stage/chưa commit.

Kiểm tra bàn giao cuối: git diff --check PASS; git diff --cached rỗng; HEAD vẫn 82c14ed trên nhánh feature/nutrition-optimizer-local. Rules snapshot và các file Nutrition không có diff.
