import { test, before, beforeEach, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { enableLogging } from 'firebase/database';
import { baseline, candidate as defaultCandidate, candidateText as defaultCandidateText, SNAPSHOT_SHA256 } from '../scripts/build-rules.mjs';
import { makePlan, planId, pathFor, readArchive, appendUpdates, appendPrepared, publish } from './protocol-client.mjs';

const PROJECT = 'demo-kgyms-personal-plans';
// Optional local input: preserve every existing assertion, but load and inspect the actual selected file.
const selectedBytes = process.env.KGYMS_TEST_RULES_FILE ? readFileSync(process.env.KGYMS_TEST_RULES_FILE) : null;
const candidateText = selectedBytes ? selectedBytes.toString('utf8') : defaultCandidateText;
const candidate = selectedBytes ? JSON.parse(candidateText) : defaultCandidate;
if (selectedBytes) {
  const sha256 = createHash('sha256').update(selectedBytes).digest('hex');
  assert.equal(sha256, process.env.KGYMS_TEST_RULES_SHA256, 'Selected Rules changed before the test started');
  console.log(`RULES_UNDER_TEST ${process.env.KGYMS_TEST_RULES_FILE} SHA256=${sha256}`);
}
assert.equal(process.env.GCLOUD_PROJECT, PROJECT, 'Demo project required');
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, '127.0.0.1:9000', 'Local emulator required before any database operation');
enableLogging(false);
let env, a, b, guest;
before(async () => {
  env = await initializeTestEnvironment({ projectId: PROJECT, database: { host: '127.0.0.1', port: 9000, rules: candidateText } });
});
beforeEach(async () => {
  await env.clearDatabase(); // Isolated demo namespace only; never a production instance.
  a = env.authenticatedContext('A').database();
  b = env.authenticatedContext('B').database();
  guest = env.unauthenticatedContext().database();
});
after(async () => { if (env) await env.cleanup(); });
const append = (db = a, plan = makePlan()) => publish(db, plan, { activate: true });
async function badAppend(mutate, { base = null, db = a, plan = makePlan() } = {}) {
  const updates = appendUpdates(base, plan, true);
  mutate(updates, plan);
  await assertFails(appendPrepared(db, plan.uid, updates));
  assert.deepEqual(await readArchive(db, plan.uid), base, 'Rejected atomic write must leave no partial data');
}

test('supplied snapshot hash and all 19 legacy groups are preserved exactly', () => {
  const bytes = readFileSync(new URL('../rules.production.snapshot.json', import.meta.url));
  assert.equal(createHash('sha256').update(bytes).digest('hex'), SNAPSHOT_SHA256);
  assert.equal(Object.keys(baseline.rules).length, 19);
  assert.equal(Object.keys(candidate.rules).length, 20);
  for (const [key, rule] of Object.entries(baseline.rules)) assert.deepEqual(candidate.rules[key], rule, key);
  assert.equal(candidate.rules['.read'], undefined);
  assert.equal(candidate.rules['.write'], undefined);
  for (const branch of [candidate.rules.memberWorkoutPlans, candidate.rules.memberWorkoutPlans.$uid,
    candidate.rules.memberWorkoutPlans.$uid.plans, candidate.rules.memberWorkoutPlans.$uid.versionOrder]) {
    assert.ok(!branch['.write'] || branch['.write'] === false, 'No broad write grant above individual records');
  }
});

