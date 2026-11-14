import {readFileSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';

const root=new URL('../',import.meta.url);
export const mergedBytes=readFileSync(new URL('KgyMs-RTDB-Rules-MERGED-20261114.json',root));
export const mergedHash=createHash('sha256').update(mergedBytes).digest('hex');
export const merged=JSON.parse(mergedBytes.toString('utf8'));
assert.equal(Object.keys(merged.rules).length,20);
assert.ok(merged.rules.memberWorkoutPlans);
assert.equal(merged.rules['.write'],undefined); assert.equal(merged.rules['.read'],undefined);
const owner='auth != null && auth.uid == $uid';
const admin="auth != null && root.child('users').child(auth.uid).child('role').val() == 'admin'";
const object=(required,fields)=>({'.validate':`newData.hasChildren(${JSON.stringify(required)})`,...fields,'$other':{'.validate':false}});
const text=(max=512,empty=false)=>({'.validate':`newData.isString() && newData.val().length ${empty?'>= 0':'> 0'} && newData.val().length <= ${max}`});
const timestamp=extra=>({'.validate':`newData.isNumber() && newData.val() % 1 == 0 && newData.val() >= 0 && newData.val() <= 9007199254740991${extra?' && '+extra:''}`});
const idRule={'.validate':"newData.isString() && newData.val() == $attemptId && $attemptId.matches(/^[A-Za-z0-9_-]{1,128}$/)"};
const unique=Array.from({length:64},(_,i)=>`($index == '${i}' || newData.child('id').val() != newData.parent().child('${i}/id').val())`).join(' && ');
const dense=Array.from({length:64},(_,i)=>`($index == '${i}'${i?` && newData.parent().hasChild('${i-1}')`:''})`).join(' || ');
const attempt=object(['schemaVersion','id','uid','logicalSessionId','planVersionId','workoutId','titleVi','titleEn','startedAt','finishedAt','receivedAt','exercises'],{
  schemaVersion:{'.validate':'newData.val() == 1'},id:idRule,uid:{'.validate':'newData.val() == $uid'},
  logicalSessionId:text(),planVersionId:text(128,true),workoutId:text(256),titleVi:text(200),titleEn:text(200),
  startedAt:timestamp("newData.val() <= newData.parent().child('finishedAt').val()"),
  finishedAt:timestamp("newData.val() >= newData.parent().child('startedAt').val() && newData.val() <= now + 300000"),
  receivedAt:{'.validate':'newData.isNumber() && newData.val() == now'},
  exercises:{'.validate':"newData.hasChild('0')",'$index':{
    ...object(['id','performed'],{id:text(256),performed:{'.validate':'newData.isBoolean()'}}),
    '.validate':`newData.hasChildren(['id','performed']) && (${dense}) && (${unique})`
  }}
});
attempt['.write']=`(${owner}) && !data.exists() && newData.exists()`;
const confirmation=object(['schemaVersion','uid','attemptId','confirmedBy','confirmedAt'],{
  schemaVersion:{'.validate':'newData.val() == 1'},uid:{'.validate':'newData.val() == $uid'},
  attemptId:{'.validate':'newData.val() == $attemptId'},confirmedBy:{'.validate':'newData.val() == auth.uid'},
  confirmedAt:{'.validate':'newData.isNumber() && newData.val() == now'}
});
confirmation['.write']=`(${admin}) && !data.exists() && newData.exists() && root.child('memberWorkoutAttempts').child($uid).child($attemptId).exists() && root.child('memberWorkoutAttempts').child($uid).child($attemptId).child('uid').val() == $uid`;
const ownerBranch=child=>({'.read':false,'.write':false,'$uid':{'.read':`(${owner}) || (${admin})`,'$attemptId':child}});
export const proposed={...merged,rules:{...merged.rules,memberWorkoutAttempts:ownerBranch(attempt),memberWorkoutConfirmations:ownerBranch(confirmation)}};
for(const [key,value] of Object.entries(merged.rules))assert.deepEqual(proposed.rules[key],value);
export const proposedText=JSON.stringify(proposed,null,2)+'\n';
export const proposedPath=fileURLToPath(new URL('KgyMs-RTDB-Rules-M2C-PROPOSED-20261115.json',root));
if(process.argv[1]===fileURLToPath(import.meta.url)) {
  writeFileSync(proposedPath,proposedText);
  assert.equal(createHash('sha256').update(readFileSync(new URL('KgyMs-RTDB-Rules-MERGED-20261114.json',root))).digest('hex'),mergedHash);
  console.log(`PROPOSAL ONLY: ${proposedPath}. All 20 checkpoint branches unchanged. MERGED SHA256=${mergedHash}`);
}
