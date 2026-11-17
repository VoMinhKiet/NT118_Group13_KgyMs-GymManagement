# M2C Member Progress — bàn giao 15/11/2026

Branch `TruongTanKiet`, HEAD giữ nguyên `ff8017d`. Chưa stage/commit/push.

## Luồng dữ liệu và cách đếm

Workout Start lưu draft + UUID attempt vào SharedPreferences riêng theo Firebase UID. Đánh dấu bài được commit xuống thiết bị. Finish atomically lưu snapshot bất biến, xóa draft tương ứng và ghi marker chống Finish lặp. Chỉ Finish mới tạo kết quả. Start again tạo UUID khác.

Repository tải server trước khi upload, hợp nhất theo attempt ID, không ghi đè archive. Retry sau mất acknowledgement so sánh đúng ID và toàn bộ payload. Payload khác cùng ID chuyển Conflict, giữ dữ liệu local. Pending queue, acknowledgement, confirmation và draft tồn tại qua process restart. Không xóa dữ liệu cũ.

MemberApp cung cấp một WorkoutHistoryState cho Home, Workout và Progress. Listener Firebase đánh thức sync; chỉ REST server read/write thành công mới xác nhận đồng bộ. Merge giữ pending và draft hiện có. Đổi UID đóng listener/repository cũ; token và UID được kiểm tra trước/sau network I/O. Sync chỉ chạy khi Member foreground; không triển khai background WorkManager.

- LocalPending: local commit xong, chưa có server ACK.
- SelfReported: server đã nhận attempt; chưa có confirmation hợp lệ.
- Confirmed: server có confirmation riêng được Rules bảo vệ, trỏ đúng attempt/UID.
- Attempts: mọi lần Finish trong tuần.
- Unique sessions: nhóm theo logicalSessionId, chỉ tính tuần của lần Finish đầu tiên; tập lại không tăng unique hoặc chuyển session sang tuần mới.
- Personal Plan dùng session.id gốc làm logical ID, plan.id lưu riêng; runtime ID chỉ dùng tìm session. Các revision chỉnh bài giữ logical ID. Tạo một lịch độc lập là các session mới.
- Tập trực tiếp từ thư viện: logical ID là workout + ngày local. Tập lại cùng bài trong cùng ngày là nhiều attempts nhưng một unique session.
- Confirmed unique đếm riêng từ attempt có confirmation thực, không lấy cờ từ attempt/client hoặc Planned session.
- Mục tiêu: lựa chọn đã lưu của UID > sessionsPerWeek của active plan > chưa đặt. Không hardcode 6. Không dùng calories/thời lượng catalog làm kết quả đo thực tế.

## Firebase và Rules thủ công

Paths mới duy nhất cho dữ liệu M2C:

```
memberWorkoutAttempts/{uid}/{attemptId}
memberWorkoutConfirmations/{uid}/{attemptId}
```

Attempt schemaVersion 1: id, uid, logicalSessionId, planVersionId, workoutId, titleVi/titleEn, startedAt/finishedAt, receivedAt server timestamp, exercises [{id, performed}]. Không có trường confirmed/status/trainerUid do client gửi.

Owner chỉ đọc UID của mình và tạo attempt mới; không sửa/xóa. Admin theo `users/{auth.uid}/role == admin` được đọc UID cụ thể và tạo confirmation cho attempt đã tồn tại. Confirmation gồm schemaVersion, uid, attemptId, confirmedBy=auth.uid, confirmedAt server timestamp; không sửa/xóa, kể cả admin. Không đọc toàn bộ danh sách UID. Không cấp write ở node cha.

File đề xuất: `KgyMs-RTDB-Rules-M2C-PROPOSED-20261115.json`.

Giữ nguyên cả 20 nhánh trong MERGED checkpoint, chỉ thêm 2 nhánh M2C. MERGED SHA256:
`439704e6bc23cc3708c986dcb861e44a6ce7570bfd1c6d271aa946c44d5fe49e`.

Trước Publish: người dùng đọc Rules production mới nhất trong Console, đối chiếu 20 nhánh với checkpoint để tránh ghi đè thay đổi của người khác, xem diff proposal và tự Publish sau khi duyệt. Nếu production đã khác checkpoint, cần hợp nhất lại và test bản mới. Không mở toàn DB để chạy tính năng. Không dùng workoutSessions/checkIns thay thế.

Chưa đọc/ghi thử history production, chưa Publish Rules. Nếu production từ chối, local pending còn nguyên và UI báo thiếu quyền; sau khi Rules được duyệt, nhấn Thử đồng bộ lại. Chưa có màn hình/quy trình admin xác minh thực tế. Không giả lập admin confirmation trong ứng dụng chính. Test tạo role admin chỉ là fixture ở demo Emulator, sau đó dùng client authenticated thông thường để kiểm tra quyền.

## Kiểm thử thực tế

