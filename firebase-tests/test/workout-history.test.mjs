import {test,before,beforeEach,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {merged,proposed,proposedPath} from '../scripts/build-workout-history-rules.mjs';
assert.equal(process.env.GCLOUD_PROJECT,'demo-kgyms-personal-plans');
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST,'127.0.0.1:9000');
const rules=readFileSync(proposedPath,'utf8');
let env,a,b,admin,guest;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-kgyms-personal-plans',database:{host:'127.0.0.1',port:9000,rules}});});
beforeEach(async()=>{await env.clearDatabase(); await env.withSecurityRulesDisabled(async c=>{
  await c.database().ref('users/admin').set({role:'admin',status:'active'});
  await c.database().ref('users/A').set({role:'user',status:'active'});
});a=env.authenticatedContext('A').database();b=env.authenticatedContext('B').database();admin=env.authenticatedContext('admin').database();guest=env.unauthenticatedContext().database();});
after(async()=>{await env?.cleanup();});
const ts={'.sv':'timestamp'};
const attempt=(uid='A',id='attempt-1')=>({schemaVersion:1,id,uid,logicalSessionId:'logical-1',planVersionId:'plan-1',workoutId:'full-body',titleVi:'Test',titleEn:'Test',startedAt:1,finishedAt:2,receivedAt:ts,exercises:[{id:'squat',performed:true}]});
const confirmation=(uid='A',id='attempt-1')=>({schemaVersion:1,uid,attemptId:id,confirmedBy:'admin',confirmedAt:ts});
const path=(uid='A',id='attempt-1')=>`memberWorkoutAttempts/${uid}/${id}`;
const conf=(uid='A',id='attempt-1')=>`memberWorkoutConfirmations/${uid}/${id}`;
test('all 20 MERGED branches remain exactly unchanged; only two M2C branches are added',()=>{
 const actual=JSON.parse(rules);for(const [key,value]of Object.entries(merged.rules))assert.deepEqual(actual.rules[key],value,key);
 assert.deepEqual(actual,proposed);assert.equal(Object.keys(actual.rules).length,22);
 for(const key of ['memberWorkoutAttempts','memberWorkoutConfirmations']){assert.equal(actual.rules[key]['.write'],false);assert.equal(actual.rules[key].$uid['.write'],undefined);}
});
test('owner creates and reads an immutable attempt; admin can inspect it',async()=>{await assertSucceeds(a.ref(path()).set(attempt()));await assertSucceeds(a.ref('memberWorkoutAttempts/A').get());await assertSucceeds(admin.ref(path()).get());});
test('B has independent attempts and cannot read or write A',async()=>{await a.ref(path()).set(attempt());await assertSucceeds(b.ref(path('B')).set(attempt('B')));await assertFails(b.ref(path()).get());await assertFails(b.ref(path('A','bad')).set(attempt('A','bad')));});
test('A cannot read or write B',async()=>{await b.ref(path('B')).set(attempt('B'));await assertFails(a.ref(path('B')).get());await assertFails(a.ref(path('B','bad')).set(attempt('B','bad')));});
test('unauthenticated clients cannot read or write either node',async()=>{await a.ref(path()).set(attempt());for(const dbPath of [path(),conf(),'memberWorkoutAttempts/A','memberWorkoutConfirmations/A'])await assertFails(guest.ref(dbPath).get());await assertFails(guest.ref(path('A','bad')).set(attempt('A','bad')));await assertFails(guest.ref(conf()).set(confirmation()));});
test('root and whole UID reads/writes do not bypass child rules',async()=>{await assertFails(a.ref('memberWorkoutAttempts').get());await assertFails(admin.ref('memberWorkoutAttempts').get());await assertFails(a.ref('memberWorkoutAttempts/A').set({'attempt-1':attempt()}));await assertFails(admin.ref('memberWorkoutConfirmations/A').set({'attempt-1':confirmation()}));});
test('attempt cannot be overwritten even with identical data, changed, or deleted',async()=>{await a.ref(path()).set(attempt());const saved=(await a.ref(path()).get()).val();await assertFails(a.ref(path()).set(saved));await assertFails(a.ref(path()+'/finishedAt').set(3));await assertFails(a.ref(path()).remove());await assertFails(admin.ref(path()).remove());});
test('client cannot smuggle confirmed/status/trainerUid fields into an attempt',async()=>{for(const key of ['confirmed','status','trainerUid'])await assertFails(a.ref(path()).set({...attempt(),[key]:key==='confirmed'?true:'admin'}));});
test('UID and attempt ID must match the path',async()=>{await assertFails(a.ref(path()).set(attempt('B')));await assertFails(a.ref(path()).set(attempt('A','other')));});
test('malformed timestamps, exercise lists, flags, and unknown schema are denied',async()=>{
 for(const bad of [{schemaVersion:2},{startedAt:5,finishedAt:2},{finishedAt:Date.now()+3600000},{receivedAt:1},{exercises:[]},{exercises:[{id:'squat',performed:'yes'}]},{exercises:[{id:'squat',performed:true},{id:'squat',performed:false}]},{exercises:{0:{id:'squat',performed:true},2:{id:'row',performed:true}}}])await assertFails(a.ref(path()).set({...attempt(),...bad}));
});
test('member cannot create, update, delete, or forge a confirmation',async()=>{await a.ref(path()).set(attempt());await assertFails(a.ref(conf()).set(confirmation()));await admin.ref(conf()).set(confirmation());await assertFails(a.ref(conf()+'/confirmedBy').set('A'));await assertFails(a.ref(conf()).remove());});
test('admin claim without database admin role cannot confirm',async()=>{await a.ref(path()).set(attempt());const fake=env.authenticatedContext('A',{admin:true,role:'admin'}).database();await assertFails(fake.ref(conf()).set({...confirmation(),confirmedBy:'A'}));});
test('authorized admin confirms an existing owner attempt; owner sees it',async()=>{await a.ref(path()).set(attempt());await assertSucceeds(admin.ref(conf()).set(confirmation()));await assertSucceeds(a.ref(conf()).get());await assertFails(b.ref(conf()).get());});
test('confirmation must refer to an existing attempt under the same UID',async()=>{await a.ref(path()).set(attempt());await assertFails(admin.ref(conf('B')).set(confirmation('B')));await assertFails(admin.ref(conf('A','missing')).set(confirmation('A','missing')));await assertFails(admin.ref(conf()).set({...confirmation(),uid:'B'}));await assertFails(admin.ref(conf()).set({...confirmation(),attemptId:'other'}));});
test('admin cannot spoof confirmedBy, timestamp, or extra confirmation fields',async()=>{await a.ref(path()).set(attempt());for(const bad of [{confirmedBy:'A'},{confirmedAt:1},{confirmed:true},{trainerUid:'admin'}])await assertFails(admin.ref(conf()).set({...confirmation(),...bad}));});
test('even admin cannot modify or delete an existing confirmation',async()=>{await a.ref(path()).set(attempt());await admin.ref(conf()).set(confirmation());await assertFails(admin.ref(conf()+'/confirmedAt').set(ts));await assertFails(admin.ref(conf()).remove());});
test('atomic member attempt plus forged confirmation rejects the entire write',async()=>{await assertFails(a.ref().update({[path()]:attempt(),[conf()]:confirmation()}));assert.equal((await a.ref(path()).get()).exists(),false);});
test('multi-location owner writes cannot include another UID; no partial attempt survives',async()=>{await assertFails(a.ref().update({[path()]:attempt(),[path('B')]:attempt('B')}));assert.equal((await a.ref(path()).get()).exists(),false);});
test('valid atomic own attempts work, but attempt mutation plus creation is rejected',async()=>{await assertSucceeds(a.ref().update({[path()]:attempt(),[path('A','attempt-2')]:attempt('A','attempt-2')}));await assertFails(a.ref().update({[path()+'/finishedAt']:3,[path('A','attempt-3')]:attempt('A','attempt-3')}));assert.equal((await a.ref(path('A','attempt-3')).get()).exists(),false);});
test('two clients retrying one ID create exactly one immutable record',async()=>{const other=env.authenticatedContext('A').database();const results=await Promise.allSettled([a.ref(path()).set(attempt()),other.ref(path()).set(attempt())]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.equal(Object.keys((await a.ref('memberWorkoutAttempts/A').get()).val()).length,1);});
