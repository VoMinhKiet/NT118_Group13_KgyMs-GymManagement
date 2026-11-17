# Personal Plan — isolated Android/Firebase Emulator (Phase 2B)

Run commands from the Android directory `KgyMs/`. This is a separate test APK,
`com.kgyms.app.personalplanemu`; the installed ordinary KgyMs app, its accounts,
and its local preferences are not replaced or imported automatically.

## Isolation

- Fixed project: `demo-kgyms-personal-plans` for both Auth and Realtime Database.
- Auth: `10.0.2.2:9099`; Database: `10.0.2.2:9000` from the Android Emulator.
- Dedicated launcher, named demo FirebaseApp, no default FirebaseApp, no
  FirebaseInitProvider, and no generated production Google Services resources.
- `google-services.json` and normal debug/release Firebase configuration stay unchanged.
- The connection factory refuses the ordinary app package and any existing
  non-demo/default FirebaseApp. Tokens must have the demo audience and issuer.
- Only `memberWorkoutPlans/{demoUid}` is used. No Storage adapter, production
  login, profile, or avatar flow is entered. Never launch the ordinary debug
  APK for this network test. Never run `firebase deploy`.

## Start local services

Requires the existing Phase 2A dependencies in `../firebase-tests/node_modules`,
the cached emulator JAR, Node, and the Android Studio JBR. For a new machine,
install the pinned Phase 2A dependencies with `npm ci` in `firebase-tests/` first.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
node tools/personal-plan-emulators.mjs check
node tools/personal-plan-emulators.mjs start
```

The wrapper verifies the original Phase 2A candidate and binds to loopback. It
refuses occupied ports instead of stopping an existing emulator. It never
uses a production project alias, service account, or Firebase token.

Stop with Ctrl+C and let Firebase finish exporting. Auth and Database state
are exported to a new generation under `app/build/personal-plan-emulator/exports`
and the newest complete export is imported on the next start. Previous exports
are retained, never overwritten or deleted. This directory contains local test data: do not clean the
build directory if you want to retain it. A forced power loss may lose server
changes since the last export; Android's committed local archive is separate.
The wrapper can also import the original `saved-state` export if no generation
exists yet; the original export is kept intact.

## Build and install the isolated APK

```powershell
.\gradlew.bat -PpersonalPlanEmulatorTests=true :app:assemblePersonalPlanEmulator :app:assemblePersonalPlanEmulatorAndroidTest :app:testPersonalPlanEmulatorUnitTest
node tools/verify-personal-plan-apk.mjs
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adb -s emulator-5554 reverse --list
# Only when no existing tcp:9000 mapping is present:
& $adb -s emulator-5554 reverse --no-rebind tcp:9000 tcp:9000
& $adb -s emulator-5554 install -r -t app/build/outputs/apk/personalPlanEmulator/app-personalPlanEmulator.apk
& $adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/personalPlanEmulator/app-personalPlanEmulator-androidTest.apk
& $adb -s emulator-5554 shell am start -n com.kgyms.app.personalplanemu/com.kgyms.app.personalplan.PersonalPlanEmulatorActivity
```

Use your own device serial if different. Preserve an existing port mapping;
do not replace one pointing elsewhere. The reverse route is necessary because
the loopback-bound Database Emulator advertises `127.0.0.1:9000` in its
WebSocket handshake. First connection uses `10.0.2.2`, but reconnect uses the
advertised address. ADB routes that emulator-only loopback port back to the
same local Database Emulator. No port is forwarded to a production service.

The demo launcher has A/B accounts, Home/Plan tabs, and Offline/Online controls.
Create a plan, edit exercises, and watch the short sync status. Switch A/B to
check ownership. Offline edits commit immediately to this APK's UID-scoped
local preferences. Online sync runs periodically while connected; Retry is
also available. Active-plan conflicts require an explicit choice. The demo
launcher is intentionally limited and does not exercise real account login,
Admin, Profile, Storage, or production history.

## Data and acknowledgement contract

`PersonalPlanRemoteCodec` preserves the existing flattened payload, including
plan/session/template/exercise IDs, `revisionOf`, and suggested exercises:

```text
memberWorkoutPlans/{uid}/
  remoteSchemaVersion: 1
  state: { revision, nextOrdinal, activeId? }
  plans/{planId}: { ordinal, uploadedAt, payload }
  versionOrder/v{ordinal}: planId
