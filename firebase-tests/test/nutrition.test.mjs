import assert from 'node:assert/strict';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { ref, get, set, serverTimestamp, update } from 'firebase/database';
import { base, catalog, candidate } from '../scripts/build-nutrition-rules.mjs';

const projectId = 'demo-kgyms-nutrition';
const dbHost = '127.0.0.1:9010';
const authHost = '127.0.0.1:9110';
assert.equal(process.env.GCLOUD_PROJECT, projectId);
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, dbHost);
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, authHost);
assert.equal(process.env.FIREBASE_PROJECT, projectId);

const namespace = projectId + '-default-rtdb';
const localDb = context => context.database('http://' + dbHost + '/?ns=' + namespace);
const pathFor = uid => 'memberNutritionPlans/' + uid;
const document = (revision = 1) => ({
  schemaVersion: 1, revision, updatedAt: serverTimestamp(), writeId: revision.toString(16).padStart(32, '0'),
  setup: { goal: 'MAINTAIN', activity: 'LOW', diet: 'REGULAR' },
  generatedFor: 'MAINTAIN:LOW:REGULAR',
  meals: { '0:BREAKFAST': 'bf_oats', '2:DINNER': 'di_ca_hap_gung' },
});
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId, database: { host: '127.0.0.1', port: 9010 } });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.withSecurityRulesDisabled(async context => {
    await set(ref(localDb(context)), {
      users: { A: { status: 'active', role: 'member' }, B: { status: 'active', role: 'member' },
        blocked: { status: 'inactive', role: 'member' }, admin: { status: 'active', role: 'admin' } },
      packages: { testPackage: { name: 'Read-only fixture' } },
    });
  });
});
const user = uid => localDb(env.authenticatedContext(uid));
const record = (uid, db = user(uid)) => ref(db, pathFor(uid));

test('owner creates, reads, updates and clears optional meal selections', async () => {
  const db = user('A');
  const row = record('A', db);
  assert.equal((await get(row)).exists(), false);
  await set(row, document());
  const original = (await get(row)).val();
  assert.equal(original.revision, 1);
  assert.equal(typeof original.updatedAt, 'number');
  assert.equal(original.meals['2:DINNER'], 'di_ca_hap_gung');
  const empty = document(2);
  delete empty.meals;
  empty.generatedFor = '';
  await set(row, empty);
  assert.equal((await get(row)).hasChild('meals'), false);
  assert.equal((await get(row)).child('generatedFor').val(), '');
  await assertFails(set(row, null));
});

test('other UID, anonymous, inactive owner and admin cannot access a member record', async () => {
  await set(record('A'), document());
  for (const database of [user('B'), localDb(env.unauthenticatedContext()), user('admin')]) {
    await assertFails(get(record('A', database)));
    await assertFails(set(record('A', database), document(2)));
  }
  await assertFails(get(record('blocked')));
  await assertFails(set(record('blocked'), document()));
  await assertFails(get(ref(user('A'), 'memberNutritionPlans')));
  await assertFails(set(ref(user('A'), 'memberNutritionPlans'), { A: document(2) }));
  await set(record('B'), document());
  assert.equal((await get(record('A'))).child('revision').val(), 1);
});

test('invalid schema, slot, IDs, enums, unknown fields, size and deletion are rejected', async () => {
  const row = record('A');
  for (const broken of [
    { schemaVersion: 2 }, { revision: 1.5 }, { revision: '1' },
    { updatedAt: Date.now() + 100000 }, { writeId: 'too-short' },
    { generatedFor: 'x'.repeat(5000) }, { extra: { secret: true } },
    { setup: { goal: 'UNKNOWN', activity: 'LOW', diet: 'REGULAR' } },
    { setup: { goal: 'MAINTAIN', activity: 'NEVER', diet: 'REGULAR' } },
    { setup: { goal: 'MAINTAIN', activity: 'LOW', diet: 'REGULAR', extra: true } },
    { setup: { goal: 'MAINTAIN' } }, { meals: [1, 2] }, { meals: 'too large' },
    { meals: { '7:LUNCH': 'lu_fish' } }, { meals: { '0:DINNER': 'bf_oats' } },
    { meals: { '0:BREAKFAST': 'not_in_catalog' } }, { meals: { '0:BREAKFAST': { id: 'bf_oats' } } },
  ]) await assertFails(set(row, { ...document(), ...broken }));
  await set(row, document());
  await assertFails(update(row, { 'setup/goal': 'FAT_LOSS' }));
  assert.equal((await get(row)).child('setup/goal').val(), 'MAINTAIN');
});

test('catalog allowlist covers every legacy and added ID in its proper slot', async () => {
  const row = record('A');
  for (const [index, { id, slot }] of catalog.entries()) {
    const value = { ...document(index + 1), meals: { ['6:' + slot]: id } };
    await set(row, value);
    assert.equal((await get(row)).child('meals').child('6:' + slot).val(), id);
  }
});

test('revision and schema guards block stale writers and protect future records', async () => {
  const row = record('A');
  await set(row, document());
  await assertFails(set(row, document(3)));
  await set(row, document(2));
  await assertFails(set(row, { ...document(2), writeId: 'f'.repeat(32) }));
  await assertFails(set(row, { ...document(3), writeId: document(2).writeId }));
  await assertFails(set(row, document(1)));
  await env.withSecurityRulesDisabled(context => set(record('A', localDb(context)),
    { ...document(10), schemaVersion: 2 }));
  await assertFails(set(row, document(11)));
  assert.equal((await get(row)).child('schemaVersion').val(), 2);
});

test('other 22 groups remain identical and their permissions still apply', async () => {
  const { memberNutritionPlans, ...unchanged } = candidate.rules;
  assert.ok(memberNutritionPlans);
  assert.equal(Object.keys(unchanged).length, 22);
  assert.deepEqual(unchanged, base.rules);
  await get(ref(user('A'), 'packages/testPackage'));
  await assertFails(set(ref(user('A'), 'packages/testPackage'), { name: 'Forbidden' }));
  await assertFails(get(ref(user('B'), 'memberWorkoutPlans/A')));
  await assertFails(get(ref(user('B'), 'memberWorkoutAttempts/A')));
});

test('authoritative REST GET returns an absent ETag and conditional PUT rejects a stale ETag', async () => {
  const signUp = await fetch('http://' + authHost + '/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'nutrition-owner@example.test', password: 'local-test-credential', returnSecureToken: true }),
  });
  assert.equal(signUp.status, 200);
  const { idToken, localId } = await signUp.json();
  await env.withSecurityRulesDisabled(context => set(ref(localDb(context), 'users/' + localId),
    { status: 'active', role: 'member' }));
  const url = new URL('http://' + dbHost + '/' + pathFor(localId) + '.json');
  url.search = new URLSearchParams({ ns: namespace, auth: idToken });
  const read = () => fetch(url, { headers: { 'X-Firebase-ETag': 'true' } });
  const write = (value, tag) => fetch(url, { method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'If-Match': tag }, body: JSON.stringify(value) });
  const absent = await read();
  assert.equal(absent.status, 200);
  assert.equal(await absent.json(), null);
  const absentTag = absent.headers.get('etag');
  assert.ok(absentTag);
  assert.equal((await write(document(), absentTag)).status, 200);
  assert.equal((await write(document(2), absentTag)).status, 412);
  const saved = await read();
  assert.equal((await saved.json()).revision, 1);
  assert.equal((await write(document(2), saved.headers.get('etag'))).status, 200);
});
