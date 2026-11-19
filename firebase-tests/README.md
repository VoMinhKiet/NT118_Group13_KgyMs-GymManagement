# Personal Plan — Emulator only (Phase 2A)

This folder does not connect the Android app to Firebase. The app continues to use its existing local repository. Do not deploy these rules or point this test suite at a real project.

## Requirements and commands (PowerShell)

Use Node 24.12+ in the Node 24 LTS line to satisfy all tool engine ranges. This workspace was also verified with Node 26.13.0 / npm 11.20.0; the unused Hosting dependency `superstatic` warns about Node 26. A Java 21+ runtime is required. The runner discovers Android Studio's bundled JDK on Windows if `JAVA_HOME` is unset (tested with JBR 25.0.3).

```powershell
Set-Location C:\Users\ADMIN\Desktop\KgyMs\firebase-tests
npm.cmd ci --ignore-scripts --cache .npm-cache
npm.cmd run rules:check
npm.cmd test
```

For an interactive local Emulator session:

```powershell
npm.cmd run emulators:start
```

Stop it with Ctrl+C before running `npm.cmd test`; do not kill unrelated services if a port is in use. This suite only creates/resets its disposable demo namespace. It does not import production records or export/replace Android device data.

The runner invokes the local, pinned CLI with these fixed options:

```text
--project demo-kgyms-personal-plans
--config firebase-tests/firebase.json (resolved to an absolute path)
--non-interactive
emulators:exec OR emulators:start
--only database,auth
```

There is no `.firebaserc`, production alias, login, service-account file, or deploy script. `run-emulators.mjs` accepts only `exec` and `start`, uses a separate CLI config/cache directory, omits credential environment variables in its child process, and normalizes Windows PATH without changing system settings. Tests require the exact demo project and loopback endpoints before accessing or clearing data.

| Service | Host | Port |
| --- | --- | --- |
| Realtime Database | 127.0.0.1 | 9000 |
| Authentication | 127.0.0.1 | 9099 |
| Emulator Hub | 127.0.0.1 | 4400 |
| Emulator logging | 127.0.0.1 | 4500 |

UI is disabled. Cache, node_modules, npm cache, logs and artifacts are ignored by Git. Results are written to `artifacts/test-results.tap`; this file is produced by the actual Node test process, with no skipped tests required for success.

## Supplied baseline and candidate

`rules.production.snapshot.json` is a byte-for-byte copy of the supplied desktop snapshot. Its SHA-256 is:

```text
c5768d2f8de30787f4f14f3d0b8b999b808c7e92444d8a7cfd71ae57c513f883
```

The JSON has exactly 19 top-level rule groups and no broad root read/write grants. `scripts/build-rules.mjs` verifies the checksum and preserves every existing rule string, wildcard, index and validation. It adds only `rules.memberWorkoutPlans`. `npm.cmd run rules:check` and the first test verify that the candidate equals the generated result and all 19 legacy groups are deeply equal. Only `database.candidate.rules.json` is loaded by the emulator config.

To intentionally regenerate a reviewed candidate after editing the generator:

```powershell
npm.cmd run rules:build
npm.cmd run rules:check
```

Do not edit the supplied snapshot to make a check pass. Candidate size limits and expressions are finite because RTDB Rules have no general loops or child-count API.

## Data contract

```text
memberWorkoutPlans/{uid}
  remoteSchemaVersion: 1
  state:
    revision: positive integer
    nextOrdinal: next unused integer
    activeId?: existing plan ID
  plans/{planId}:
    ordinal: fixed positive integer
    uploadedAt: server timestamp
    payload: existing local plan schema 1, including ordered sessions,
             exerciseIds, optional suggestedExerciseIds and revisionOf
  versionOrder/v{ordinal}: planId
```

Plan IDs are keys and must equal `payload.id`; `payload.uid` must equal the owner path. Session IDs stay inside each plan payload, so revisions can retain logical session IDs without collisions across versions. No confirmed workout/history fields belong here. The emulator fixture uses existing catalog exercise/template IDs; it does not add catalog records.

`activeId` may be absent during initial history import. After it is first selected, clearing it is denied. Revision parents must already exist: upload original history in order before its revisions. Past dates are allowed for preserved history.

## Atomic compare-and-set protocol

There is no write grant at `memberWorkoutPlans`, `{uid}`, `plans` or `versionOrder`. Ownership reads are granted only at `{uid}`. The new branch has no admin/PT bypass. Write grants are limited to a new plan record, a new order slot, the fixed schema value on creation, and the state record.