```

An append atomically creates one immutable payload, its ordinal, and revision
N+1. A stale writer is rejected by the unchanged Phase 2A rules. Append retries
re-read and merge by ID; equal content is idempotent, different content is a
conflict. An outdated active-plan selection is never automatically rebased.
There is no archive-wide `setValue`, overwrite, delete, or regenerated plan.

Reads use authenticated, non-redirecting **local emulator HTTP** so an SDK cache
fallback cannot be mistaken for a server acknowledgement. Writes use SDK
`updateChildren` and await server completion. A read-back validates the snapshot.
The SDK state subscription is only a connectivity keepalive, never evidence
of successful synchronization. Pending status survives restart through local
archive + metadata committed together under the per-UID local lock/CAS.

Server ordinals name confirmed versions “Lịch tập N”. Pending local versions
have provisional local numbers until merged; another device can legitimately
have already claimed that ordinal. Switching the active version never changes
confirmed ordinals. Different plan IDs and all prior versions are retained.

## Tests

Do not run clearing Rules tests against the manually running Android server.
Stop/export it first; the following command starts a separate disposable
Emulator process without importing the saved Android data:

```powershell
node tools/personal-plan-emulators.mjs rules
```

Ordinary app build and unit regression (build only; do not install/run its APK):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

With the demo server running, isolated APKs installed and reverse route ready:

```powershell
$runner = 'com.kgyms.app.personalplanemu.test/androidx.test.runner.AndroidJUnitRunner'
& $adb -s emulator-5554 shell am instrument -w -r -e class 'com.kgyms.app.personalplan.PersonalPlanFirebaseIntegrationTest,com.kgyms.app.personalplan.PersonalPlanFirebaseUiTest' $runner
# Real process restart, preserving preferences and server data:
& $adb -s emulator-5554 shell am instrument -w -r -e class 'com.kgyms.app.personalplan.PersonalPlanProcessRestartTest#seed' $runner
& $adb -s emulator-5554 shell am force-stop com.kgyms.app.personalplanemu
& $adb -s emulator-5554 shell am instrument -w -r -e class 'com.kgyms.app.personalplan.PersonalPlanProcessRestartTest#verify' $runner
```

The restart pair must run in that order, in separate instrumentation processes.
The fixture checks a different PID, restores pending edited data before any
network call, then verifies synchronization. Tests create unique demo users
and never clear the shared Database or Auth state. Repeated runs retain data.
All existing Home/Plan/Workout/Progress Compose classes can also run in this
isolated instrumentation APK, with no default Firebase initialization.

The demo APK remains isolated. The subsequent main-app integration uses the
existing production Auth identity; it requires separately approved production
Rules. See `PERSONAL_PLAN_FIREBASE.md` for current verification and rollout limits.

## Phase 2B file inventory

Modified tracked files (relative to this Android directory):

- `app/build.gradle.kts` — isolated build/test variant; ordinary variants unchanged.
- `app/src/main/java/com/kgyms/app/data/local/LocalPersonalPlanRepository.kt` — additive per-UID metadata and atomic local CAS.
- `app/src/main/java/com/kgyms/app/ui/member/plan/PersonalPlanState.kt` — shared sync state and confirmed ordinal display.
- `app/src/main/java/com/kgyms/app/ui/member/plan/PersonalPlanScreens.kt` — compact Home/Plan sync controls and explicit conflict choice.

Untracked Phase 2B files retained/added:

- `app/src/main/java/com/kgyms/app/data/model/PersonalPlanSync.kt`
- `app/src/main/java/com/kgyms/app/data/firebase/PersonalPlanRemoteCodec.kt`
- `app/src/main/java/com/kgyms/app/data/firebase/PersonalPlanEmulatorConnection.kt`
- `app/src/main/java/com/kgyms/app/data/firebase/PersonalPlanFirebaseService.kt`
- `app/src/main/java/com/kgyms/app/data/repository/SyncedPersonalPlanRepository.kt`
- `app/src/test/java/com/kgyms/app/ui/member/plan/PersonalPlanSyncTest.kt`
- `app/src/personalPlanEmulator/AndroidManifest.xml`
- `app/src/personalPlanEmulator/java/com/kgyms/app/personalplan/PersonalPlanEmulatorActivity.kt`
- `app/src/personalPlanEmulator/res/values/strings.xml`
- `app/src/personalPlanEmulator/res/xml/personal_plan_emulator_network_security.xml`
- `app/src/androidTestPersonalPlanEmulator/AndroidManifest.xml`
- `app/src/androidTestPersonalPlanEmulator/java/com/kgyms/app/personalplan/PersonalPlanFirebaseIntegrationTest.kt`
- `app/src/androidTestPersonalPlanEmulator/java/com/kgyms/app/personalplan/PersonalPlanFirebaseUiTest.kt`
- `tools/personal-plan-emulators.mjs`
- `tools/verify-personal-plan-apk.mjs`
- `tools/verify-personal-plan-apk.ps1` — retained original verifier; use the Node equivalent if PowerShell script policy blocks this file. Do not bypass policy.
- `PERSONAL_PLAN_EMULATOR.md`

The pre-existing untracked root `../docs/` and `../firebase-tests/` are preserved.
No original tests or personal-plan business logic files were removed or disabled.

## Verified on 14 November 2026

Android Emulator `emulator-5554`, API 28; Java from Android Studio; demo project only.

| Check | Actual result |
| --- | --- |
| `assembleDebug`, `assembleDebugAndroidTest` | PASS (build only, ordinary APK never launched) |
| `testDebugUnitTest` | 98 tests, 0 failures/errors/skips |
| Isolated APK + instrumentation APK build | PASS |
| `testPersonalPlanEmulatorUnitTest` | 98 tests, 0 failures/errors/skips (same unit suite) |
| Phase 2A Security Rules | 63/63 PASS; all 19 original groups and snapshot SHA-256 verified |
| Existing Home/Plan/Workout/M2C Compose regression | 52/52 PASS |
| Real Firebase Emulator + Compose UI | 1/1 PASS |
| Android Auth/Database sync integration | 9/9 PASS |
| Cold process restart seed / verify | 2/2 PASS |
| Restart both Firebase emulators, import snapshot, verify same fixture again | PASS |
| Dedicated demo launcher smoke test / APK isolation verifier | PASS |

The sync integration covers upload, fresh-client download, edited snapshots,
A/B/unauthenticated denial, simultaneous writers, stale active selection,
idempotent retry, collision conflict, offline/reconnect, and closing a UID
session during synchronization. The Firebase UI test also verifies unavailable
history, pending status after an offline edit, confirmed sync, and the exact
edited Workout session; it does not create confirmed workout history.

Logs are retained in `app/build/personal-plan-2b-*.txt`; specifically
`final-debug`, `final-demo-build`, `final-integration`, `final-regression`,
`restart-seed`, `restart-verify`, `restart-after-server-import`, and `rules`.
Emulator snapshots are kept under the `exports` path described above.
No production or Storage adapter was enabled in the isolated APK. Physical devices and production
deployment were not tested; neither is part of this isolated milestone.