- Debug assemble + DebugAndroidTest assemble: PASS.
- Demo assemble + DemoAndroidTest assemble + kiểm tra APK cô lập: PASS.
- Unit tập trung: 52/52 PASS (History12, Progress2, HomeGoal9, WorkoutStability7, PlanSync15, PlanProduction7).
- Rules trên chính file M2C proposal: 20/20 PASS, gồm bảo toàn 20 nhánh cũ, owner/other/guest/admin, schema, immutable/delete, fake confirmation, multi-path rollback và hai client cùng ID.
- Android Firebase demo integration: 5/5 PASS (hai client live, account isolation, retry/version/retake, permission failure, admin confirmation thật trên Emulator).
- Compose UI: 7/7 PASS (4 regression Progress + 3 M2C Home/Workout/goal). Lần đầu một test mới dùng sai nhãn nút Đóng; sửa selector Đã hiểu rồi chạy lại đầy đủ 7 test PASS, không bỏ assertion/test.
- Process restart: seed offline 1/1 + verify ở PID mới 1/1 PASS. Đọc pending/draft từ đĩa trước khi kết nối lại, upload đúng một attempt.
- Smoke launcher demo: mở được activity riêng, hierarchy đọc được, không có AndroidRuntime crash.
- NOT RUN: production end-to-end trước Publish, full test suite toàn ứng dụng, hai điện thoại vật lý, mọi kích thước/font UI, stress test và background sync khi app bị đóng. Hai client được kiểm tra bằng hai named FirebaseApp trên cùng Android Emulator.

Logs trong `KgyMs/app/build/m2c-*` và `firebase-tests/artifacts/m2c-rules-latest.log` (build artifacts không stage).

## Chạy an toàn trên Emulator

Trong `firebase-tests`:

```powershell
node scripts/run-workout-history-tests.mjs test
node scripts/run-workout-history-tests.mjs start
```

Không chạy hai lệnh cùng lúc. Test runner từ chối nếu cổng demo đang được dùng. Mode test dùng instance tạm, không import dữ liệu đã lưu; mode start import snapshot đầy đủ gần nhất và export ra thư mục thế hệ mới khi Ctrl+C, giữ snapshot trước. Project chỉ `demo-kgyms-personal-plans`, Auth9099 và Database9000. Không deploy.

Trong `KgyMs`, dùng JDK Android Studio hiện có:

```powershell
.\gradlew.bat -PpersonalPlanEmulatorTests=true :app:assemblePersonalPlanEmulator :app:assemblePersonalPlanEmulatorAndroidTest
node tools/verify-personal-plan-apk.mjs
adb install -r -t app/build/outputs/apk/personalPlanEmulator/app-personalPlanEmulator.apk
adb install -r -t app/build/outputs/apk/androidTest/personalPlanEmulator/app-personalPlanEmulator-androidTest.apk
adb shell am start -n com.kgyms.app.personalplanemu/com.kgyms.app.personalplan.PersonalPlanEmulatorActivity
```

Dùng package `.personalplanemu`, không mở production APK để thử. Nếu cấu hình Auth cũ yêu cầu bridge loopback, giữ `adb reverse tcp:9000 tcp:9000` hiện có. Các endpoint từ Android là 10.0.2.2:9099/9000. Launcher demo có Home/Plan/Progress, nút Offline/Online và Demo A/B; không Profile/Storage adapter. Tạo lịch, tập/đánh dấu/Finish, xem số attempts/unique và pending; thử Offline/Online. Dữ liệu demo được giữ lại, không uninstall/clear app data.

## File đã thay đổi

Sửa (8):
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/MemberApp.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/MemberEntryScreens.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/MemberPreferences.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/home/MemberHomeScreen.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/progress/MemberProgressScreen.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/workout/MemberWorkoutScreen.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/profile/ProfileFlow.kt (chỉ guard một dòng: goal chưa đặt không truyền 0 vào editor cũ; không sửa Profile/Firebase/avatar nghiệp vụ)
- KgyMs/app/src/personalPlanEmulator/java/com/kgyms/app/personalplan/PersonalPlanEmulatorActivity.kt

