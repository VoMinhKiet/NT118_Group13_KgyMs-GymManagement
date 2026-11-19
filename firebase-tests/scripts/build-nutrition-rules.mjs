import assert from 'node:assert/strict';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const baseUrl = new URL('../KgyMs-RTDB-Rules-M2D3-CANDIDATE-20261115.json', import.meta.url);
export const candidateUrl = new URL('../KgyMs-RTDB-Rules-NUTRITION-CANDIDATE.json', import.meta.url);
export const base = JSON.parse(readFileSync(baseUrl, 'utf8'));
assert.equal(Object.keys(base.rules).length, 22);
assert.ok(!Object.hasOwn(base.rules, 'memberNutritionPlans'));
const model = '../../KgyMs/app/src/main/java/com/kgyms/app/data/model/';
const source = ['NutritionPlan.kt', 'NutritionExtraMeals.kt'].map(file =>
  readFileSync(new URL(model + file, import.meta.url), 'utf8')).join('\n');
export const catalog = [...source.matchAll(/NutritionMeal\("([a-z0-9_]+)",\s*MealSlot\.([A-Z]+),/g)]
  .map(([, id, slot]) => ({ id, slot }));
assert.equal(catalog.length, 31);
assert.equal(new Set(catalog.map(({ id }) => id)).size, catalog.length);

const goals = ['FAT_LOSS', 'MUSCLE_GAIN', 'MAINTAIN'];
const activities = ['LOW', 'MODERATE', 'HIGH'];
const diets = ['REGULAR', 'VEGETARIAN'];
const oneOf = values => '(' + values.map(value => "newData.val() == '" + value + "'").join(' || ') + ')';
const field = values => ({ '.validate': 'newData.isString() && ' + oneOf(values) });
const denyUnknown = { '.validate': false };
const owner = "auth != null && auth.uid == $uid && $uid.matches(/^[A-Za-z0-9_-]{1,128}$/) && root.child('users').child($uid).child('status').val() == 'active'";
const mealValidation = ['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'].map(slot => {
  const allowedIds = catalog.filter(entry => entry.slot === slot).map(entry => entry.id);
  return '($slot.matches(/^[0-6]:' + slot + '$/) && ' + oneOf(allowedIds) + ')';
}).join(' || ');
export const candidate = structuredClone(base);
candidate.rules.memberNutritionPlans = {
  '$uid': {
    '.read': owner,
    '.write': owner + " && newData.exists() && ((!data.exists() && newData.child('revision').val() == 1) || (data.child('schemaVersion').val() == 1 && newData.child('revision').val() == data.child('revision').val() + 1 && newData.child('writeId').val() != data.child('writeId').val()))",
    '.validate': "newData.hasChildren(['schemaVersion','revision','writeId','updatedAt','setup','generatedFor'])",
    schemaVersion: { '.validate': 'newData.isNumber() && newData.val() == 1' },
    revision: { '.validate': 'newData.isNumber() && newData.val() >= 1 && newData.val() <= 2147483647' },
    writeId: { '.validate': 'newData.isString() && newData.val().matches(/^[0-9a-f]{32}$/)' },
    updatedAt: { '.validate': 'newData.isNumber() && newData.val() == now' },
    setup: {
      '.validate': "newData.hasChildren(['goal','activity','diet'])",
      goal: field(goals), activity: field(activities), diet: field(diets), '$other': denyUnknown,
    },
    generatedFor: field(['', ...goals.flatMap(g => activities.flatMap(a => diets.map(d => [g, a, d].join(':'))))]),
    meals: {
      '.validate': "newData.hasChildren()",
      '$slot': { '.validate': 'newData.isString() && (' + mealValidation + ')' },
    },
    '$other': denyUnknown,
  },
};
const { memberNutritionPlans, ...unchanged } = candidate.rules;
assert.ok(memberNutritionPlans);
assert.deepEqual(unchanged, base.rules);

export const candidateText = JSON.stringify(candidate, null, 2) + '\n';
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv.includes('--check')) assert.equal(readFileSync(candidateUrl, 'utf8'), candidateText);
  else writeFileSync(candidateUrl, candidateText);
  console.log('Nutrition candidate verified against M2D3 and the ' + catalog.length + ' meal IDs.');
}
