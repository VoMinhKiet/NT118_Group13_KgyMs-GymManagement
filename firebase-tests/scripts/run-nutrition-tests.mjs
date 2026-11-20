import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { candidateText, candidateUrl } from './build-nutrition-rules.mjs';
import { checkMergedFile } from './build-nutrition-live-rules.mjs';

const project = 'demo-kgyms-nutrition';
const folder = fileURLToPath(new URL('../', import.meta.url));
const args = process.argv.slice(2);
assert.ok(args.length === 0 || (args.length === 2 && args[0] === '--live-merged'),
  'Usage: node run-nutrition-tests.mjs [--live-merged <live-snapshot.json>]');
const livePath = args.length ? path.resolve(args[1]) : null;
assert.equal(readFileSync(candidateUrl, 'utf8'), candidateText, 'Regenerate the Nutrition candidate first');
const rulesText = livePath ? checkMergedFile(livePath) : candidateText;
const endpoints = Object.fromEntries(Object.entries({ database: 9010, auth: 9110, hub: 4410, logging: 4510 })
  .map(([key, port]) => [key, { host: '127.0.0.1', port }]));
await Promise.all(Object.values(endpoints).map(endpoint => new Promise((resolve, reject) => {
  const socket = net.createConnection(endpoint);
  socket.setTimeout(700);
  socket.once('error', error => error.code === 'ECONNREFUSED' ? resolve() : reject(error));
  const occupied = () => { socket.destroy(); reject(new Error('Reserved Nutrition demo port is busy')); };
  socket.once('connect', occupied);
  socket.once('timeout', occupied);
})));
const output = path.join(folder, 'artifacts', 'nutrition', 'run-' + randomUUID());
mkdirSync(output, { recursive: true });
const configPath = path.join(output, 'firebase.json');
// Put the rules in the same working directory so the CLI cannot resolve a repository default config.
writeFileSync(path.join(output, 'nutrition.rules.json'), rulesText);
writeFileSync(configPath, JSON.stringify({
  database: { rules: 'nutrition.rules.json' },
  emulators: { ...endpoints, ui: { enabled: false }, singleProjectMode: true },
}, null, 2));
const env = { ...process.env, CI: 'true', GCLOUD_PROJECT: project, FIREBASE_PROJECT: project,
  XDG_CONFIG_HOME: path.join(output, 'cli-config'), FIREBASE_EMULATORS_PATH: path.join(folder, '.emulator-cache') };
for (const key of ['GOOGLE_APPLICATION_CREDENTIALS','FIREBASE_TOKEN','GOOGLE_CLOUD_PROJECT']) delete env[key];
delete env.KGYMS_NUTRITION_LIVE_SNAPSHOT;
if (livePath) env.KGYMS_NUTRITION_LIVE_SNAPSHOT = livePath;
const pathKeys = Object.keys(env).filter(key => key.toUpperCase() === 'PATH');
const oldPath = pathKeys.map(key => env[key]).find(Boolean) ?? '';
pathKeys.forEach(key => delete env[key]);
env.JAVA_HOME ||= 'C:/Program Files/Android/Android Studio/jbr';
env.PATH = path.join(env.JAVA_HOME, 'bin') + path.delimiter + oldPath;
const suites = ['test/nutrition.test.mjs'];
if (livePath) suites.push('test/nutrition-live-merged.test.mjs');
const test = '"' + process.execPath + '" --test --test-concurrency=1 ' +
  suites.map(file => '"' + path.join(folder, file) + '"').join(' ');
const child = spawn(process.execPath, [path.join(folder, 'node_modules/firebase-tools/lib/bin/firebase.js'), '--project',
  project, '--config', configPath, '--non-interactive', 'emulators:exec', '--only', 'database,auth', test],
{ cwd: output, env, windowsHide: true, stdio: 'inherit' });
console.log('Nutrition Rules demo only (' + project + '); logs at ' + output);
child.on('error', error => { console.error(error.message); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
