import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

export const SNAPSHOT_SHA256 = 'c5768d2f8de30787f4f14f3d0b8b999b808c7e92444d8a7cfd71ae57c513f883';
const folder = new URL('../', import.meta.url);
const bytes = readFileSync(new URL('rules.production.snapshot.json', folder));
assert.equal(createHash('sha256').update(bytes).digest('hex'), SNAPSHOT_SHA256, 'The supplied snapshot must remain byte-for-byte unchanged');
export const baseline = JSON.parse(bytes.toString('utf8'));
assert.equal(Object.keys(baseline.rules).length, 19);
assert.equal(baseline.rules['.read'], undefined, 'Unexpected broad parent read grant');
assert.equal(baseline.rules['.write'], undefined, 'Unexpected broad parent write grant');
assert.equal(baseline.rules.memberWorkoutPlans, undefined);

const owner = 'auth != null && auth.uid == $uid';
const oldUid = "root.child('memberWorkoutPlans').child($uid)";
const oldState = `${oldUid}.child('state')`;
const oldNext = `(${oldState}.exists() ? ${oldState}.child('nextOrdinal').val() : 1)`;
const oldRevision = `(${oldState}.exists() ? ${oldState}.child('revision').val() : 0)`;
const all = (...parts) => parts.map(p => `(${p})`).join(' && ');
const any = (...parts) => parts.map(p => `(${p})`).join(' || ');
const string = (limit = 256) => `newData.isString() && newData.val().length > 0 && newData.val().length <= ${limit}`;
const integer = (min, max) => `newData.isNumber() && newData.val() % 1 == 0 && newData.val() >= ${min} && newData.val() <= ${max}`;
const object = (required, children) => ({ '.validate': `newData.hasChildren(${JSON.stringify(required)})`, ...children, '$other': { '.validate': false } });
const scalar = expression => ({ '.validate': expression });
const enumOf = values => scalar(any(...values.map(v => `newData.val() == '${v}'`)));
const date = all(string(10), 'newData.val().matches(/^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/)');

// RTDB Rules have neither loops nor numChildren(). Generate bounded, explicit checks.
function prefixIndex(key, max) {
  return any(...Array.from({ length: max }, (_, i) => all(`${key} == '${i}'`, i ? `newData.parent().hasChild('${i - 1}')` : 'true')));
}
function uniqueValue(key, count, parent = 'newData.parent()', suffix = '') {
  return all(...Array.from({ length: count }, (_, i) => any(`${key} == '${i}'`, `newData.val() != ${parent}.child('${i}${suffix}').val()`)));
}
function idsList() {
  return { '.validate': "newData.hasChildren(['0'])", '$exerciseIndex': scalar(all(
    string(), prefixIndex('$exerciseIndex', 64), uniqueValue('$exerciseIndex', 64))) };
}
const sessions = {
  '.validate': any(...Array.from({ length: 7 }, (_, i) => all(
    `newData.parent().child('sessionsPerWeek').val() == ${i + 1}`,
    `newData.hasChildren(${JSON.stringify(Array.from({ length: (i + 1) * 4 }, (_, n) => String(n)))})`,
    `!newData.hasChild('${(i + 1) * 4}')`))),
  '$sessionIndex': {
    ...object(['id', 'date', 'templateId', 'exerciseIds'], {
      id: scalar(all(string(), uniqueValue('$sessionIndex', 28, 'newData.parent().parent()', '/id'))),
      date: scalar(all(date, uniqueValue('$sessionIndex', 28, 'newData.parent().parent()', '/date'))),
      templateId: scalar(string()),
      exerciseIds: idsList(),
      suggestedExerciseIds: idsList()
    }),
    '.validate': all("newData.hasChildren(['id','date','templateId','exerciseIds'])", prefixIndex('$sessionIndex', 28))
  }
};
const weekdays = {
  '.validate': any(...Array.from({ length: 7 }, (_, i) => all(
    `newData.parent().child('sessionsPerWeek').val() == ${i + 1}`,
    `newData.hasChildren(${JSON.stringify(Array.from({ length: i + 1 }, (_, n) => String(n)))})`,
    `!newData.hasChild('${i + 1}')`))),
  '$weekdayIndex': scalar(all(integer(0, 6), prefixIndex('$weekdayIndex', 7), uniqueValue('$weekdayIndex', 7)))
};
const payload = object(['id','uid','schemaVersion','generatorVersion','level','sessionsPerWeek','startDate','zoneId','weekdays','repeated','sessions'], {
  id: scalar(all(string(128), 'newData.val() == $planId')),
  uid: scalar(all(string(128), 'newData.val() == $uid')),
  schemaVersion: scalar('newData.val() == 1'),
  generatorVersion: scalar('newData.val() == 1'),
  level: enumOf(['Beginner','Intermediate','Advanced']),
  sessionsPerWeek: scalar(integer(1, 7)),
  startDate: scalar(date), zoneId: scalar(string(100)), weekdays,
  repeated: scalar('newData.isBoolean()'), sessions,
  revisionOf: scalar(all(string(128), 'newData.val() != $planId', `${oldUid}.child('plans').child(newData.val()).exists()`))
});