Read the current state `(revision=r, nextOrdinal=n)`. An append submits ONE `update()` at the UID reference with these child paths:

```text
plans/{newId}        = { ordinal: n, uploadedAt: serverTimestamp(), payload }
versionOrder/v{n}    = newId
state               = { revision: r+1, nextOrdinal: n+1, activeId?: ... }
remoteSchemaVersion = 1  (first commit only; omit on later commits)
```

For the first commit, missing state is treated as `r=0, n=1`. Rules check cross-path post-write state with `newData.parent()`, and pre-write state with `root`. Each newly created record must match the same old counter and new revision. The state append branch must resolve to a genuinely new plan and matching order entry. Existing plan records/order entries reject any writes, including identical rewrites and deletion. There is no full-archive `set()` or root transaction.

An activation-only commit writes `state` with `r+1`, keeps `nextOrdinal` unchanged and references an existing plan. It cannot add another snapshot/order slot. Counter-only jumps, missing companion writes, extra snapshots and stale revisions fail atomically.

Two devices using the same base revision cannot both commit it. The append-only test protocol reads the new state and retries the immutable losing snapshot with the next free ordinal. It preserves the winning active selection. Stale activation is not automatically retried; the future Android adapter must ask for conflict resolution. The revision also catches an A → B → A active-pointer change.

`test/protocol-client.mjs` is a test helper, NOT a production adapter. It demonstrates idempotent retries: if the plan ID already exists with equal payload, no write or reactivation occurs. Different content under the same ID is a conflict, not an overwrite. The real adapter will need canonical decoding and durable per-UID pending/acknowledgement state.

## Validated structure and deliberate limits

- Strict required/allowed fields and types, schema/generator version 1, level enum, 1–7 integer days/week.
- Dense, unique weekday list matching the configured count.
- Exactly `sessionsPerWeek × 4` dense sessions; unique session IDs and dates; required session fields.
- Dense, nonempty, unique exercise/suggestion IDs, up to 64 per list. Current catalog is within this limit.
- Plan/UID strings up to 128 characters; exercise/session/template IDs up to 256; timezone up to 100. Review future format extensions before changing these limits.
- ISO date shape/month/day ranges are checked. Actual calendar validity (e.g. February 31), valid IANA timezone, exact rolling-week date distribution and catalog membership/difficulty remain domain/adapter validation. Rules do not claim to verify the Kotlin catalog or compute plan hashes.
- Firebase omits null/empty containers. Optional fields must be normalized by the future adapter without manufacturing suggestions or dropping unknown exercise IDs.

## Test coverage and legacy isolation

The suite uses official `authenticatedContext('A')`, `authenticatedContext('B')`, separate same-UID client contexts, and `unauthenticatedContext()`. All permission assertions run as client SDK requests. `withSecurityRulesDisabled` is used only to seed a fake admin record needed by the OLD rules; it is never used to prove a member operation succeeds. No Admin SDK is imported.

Coverage includes owner/cross-owner/guest/parent-list permissions, immutable snapshots and indexes, malformed schema, duplicate IDs, invalid activation, partial atomic writes, concurrent distinct/same-ID retries, six simultaneous contenders, ABA conflicts, and a fresh client reading the saved result. Legacy behavior is tested for users, gymInfo, workoutSessions, notifications, packages/memberships and activityLogs. `workoutSessions` is used only for an isolated legacy regression fixture; Personal Plan records never reuse that node.

## Before a real Android adapter / production rollout

1. Keep this candidate emulator-only. Review and approve rules deployment separately; do not deploy this entire snapshot blindly because production rules may change meanwhile.
2. Implement a separately injected Personal Plan service, retaining the current local repository and per-UID data. No current Android/Auth/UI source has been changed by Phase 2A.
3. Define canonical merge, pending/confirmed status, active conflict UI and numbering when multiple devices have independently named local histories. Never report Firebase sync on a local listener event alone.
4. Add Android Auth+Database emulator integration and offline/process-restart tests. The current suite proves RTDB rules/client protocol, not a not-yet-written Android adapter or real sign-in UI.
5. Tooling uses pinned dependencies and a lockfile. `@grpc/grpc-js` is overridden to patched 1.14.7 (same major) to remove the high-severity transitive advisory. Remaining npm audit findings are moderate dev-tool dependencies; no Android dependencies were altered. See ignored `artifacts/npm-audit.json` when auditing the installed toolchain. Do not apply `npm audit fix --force` as a shortcut.
