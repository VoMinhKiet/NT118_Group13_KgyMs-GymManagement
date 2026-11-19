import { spawn } from 'node:child_process';
import { createWriteStream, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

assert.equal(process.env.FIREBASE_DATABASE_EMULATOR_HOST, '127.0.0.1:9000', 'Refusing a non-loopback or missing Database Emulator');
assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, '127.0.0.1:9099', 'Refusing a non-loopback or missing Auth Emulator');
assert.equal(process.env.GCLOUD_PROJECT, 'demo-kgyms-personal-plans');
const folder = fileURLToPath(new URL('../', import.meta.url));
mkdirSync(new URL('../artifacts/', import.meta.url), { recursive: true });
const output = createWriteStream(new URL('../artifacts/test-results.tap', import.meta.url));
const child = spawn(process.execPath, ['--test','--test-concurrency=1','--test-reporter=tap','test/personal-plans.test.mjs'], {
  cwd: folder, env: process.env, windowsHide: true, stdio: ['ignore','pipe','pipe']
});
child.stdout.on('data', data => { process.stdout.write(data); output.write(data); });
child.stderr.on('data', data => { process.stderr.write(data); output.write(data); });
child.on('error', error => { console.error(error); output.end(); process.exitCode = 1; });
child.on('exit', code => { output.end(); process.exitCode = code ?? 1; });