// At a new plan record, parent().parent() is the whole UID after the atomic write.
const postUidAtPlan = 'newData.parent().parent()';
const nextRecordChecks = postUid => all(
  `${postUid}.child('remoteSchemaVersion').val() == 1`,
  `${postUid}.child('state/revision').val() == ${oldRevision} + 1`,
  `${postUid}.child('state/nextOrdinal').val() == ${oldNext} + 1`);
const orderPlanId = `newData.parent().child('versionOrder').child('v' + ${oldNext}).val()`;
const newOrderedPlan = `newData.parent().child('plans').child(${orderPlanId})`;
const appendState = all(
  `newData.child('nextOrdinal').val() == ${oldNext} + 1`,
  `newData.parent().child('versionOrder').child('v' + ${oldNext}).isString()`,
  `!${oldUid}.child('versionOrder').child('v' + ${oldNext}).exists()`,
  `${newOrderedPlan}.child('ordinal').val() == ${oldNext}`,
  `${newOrderedPlan}.child('payload/id').val() == ${orderPlanId}`,
  `!${oldUid}.child('plans').child(${orderPlanId}).exists()`);
const activateOnly = all(`${oldState}.exists()`, `newData.child('nextOrdinal').val() == ${oldNext}`);

export const personalRules = {
  '.read': false, '.write': false,
  '$uid': {
    '.read': owner,
    '.validate': "newData.hasChildren(['remoteSchemaVersion','state','plans','versionOrder'])",
    remoteSchemaVersion: { '.write': all(owner, '!data.exists()', 'newData.val() == 1'), '.validate': 'newData.val() == 1' },
    state: {
      ...object(['revision','nextOrdinal'], {
        revision: scalar(integer(1, 1_000_000_000)),
        nextOrdinal: scalar(integer(2, 1_000_000_000)),
        activeId: scalar(all(string(128), "newData.parent().parent().child('plans').child(newData.val()).exists()"))
      }),
      '.write': all(owner, 'newData.exists()',
        "newData.parent().child('remoteSchemaVersion').val() == 1",
        `newData.child('revision').val() == ${oldRevision} + 1`,
        any(activateOnly, appendState),
        any("!data.child('activeId').exists()", "newData.child('activeId').exists()"))
    },
    plans: { '$planId': {
      '.write': all(owner, '!data.exists()', 'newData.exists()', nextRecordChecks(postUidAtPlan),
        `newData.child('ordinal').val() == ${oldNext}`,
        `${postUidAtPlan}.child('versionOrder').child('v' + ${oldNext}).val() == $planId`),
      ...object(['ordinal','uploadedAt','payload'], {
        ordinal: scalar(integer(1, 999_999_999)),
        uploadedAt: scalar('newData.isNumber() && newData.val() == now'),
        payload
      })
    } },
    versionOrder: { '$versionKey': {
      '.write': all(owner, '!data.exists()', string(128), `$versionKey == 'v' + ${oldNext}`,
        nextRecordChecks('newData.parent().parent()'),
        `!${oldUid}.child('plans').child(newData.val()).exists()`,
        `newData.parent().parent().child('plans').child(newData.val()).child('ordinal').val() == ${oldNext}`,
        `newData.parent().parent().child('plans').child(newData.val()).child('payload/id').val() == newData.val()`),
      '.validate': string(128)
    } },
    '$other': { '.validate': false }
  }
};
export const candidate = { ...baseline, rules: { ...baseline.rules, memberWorkoutPlans: personalRules } };
export const candidateText = JSON.stringify(candidate, null, 2) + '\n';
for (const [key, value] of Object.entries(baseline.rules)) assert.deepEqual(candidate.rules[key], value);
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const target = new URL('database.candidate.rules.json', folder);
  if (process.argv.includes('--check')) assert.equal(readFileSync(target, 'utf8'), candidateText, 'Run npm run rules:build');
  else writeFileSync(target, candidateText);
  console.log('Snapshot SHA-256 verified; all 19 legacy groups unchanged; candidate contains exactly one new group.');
}