Tạo (16, gồm file này):
- KgyMs/app/src/main/java/com/kgyms/app/data/model/WorkoutHistory.kt
- KgyMs/app/src/main/java/com/kgyms/app/data/repository/WorkoutHistoryRepository.kt
- KgyMs/app/src/main/java/com/kgyms/app/data/local/LocalWorkoutHistoryStore.kt
- KgyMs/app/src/main/java/com/kgyms/app/data/firebase/WorkoutHistoryCodec.kt
- KgyMs/app/src/main/java/com/kgyms/app/data/firebase/WorkoutHistoryFirebaseRemote.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/progress/WorkoutHistoryState.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/progress/WorkoutHistoryComponents.kt
- KgyMs/app/src/main/java/com/kgyms/app/ui/member/workout/WorkoutHistoryRunContext.kt
- KgyMs/app/src/test/java/com/kgyms/app/ui/member/progress/WorkoutHistoryTest.kt
- KgyMs/app/src/androidTestPersonalPlanEmulator/java/com/kgyms/app/memberprogress/WorkoutHistoryIntegrationTest.kt
- KgyMs/app/src/androidTestPersonalPlanEmulator/java/com/kgyms/app/memberprogress/WorkoutHistoryUiTest.kt
- firebase-tests/KgyMs-RTDB-Rules-M2C-PROPOSED-20261115.json
- firebase-tests/scripts/build-workout-history-rules.mjs
- firebase-tests/scripts/run-workout-history-tests.mjs
- firebase-tests/test/workout-history.test.mjs
- firebase-tests/M2C-HANDOFF.md

`docs/` có 4 file untracked từ trước; không sửa/ghi/xóa. Không sửa generator/editor/archive/sync Personal Plan, Auth/Admin/Membership/Payment/Check-in/Nutrition, google-services.json hoặc MERGED Rules. Không dùng Storage/Billing, không thay đổi production data trong kiểm thử.

Giới hạn: tải archive M2C theo đúng một UID, chưa phân trang; nên bổ sung phân trang khi dữ liệu lớn. Dữ liệu offline dựa vào đồng hồ thiết bị (Rules chặn thời điểm kết thúc quá 5 phút tương lai), lệch đồng hồ sẽ giữ pending/báo lỗi thay vì sửa kết quả âm thầm. Local file hỏng báo lỗi và không xóa/ghi đè. Confirmation bất biến, chưa có quy trình thu hồi/sửa xác nhận admin.

## Git status cuối cùng

```text
## TruongTanKiet...origin/TruongTanKiet [ahead 1]
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/MemberApp.kt
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/MemberEntryScreens.kt
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/MemberPreferences.kt
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/home/MemberHomeScreen.kt
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/profile/ProfileFlow.kt
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/progress/MemberProgressScreen.kt
 M KgyMs/app/src/main/java/com/kgyms/app/ui/member/workout/MemberWorkoutScreen.kt
 M KgyMs/app/src/personalPlanEmulator/java/com/kgyms/app/personalplan/PersonalPlanEmulatorActivity.kt
?? KgyMs/app/src/androidTestPersonalPlanEmulator/java/com/kgyms/app/memberprogress/
?? KgyMs/app/src/main/java/com/kgyms/app/data/firebase/WorkoutHistoryCodec.kt
?? KgyMs/app/src/main/java/com/kgyms/app/data/firebase/WorkoutHistoryFirebaseRemote.kt
?? KgyMs/app/src/main/java/com/kgyms/app/data/local/LocalWorkoutHistoryStore.kt
?? KgyMs/app/src/main/java/com/kgyms/app/data/model/WorkoutHistory.kt
?? KgyMs/app/src/main/java/com/kgyms/app/data/repository/WorkoutHistoryRepository.kt
?? KgyMs/app/src/main/java/com/kgyms/app/ui/member/progress/WorkoutHistoryComponents.kt
?? KgyMs/app/src/main/java/com/kgyms/app/ui/member/progress/WorkoutHistoryState.kt
?? KgyMs/app/src/main/java/com/kgyms/app/ui/member/workout/WorkoutHistoryRunContext.kt
?? KgyMs/app/src/test/java/com/kgyms/app/ui/member/progress/WorkoutHistoryTest.kt
?? docs/
?? firebase-tests/KgyMs-RTDB-Rules-M2C-PROPOSED-20261115.json
?? firebase-tests/M2C-HANDOFF.md
?? firebase-tests/scripts/build-workout-history-rules.mjs
?? firebase-tests/scripts/run-workout-history-tests.mjs
?? firebase-tests/test/workout-history.test.mjs
```

```text
 .../main/java/com/kgyms/app/ui/member/MemberApp.kt | 17 +++++----
 .../com/kgyms/app/ui/member/MemberEntryScreens.kt  |  2 +-
 .../com/kgyms/app/ui/member/MemberPreferences.kt   | 12 ++++---
 .../kgyms/app/ui/member/home/MemberHomeScreen.kt   | 10 ++++--
 .../com/kgyms/app/ui/member/profile/ProfileFlow.kt |  2 +-
 .../app/ui/member/progress/MemberProgressScreen.kt | 41 ++++++++++++++++++----
 .../app/ui/member/workout/MemberWorkoutScreen.kt   | 35 ++++++++++++------
 .../personalplan/PersonalPlanEmulatorActivity.kt   | 30 +++++++++++++++-
 8 files changed, 114 insertions(+), 35 deletions(-)
```

8 tracked files sửa; 16 file M2C mới chưa tracked. 4 file docs/ untracked đã tồn tại trước lượt này. Index không có staged changes. Không thay đổi HEAD/branch.
