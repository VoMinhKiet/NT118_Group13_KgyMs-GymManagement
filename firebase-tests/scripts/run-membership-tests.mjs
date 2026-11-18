import {spawn} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import net from 'node:net';
import assert from 'node:assert/strict';

const folder = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(folder, 'artifacts', 'memberships', new Date().toISOString().replace(/[:.]/g, '-'));
const emulators = {
  database: {host: '127.0.0.1', port: 9100}, auth: {host: '127.0.0.1', port: 9199},
  hub: {host: '127.0.0.1', port: 4411}, logging: {host: '127.0.0.1', port: 4511}, ui: {enabled: false},
  singleProjectMode: true,
};
for (const name of ['database', 'auth', 'hub', 'logging']) {
  const busy = await new Promise(resolve => {
    const socket = net.createConnection(emulators[name]); socket.setTimeout(500);
    socket.once('connect', () => {socket.destroy(); resolve(true);});
    socket.once('error', e => {socket.destroy(); resolve(e.code !== 'ECONNREFUSED');});
    socket.once('timeout', () => {socket.destroy(); resolve(true);});
  });
  assert.equal(busy, false, `${name} port is occupied; refusing to touch an existing emulator`);
}
mkdirSync(output, {recursive: true});
const configPath = path.join(output, 'firebase.json');
// Load the existing production Rules snapshot unchanged, into a demo emulator only.
writeFileSync(configPath, JSON.stringify({database: {rules: path.join(folder, 'rules.production.snapshot.json')}, emulators}, null, 2));
const env = {...process.env, CI: 'true', GCLOUD_PROJECT: 'demo-kgyms-memberships',
  XDG_CONFIG_HOME: path.join(output, 'cli-config'), FIREBASE_EMULATORS_PATH: path.join(folder, '.emulator-cache')};
for (const key of ['GOOGLE_APPLICATION_CREDENTIALS', 'FIREBASE_TOKEN', 'GOOGLE_CLOUD_PROJECT']) delete env[key];
env.JAVA_HOME ||= 'C:/Program Files/Android/Android Studio/jbr';
const pathKeys = Object.keys(env).filter(k => k.toUpperCase() === 'PATH');
const oldPath = pathKeys.map(k => env[k]).find(Boolean) ?? ''; pathKeys.forEach(k => delete env[k]);
env.PATH = path.join(env.JAVA_HOME, 'bin') + path.delimiter + oldPath;
const args = [path.join(folder, 'node_modules/firebase-tools/lib/bin/firebase.js'), '--project', 'demo-kgyms-memberships',
  '--config', configPath, '--non-interactive', 'emulators:exec', '--only', 'database,auth',
  `"${process.execPath}" --test --test-concurrency=1 --test-reporter=tap "${path.join(folder, 'test/memberships.test.mjs')}"`];
console.log(`Demo-only membership Rules tests; logs: ${output}`);
const child = spawn(process.execPath, args, {cwd: output, env, windowsHide: true, stdio: 'inherit'});
child.on('error', error => {console.error(error); process.exitCode = 1;});
child.on('exit', code => {process.exitCode = code ?? 1;});
