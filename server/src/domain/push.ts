/**
 * Phân loại thông báo để push, khớp với cách app lọc hộp thư (NotificationInboxPreferences):
 * - reminder              → "reminders"  (nhắc hết hạn...)
 * - promotion             → "gym"        (tin phòng tập, ưu đãi)
 * - general + broadcastId → "gym"        (admin gửi thông báo chung)
 * - còn lại (thanh toán, tài khoản...)  → "transactions"
 * App đối chiếu category với công tắc người dùng chọn trong Cài đặt → Thông báo.
 */
export type PushCategory = 'gym' | 'reminders' | 'transactions';

export interface StoredNotification {
  title: string;
  message: string;
  type: string;
  broadcastId: string;
}

export function categoryOf(n: Pick<StoredNotification, 'type' | 'broadcastId'>): PushCategory {
  if (n.type === 'reminder') return 'reminders';
  if (n.type === 'promotion') return 'gym';
  if (n.type === 'general' && n.broadcastId !== '') return 'gym';
  return 'transactions';
}

/** Đọc một node notifications/{uid}/{id}; null nếu thiếu tiêu đề/nội dung. */
export function toStoredNotification(raw: unknown): StoredNotification | null {
  if (!raw || typeof raw !== 'object') return null;
  const r = raw as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === 'string' ? v : '');
  const title = str(r.title).trim();
  const message = str(r.message).trim();
  if (!title || !message) return null;
  return { title, message, type: str(r.type) || 'general', broadcastId: str(r.broadcastId) };
}

/** Payload FCM kiểu data-only (mọi giá trị là chuỗi): app tự quyết định có hiện hay không. */
export function pushData(uid: string, notificationId: string, n: StoredNotification): Record<string, string> {
  return {
    uid,
    notificationId,
    title: n.title.slice(0, 200),
    message: n.message.slice(0, 1000),
    category: categoryOf(n)
  };
}

/** FCM token chỉ gồm chữ, số, ':', '_', '-' (an toàn để làm key Realtime Database). */
export const FCM_TOKEN = /^[A-Za-z0-9:_-]{20,512}$/;
