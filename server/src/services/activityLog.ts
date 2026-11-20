import { timestamp } from '../domain/dates.js';
import { actorDisplayName, buildLog, SYSTEM_ACTOR_NAME, SYSTEM_ACTOR_UID, type LogAction } from '../domain/log.js';
import type { Ctx } from './context.js';

const LOG_TIMEOUT_MS = 3_000;

/** Ghi activityLogs. Lỗi ghi log không bao giờ làm hỏng thao tác chính. */
export async function writeLog(ctx: Ctx, actorUid: string, action: LogAction, description: string): Promise<void> {
  try {
    let name = SYSTEM_ACTOR_NAME;
    if (actorUid !== SYSTEM_ACTOR_UID) {
      const user = (await ctx.db.get(`users/${actorUid}`)) as Record<string, unknown> | null;
      name = actorDisplayName(user);
    }
    const entry = buildLog(actorUid, name, action, description, timestamp(ctx.now()));
    const key = ctx.db.pushKey('activityLogs');
    await withTimeout(ctx.db.update({ [`activityLogs/${key}`]: entry }), LOG_TIMEOUT_MS);
  } catch (error) {
    ctx.logger.warn(`[activityLog] Không ghi được log: ${(error as Error).message}`);
  }
}

export const systemLog = (ctx: Ctx, action: LogAction, description: string) =>
  writeLog(ctx, SYSTEM_ACTOR_UID, action, description);

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('timeout')), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}
