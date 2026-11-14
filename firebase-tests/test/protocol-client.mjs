import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { serverTimestamp } from 'firebase/database';

// Emulator test protocol only; this is NOT an Android/Firebase production adapter.
export const pathFor = uid => `memberWorkoutPlans/${uid}`;
export const planId = seed => 'plan-' + createHash('sha256').update(seed).digest('hex').slice(0, 24);
export function makePlan(uid = 'A', seed = 'one', count = 1) {
  const id = planId(`${uid}-${seed}`);
  const startDate = '2026-11-13';
  const weekdays = Array.from({ length: count }, (_, i) => i);
  const sessions = [];
  for (let offset = 0; offset < 28; offset++) {
    const instant = new Date(`${startDate}T00:00:00Z`);
    instant.setUTCDate(instant.getUTCDate() + offset);
    if (weekdays.includes((instant.getUTCDay() + 6) % 7)) sessions.push({
      id: `${id}-${sessions.length}`, date: instant.toISOString().slice(0, 10),
      templateId: 'full-body', exerciseIds: ['squat','press','row']
    });
  }
  return { id, uid, schemaVersion: 1, generatorVersion: 1, level: 'Beginner', sessionsPerWeek: count,
    startDate, zoneId: 'Asia/Ho_Chi_Minh', weekdays, repeated: true, sessions };
}
export async function readArchive(db, uid = 'A') {
  return (await db.ref(pathFor(uid)).get()).val();
}
export function appendUpdates(base, plan, activate = false) {
  const ordinal = base?.state.nextOrdinal ?? 1;
  const state = { revision: (base?.state.revision ?? 0) + 1, nextOrdinal: ordinal + 1 };
  const active = activate ? plan.id : base?.state.activeId;
  if (active) state.activeId = active;
  return {
    ...(!base ? { remoteSchemaVersion: 1 } : {}),
    [`plans/${plan.id}`]: { ordinal, uploadedAt: serverTimestamp(), payload: structuredClone(plan) },
    [`versionOrder/v${ordinal}`]: plan.id,
    state
  };
}
export async function appendPrepared(db, uid, updates) { await db.ref(pathFor(uid)).update(updates); }

/** Retry append-only snapshots, never silently retry a stale active-plan selection. */
export async function publish(db, plan, { base, activate = false, retries = 12 } = {}) {
  let current = base === undefined ? await readArchive(db, plan.uid) : base;
  for (let attempt = 0; attempt <= retries; attempt++) {
    const existing = current?.plans?.[plan.id];
    if (existing) {
      assert.deepEqual(existing.payload, plan, 'Same ID with different payload must not overwrite an existing version');
      return { created: false, ordinal: existing.ordinal };
    }
    try {
      const updates = appendUpdates(current, plan, activate);
      await appendPrepared(db, plan.uid, updates);
      return { created: true, ordinal: updates[`plans/${plan.id}`].ordinal };
    } catch (error) {
      if (!String(error.code).toLowerCase().includes('permission_denied')) throw error;
      const fresh = await readArchive(db, plan.uid);
      if ((fresh?.state.revision ?? 0) === (current?.state.revision ?? 0)) throw error; // invalid schema/auth, not a CAS conflict
      if (activate) throw error; // explicit conflict resolution required for activation
      current = fresh;
    }
  }
  throw new Error('Append retry limit exceeded; retain the local snapshot for a later retry');
}
