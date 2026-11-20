import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { checkMergedFile, verifyMerge } from '../scripts/build-nutrition-live-rules.mjs';
import { makePlan, publish } from './protocol-client.mjs';

const projectId = 'demo-kgyms-nutrition';
const databaseHost = '127.0.0.1:9010';
assert.equal(process.env.GCLOUD_PROJECT, projectId);
assert.equal(process.env.FIREBASE_PROJECT, projectId);
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, databaseHost);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:9110');
const livePath = process.env.KGYMS_NUTRITION_LIVE_SNAPSHOT;
assert.ok(livePath, 'Use run-nutrition-tests.mjs --live-merged <live-export.json>');
const localDb = context => context.database('http://' + databaseHost + '/?ns=' + projectId + '-default-rtdb');
const user = uid => localDb(env.authenticatedContext(uid));
const timestamp = () => ({ '.sv': 'timestamp' });

const membership = (uid = 'A', status = 'pending') => ({
  uid, status, packageId: 'gym', trainerUid: '', startDate: '', endDate: '',
});
const payment = (uid = 'A') => ({
  uid, membershipId: 'pendingA', amount: 300000, discount: 0, method: 'transfer', status: 'pending', paidAt: '',
});
const checkIn = (uid = 'A') => ({ uid, checkInAt: 100, checkOutAt: 0 });
const attempt = (uid, id) => ({
  schemaVersion: 1, id, uid, logicalSessionId: 'logical-1', planVersionId: 'plan-1', workoutId: 'full-body',
  titleVi: 'Fixture', titleEn: 'Fixture', startedAt: 1, finishedAt: 2, receivedAt: timestamp(),
  exercises: [{ id: 'squat', performed: true }],
});

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId, database: { host: '127.0.0.1', port: 9010 } });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.withSecurityRulesDisabled(context => localDb(context).ref().set({
    users: {
      A: { status: 'active', role: 'user' }, B: { status: 'active', role: 'user' },
      locked: { status: 'locked', role: 'user' }, admin: { status: 'active', role: 'admin' },
      upper: { status: 'ACTIVE', role: 'user' }, 'bad:uid': { status: 'active', role: 'user' },
    },
    gymInfo: { adminUid: 'admin', phone: '000000', name: 'Demo only', checkInSecret: 'fixture-not-a-real-secret' },
    packages: { gym: { price: 300000, trainerUid: '', isActive: true } },
    memberships: { pendingA: membership(), foreign: membership('B') },
    payments: { paymentA: payment(), foreign: payment('B') },
    checkIns: { checkInA: checkIn(), foreign: checkIn('B') },
    bankTransactions: { bankFixture: { source: 'demo' } },
    reconciliation: { reconFixture: { status: 'pending' }, closedFixture: { status: 'done' } },
  }));
});

test('the exact loaded artifact preserves all 24 live groups and adds only the candidate Nutrition branch', () => {
  const mergedText = checkMergedFile(livePath);
  assert.equal(readFileSync('nutrition.rules.json', 'utf8'), mergedText, 'Emulator loaded the wrong Rules file');
  const liveText = readFileSync(livePath, 'utf8');
  const changed = JSON.parse(mergedText);
  changed.rules.reconciliation['.indexOn'].push('extra');
  assert.throws(() => verifyMerge(liveText, JSON.stringify(changed)), /Existing live Rules/);
  const weakened = JSON.parse(mergedText);
  weakened.rules.memberNutritionPlans.$uid['.read'] = true;
  assert.throws(() => verifyMerge(liveText, JSON.stringify(weakened)), /Nutrition Rules differ/);
});

test('bankTransactions and indexed reconciliation stay admin-readable and unwritable by clients', async () => {
  const admin = user('admin');
  const member = user('A');
  const guest = localDb(env.unauthenticatedContext());
  assert.equal((await admin.ref('bankTransactions/bankFixture').get()).val().source, 'demo');
  const results = await admin.ref('reconciliation').orderByChild('status').equalTo('pending').get();
  assert.deepEqual(Object.keys(results.val()), ['reconFixture']);
  for (const db of [member, guest]) {
    await assertFails(db.ref('bankTransactions/bankFixture').get());
    await assertFails(db.ref('reconciliation').orderByChild('status').equalTo('pending').get());
  }
  for (const db of [admin, member, guest]) {
    await assertFails(db.ref('bankTransactions/bankFixture/source').set('changed'));
    await assertFails(db.ref('reconciliation/reconFixture/status').set('done'));
  }
});

