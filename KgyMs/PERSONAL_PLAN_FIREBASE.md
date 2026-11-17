# Personal Plan — main app Firebase integration

The ordinary `com.kgyms.app` APK now uses its existing authenticated FirebaseApp
(`kgyms-72c74`) and Database
`https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app`.
It touches only `memberWorkoutPlans/{currentAuthUid}`. No new login flow,
Storage, Functions, Billing, production API configuration, or Google Services
file is introduced. The `.personalplanemu` APK remains demo-only.

## Runtime

- Existing per-UID `archive_v1` data is retained. Production metadata is additive
  under `firebase_sync_v1`; demo metadata is not reused or overwritten.
- Home and Plan share the same repository/state. Save commits locally first,
  then requests foreground sync. Startup, reconnect, and a newer remote revision
  also trigger sync. Only the small owner's `state` node is observed; there is
  no periodic full-archive polling. Transient failures have three bounded
  backoff retries; permission failures and active conflicts are not polled.
- Phase 2B immutable append/CAS/merge protocol is reused. Different content under
  one plan ID is a conflict; stale active choices are not automatically rebased.
  An explicit choice in the UI resolves active conflicts while keeping versions.
- Sync acknowledgements come from the server, not cached listener data. Offline
  edits persist across process restarts. No workout history/M2C confirmation is
  generated. Stopping the observer only removes its own listeners; it never
  signs out, purges writes, or takes the shared Database offline.
- A denied read/write shows “Chưa có quyền đồng bộ”; plans remain usable locally.
  An unverified cloud read is never displayed as a confirmed empty archive.

## Rules proposal — NOT deployed

Read-only request to `/.settings/rules.json` returned **403** on 14 November 2026.
There is no cached CLI authentication session. This does **not** prove whether
member reads/writes are allowed; current deployed Rules could not be inspected.
No production member data was read or written during validation.

The generated proposal is:
`app/build/personal-plan-production-rules/database.proposed.rules.json`.
Its provenance/status is in the neighboring `read-status.json`.
It uses the supplied 2026-11-13 snapshot, preserves all 19 old groups, and is
identical to the Phase 2A candidate covered by 63 Rules tests. It is **not a
verified copy of the latest production Rules**.

Before applying anything, compare the latest Rules in Firebase Console with
the snapshot. Add only the `memberWorkoutPlans` branch from the proposal to
the current `rules` object, retaining all other current branches. A parent
read/write grant can bypass child protections and requires separate review.
Do not replace the entire live Rules file with an unverified older snapshot.
Publishing requires the owner's separate approval; no deploy command is run
or included in this integration. If an existing Personal Plan branch differs,
review it instead of overwriting it.

After approved Rules are applied, open Home/Plan and press “Đồng bộ” to retry
and reattach the owner's metadata listener. Existing local versions upload
with the same IDs. Use only a small, authorized account check on production;
do concurrency/stress/security testing on the demo Emulator. Successful real
production account read/write is still unverified in this environment.

## Validation and files

Ordinary build: `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`.
For the separate APK/test commands, use `PERSONAL_PLAN_EMULATOR.md`.
Production policy/coordinator unit tests are `PersonalPlanProductionTest`.
Additional Android tests are `PersonalPlanProductionIntegrationTest` and
`PersonalPlanProductionUiTest`; network cases use only demo Auth/Database.

This integration changes `PersonalPlanState`, `PersonalPlanScreens`,
`LocalPersonalPlanRepository`, `PersonalPlanSync`, `SyncedPersonalPlanRepository`,
`PersonalPlanFirebaseService`, and the small connection interface on
`PersonalPlanEmulatorConnection`. New implementation files are
`PersonalPlanConnection`, `PersonalPlanProductionConnection`,
`PersonalPlanProductionPolicy`, and `PersonalPlanSyncCoordinator`.
`PersonalPlanSyncTest` gains a permission-denial regression. The new Rules
read/proposal helper is `tools/personal-plan-production-rules.mjs` (GET only).
All Phase 2A source and original regression tests are retained.

Final validation on 14 November 2026: Debug build + Debug AndroidTest APK PASS;
106/106 JVM unit tests PASS; isolated demo APK + test APK PASS; 63/63 Rules
tests PASS; 67/67 Android UI/integration tests PASS (including the 52 original
Home/Plan/Workout/M2C regressions); process-restart seed and verify both PASS.
Full regression ran once at the end. Logs are in
`app/build/personal-plan-production-final-*.txt`,
`personal-plan-production-rules-tests.txt`, and
`personal-plan-production-restart-{seed,verify}.txt`.
The ordinary production APK was built, never launched during network testing.
