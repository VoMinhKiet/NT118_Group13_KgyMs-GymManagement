import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, createWriteStream } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createHash, randomUUID } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import net from 'node:net';
import path from 'node:path';
import assert from 'node:assert/strict';
import { baseline, candidate as generatedCandidate } from './build-rules.mjs';

const folder=fileURLToPath(new URL('../',import.meta.url));
const input=path.join(folder,'KgyMs-RTDB-Rules-MERGED-20261114.json');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const bytes=readFileSync(input); const merged=JSON.parse(bytes.toString('utf8'));
assert.ok(merged.rules && typeof merged.rules==='object','Invalid Rules JSON');
const changed=Object.keys(baseline.rules).filter(key=>!isDeepStrictEqual(merged.rules[key],baseline.rules[key]));
const added=Object.keys(merged.rules).filter(key=>!(key in baseline.rules));
const sameEnvelope=isDeepStrictEqual(Object.keys(merged).sort(),Object.keys(baseline).sort()) &&
  Object.keys(baseline).filter(key=>key!=='rules').every(key=>isDeepStrictEqual(merged[key],baseline[key]));
const audit={input,sha256:hash(bytes),oldGroups:Object.keys(baseline.rules).length,
  mergedGroups:Object.keys(merged.rules).length,changedOldGroups:changed,addedGroups:added,
  onlyExpectedAddition:added.length===1 && added[0]==='memberWorkoutPlans' && sameEnvelope,
  legacyPreserved:changed.length===0,sameAsOldCandidate:isDeepStrictEqual(merged,generatedCandidate),
  personalBranchMatchesPhase2A:isDeepStrictEqual(merged.rules.memberWorkoutPlans,generatedCandidate.rules.memberWorkoutPlans)};
const config=JSON.parse(readFileSync(path.join(folder,'firebase.json'),'utf8'));
for(const service of ['database','auth','hub','logging']) assert.equal(config.emulators[service].host,'127.0.0.1');
assert.equal(config.emulators.ui.enabled,false);
const ports=await Promise.all(['database','auth','hub','logging'].map(service=>new Promise(resolve=>{
  const endpoint=config.emulators[service]; const socket=net.createConnection(endpoint);
  socket.setTimeout(500);
  socket.once('connect',()=>{socket.destroy();resolve({port:endpoint.port,busy:true});});
  socket.once('error',error=>{socket.destroy();resolve({port:endpoint.port,busy:error.code!=='ECONNREFUSED'});});
  socket.once('timeout',()=>{socket.destroy();resolve({port:endpoint.port,busy:true});});
})));
assert.ok(ports.every(port=>!port.busy),`Refusing to clear/reuse an existing emulator: ${JSON.stringify(ports)}`);
const output=path.join(folder,'artifacts',`merged-${new Date().toISOString().replace(/[:.]/g,'-')}-${randomUUID()}`);
mkdirSync(output,{recursive:true});
config.database.rules=input;
const configPath=path.join(output,'firebase.json');
writeFileSync(configPath,JSON.stringify(config,null,2)+'\n');
writeFileSync(path.join(output,'audit.json'),JSON.stringify(audit,null,2)+'\n');
console.log(JSON.stringify(audit,null,2));
console.log(`Exact MERGED file is configured in both the CLI and initializeTestEnvironment. Report: ${output}`);
const env={...process.env,CI:'true',GCLOUD_PROJECT:'demo-kgyms-personal-plans',
  FIREBASE_EMULATORS_PATH:path.join(folder,'.emulator-cache'),XDG_CONFIG_HOME:path.join(output,'cli-config'),
  KGYMS_TEST_RULES_FILE:input,KGYMS_TEST_RULES_SHA256:audit.sha256};
delete env.GOOGLE_APPLICATION_CREDENTIALS; delete env.FIREBASE_TOKEN; delete env.GOOGLE_CLOUD_PROJECT;
const pathKeys=Object.keys(env).filter(key=>key.toUpperCase()==='PATH');
const originalPath=pathKeys.map(key=>env[key]).find(Boolean)??'';
pathKeys.forEach(key=>delete env[key]);
env.JAVA_HOME=env.JAVA_HOME||'C:/Program Files/Android/Android Studio/jbr';
env.PATH=path.join(env.JAVA_HOME,'bin')+path.delimiter+originalPath;
const command=`"${process.execPath}" --test --test-concurrency=1 --test-reporter=tap "${path.join(folder,'test/personal-plans.test.mjs')}"`;
const args=[path.join(folder,'node_modules/firebase-tools/lib/bin/firebase.js'),'--project','demo-kgyms-personal-plans',
  '--config',configPath,'--non-interactive','emulators:exec','--only','database,auth',command];
const log=createWriteStream(path.join(output,'test-results.log'));
const child=spawn(process.execPath,args,{cwd:output,env,windowsHide:true,stdio:['ignore','pipe','pipe']});
let transcript='';
child.stdout.on('data',data=>{transcript+=data;log.write(data);process.stdout.write(data);});
child.stderr.on('data',data=>{transcript+=data;log.write(data);process.stderr.write(data);});
process.on('SIGINT',()=>console.log('Waiting for the owned demo CLI to shut down...'));
child.on('error',error=>{console.error(error.message);process.exitCode=1;});
child.on('close',async code=>{
  await new Promise(resolve=>log.end(resolve));
  const counts={}; for(const name of ['tests','pass','fail','cancelled','skipped'])counts[name]=Number(transcript.match(new RegExp(`^# ${name} (\\d+)\\r?$`,'m'))?.[1]??-1);
  const unchanged=hash(readFileSync(input))===audit.sha256;
  const result={...audit,counts,inputUnchanged:unchanged,emulatorExitCode:code,
    verdict:code===0 && counts.tests===63 && counts.pass===63 && counts.fail===0 && counts.cancelled===0 && counts.skipped===0 && unchanged && audit.legacyPreserved && audit.onlyExpectedAddition?'PASS':'FAIL'};
  writeFileSync(path.join(output,'result.json'),JSON.stringify(result,null,2)+'\n');
  console.log('MERGED_RESULT '+JSON.stringify(result));
  process.exitCode=result.verdict==='PASS'?0:1;
});