test('A can create and read A; B can create and read B, with independent numbering', async () => {
  const pa = makePlan(); const pb = makePlan('B');
  await assertSucceeds(append(a, pa)); await assertSucceeds(append(b, pb));
  assert.deepEqual((await readArchive(a)).plans[pa.id].payload, pa);
  const other = await readArchive(b, 'B');
  assert.deepEqual(other.plans[pb.id].payload, pb);
  assert.equal(other.plans[pb.id].ordinal, 1);
  assert.equal((await readArchive(a)).plans[pa.id].ordinal, 1);
});
test('A cannot read or write B even with a valid B payload', async () => {
  await append(b, makePlan('B'));
  await assertFails(a.ref(pathFor('B')).get());
  await assertFails(appendPrepared(a, 'B', appendUpdates(null, makePlan('B','attack'), true)));
});
test('B cannot read or write A even with a valid A payload', async () => {
  await append();
  await assertFails(b.ref(pathFor('A')).get());
  await assertFails(appendPrepared(b, 'A', appendUpdates(null, makePlan('A','attack'), true)));
});
test('unauthenticated clients cannot read, create, modify or delete plans', async () => {
  const plan = makePlan(); await append(a, plan);
  await assertFails(guest.ref(pathFor('A')).get());
  await assertFails(appendPrepared(guest, 'A', appendUpdates(null, makePlan('A','guest'), true)));
  await assertFails(guest.ref(`${pathFor('A')}/state/activeId`).set(plan.id));
  await assertFails(guest.ref(pathFor('A')).remove());
});
test('neither members nor an admin-role client can list all personal-plan UIDs', async () => {
  await append();
  await env.withSecurityRulesDisabled(async ctx => ctx.database().ref('users/admin').set({ role: 'admin', status: 'active' }));
  for (const db of [a, b, guest, env.authenticatedContext('admin').database()]) {
    await assertFails(db.ref('memberWorkoutPlans').get());
    await assertFails(db.ref('memberWorkoutPlans').orderByKey().equalTo('A').get());
  }
});
test('admin role does not bypass ownership of another member personal plan', async () => {
  await append();
  await env.withSecurityRulesDisabled(async ctx => ctx.database().ref('users/admin').set({ role: 'admin' }));
  const admin = env.authenticatedContext('admin').database();
  await assertFails(admin.ref(pathFor('A')).get());
  await assertFails(admin.ref(pathFor('A')).remove());
});
test('whole UID replacement and whole parent replacement are denied', async () => {
  await append(); const archive = await readArchive(a);
  await assertFails(a.ref(pathFor('A')).set(archive));
  await assertFails(a.ref('memberWorkoutPlans').set({ A: archive }));
  await assertFails(a.ref(`${pathFor('A')}/plans`).set(archive.plans));
});
test('saved snapshot payload, fields, metadata and nested exercise lists cannot be changed', async () => {
  const plan = makePlan(); await append(a, plan);
  const root = `${pathFor('A')}/plans/${plan.id}`;
  for (const [suffix, value] of [['payload/level','Advanced'], ['payload/uid','B'], ['ordinal',42],
    ['uploadedAt',1], ['payload/sessions/0/exerciseIds/0','bridge'], ['payload/extra',true]]) {
    await assertFails(a.ref(`${root}/${suffix}`).set(value));
  }
  await assertFails(a.ref(root).set((await readArchive(a)).plans[plan.id]));
  assert.deepEqual((await readArchive(a)).plans[plan.id].payload, plan);
});
test('no removal at any saved-plan, index, state, UID or parent path', async () => {
  const plan = makePlan(); await append(a, plan);
  const before = await readArchive(a);
  for (const suffix of ['', '/plans', `/plans/${plan.id}`, `/plans/${plan.id}/payload/sessions/0`,
    `/plans/${plan.id}/payload/sessions/0/exerciseIds/0`, '/versionOrder', '/versionOrder/v1', '/state', '/remoteSchemaVersion']) {
    await assertFails(a.ref(pathFor('A') + suffix).remove());
  }
  await assertFails(a.ref('memberWorkoutPlans').remove());
  assert.deepEqual(await readArchive(a), before);
});
test('version order is immutable and cannot be reused or renumbered', async () => {
  const plan = makePlan(); await append(a, plan);
  await assertFails(a.ref(`${pathFor('A')}/versionOrder/v1`).set(planId('other')));
  await assertFails(a.ref(`${pathFor('A')}/versionOrder/v2`).set(plan.id));
});

