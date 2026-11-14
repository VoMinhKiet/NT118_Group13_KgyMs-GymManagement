import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import net from 'node:net';
import path from 'node:path';
import assert from 'node:assert/strict';

const androidRoot = fileURLToPath(new URL('../', import.meta.url));
const phase2a = fileURLToPath(new URL('../../firebase-tests/', import.meta.url));
const mode = process.argv[2];
assert.ok(['check','rules','start'].includes(mode), 'Only check, rules test, or local start is supported');
const project = 'demo-kgyms-personal-plans';
const configPath = path.join(phase2a,'firebase.json');
const config = JSON.parse(readFileSync(configPath,'utf8'));
const endpoints = ['database','auth','hub','logging'].map(name => config.emulators[name]);
assert.ok(endpoints.every(endpoint => endpoint.host === '127.0.0.1'));
const ports = await Promise.all(endpoints.map(endpoint => new Promise(resolve => {
  const socket = net.createConnection({host:endpoint.host,port:endpoint.port});
  socket.setTimeout(500);
  socket.once('connect',()=>{ socket.destroy(); resolve({port:endpoint.port,busy:true}); });
  socket.once('error',error=>{socket.destroy(); resolve({port:endpoint.port,busy:error.code!=='ECONNREFUSED'});});
  socket.once('timeout',()=>{socket.destroy(); resolve({port:endpoint.port,busy:true});});
})));
assert.ok(ports.every(p=>!p.busy), `Ports already occupied: ${JSON.stringify(ports)}. Refusing to stop/reset an existing emulator.`);
const { candidateText } = await import(new URL('../../firebase-tests/scripts/build-rules.mjs',import.meta.url));
assert.equal(readFileSync(path.join(phase2a,'database.candidate.rules.json'),'utf8'), candidateText);
console.log('Demo config and unchanged Phase 2A verified. Ports free. No existing emulator data accessed/reset.');
if(mode === 'check') process.exit(0);
const root=path.join(androidRoot,'app/build/personal-plan-emulator');
mkdirSync(root,{recursive:true});
const env={...process.env,CI:'true',GCLOUD_PROJECT:project,XDG_CONFIG_HOME:path.join(root,'cli-config'),FIREBASE_EMULATORS_PATH:path.join(phase2a,'.emulator-cache')};
delete env.GOOGLE_APPLICATION_CREDENTIALS; delete env.FIREBASE_TOKEN; delete env.GOOGLE_CLOUD_PROJECT;
const pathKeys=Object.keys(env).filter(key=>key.toUpperCase()==='PATH');
const originalPath=pathKeys.map(key=>env[key]).find(Boolean)??'';
pathKeys.forEach(key=>delete env[key]);
env.JAVA_HOME=env.JAVA_HOME||'C:/Program Files/Android/Android Studio/jbr';
env.PATH=path.join(env.JAVA_HOME,'bin')+path.delimiter+originalPath;
const args=[path.join(phase2a,'node_modules/firebase-tools/lib/bin/firebase.js'),'--project',project,'--config',configPath,'--non-interactive'];
const state=path.join(root,'exports');
const legacyState=path.join(root,'saved-state');
if(mode==='rules') {
  // Fresh disposable process, never import saved Android integration/manual data into a clearing rules suite.
  args.push('emulators:exec','--only','database,auth',`"${process.execPath}" "${path.join(phase2a,'scripts/run-tests.mjs')}"`);
} else {
  // Never overwrite a previous export. Import the newest complete generation and export
  // to a fresh directory; interrupted exports without metadata are not selected.
  const generations = existsSync(state) ? readdirSync(state).filter(name => /^run-[0-9T-]+-[a-f0-9-]+$/.test(name))
    .sort().map(name => path.join(state,name)).filter(dir => existsSync(path.join(dir,'firebase-export-metadata.json'))) : [];
  const previous = generations.at(-1) ?? (existsSync(path.join(legacyState,'firebase-export-metadata.json')) ? legacyState : null);
  const next = path.join(state,`run-${new Date().toISOString().replace(/[:.Z]/g,'-')}-${randomUUID()}`);
  assert.ok(next.startsWith(root+path.sep) && !existsSync(next), 'Export target must be a new directory inside the test workspace');
  args.push('emulators:start','--only','database,auth',`--export-on-exit=${next}`);
  if(previous) args.push(`--import=${previous}`);
  console.log(`Import: ${previous ?? '(new demo state)'}. Export on clean exit: ${next}. Previous exports retained.`);
}
const child=spawn(process.execPath,args,{cwd:mode==='rules'?phase2a:root,env,stdio:'inherit',windowsHide:true});
// The console sends Ctrl+C to the CLI as well. Keep its parent alive until the
// CLI completes its own graceful export/shutdown; do not kill its process tree.
process.on('SIGINT',()=>console.log('Waiting for Firebase CLI to finish its clean shutdown/export...'));
child.on('error',error=>{console.error(error);process.exitCode=1;});
child.on('exit',code=>{process.exitCode=code??1;});
