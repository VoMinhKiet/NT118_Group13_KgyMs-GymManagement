/**
 * Kích hoạt gói khi hoá đơn được thanh toán (port PaymentRepository.paidUpdates()):
 * 1. Transaction trên payments/{id}/status: chỉ chuyển sang `paid` từ trạng thái cho phép.
 * 2. Tính ngày bắt đầu/kết thúc (nối tiếp gói cùng loại đang chạy).
 * 3. Một multi-path update: hoá đơn, membership, thông báo, bankTransactions.
 * Bước 3 lỗi sau khi bước 1 thành công ⇒ ném [ActivationError] để nơi gọi ghi đối soát.
 */
import { computePeriod, formatPeriod, type Period } from '../domain/activation.js';
import { buildActivationUpdates } from '../domain/activation.js';
import { timestamp, vnDay } from '../domain/dates.js';
import { entries, PaymentStatus, toMembership, toPackage, toPayment, type GymPackage, type Payment } from '../domain/models.js';
import type { Ctx } from './context.js';

export class ActivationError extends Error {
  constructor(
    readonly paymentId: string,
    readonly cause: unknown
  ) {
    super(`Kích hoạt hoá đơn ${paymentId} thất bại sau khi đã đổi trạng thái: ${(cause as Error)?.message ?? cause}`);
  }
}

export type ActivationResult =
  | { ok: true; payment: Payment; packageName: string; period: Period | null }
  | { ok: false; reason: 'not_found' | 'not_pending' };

export async function activatePayment(
  ctx: Ctx,
  args: {
    paymentId: string;
    paidVia: 'sepay' | 'admin';
    bankTransactionId: string | null;
    /**
     * Admin được kích hoạt cả hoá đơn đã bị huỷ (vd tự huỷ vì quá hạn nhưng khách vẫn chuyển tiền),
     * và hoá đơn `paid` nhưng chưa có paidAt (lần kích hoạt trước bị lỗi giữa chừng).
     */
    allowRecovery?: boolean;
    /** Ghi thêm vào cùng multi-path update (vd đóng đối soát). */
    extraUpdates?: Record<string, unknown>;
  }
): Promise<ActivationResult> {
  const { db } = ctx;
  const { paymentId, paidVia, bankTransactionId, allowRecovery = false, extraUpdates = {} } = args;

  const rawPayment = await db.get(`payments/${paymentId}`);
  if (!rawPayment || typeof rawPayment !== 'object') return { ok: false, reason: 'not_found' };
  const payment = toPayment(paymentId, rawPayment as Record<string, unknown>);

  const allowed = new Set<string>([PaymentStatus.PENDING]);
  if (allowRecovery) allowed.add(PaymentStatus.CANCELLED);
  const incompletePaid = allowRecovery && payment.status === PaymentStatus.PAID && payment.paidAt === '';
  if (!allowed.has(payment.status) && !incompletePaid) return { ok: false, reason: 'not_pending' };

  // Đọc trước dữ liệu cần để tính ngày (đọc trước transaction để transaction ngắn nhất có thể).
  const membershipRaw = payment.membershipId ? await db.get(`memberships/${payment.membershipId}`) : null;
  const membership =
    membershipRaw && typeof membershipRaw === 'object'
      ? toMembership(payment.membershipId, membershipRaw as Record<string, unknown>)
      : null;
  const packages = new Map<string, GymPackage>(entries(await db.get('packages'), toPackage).map((p) => [p.id, p]));
  const pkg = membership ? packages.get(membership.packageId) ?? null : null;
  const userMemberships = entries(await db.queryByChild('memberships', 'uid', payment.uid), toMembership);

  // Bước 1: chỉ một giao dịch được chuyển hoá đơn sang `paid`.
  const tx = await db.transaction(`payments/${paymentId}/status`, (current) => {
    if (current === null || current === undefined) return null; // chưa có cache: để Firebase chạy lại với giá trị thật
    if (typeof current === 'string' && allowed.has(current)) return PaymentStatus.PAID;
    if (incompletePaid && current === PaymentStatus.PAID) return PaymentStatus.PAID;
    return undefined;
  });
  if (!tx.committed || tx.value !== PaymentStatus.PAID) {
    return { ok: false, reason: tx.value === null ? 'not_found' : 'not_pending' };
  }

  const nowMillis = ctx.now();
  const period = membership && pkg
    ? computePeriod({ membershipId: membership.id, pkg, userMemberships, packages, today: vnDay(nowMillis) })
    : null;

  try {
    const updates = buildActivationUpdates({
      payment,
      membership,
      pkg,
      period,
      nowStamp: timestamp(nowMillis),
      paidVia,
      bankTransactionId,
      notificationId: db.pushKey(`notifications/${payment.uid}`)
    });
    await db.update({ ...updates, ...extraUpdates });
  } catch (error) {
    throw new ActivationError(paymentId, error);
  }

  return { ok: true, payment, packageName: pkg?.name || 'gói tập', period };
}

export const describePeriod = (period: Period | null) => (period ? formatPeriod(period) : '');
