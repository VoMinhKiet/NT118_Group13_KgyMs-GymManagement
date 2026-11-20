/**
 * Logic kích hoạt gói khi hoá đơn được thanh toán, port từ PaymentRepository.paidUpdates() (app Android).
 * Hàm thuần: không gọi Firebase, chỉ tính toán và trả về các đường dẫn cần ghi.
 */
import { addDays, formatDay, parseDay, type DayValue } from './dates.js';
import { formatVnd } from './paymentCode.js';
import { MembershipStatus, type GymPackage, type Membership, type Payment } from './models.js';

export interface Period {
  start: DayValue;
  end: DayValue;
}

/**
 * Lấy endDate muộn nhất (≥ hôm nay) của các membership **khác** đang `active` cùng `package.type`;
 * start = ngày sau đó, không có thì hôm nay; end = start + max(durationDays, 1) - 1.
 */
export function computePeriod(args: {
  membershipId: string;
  pkg: GymPackage;
  userMemberships: Membership[];
  packages: Map<string, GymPackage>;
  today: DayValue;
}): Period {
  const { membershipId, pkg, userMemberships, packages, today } = args;
  let latestEnd: DayValue | null = null;
  for (const m of userMemberships) {
    if (m.id === membershipId || m.status !== MembershipStatus.ACTIVE) continue;
    if (packages.get(m.packageId)?.type !== pkg.type) continue;
    const end = parseDay(m.endDate);
    if (end === null || end < today) continue;
    if (latestEnd === null || end > latestEnd) latestEnd = end;
  }
  const start = latestEnd === null ? today : addDays(latestEnd, 1);
  const end = addDays(start, Math.max(Math.trunc(pkg.durationDays), 1) - 1);
  return { start, end };
}

export function formatPeriod(period: Period): string {
  return `${formatDay(period.start)} – ${formatDay(period.end)}`;
}

export function paidNotificationMessage(payment: Payment, packageName: string, period: Period | null): string {
  let message = `KgyMs đã nhận ${formatVnd(payment.amount)} cho hoá đơn ${payment.id} (${packageName}).`;
  if (period) message += ` Thời hạn: ${formatPeriod(period)}.`;
  return message;
}

/**
 * Các đường dẫn ghi trong **một** multi-path update sau khi đã chuyển payments/{id}/status sang `paid`
 * bằng transaction (status không nằm ở đây).
 */
export function buildActivationUpdates(args: {
  payment: Payment;
  membership: Membership | null;
  pkg: GymPackage | null;
  period: Period | null;
  nowStamp: string;
  paidVia: 'sepay' | 'admin';
  bankTransactionId: string | null;
  notificationId: string;
}): Record<string, unknown> {
  const { payment, membership, pkg, period, nowStamp, paidVia, bankTransactionId, notificationId } = args;
  const updates: Record<string, unknown> = {
    [`payments/${payment.id}/paidAt`]: nowStamp,
    [`payments/${payment.id}/paidVia`]: paidVia
  };
  if (bankTransactionId) {
    updates[`payments/${payment.id}/bankTransactionId`] = bankTransactionId;
    updates[`bankTransactions/${bankTransactionId}/status`] = 'matched';
    updates[`bankTransactions/${bankTransactionId}/paymentId`] = payment.id;
  }
  if (membership && pkg && period) {
    updates[`memberships/${membership.id}/status`] = MembershipStatus.ACTIVE;
    updates[`memberships/${membership.id}/startDate`] = formatDay(period.start);
    updates[`memberships/${membership.id}/endDate`] = formatDay(period.end);
  }
  updates[`notifications/${payment.uid}/${notificationId}`] = {
    title: 'Thanh toán thành công',
    message: paidNotificationMessage(payment, pkg?.name || 'gói tập', membership && pkg ? period : null),
    type: 'general',
    isRead: false,
    createdAt: nowStamp
  };
  return updates;
}