const invalidPayloads = {
  'wrong owner': p => { p.uid = 'B'; },
  'wrong plan ID': p => { p.id = planId('mismatch'); },
  'unsupported schema': p => { p.schemaVersion = 2; },
  'unsupported generator': p => { p.generatorVersion = 2; },
  'missing required field': p => { delete p.level; },
  'unknown field': p => { p.confirmedAt = 123; },
  'wrong level': p => { p.level = 'PT'; },
  'wrong boolean': p => { p.repeated = 'true'; },
  'zero days': p => { p.sessionsPerWeek = 0; },
  'eight days': p => { p.sessionsPerWeek = 8; },
  'fractional days': p => { p.sessionsPerWeek = 1.5; },
  'invalid weekday': p => { p.weekdays[0] = 7; },
  'wrong weekday count': p => { p.weekdays = [0, 1]; },
  'invalid date format': p => { p.startDate = '2026-13-40'; },
  'empty timezone': p => { p.zoneId = ''; },
  'missing session': p => { p.sessions.pop(); },
  'extra session': p => { p.sessions.push({ ...p.sessions[0], id: 'extra', date: '2026-12-31' }); },
  'session index hole': p => { p.sessions = { 0: p.sessions[0], 2: p.sessions[2], 3: p.sessions[3] }; },
  'duplicate session ID': p => { p.sessions[1].id = p.sessions[0].id; },
  'duplicate session date': p => { p.sessions[1].date = p.sessions[0].date; },
  'unknown session field': p => { p.sessions[0].completedAt = 123; },
  'missing session ID': p => { delete p.sessions[0].id; },
  'empty template': p => { p.sessions[0].templateId = ''; },
  'empty exercises': p => { p.sessions[0].exerciseIds = []; },
  'duplicate exercises': p => { p.sessions[0].exerciseIds = ['squat','squat']; },
  'non-string exercise': p => { p.sessions[0].exerciseIds = ['squat', 2]; },
  'exercise index hole': p => { p.sessions[0].exerciseIds = { 0: 'squat', 2: 'row' }; },
  'duplicate suggestions': p => { p.sessions[0].suggestedExerciseIds = ['squat','squat']; },
  'unknown revision parent': p => { p.revisionOf = planId('missing'); },
  'self revision parent': p => { p.revisionOf = p.id; }
};
for (const [name, mutate] of Object.entries(invalidPayloads)) test(`rejects invalid payload: ${name}`, async () => {
  await badAppend((updates, plan) => mutate(updates[`plans/${plan.id}`].payload));
});
test('duplicate weekdays are rejected', async () => {
  const plan = makePlan('A','weekdays',2);
  await badAppend((updates, p) => { updates[`plans/${p.id}`].payload.weekdays = [0,0]; }, { plan });
});
test('all levels and 1–7 days/week keep dense four-week schedules readable', async () => {
  for (const level of ['Beginner','Intermediate','Advanced']) for (let count = 1; count <= 7; count++) {
    const plan = makePlan('A', `${level}-${count}`, count); plan.level = level;
    await publish(a, plan);
    assert.deepEqual((await readArchive(a)).plans[plan.id].payload, plan);
  }
  const result = await readArchive(a);
  assert.equal(Object.keys(result.plans).length, 21);
  assert.equal(result.state.nextOrdinal, 22);
});
test('edited snapshot retains same logical sessions, revision parent and original suggestions', async () => {
  const original = makePlan(); await append(a, original);
  const edited = structuredClone(original); edited.id = planId('revision'); edited.revisionOf = original.id;
  edited.sessions[0].suggestedExerciseIds = original.sessions[0].exerciseIds;
  edited.sessions[0].exerciseIds = ['bridge','row'];
  await publish(a, edited, { activate: true });
  const archive = await readArchive(a);
  assert.deepEqual(archive.plans[original.id].payload, original);
  assert.deepEqual(archive.plans[edited.id].payload, edited);
  assert.equal(archive.plans[edited.id].ordinal, 2);
  assert.equal(archive.state.activeId, edited.id);
});
test('bootstrap may defer active selection; once selected it cannot be silently cleared', async () => {
  const plan = makePlan(); await publish(a, plan);
  let before = await readArchive(a); assert.equal(before.state.activeId, undefined);
  await assertSucceeds(a.ref(`${pathFor('A')}/state`).set({ ...before.state, revision: before.state.revision + 1, activeId: plan.id }));
  before = await readArchive(a);
  const state = { ...before.state, revision: before.state.revision + 1 }; delete state.activeId;
  await assertFails(a.ref(`${pathFor('A')}/state`).set(state));
});
test('invalid active ID or malformed state cannot be committed', async () => {
  const plan = makePlan(); await append(a, plan);
  const before = await readArchive(a);
  for (const change of [{ activeId: planId('missing') }, { activeId: 99 }, { revision: 99 }, { revision: 1.5 },
    { nextOrdinal: 0 }, { nextOrdinal: 100 }, { extra: true }]) {
    await assertFails(a.ref(`${pathFor('A')}/state`).set({ ...before.state, revision: before.state.revision + 1, ...change }));
  }
  assert.deepEqual(await readArchive(a), before);
});
test('active selection may change to an older version without renaming or rewriting it', async () => {
  const first = makePlan(); const second = makePlan('A','second');
  await append(a, first); await publish(a, second, { activate: true });
  const before = await readArchive(a);
  await assertSucceeds(a.ref(`${pathFor('A')}/state`).set({ ...before.state, revision: before.state.revision + 1, activeId: first.id }));
  const after = await readArchive(a);
  assert.equal(after.state.activeId, first.id);
  assert.deepEqual(after.plans, before.plans); assert.deepEqual(after.versionOrder, before.versionOrder);
});
test('partial multi-path writes and counter-only jumps leave no orphan records', async () => {
  await append(); const base = await readArchive(a);
  const second = makePlan('A','second');
  for (const missing of ['state', `plans/${second.id}`, 'versionOrder/v2']) {
    await badAppend(updates => { delete updates[missing]; }, { base, plan: second });
  }
  await assertFails(a.ref(`${pathFor('A')}/state`).set({ ...base.state, revision: base.state.revision + 1, nextOrdinal: 3 }));
  assert.deepEqual(await readArchive(a), base);
});
test('mismatched ordinal, order key or order target rejects the whole commit', async () => {
  await append(); const base = await readArchive(a); const second = makePlan('A','second');
  for (const mutate of [
    u => { u[`plans/${second.id}`].ordinal = 4; },
    u => { u.versionOrder = { v5: second.id }; delete u['versionOrder/v2']; },
    u => { u['versionOrder/v2'] = planId('wrong-target'); }
  ]) await badAppend(mutate, { base, plan: second });
});
test('cannot smuggle a second snapshot or index through one revision increment', async () => {
  await append(); const base = await readArchive(a); const second = makePlan('A','second'); const third = makePlan('A','third');
  await badAppend(u => { u[`plans/${third.id}`] = { ...u[`plans/${second.id}`], payload: third }; }, { base, plan: second });
  await badAppend(u => { u['versionOrder/v3'] = second.id; }, { base, plan: second });
});
test('two devices with identical base revision: one wins; loser retries append-only without losing either version', async () => {
  const original = makePlan(); await append(a, original);
  const a2 = env.authenticatedContext('A').database();
  const [base1, base2] = await Promise.all([readArchive(a), readArchive(a2)]);
  assert.equal(base1.state.revision, base2.state.revision);
  const one = makePlan('A','device-one'); const two = makePlan('A','device-two');
  const outcomes = await Promise.allSettled([
    appendPrepared(a, 'A', appendUpdates(base1, one)), appendPrepared(a2, 'A', appendUpdates(base2, two))
  ]);
  assert.equal(outcomes.filter(r => r.status === 'fulfilled').length, 1);
  assert.equal(outcomes.filter(r => r.status === 'rejected').length, 1);
  const intermediate = await readArchive(a);
  assert.equal(Object.keys(intermediate.plans).length, 2);
  assert.equal(Object.keys(intermediate.versionOrder).length, 2);
  const loser = outcomes[0].status === 'rejected' ? one : two;
  await publish(a2, loser);
  const final = await readArchive(a);
  assert.equal(Object.keys(final.plans).length, 3);
  assert.deepEqual(final.plans[one.id].payload, one); assert.deepEqual(final.plans[two.id].payload, two);
  assert.deepEqual(final.plans[original.id].payload, original);
  assert.equal(final.state.activeId, original.id);
  assert.deepEqual(Object.values(final.plans).map(p => p.ordinal).sort(), [1,2,3]);
});
test('concurrent append retry protocol preserves six independent snapshots and unique ordinals', async () => {
  await append(); const base = await readArchive(a);
  const plans = Array.from({ length: 6 }, (_, i) => makePlan('A', `contender-${i}`));
  await Promise.all(plans.map(p => publish(env.authenticatedContext('A').database(), p, { base })));
  const final = await readArchive(a);
  assert.equal(Object.keys(final.plans).length, 7);
  assert.deepEqual(Object.values(final.plans).map(p => p.ordinal).sort((x,y) => x-y), [1,2,3,4,5,6,7]);
  for (const p of plans) assert.deepEqual(final.plans[p.id].payload, p);
});
test('retry after lost acknowledgement is idempotent and never reactivates an older plan', async () => {
  const first = makePlan(); await append(a, first);
  const before = await readArchive(a);
  assert.deepEqual(await publish(a, first), { created: false, ordinal: 1 });
  assert.deepEqual(await readArchive(a), before);
  const second = makePlan('A','second'); await publish(a, second, { activate: true });
  const next = await readArchive(a);
  await publish(a, first, { activate: true }); // already exists: skip write, including activation
  assert.deepEqual(await readArchive(a), next);
});
test('two devices retrying the same plan concurrently allocate only one ordinal', async () => {
  await append(); const base = await readArchive(a);
  const plan = makePlan('A','same-pending-plan');
  const results = await Promise.all([
    publish(a, plan, { base }), publish(env.authenticatedContext('A').database(), plan, { base })
  ]);
  assert.equal(results.filter(result => result.created).length, 1);
  assert.equal(results.filter(result => !result.created).length, 1);
  const final = await readArchive(a);
  assert.equal(Object.keys(final.plans).length, 2);
  assert.equal(Object.keys(final.versionOrder).length, 2);
  assert.equal(final.state.nextOrdinal, 3);
  assert.equal(final.plans[plan.id].ordinal, 2);
});
test('a new client connection reloads the exact immutable versions and active selection', async () => {
  const first = makePlan(); const second = makePlan('A','reopen');
  await append(a, first); await publish(a, second, { activate: true });
  const saved = await readArchive(a);
  const reopened = env.authenticatedContext('A').database();
  assert.deepEqual(await readArchive(reopened), saved);
});
test('same plan ID with different payload is rejected, not overwritten or renumbered', async () => {
  const plan = makePlan(); await append(a, plan);
  const conflict = structuredClone(plan); conflict.sessions[0].exerciseIds = ['bridge'];
  await assert.rejects(publish(a, conflict), /Same ID with different payload/);
  assert.deepEqual((await readArchive(a)).plans[plan.id].payload, plan);
});
test('stale active selection is denied even after active goes A to B to A (ABA)', async () => {
  const first = makePlan(); const second = makePlan('A','second'); await append(a, first); await publish(a, second);
  const old = await readArchive(a);
  await a.ref(`${pathFor('A')}/state`).set({ ...old.state, revision: old.state.revision + 1, activeId: second.id });
  const newer = await readArchive(a);
  await a.ref(`${pathFor('A')}/state`).set({ ...newer.state, revision: newer.state.revision + 1, activeId: first.id });
  await assertFails(a.ref(`${pathFor('A')}/state`).set({ ...old.state, revision: old.state.revision + 1, activeId: second.id }));
  assert.equal((await readArchive(a)).state.activeId, first.id);
});

