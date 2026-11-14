import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';

// Read rules only. No deploy/PUT/PATCH/DELETE, login, refresh, service account, or database data access.
const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'app/build/personal-plan-production-rules');
mkdirSync(output, { recursive: true });
const endpoint = 'https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/.settings/rules.json';
let token;
try {
  const require = createRequire(import.meta.url);
  const { configstore } = require('../../firebase-tests/node_modules/firebase-tools/lib/configstore.js');
  const tokens = configstore.get('tokens');
  if (tokens?.access_token && tokens.expires_at > Date.now() + 60_000) token = tokens.access_token;
} catch { /* No usable existing CLI session. Never request credentials or print tokens. */ }
let latest;
const status = { endpoint, method:'GET', authenticated:!!token, latestRetrieved:false, checkedAt:new Date().toISOString() };
try {
  const response = await fetch(endpoint, { method:'GET', headers:token?{Authorization:`Bearer ${token}`}:{}, redirect:'error', signal:AbortSignal.timeout(12_000) });
  status.httpStatus=response.status;
  if (response.ok) {
    const text=await response.text(); assert.ok(text.length < 2_000_000);
    const value=JSON.parse(text); assert.ok(value.rules && typeof value.rules === 'object');
    latest=value; status.latestRetrieved=true;
    writeFileSync(path.join(output,`production-read-${Date.now()}.json`),JSON.stringify(latest,null,2)+'\n');
  }
} catch (error) { status.error=error.name; }
const { baseline, candidate } = await import(new URL('../../firebase-tests/scripts/build-rules.mjs',import.meta.url));
const source=latest??baseline;
assert.ok(source.rules['.read'] === undefined || source.rules['.read'] === false,'Parent read grant needs manual review; cannot claim UID isolation');
assert.ok(source.rules['.write'] === undefined || source.rules['.write'] === false,'Parent write grant needs manual review; cannot protect immutable plans');
if (source.rules.memberWorkoutPlans) assert.deepEqual(source.rules.memberWorkoutPlans,candidate.rules.memberWorkoutPlans,'An existing Personal Plan branch differs: do not overwrite it');
const proposed={...source,rules:{...source.rules,memberWorkoutPlans:candidate.rules.memberWorkoutPlans}};
for(const [key,value] of Object.entries(source.rules)) assert.deepEqual(proposed.rules[key],value);
const destination=path.join(output,'database.proposed.rules.json');
const content=JSON.stringify(proposed,null,2)+'\n';
// Generated proposal only, never the provided snapshot, Phase 2A files, or deployed rules.
writeFileSync(destination,content);
status.proposalSource=latest?'latest-production-read':'provided-20261113-snapshot-NOT-verified-current';
status.proposalEqualsPhase2ATestedCandidate=JSON.stringify(proposed)===JSON.stringify(candidate);
status.proposalSha256=createHash('sha256').update(content).digest('hex');
status.legacyGroupsPreserved=Object.keys(source.rules).filter(key=>key!=='memberWorkoutPlans').length;
writeFileSync(path.join(output,'read-status.json'),JSON.stringify(status,null,2)+'\n');
console.log(JSON.stringify(status,null,2));
console.log(`PROPOSAL ONLY — NOT DEPLOYED: ${destination}`);
