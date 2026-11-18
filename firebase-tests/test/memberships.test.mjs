import {test, before, beforeEach, after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';

assert.equal(process.env.GCLOUD_PROJECT, 'demo-kgyms-memberships');
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, '127.0.0.1:9100');
let env, a, b, guest;
const membership = (uid = 'A', status = 'pending') => ({uid, status, packageId: 'gym', trainerUid: '',
  startDate: '', endDate: '', sessionsUsed: 0, createdAt: '10:00 18/11/2026'});
before(async () => {
  env = await initializeTestEnvironment({projectId: 'demo-kgyms-memberships', database: {host: '127.0.0.1', port: 9100,
    rules: readFileSync(new URL('../rules.production.snapshot.json', import.meta.url), 'utf8')}});
});
beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async context => context.database().ref().set({
    users: {A: {role: 'user'}, B: {role: 'user'}, trainer: {role: 'user', isStaff: true}},
    staff: {trainer: {fullName: 'Coach Mai', position: 'pt', status: 'active'}},
    packages: {gym: {name: 'Gym', type: 'membership', price: 300000, durationDays: 30, isActive: true, trainerUid: ''},
      pt: {name: 'Coaching', type: 'pt', price: 1200000, durationDays: 30, sessions: 12, isActive: true, trainerUid: 'trainer'},
      old: {name: 'Retired', type: 'membership', price: 100000, durationDays: 30, isActive: false}},
    memberships: Object.fromEntries(['pending', 'active', 'frozen', 'expired', 'cancelled'].map(status => [status, membership('A', status)])),
    notifications: {A: {n: {title: 'A'}}, B: {n: {title: 'B'}}},
  }));
  await env.withSecurityRulesDisabled(async context => context.database().ref('memberships/foreign').set(membership('B')));
  a = env.authenticatedContext('A').database(); b = env.authenticatedContext('B').database();
  guest = env.unauthenticatedContext().database();
});
after(async () => {await env?.cleanup();});

test('member reads only UID-filtered own memberships, with all five statuses', async () => {
  const snapshot = await assertSucceeds(a.ref('memberships').orderByChild('uid').equalTo('A').get());
  assert.equal(snapshot.numChildren(), 5);
  snapshot.forEach(child => assert.equal(child.val().uid, 'A'));
  await assertFails(a.ref('memberships').get());
  await assertFails(a.ref('memberships').orderByChild('uid').equalTo('B').get());
  await assertFails(b.ref('memberships/active').get());
  await assertFails(guest.ref('memberships').orderByChild('uid').equalTo('A').get());
});

test('catalog is readable including retired history, and staff names do not need private users', async () => {
  const packages = (await assertSucceeds(a.ref('packages').get())).val();
  assert.equal(packages.pt.type, 'pt'); assert.equal(packages.gym.type, 'membership');
  assert.equal(packages.old.isActive, false);
  assert.equal((await assertSucceeds(a.ref('staff/trainer/fullName').get())).val(), 'Coach Mai');
  await assertFails(a.ref('users/trainer').get());
  await assertFails(a.ref('users').get());
  await assertFails(guest.ref('packages').get());
});

test('member cannot write catalog, activate membership, or edit used PT sessions', async () => {
  await assertFails(a.ref('packages/gym/price').set(1));
  await assertFails(a.ref('memberships/new-active').set(membership('A', 'active')));
  await assertFails(a.ref('memberships/pending/status').set('active'));
  await assertFails(a.ref('memberships/active/sessionsUsed').set(3));
});

test('pending-to-active updates from an authority arrive through the member UID listener', async () => {
  const query = a.ref('memberships').orderByChild('uid').equalTo('A');
  let resolveActive, rejectActive;
  const active = new Promise((resolve, reject) => {resolveActive = resolve; rejectActive = reject;});
  const timer = setTimeout(() => rejectActive(new Error('Realtime update timeout')), 10000);
  const listener = query.on('value', snapshot => {
    if (snapshot.child('pending/status').val() === 'active') resolveActive(snapshot);
  }, rejectActive);
  try {
    await env.withSecurityRulesDisabled(async context => context.database().ref('memberships/pending').update({
      status: 'active', startDate: '18/11/2026', endDate: '17/12/2026'}));
    const snapshot = await active;
    assert.equal(snapshot.child('pending/status').val(), 'active');
    assert.equal(snapshot.hasChild('foreign'), false);
  } finally {clearTimeout(timer); query.off('value', listener);}
});

test('payment reads require own UID; member cannot mark paid or publish notifications', async () => {
  await env.withSecurityRulesDisabled(async context => context.database().ref('payments').set({
    p: {uid: 'A', membershipId: 'pending', amount: 300000, discount: 0, method: 'transfer', status: 'pending', createdAt: '', paidAt: ''},
    other: {uid: 'B', status: 'pending'},
  }));
  assert.equal((await assertSucceeds(a.ref('payments').orderByChild('uid').equalTo('A').get())).numChildren(), 1);
  await assertFails(a.ref('payments').get());
  await assertFails(a.ref('payments/p/status').set('paid'));
  await assertSucceeds(a.ref('notifications/A').get());
  await assertFails(a.ref('notifications/B').get());
  await assertFails(a.ref('notifications/A/new').set({title: 'Paid'}));
});