/**
 * Push FCM. Server là nơi duy nhất đọc/ghi fcmTokens (rules mặc định chặn app truy cập):
 * - fcmTokens/{uid}/{token}  = thời điểm đăng ký (millis)
 * - fcmTokenOwners/{token}   = uid đang giữ token (một máy đổi tài khoản thì token chuyển chủ)
 * Push chỉ gửi cho thông báo ĐÃ có trong notifications/{uid}/{id}; gửi xong ghi pushedAt để không gửi trùng.
 */
import { pushData, toStoredNotification, FCM_TOKEN } from '../domain/push.js';
import { badRequest } from '../errors.js';
import type { Ctx } from './context.js';

export interface PushSender {
  /** Gửi data message tới [tokens]; trả về các token FCM báo không còn hợp lệ. */
  send(tokens: string[], data: Record<string, string>): Promise<{ invalidTokens: string[] }>;
}

export interface PushTarget {
  uid: string;
  id: string;
}

export async function registerToken(ctx: Ctx, uid: string, token: unknown): Promise<void> {
  if (typeof token !== 'string' || !FCM_TOKEN.test(token)) throw badRequest('invalid_token', 'FCM token không hợp lệ.');
  const previousOwner = await ctx.db.get(`fcmTokenOwners/${token}`);
  const updates: Record<string, unknown> = {
    [`fcmTokens/${uid}/${token}`]: ctx.now(),
    [`fcmTokenOwners/${token}`]: uid
  };
  if (typeof previousOwner === 'string' && previousOwner !== uid) updates[`fcmTokens/${previousOwner}/${token}`] = null;
  await ctx.db.update(updates);
}

export async function unregisterToken(ctx: Ctx, uid: string, token: unknown): Promise<void> {
  if (typeof token !== 'string' || !FCM_TOKEN.test(token)) throw badRequest('invalid_token', 'FCM token không hợp lệ.');
  const owner = await ctx.db.get(`fcmTokenOwners/${token}`);
  const updates: Record<string, unknown> = { [`fcmTokens/${uid}/${token}`]: null };
  if (owner === uid) updates[`fcmTokenOwners/${token}`] = null;
  await ctx.db.update(updates);
}

/** Gửi push cho các thông báo đã lưu. Trả số thông báo đã gửi tới ít nhất một thiết bị. */
export async function pushStoredNotifications(ctx: Ctx, targets: PushTarget[]): Promise<{ sent: number; skipped: number }> {
  let sent = 0;
  let skipped = 0;
  const CONCURRENCY = 25;
  for (let i = 0; i < targets.length; i += CONCURRENCY) {
    const results = await Promise.all(targets.slice(i, i + CONCURRENCY).map((target) => pushOne(ctx, target)));
    for (const delivered of results) delivered ? sent++ : skipped++;
  }
  return { sent, skipped };
}

/** Như [pushStoredNotifications] nhưng không bao giờ ném lỗi: push hỏng không được làm hỏng nghiệp vụ chính. */
export async function pushSafely(ctx: Ctx, targets: PushTarget[]): Promise<void> {
  try {
    await pushStoredNotifications(ctx, targets);
  } catch (error) {
    ctx.logger.error(`[push] ${(error as Error)?.message ?? error}`);
  }
}

async function pushOne(ctx: Ctx, { uid, id }: PushTarget): Promise<boolean> {
  const raw = await ctx.db.get(`notifications/${uid}/${id}`);
  const notification = toStoredNotification(raw);
  if (!notification) return false;
  if ((raw as Record<string, unknown>).pushedAt !== undefined) return false; // đã push rồi

  const stored = await ctx.db.get(`fcmTokens/${uid}`);
  const tokens = stored && typeof stored === 'object' ? Object.keys(stored as Record<string, unknown>) : [];
  const updates: Record<string, unknown> = { [`notifications/${uid}/${id}/pushedAt`]: ctx.now() };
  let delivered = false;

  if (tokens.length > 0) {
    const { invalidTokens } = await ctx.push.send(tokens, pushData(uid, id, notification));
    for (const token of invalidTokens) {
      updates[`fcmTokens/${uid}/${token}`] = null;
      updates[`fcmTokenOwners/${token}`] = null;
    }
    delivered = invalidTokens.length < tokens.length;
  }
  await ctx.db.update(updates);
  return delivered;
}
