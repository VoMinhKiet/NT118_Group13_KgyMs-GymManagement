/**
 * Quyết định check-in / check-out, port từ CheckInRepository.handleScan() (app Android).
 * Hàm thuần: nhận dữ liệu đã đọc, trả về kết quả + các đường dẫn cần ghi.
 */
import { endOfVnDayMillis, parseDay, startOfVnDayMillis, vnDay } from './dates.js';
import { MembershipStatus, type CheckIn, type Membership } from './models.js';

export type CheckInDecision =
  | { result: 'checked_out'; checkInAt: number; checkOutAt: number; updates: Record<string, unknown> }
  | { result: 'no_active_membership'; updates: Record<string, unknown> }
  | { result: 'checked_in'; at: number; membership: Membership; updates: Record<string, unknown> };

export function decideCheckIn(args: {
  uid: string;
  checkIns: CheckIn[];
  memberships: Membership[];
  nowMillis: number;
  newCheckInId: () => string;
}): CheckInDecision {
  const { uid, checkIns, memberships, nowMillis, newCheckInId } = args;
  const todayStart = startOfVnDayMillis(nowMillis);
  const open = checkIns.filter((c) => c.checkOutAt === 0 && c.checkInAt > 0);
  const updates: Record<string, unknown> = {};

  // Quên check-out hôm trước: đóng lúc 23:59 (giờ VN) của ngày đó.
  for (const c of open.filter((c) => c.checkInAt < todayStart)) {
    updates[`checkIns/${c.id}/checkOutAt`] = endOfVnDayMillis(c.checkInAt);
  }

  const openToday = open.filter((c) => c.checkInAt >= todayStart);
  if (openToday.length > 0) {
    for (const c of openToday) updates[`checkIns/${c.id}/checkOutAt`] = nowMillis;
    return {
      result: 'checked_out',
      checkInAt: Math.min(...openToday.map((c) => c.checkInAt)),
      checkOutAt: nowMillis,
      updates
    };
  }

  const today = vnDay(nowMillis);
  let best: Membership | null = null;
  let bestEnd = -Infinity;
  for (const m of memberships) {
    if (m.status !== MembershipStatus.ACTIVE) continue;
    const start = parseDay(m.startDate);
    const end = parseDay(m.endDate);
    if (start === null || end === null || today < start || today > end) continue;
    if (end > bestEnd) {
      best = m;
      bestEnd = end;
    }
  }

  if (!best) return { result: 'no_active_membership', updates };

  updates[`checkIns/${newCheckInId()}`] = { uid, checkInAt: nowMillis, checkOutAt: 0 };
  return { result: 'checked_in', at: nowMillis, membership: best, updates };
}