// Legacy fixtures use a disabled-rules context ONLY to seed the fake admin/notification.
// Every permission assertion below uses an authenticated/unauthenticated client, never Admin SDK.
async function adminFixture() {
  await env.withSecurityRulesDisabled(async ctx => ctx.database().ref('users/admin').set({ role: 'admin', status: 'active' }));
  return env.authenticatedContext('admin').database();
}
test('legacy users: own registration/profile update works; cross-account read and privilege escalation fail', async () => {
  await assertSucceeds(a.ref('users/A').set({ role: 'user', status: 'active', fullName: 'Test A' }));
  await assertSucceeds(a.ref('users/A/fullName').set('Test A updated'));
  await assertSucceeds(a.ref('users/A').get());
  await assertFails(b.ref('users/A').get());
  await assertFails(a.ref('users/A/role').set('admin'));
});
test('legacy gymInfo: member read and admin-client write keep original permissions', async () => {
  const admin = await adminFixture();
  await assertSucceeds(admin.ref('gymInfo').set({ adminUid: 'admin', phone: '0123456789' }));
  await assertSucceeds(a.ref('gymInfo').get());
  await assertFails(guest.ref('gymInfo').get());
  await assertFails(a.ref('gymInfo/phone').set('111'));
});
test('legacy workoutSessions: self session works; new personal plans do not alter legacy session data', async () => {
  await assertSucceeds(a.ref('workoutSessions/A/legacy').set({ status: 'planned', scheduledAt: 1 }));
  const before = (await a.ref('workoutSessions/A').get()).val();
  await append();
  assert.deepEqual((await a.ref('workoutSessions/A').get()).val(), before);
  await assertSucceeds(a.ref('workoutSessions/A/legacy/status').set('completed'));
  await assertFails(a.ref('workoutSessions/A/legacy/status').set('invalid'));
  await assertFails(b.ref('workoutSessions/A').get());
});
test('legacy notifications: owner may mark read, but cannot edit message or read another UID', async () => {
  const admin = await adminFixture();
  await assertSucceeds(admin.ref('notifications/A/n1').set({ title: 'Test', message: 'Local fixture', type: 'general', isRead: false, createdAt: '2026-11-13' }));
  await assertSucceeds(a.ref('notifications/A').get());
  await assertSucceeds(a.ref('notifications/A/n1/isRead').set(true));
  await assertFails(a.ref('notifications/A/n1/message').set('changed'));
  await assertFails(b.ref('notifications/A').get());
});
test('legacy memberships/packages: member creates own pending request and queries only own records', async () => {
  const admin = await adminFixture();
  await assertSucceeds(admin.ref('packages/basic').set({ name: 'Emulator fixture', type: 'membership', price: 10, durationDays: 30, isActive: true }));
  await assertSucceeds(a.ref('memberships/m1').set({ uid: 'A', packageId: 'basic', status: 'pending' }));
  await assertSucceeds(a.ref('memberships').orderByChild('uid').equalTo('A').get());
  await assertFails(a.ref('memberships').get());
  await assertFails(b.ref('memberships/m2').set({ uid: 'A', packageId: 'basic', status: 'pending' }));
});
test('legacy activityLogs: member may create own action but not rewrite or spoof another actor', async () => {
  await assertSucceeds(a.ref('activityLogs/l1').set({ actorUid: 'A', action: 'workout' }));
  await assertFails(a.ref('activityLogs/l1/action').set('payment'));
  await assertFails(b.ref('activityLogs/l2').set({ actorUid: 'A', action: 'workout' }));
});
