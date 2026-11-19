/**
 * Bản ghi activityLogs (giống ActivityLogRepository trong app):
 * title = "<tên người làm> <mô tả>", admin có tiền tố "Admin". Thao tác tự động của server dùng
 * actorUid = "system" và tên "Hệ thống".
 */
export const SYSTEM_ACTOR_UID = 'system';
export const SYSTEM_ACTOR_NAME = 'Hệ thống';

export type LogAction = 'payment' | 'check_in' | 'membership';

export interface ActivityLogEntry {
  title: string;
  actorUid: string;
  action: LogAction;
  createdAt: string;
}

/** Tên hiển thị của người thực hiện, giống ActivityLogRepository.resolveActorName(). */
export function actorDisplayName(user: { fullName?: unknown; email?: unknown; role?: unknown } | null): string {
  const fullName = typeof user?.fullName === 'string' ? user.fullName.trim() : '';
  const email = typeof user?.email === 'string' ? user.email : '';
  const name = fullName || email.split('@')[0] || 'Người dùng';
  if (user?.role !== 'admin') return name;
  return name.toLowerCase().startsWith('admin') ? name : `Admin ${name}`;
}

export function buildLog(actorUid: string, actorName: string, action: LogAction, description: string, nowStamp: string): ActivityLogEntry {
  return { title: `${actorName} ${description}`, actorUid, action, createdAt: nowStamp };
}

export const RECONCILIATION_REASON_TEXT: Record<string, string> = {
  no_code: 'không có mã hoá đơn',
  invoice_not_found: 'không tìm thấy hoá đơn',
  invoice_not_pending: 'hoá đơn không còn chờ thanh toán',
  invoice_expired: 'hoá đơn đã hết hạn',
  underpaid: 'chuyển thiếu tiền',
  overpaid: 'chuyển dư tiền',
  activation_error: 'lỗi khi kích hoạt gói'
};