test('gymInfo keeps collection and check-in secret private while authenticated members can read public fields', async () => {
  const admin = user('admin');
  const member = user('A');
  const guest = localDb(env.unauthenticatedContext());
  assert.ok((await admin.ref('gymInfo').get()).hasChild('checkInSecret'));
  await assertFails(member.ref('gymInfo').get());
  await assertFails(member.ref('gymInfo/checkInSecret').get());
  for (const field of ['adminUid', 'phone', 'name']) {
    assert.ok((await member.ref('gymInfo/' + field).get()).exists());
    await assertFails(guest.ref('gymInfo/' + field).get());
  }
});

test('membership and check-in UID queries work while create and update stay admin-only', async () => {
  const admin = user('admin');
  const member = user('A');
  assert.deepEqual(Object.keys((await member.ref('memberships').orderByChild('uid').equalTo('A').get()).val()), ['pendingA']);
  assert.deepEqual(Object.keys((await member.ref('checkIns').orderByChild('uid').equalTo('A').get()).val()), ['checkInA']);
  for (const path of ['memberships', 'checkIns']) {
    await assertFails(member.ref(path).get());
    await assertFails(member.ref(path).orderByChild('uid').equalTo('B').get());
  }
  await assertFails(member.ref('memberships/new').set(membership()));
  await assertFails(member.ref('memberships/pendingA/status').set('cancelled'));
  await assertFails(member.ref('checkIns/new').set(checkIn()));
  await assertFails(member.ref('checkIns/checkInA/checkOutAt').set(200));
  await admin.ref('memberships/new').set(membership());
  await admin.ref('memberships/pendingA/status').set('active');
  await admin.ref('checkIns/new').set(checkIn());
  await admin.ref('checkIns/checkInA/checkOutAt').set(200);
});

test('payments keep owner-filtered reads, admin-only transactions, and paidVia/expiresAt validation', async () => {
  const admin = user('admin');
  const member = user('A');
  assert.deepEqual(Object.keys((await member.ref('payments').orderByChild('uid').equalTo('A').get()).val()), ['paymentA']);
  await assertFails(member.ref('payments').get());
  await assertFails(member.ref('payments').orderByChild('uid').equalTo('B').get());
  await assertFails(member.ref('payments/new').set(payment()));
  await assertFails(member.ref('payments/paymentA/status').set('cancelled'));
  await assertFails(member.ref('payments/paymentA/status').set('paid'));
  await assertFails(admin.ref('payments/paymentA/paidVia').set('manual'));
  await assertFails(admin.ref('payments/paymentA/expiresAt').set(0));
  await admin.ref('payments/paymentA').update({ status: 'paid', paidVia: 'sepay', expiresAt: 1000 });
  assert.equal((await member.ref('payments/paymentA/paidVia').get()).val(), 'sepay');
});

test('workout plan, attempts and confirmations retain the live UID and admin Role rules', async () => {
  const locked = user('locked');
  const outsider = user('B');
  const admin = user('admin');
  const plan = makePlan('locked', 'live-regression');
  await publish(locked, plan, { activate: true });
  assert.equal((await locked.ref('memberWorkoutPlans/locked/state/activeId').get()).val(), plan.id);
  await assertFails(outsider.ref('memberWorkoutPlans/locked').get());

  const id = 'attempt-fixture';
  const path = 'memberWorkoutAttempts/locked/' + id;
  await locked.ref(path).set(attempt('locked', id));
  assert.ok((await admin.ref(path).get()).exists());
  await assertFails(outsider.ref(path).get());
  await assertFails(locked.ref(path + '/finishedAt').set(3));
  const confirmation = 'memberWorkoutConfirmations/locked/' + id;
  const payload = { schemaVersion: 1, uid: 'locked', attemptId: id, confirmedBy: 'admin', confirmedAt: timestamp() };
  await assertFails(locked.ref(confirmation).set(payload));
  await admin.ref(confirmation).set(payload);
  assert.equal((await locked.ref(confirmation + '/confirmedBy').get()).val(), 'admin');
  await assertFails(outsider.ref(confirmation).get());
});

test('Nutrition requires both the exact active status and a UID accepted by the Android adapter', async () => {
  const plan = { schemaVersion: 1, revision: 1, updatedAt: timestamp(), writeId: '0'.repeat(32),
    setup: { goal: 'MAINTAIN', activity: 'LOW', diet: 'REGULAR' }, generatedFor: '' };
  for (const uid of ['locked', 'missing', 'upper', 'bad:uid']) {
    const owner = user(uid);
    await assertFails(owner.ref('memberNutritionPlans/' + uid).get());
    await assertFails(owner.ref('memberNutritionPlans/' + uid).set(plan));
  }
  await user('A').ref('memberNutritionPlans/A').set(plan);
  assert.equal((await user('A').ref('memberNutritionPlans/A/revision').get()).val(), 1);
});
