/**
 * Xử lý webhook SePay (tiền vào tài khoản). Thứ tự theo to-do mục 5:
 * lọc → chống trùng (transaction trên bankTransactions/{id}) → tách mã → kiểm tra hoá đơn → so tiền
 * → kích hoạt; mọi trường hợp lệch ⇒ ghi đối soát và vẫn trả 200 để SePay không gửi lại.
 * Lỗi hệ thống bất ngờ sau khi đã tạo bankTransactions ⇒ xoá bản ghi đó và ném lỗi (route trả 500).
 */
import { timestamp } from '../domain/dates.js';
import { RECONCILIATION_REASON_TEXT } from '../domain/log.js';
import { isExpired, PaymentStatus, toPayment, type ReconciliationReason } from '../domain/models.js';
import { checkAmount, extractPaymentCode } from '../domain/paymentCode.js';
import { activatePayment, ActivationError } from './activation.js';
import { systemLog } from './activityLog.js';
import type { Ctx } from './context.js';

/** Các trường SePay gửi (docs.sepay.vn/tich-hop-webhooks.html). */
export interface SepayPayload {
  id: number | string;
  gateway?: string;
  transactionDate?: string;
  accountNumber?: string;
  subAccount?: string | null;
  code?: string | null;
  content?: string;
  transferType?: string;
  description?: string;
  transferAmount?: number;
  accumulated?: number;
  referenceCode?: string;
}

export type WebhookOutcome =
  | { kind: 'invalid'; message: string }
  | { kind: 'ignored'; why: 'not_incoming' | 'other_account' }
  | { kind: 'duplicate' }
  | { kind: 'reconciliation'; reason: ReconciliationReason; paymentId: string }
  | { kind: 'matched'; paymentId: string };

export async function handleSepayWebhook(ctx: Ctx, body: unknown): Promise<WebhookOutcome> {
  if (!body || typeof body !== 'object') return { kind: 'invalid', message: 'Body không phải JSON object.' };
  const payload = body as SepayPayload;

  const txId = String(payload.id ?? '').trim();
  if (!/^[0-9A-Za-z_-]{1,64}$/.test(txId)) return { kind: 'invalid', message: 'Thiếu hoặc sai trường id.' };

  // Bước 2: chỉ xử lý tiền vào đúng tài khoản.
  if (payload.transferType !== 'in') return { kind: 'ignored', why: 'not_incoming' };
  if (String(payload.accountNumber ?? '').trim() !== ctx.config.bankAccountNo) return { kind: 'ignored', why: 'other_account' };

  const amount = Number(payload.transferAmount);
  if (!Number.isFinite(amount) || amount < 0) return { kind: 'invalid', message: 'Sai trường transferAmount.' };
  const content = typeof payload.content === 'string' ? payload.content : '';
  const transactionDate = typeof payload.transactionDate === 'string' ? payload.transactionDate : '';
  const nowStamp = timestamp(ctx.now());

  // Bước 3: chống trùng.
  const created = await ctx.db.transaction(`bankTransactions/${txId}`, (current) => {
    if (current !== null && current !== undefined) return undefined;
    return {
      amount,
      content,
      referenceCode: typeof payload.referenceCode === 'string' ? payload.referenceCode : '',
      transactionDate,
      receivedAt: nowStamp,
      status: 'processing',
      paymentId: ''
    };
  });
  if (!created.committed) return { kind: 'duplicate' };

  try {
    return await process(ctx, { txId, amount, content, transactionDate });
  } catch (error) {
    // Bước 9: lỗi bất ngờ ⇒ xoá để lần gửi lại của SePay được xử lý từ đầu.
    try {
      await ctx.db.remove(`bankTransactions/${txId}`);
    } catch (removeError) {
      ctx.logger.error(`[webhook] Không xoá được bankTransactions/${txId}: ${(removeError as Error).message}`);
    }
    throw error;
  }
}

async function process(
  ctx: Ctx,
  tx: { txId: string; amount: number; content: string; transactionDate: string }
): Promise<WebhookOutcome> {
  const reconcile = (reason: ReconciliationReason, paymentId = '', expectedAmount = 0) =>
    recordReconciliation(ctx, { ...tx, reason, paymentId, expectedAmount });

  // Bước 4: tách mã.
  const paymentId = extractPaymentCode(tx.content);
  if (!paymentId) return reconcile('no_code');

  // Bước 5: kiểm tra hoá đơn.
  const raw = await ctx.db.get(`payments/${paymentId}`);
  if (!raw || typeof raw !== 'object') return reconcile('invoice_not_found', paymentId);
  const payment = toPayment(paymentId, raw as Record<string, unknown>);
  if (payment.status !== PaymentStatus.PENDING) return reconcile('invoice_not_pending', paymentId, payment.amount);
  if (isExpired(payment, ctx.now())) return reconcile('invoice_expired', paymentId, payment.amount);

  // Bước 6: so tiền.
  const amountCheck = checkAmount(tx.amount, payment.amount);
  if (amountCheck !== 'ok') return reconcile(amountCheck, paymentId, payment.amount);

  // Bước 7: kích hoạt.
  let result;
  try {
    result = await activatePayment(ctx, { paymentId, paidVia: 'sepay', bankTransactionId: tx.txId });
  } catch (error) {
    if (error instanceof ActivationError) {
      ctx.logger.error(`[webhook] ${error.message}`);
      return reconcile('activation_error', paymentId, payment.amount);
    }
    throw error;
  }
  if (!result.ok) {
    return reconcile(result.reason === 'not_found' ? 'invoice_not_found' : 'invoice_not_pending', paymentId, payment.amount);
  }

  await systemLog(ctx, 'payment', `xác nhận thanh toán hoá đơn ${paymentId} (${result.packageName})`);
  ctx.logger.info(`[webhook] Giao dịch ${tx.txId} khớp hoá đơn ${paymentId}.`);
  return { kind: 'matched', paymentId };
}

/** Bước 8: ghi reconciliation/{id} (open) + bankTransactions/{id}/status = "reconciliation" + log. */
async function recordReconciliation(
  ctx: Ctx,
  args: {
    txId: string;
    amount: number;
    content: string;
    transactionDate: string;
    reason: ReconciliationReason;
    paymentId: string;
    expectedAmount: number;
  }
): Promise<WebhookOutcome> {
  const { txId, reason, paymentId } = args;
  await ctx.db.update({
    [`reconciliation/${txId}`]: {
      reason,
      amount: args.amount,
      content: args.content,
      paymentId,
      expectedAmount: args.expectedAmount,
      transactionDate: args.transactionDate,
      createdAt: timestamp(ctx.now()),
      status: 'open',
      resolution: '',
      resolvedBy: '',
      resolvedAt: '',
      note: ''
    },
    [`bankTransactions/${txId}/status`]: 'reconciliation',
    [`bankTransactions/${txId}/paymentId`]: paymentId
  });
  await systemLog(
    ctx,
    'payment',
    `chuyển giao dịch ngân hàng ${txId} sang đối soát (${RECONCILIATION_REASON_TEXT[reason]}${paymentId ? `, hoá đơn ${paymentId}` : ''})`
  );
  ctx.logger.warn(`[webhook] Giao dịch ${txId} cần đối soát: ${reason}.`);
  return { kind: 'reconciliation', reason, paymentId };
}
