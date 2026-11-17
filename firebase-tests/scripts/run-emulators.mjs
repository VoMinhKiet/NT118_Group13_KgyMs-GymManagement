import { spawn } from 'node:child_process';
import { readFileSync, mkdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
import { candidateText } from './build-rules.mjs';

const folder = fileURLToPath(new URL('../', import.meta.url));
const projectId = 'demo-kgyms-personal-plans';
const mode = process.argv[2];
assert.ok(['start','exec'].includes(mode), 'Only local Emulator start/exec are supported');
assert.equal(readFileSync(path.join(folder, 'database.candidate.rules.json'), 'utf8'), candidateText);
const config = JSON.parse(readFileSync(path.join(folder, 'firebase.json'), 'utf8'));
assert.equal(config.database.rules, 'database.candidate.rules.json');
for (const service of ['database','auth','hub','logging']) assert.equal(config.emulators[service].host, '127.0.0.1');
assert.equal(config.emulators.ui.enabled, false);
const env = { ...process.env, CI: 'true' };
// Explicit local demo project. No alias, login command, service account or production database URL.
delete env.GOOGLE_APPLICATION_CREDENTIALS;
delete env.FIREBASE_TOKEN;
delete env.GOOGLE_CLOUD_PROJECT;
delete env.GCLOUD_PROJECT;
env.GCLOUD_PROJECT = projectId;
env.FIREBASE_EMULATORS_PATH = path.join(folder, '.emulator-cache');
// Configstore honors XDG_CONFIG_HOME on Windows too: do not read the user's CLI login/usage config.
env.XDG_CONFIG_HOME = path.join(folder, '.emulator-cache', 'config');
const studioJdk = 'C:/Program Files/Android/Android Studio/jbr';
if (!env.JAVA_HOME && existsSync(path.join(studioJdk, 'bin/java.exe'))) env.JAVA_HOME = studioJdk;
const pathKeys = Object.keys(env).filter(key => key.toUpperCase() === 'PATH');
const systemPath = pathKeys.map(key => env[key]).find(Boolean) ?? '';
for (const key of pathKeys) delete env[key];
env.PATH = (env.JAVA_HOME ? path.join(env.JAVA_HOME, 'bin') + path.delimiter : '') + systemPath;
mkdirSync(path.join(folder, 'artifacts'), { recursive: true });
const args = [path.join(folder, 'node_modules/firebase-tools/lib/bin/firebase.js'),
  '--project', projectId, '--config', path.join(folder, 'firebase.json'), '--non-interactive',
  mode === 'exec' ? 'emulators:exec' : 'emulators:start', '--only', 'database,auth'];
if (mode === 'exec') args.push(`"${process.execPath}" "${path.join(folder, 'scripts/run-tests.mjs')}"`);
const child = spawn(process.execPath, args, { cwd: folder, env, stdio: 'inherit', windowsHide: true });
child.on('error', error => { console.error(error); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
