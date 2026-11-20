/** Điểm danh bằng QR xoay vòng (port CheckInRepository.handleScan). */
import { decideCheckIn } from '../domain/checkIn.js';
import { isCheckInCode, verifyCode } from '../domain/checkInCode.js';
import { entries, toCheckIn, toMembership } from '../domain/models.js';
import { writeLog } from './activityLog.js';
import type { Ctx } from './context.js';

export type CheckInResponse =
  | { result: 'not_checkin_code' }
  | { result: 'invalid_code' }
  | { result: 'staff_not_required' }
  | { result: 'checked_out'; checkInAt: number; checkOutAt: number }
  | { result: 'no_active_membership' }
  | { result: 'checked_in'; at: number; packageName: string };

export async function handleCheckIn(ctx: Ctx, uid: string, code: unknown): Promise<CheckInResponse> {
  const { db } = ctx;
  const nowMillis = ctx.now();

  if (!isCheckInCode(code)) return { result: 'not_checkin_code' };
  if (!verifyCode(code, ctx.config.checkInSecret, nowMillis)) return { result: 'invalid_code' };

  const user = (await db.get(`users/${uid}`)) as Record<string, unknown> | null;
  if (user?.role === 'admin' || user?.isStaff === true) return { result: 'staff_not_required' };

  const decision = decideCheckIn({
    uid,
    checkIns: entries(await db.queryByChild('checkIns', 'uid', uid), toCheckIn),
    memberships: entries(await db.queryByChild('memberships', 'uid', uid), toMembership),
    nowMillis,
    newCheckInId: () => db.pushKey('checkIns')
  });

  if (Object.keys(decision.updates).length > 0) await db.update(decision.updates);

  switch (decision.result) {
    case 'checked_out':
      await writeLog(ctx, uid, 'check_in', 'check-out khỏi phòng gym');
      return { result: 'checked_out', checkInAt: decision.checkInAt, checkOutAt: decision.checkOutAt };
    case 'no_active_membership':
      return { result: 'no_active_membership' };
    case 'checked_in': {
      await writeLog(ctx, uid, 'check_in', 'check-in tại phòng gym');
      let packageName = '';
      try {
        const pkg = (await db.get(`packages/${decision.membership.packageId}`)) as Record<string, unknown> | null;
        packageName = typeof pkg?.name === 'string' ? pkg.name : '';
      } catch {
        // Tên gói chỉ để hiển thị.
      }
      return { result: 'checked_in', at: decision.at, packageName };
    }
  }
}
