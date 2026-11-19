import {test,before,beforeEach,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {m2c,candidate,candidatePath} from '../scripts/build-account-safety-rules.mjs';
import {makePlan,publish} from './protocol-client.mjs';

assert.equal(process.env.GCLOUD_PROJECT,'demo-kgyms-personal-plans');
assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST,'127.0.0.1:9000');
const rules=readFileSync(candidatePath,'utf8');
let env,a,b,locked,inactive,admin,lockedAdmin,guest;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-kgyms-personal-plans',database:{host:'127.0.0.1',port:9000,rules}});});
beforeEach(async()=>{
 await env.clearDatabase();
 await env.withSecurityRulesDisabled(async context=>context.database().ref('users').set({
   A:{role:'user',status:'active'},B:{role:'user',status:'active'},
   locked:{role:'user',status:'locked'},inactive:{role:'user',status:'inactive'},
   admin:{role:'admin',status:'active'},lockedAdmin:{role:'admin',status:'locked'},
 }));
 a=env.authenticatedContext('A').database();b=env.authenticatedContext('B').database();
 locked=env.authenticatedContext('locked').database();inactive=env.authenticatedContext('inactive').database();
 admin=env.authenticatedContext('admin').database();lockedAdmin=env.authenticatedContext('lockedAdmin').database();
 guest=env.unauthenticatedContext().database();
});
after(async()=>{await env?.cleanup();});

const ts={'.sv':'timestamp'};
const attempt=(uid='A',id='attempt-1')=>({schemaVersion:1,id,uid,logicalSessionId:'logical-1',planVersionId:'plan-1',workoutId:'full-body',titleVi:'Test',titleEn:'Test',startedAt:1,finishedAt:2,receivedAt:ts,exercises:[{id:'squat',performed:true}]});
const confirmation=(uid='A',id='attempt-1',by='admin')=>({schemaVersion:1,uid,attemptId:id,confirmedBy:by,confirmedAt:ts});
const attemptPath=(uid='A',id='attempt-1')=>`memberWorkoutAttempts/${uid}/${id}`;
const confirmationPath=(uid='A',id='attempt-1')=>`memberWorkoutConfirmations/${uid}/${id}`;

test('candidate retains 22 branches and changes exactly the three account-sensitive branches',()=>{
 assert.deepEqual(JSON.parse(rules),candidate);
 assert.equal(Object.keys(candidate.rules).length,22);
 const changed=[];
 for(const key of Object.keys(m2c.rules)){
   if(JSON.stringify(m2c.rules[key])!==JSON.stringify(candidate.rules[key]))changed.push(key);
 }
 assert.deepEqual(changed,['memberWorkoutPlans','memberWorkoutAttempts','memberWorkoutConfirmations']);
});

test('active member can publish/read own plan while another UID cannot access it',async()=>{
 await publish(a,makePlan('A','active-plan'),{activate:true});
 await assertSucceeds(a.ref('memberWorkoutPlans/A').get());
 await assertFails(b.ref('memberWorkoutPlans/A').get());
 await assertFails(b.ref('memberWorkoutPlans/A/state').set({revision:99,nextOrdinal:1,activeId:'bad'}));
});

test('locked and inactive members cannot read or write personal plans',async()=>{
 await assertFails(locked.ref('memberWorkoutPlans/locked').get());
 await assertFails(inactive.ref('memberWorkoutPlans/inactive').get());
 await assert.rejects(()=>publish(locked,makePlan('locked','locked-plan'),{activate:true}));
 await assert.rejects(()=>publish(inactive,makePlan('inactive','inactive-plan'),{activate:true}));
});

test('locking an account denies access without deleting its existing plan',async()=>{
 await publish(a,makePlan('A','retained'),{activate:true});
 await env.withSecurityRulesDisabled(async context=>context.database().ref('users/A/status').set('locked'));
 await assertFails(a.ref('memberWorkoutPlans/A').get());
 await env.withSecurityRulesDisabled(async context=>context.database().ref('users/A/status').set('active'));
 const restored=await assertSucceeds(a.ref('memberWorkoutPlans/A').get());
 assert.equal(restored.child('plans').numChildren(),1);
});

test('active member creates own immutable attempt; locked/inactive members are denied',async()=>{
 await assertSucceeds(a.ref(attemptPath()).set(attempt()));
 await assertSucceeds(a.ref('memberWorkoutAttempts/A').get());
 await assertFails(locked.ref(attemptPath('locked')).set(attempt('locked')));
 await assertFails(inactive.ref(attemptPath('inactive')).set(attempt('inactive')));
 await assertFails(locked.ref('memberWorkoutAttempts/locked').get());
});

test('cross-account and unauthenticated access remain denied',async()=>{
 await a.ref(attemptPath()).set(attempt());
 await assertFails(b.ref(attemptPath()).get());
 await assertFails(b.ref(attemptPath('A','other')).set(attempt('A','other')));
 await assertFails(guest.ref(attemptPath()).get());
 await assertFails(guest.ref(attemptPath('A','guest')).set(attempt('A','guest')));
});

test('active admin retains M2C inspection/confirmation; locked admin is denied',async()=>{
 await a.ref(attemptPath()).set(attempt());
 await a.ref(attemptPath('A','attempt-2')).set(attempt('A','attempt-2'));
 await assertSucceeds(admin.ref(attemptPath()).get());
 await assertSucceeds(admin.ref(confirmationPath()).set(confirmation()));
 await assertSucceeds(a.ref(confirmationPath()).get());
 await assertFails(lockedAdmin.ref(attemptPath()).get());
 await assertFails(lockedAdmin.ref(confirmationPath('A','attempt-2')).set(confirmation('A','attempt-2','lockedAdmin')));
});

test('member cannot forge confirmation and locked owner cannot read confirmation',async()=>{
 await a.ref(attemptPath()).set(attempt());
 await assertFails(a.ref(confirmationPath()).set(confirmation('A','attempt-1','A')));
 await admin.ref(confirmationPath()).set(confirmation());
 await env.withSecurityRulesDisabled(async context=>context.database().ref('users/A/status').set('locked'));
 await assertFails(a.ref(confirmationPath()).get());
});

test('locked multi-location write is atomic and leaves no partial attempt',async()=>{
 const own=attemptPath('locked','one');const other=attemptPath('A','two');
 await assertFails(locked.ref().update({[own]:attempt('locked','one'),[other]:attempt('A','two')}));
 await env.withSecurityRulesDisabled(async context=>{
   assert.equal((await context.database().ref(own).get()).exists(),false);
   assert.equal((await context.database().ref(other).get()).exists(),false);
 });
});

test('status guard does not weaken revision or immutable-write protections',async()=>{
 const plan=makePlan('A','revision');await publish(a,plan,{activate:true});
 await assertFails(a.ref(`memberWorkoutPlans/A/plans/${plan.id}/payload/level`).set('advanced'));
 await a.ref(attemptPath()).set(attempt());
 await assertFails(a.ref(`${attemptPath()}/finishedAt`).set(3));
 await assertFails(a.ref(attemptPath()).remove());
});
