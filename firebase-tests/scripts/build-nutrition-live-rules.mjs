import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const candidateUrl = new URL('../KgyMs-RTDB-Rules-NUTRITION-CANDIDATE.json', import.meta.url);
export const mergedUrl = new URL('../KgyMs-RTDB-Rules-NUTRITION-LIVE-MERGED.json', import.meta.url);
const key = 'memberNutritionPlans';

function readRules(text) {
  const document = JSON.parse(text);
  assert.deepEqual(Object.keys(document), ['rules'], 'Expected a Firebase Rules document');
  assert.ok(document.rules && typeof document.rules === 'object' && !Array.isArray(document.rules));
  return document.rules;
}

export function verifyMerge(liveText, mergedText) {
  const live = readRules(liveText);
  const candidate = readRules(readFileSync(candidateUrl, 'utf8'));
  const merged = readRules(mergedText);
  assert.equal(Object.keys(live).length, 24);
  assert.ok(Object.hasOwn(live, 'bankTransactions') && Object.hasOwn(live, 'reconciliation'));
  assert.ok(!Object.hasOwn(live, key), 'Nutrition already exists in the live export');
  assert.ok(Object.hasOwn(candidate, key), 'Nutrition candidate node is missing');
  assert.deepEqual(Object.keys(merged), [...Object.keys(live), key]);
  const { [key]: nutrition, ...existing } = merged;
  assert.deepEqual(existing, live, 'Existing live Rules, expressions and indexes changed');
  assert.deepEqual(nutrition, candidate[key], 'Nutrition Rules differ from the candidate');
}

function buildMergedText(liveText) {
  const live = readRules(liveText);
  assert.equal(Object.keys(live).length, 24);
  assert.ok(!Object.hasOwn(live, key), 'Nutrition already exists in the live export');
  const candidate = readRules(readFileSync(candidateUrl, 'utf8'));
  assert.ok(Object.hasOwn(candidate, key), 'Nutrition candidate node is missing');

  // A Rules document has only one root property. The penultimate brace closes its rules object.
  const rootClose = liveText.lastIndexOf('}');
  const rulesClose = liveText.lastIndexOf('}', rootClose - 1);
  const insertion = liveText.slice(0, rulesClose).trimEnd().length;
  const newline = liveText.includes('\r\n') ? '\r\n' : '\n';
  const property = JSON.stringify({ [key]: candidate[key] }, null, 2).split('\n')
    .slice(1, -1).map(line => '  ' + line).join(newline);
  const mergedText = liveText.slice(0, insertion) + ',' + newline + property + liveText.slice(insertion);
  verifyMerge(liveText, mergedText);
  return mergedText;
}

export function checkMergedFile(livePath) {
  const liveText = readFileSync(livePath, 'utf8');
  const mergedText = readFileSync(mergedUrl, 'utf8');
  verifyMerge(liveText, mergedText);
  assert.equal(mergedText, buildMergedText(liveText), 'The file must preserve the live export verbatim apart from the insertion');
  return mergedText;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  assert.ok((args.length === 1 && !args[0].startsWith('--')) ||
    (args.length === 2 && args[0] === '--check'), 'Usage: node build-nutrition-live-rules.mjs [--check] <live-snapshot.json>');
  const livePath = path.resolve(args.at(-1));
  assert.notEqual(livePath.toLowerCase(), fileURLToPath(mergedUrl).toLowerCase(), 'Provide the original live export');
  if (args[0] === '--check') checkMergedFile(livePath);
  else writeFileSync(mergedUrl, buildMergedText(readFileSync(livePath, 'utf8')));
  console.log('Verified 24 unchanged live Rules groups and the exact Nutrition candidate node (25 groups).');
}
