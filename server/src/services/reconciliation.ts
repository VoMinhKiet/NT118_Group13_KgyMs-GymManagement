/** Admin xử lý một giao dịch đang đối soát: kích hoạt hoá đơn, đã hoàn tiền, hoặc bỏ qua. */
import { timestamp } from '../domain/dates.js';
import { badRequest, HttpError } from '../errors.js';
import { activatePayment, ActivationError } from './activation.js';
import { writeLog } from './activityLog.js';
import type { Ctx } from './context.js';

const ACTIONS = ['activate', 'refunded', 'ignored'] as const;
type ResolveAction = (typeof ACTIONS)[number];

const KEY = /^[A-Za-z0-9_-]{1,64}$/;

export async function resolveReconciliation(
  ctx: Ctx,
  adminUid: string,
  reconciliationId: string,
  body: unknown
): Promise<{ status: 'resolved'; resolution: ResolveAction; paymentId: string }> {
  const input = (body && typeof body === 'object' ? body : {}) as Record<string, unknown>;
  const action = input.action as ResolveAction;
  if (!ACTIONS.includes(action)) {
    throw badRequest('invalid_action', 'action phải là "activate", "refunded" hoặc "ignored".');
  }
  const note = typeof input.note === 'string' ? input.note.trim().slice(0, 500) : '';
  if (input.note !== undefined && typeof input.note !== 'string') throw badRequest('invalid_note', 'note phải là chuỗi.');

  const record = (await ctx.db.get(`reconciliation/${reconciliationId}`)) as Record<string, unknown> | null;
  if (!record || typeof record !== 'object') {
    throw new HttpError(404, 'reconciliation_not_found', 'Không tìm thấy giao dịch đối soát.');
  }
  if (record.status !== 'open') throw badRequest('reconciliation_resolved', 'Giao dịch này đã được xử lý.');

  const resolvedFields = {
    [`reconciliation/${reconciliationId}/status`]: 'resolved',
    [`reconciliation/${reconciliationId}/resolution`]: action,
    [`reconciliation/${reconciliationId}/resolvedBy`]: adminUid,
    [`reconciliation/${reconciliationId}/resolvedAt`]: timestamp(ctx.now()),
    [`reconciliation/${reconciliationId}/note`]: note
  };

  if (action !== 'activate') {
    await ctx.db.update(resolvedFields);
    const label = action === 'refunded' ? 'đã hoàn tiền' : 'bỏ qua';
    await writeLog(ctx, adminUid, 'payment', `xử lý đối soát giao dịch ${reconciliationId}: ${label}`);
    return { status: 'resolved', resolution: action, paymentId: String(record.paymentId ?? '') };
  }

  const paymentId = (typeof input.paymentId === 'string' && input.paymentId.trim()) || String(record.paymentId ?? '');
  if (!paymentId) throw badRequest('payment_id_required', 'Cần paymentId để kích hoạt.');
  if (!KEY.test(paymentId)) throw badRequest('invalid_payment_id', 'paymentId không hợp lệ.');

  let result;
  try {
    result = await activatePayment(ctx, {
      paymentId,
      paidVia: 'admin',
      bankTransactionId: reconciliationId,
      allowRecovery: true,
      extraUpdates: {
        ...resolvedFields,
        [`reconciliation/${reconciliationId}/paymentId`]: paymentId
      }
    });
  } catch (error) {
    if (error instanceof ActivationError) {
      ctx.logger.error(`[reconciliation] ${error.message}`);
      throw new HttpError(
        500,
        'activation_error',
        'Hoá đơn đã chuyển sang "đã thanh toán" nhưng ghi kích hoạt gói bị lỗi. Hãy thử lại thao tác này.'
      );
    }
    throw error;
  }
  if (!result.ok) {
    throw result.reason === 'not_found'
      ? badRequest('payment_not_found', `Không tìm thấy hoá đơn ${paymentId}.`)
      : badRequest('payment_not_pending', `Hoá đơn ${paymentId} đã được thanh toán.`);
  }

  await writeLog(ctx, adminUid, 'payment', `kích hoạt hoá đơn ${paymentId} (${result.packageName}) từ đối soát giao dịch ${reconciliationId}`);
  return { status: 'resolved', resolution: 'activate', paymentId };
}
