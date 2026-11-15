import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {readFileSync,writeFileSync} from 'node:fs';
import path from 'node:path';

const folder=fileURLToPath(new URL('../',import.meta.url));
export const m2cPath=path.join(folder,'KgyMs-RTDB-Rules-M2C-PROPOSED-20261115.json');
export const candidatePath=path.join(folder,'KgyMs-RTDB-Rules-M2D3-CANDIDATE-20261115.json');
export const m2c=JSON.parse(readFileSync(m2cPath,'utf8'));
assert.equal(Object.keys(m2c.rules).length,22,'M2D.3 must start from the 22-branch M2C proposal');
for(const key of ['memberWorkoutPlans','memberWorkoutAttempts','memberWorkoutConfirmations'])assert.ok(m2c.rules[key],key);

const ownerAtom="auth != null && auth.uid == $uid";
const ownerGuard="auth != null && auth.uid == $uid && root.child('users').child(auth.uid).child('status').val() == 'active'";
const adminAtom="auth != null && root.child('users').child(auth.uid).child('role').val() == 'admin'";
const adminGuard="auth != null && root.child('users').child(auth.uid).child('role').val() == 'admin' && root.child('users').child(auth.uid).child('status').val() == 'active'";
const changed=new Set(['memberWorkoutPlans','memberWorkoutAttempts','memberWorkoutConfirmations']);
const candidate=structuredClone(m2c);

function guard(value){
 if(typeof value==='string'){
   if(value===ownerAtom)value=ownerGuard;
   value=value.split(`(${ownerAtom})`).join(`(${ownerGuard})`);
   value=value.split(`(${adminAtom})`).join(`(${adminGuard})`);
   return value;
 }
 if(Array.isArray(value))return value.map(guard);
 if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([key,next])=>[key,guard(next)]));
 return value;
}
for(const key of changed)candidate.rules[key]=guard(candidate.rules[key]);

for(const key of Object.keys(m2c.rules)){
 if(changed.has(key))assert.notDeepEqual(candidate.rules[key],m2c.rules[key],`${key} should receive a status guard`);
 else assert.deepEqual(candidate.rules[key],m2c.rules[key],`${key} changed unexpectedly`);
}
assert.equal(Object.keys(candidate.rules).length,22);
export const candidateText=JSON.stringify(candidate,null,2)+'\n';
if(process.argv[1]===fileURLToPath(import.meta.url)){
 writeFileSync(candidatePath,candidateText);
 console.log(`Generated ${candidatePath} from the 22-branch M2C proposal; changed only ${[...changed].join(', ')}.`);
}
export {candidate};
