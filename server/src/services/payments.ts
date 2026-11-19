/** Hội viên tạo / huỷ hoá đơn chuyển khoản (port PaymentRepository.startPurchase / cancelPayment). */
import { timestamp } from '../domain/dates.js';
import {
  entries,
  isExpired,
  MembershipStatus,
  PaymentStatus,
  toMembership,
  toPackage,
  toPayment
} from '../domain/models.js';
import { generatePaymentId } from '../domain/paymentCode.js';
import { badRequest } from '../errors.js';
import { writeLog } from './activityLog.js';
import type { Ctx } from './context.js';

export interface InvoiceResponse {
  paymentId: string;
  amount: number;
  /** millis; null với hoá đơn cũ không có hạn. */
  expiresAt: number | null;
  packageName: string;
}

export async function createInvoice(ctx: Ctx, uid: string, packageId: unknown): Promise<InvoiceResponse> {
  const { db } = ctx;
  if (typeof packageId !== 'string' || !/^[A-Za-z0-9_-]{1,100}$/.test(packageId)) {
    throw badRequest('invalid_package_id', 'Mã gói không hợp lệ.');
  }
  const nowMillis = ctx.now();

  const payments = entries(await db.queryByChild('payments', 'uid', uid), toPayment);
  const memberships = entries(await db.queryByChild('memberships', 'uid', uid), toMembership);
  const membershipById = new Map(memberships.map((m) => [m.id, m]));

  // 1. Huỷ các hoá đơn pending đã quá hạn của người này (hoá đơn + membership).
  const expired = payments.filter((p) => p.status === PaymentStatus.PENDING && isExpired(p, nowMillis));
  if (expired.length > 0) {
    const updates: Record<string, unknown> = {};
    for (const p of expired) {
      updates[`payments/${p.id}/status`] = PaymentStatus.CANCELLED;
      if (membershipById.get(p.membershipId)?.status === MembershipStatus.PENDING) {
        updates[`memberships/${p.membershipId}/status`] = MembershipStatus.CANCELLED;
      }
    }
    await db.update(updates);
  }

  // 2. Kiểm tra gói.
  const rawPackage = await db.get(`packages/${packageId}`);
  if (!rawPackage || typeof rawPackage !== 'object') throw badRequest('package_not_found', 'Không tìm thấy gói tập.');
  const pkg = toPackage(packageId, rawPackage as Record<string, unknown>);
  if (!pkg.isActive) throw badRequest('package_inactive', 'Gói này đã ngừng bán.');
  if (!(pkg.price > 0)) throw badRequest('package_no_price', 'Gói chưa có giá, vui lòng liên hệ quầy.');

  // 3. Đã có hoá đơn pending còn hạn của đúng gói này ⇒ trả lại.
  const existing = payments.find(
    (p) =>
      p.status === PaymentStatus.PENDING &&
      !isExpired(p, nowMillis) &&
      membershipById.get(p.membershipId)?.status === MembershipStatus.PENDING &&
      membershipById.get(p.membershipId)?.packageId === pkg.id
  );
  if (existing) {
    return { paymentId: existing.id, amount: existing.amount, expiresAt: existing.expiresAt, packageName: pkg.name };
  }

  // 4. Tạo membership + hoá đơn trong một lần ghi; amount lấy từ giá gói trên Firebase.
  const paymentId = await uniquePaymentId(ctx);
  const membershipId = db.pushKey('memberships');
  const now = timestamp(nowMillis);
  const expiresAt = nowMillis + ctx.config.paymentExpiryMinutes * 60_000;
  await db.update({
    [`memberships/${membershipId}`]: {
      uid,
      packageId: pkg.id,
      trainerUid: pkg.trainerUid,
      status: MembershipStatus.PENDING,
      startDate: '',
      endDate: '',
      sessionsUsed: 0,
      createdAt: now
    },
    [`payments/${paymentId}`]: {
      uid,
      membershipId,
      amount: pkg.price,
      discount: 0,
      method: 'transfer',
      status: PaymentStatus.PENDING,
      createdAt: now,
      paidAt: '',
      expiresAt
    }
  });

  await writeLog(ctx, uid, 'payment', `tạo hoá đơn ${paymentId} cho gói "${pkg.name}"`);
  return { paymentId, amount: pkg.price, expiresAt, packageName: pkg.name };
}

async function uniquePaymentId(ctx: Ctx): Promise<string> {
  for (let attempt = 0; attempt < 5; attempt++) {
    const id = generatePaymentId(ctx.randomInt);
    if ((await ctx.db.get(`payments/${id}`)) === null) return id;
  }
  throw new Error('Không sinh được mã hoá đơn chưa tồn tại sau 5 lần thử.');
}

export async function cancelInvoice(ctx: Ctx, uid: string, paymentId: string): Promise<void> {
  const raw = await ctx.db.get(`payments/${paymentId}`);
  if (!raw || typeof raw !== 'object') throw badRequest('payment_not_found', 'Không tìm thấy hoá đơn.');
  const payment = toPayment(paymentId, raw as Record<string, unknown>);
  if (payment.uid !== uid) throw badRequest('payment_not_owned', 'Hoá đơn này không phải của bạn.');
  if (payment.status !== PaymentStatus.PENDING) {
    throw badRequest('payment_not_pending', 'Chỉ huỷ được hoá đơn đang chờ thanh toán.');
  }

  const updates: Record<string, unknown> = { [`payments/${paymentId}/status`]: PaymentStatus.CANCELLED };
  if (payment.membershipId) {
    const membership = await ctx.db.get(`memberships/${payment.membershipId}`);
    if (membership && typeof membership === 'object' && (membership as Record<string, unknown>).status === MembershipStatus.PENDING) {
      updates[`memberships/${payment.membershipId}/status`] = MembershipStatus.CANCELLED;
    }
  }

  // Chặn trường hợp webhook vừa kích hoạt hoá đơn này: chỉ huỷ khi status vẫn là pending.
  const tx = await ctx.db.transaction(`payments/${paymentId}/status`, (current) => {
    if (current === null || current === undefined) return null;
    return current === PaymentStatus.PENDING ? PaymentStatus.CANCELLED : undefined;
  });
  if (!tx.committed || tx.value !== PaymentStatus.CANCELLED) {
    throw badRequest('payment_not_pending', 'Chỉ huỷ được hoá đơn đang chờ thanh toán.');
  }
  delete updates[`payments/${paymentId}/status`];
  if (Object.keys(updates).length > 0) await ctx.db.update(updates);

  await writeLog(ctx, uid, 'payment', `huỷ hoá đơn ${paymentId}`);
}
