/** Kiểu dữ liệu các node Firebase mà server dùng (khớp FIREBASE_STRUCTURE.md và app Android). */

export const PaymentStatus = { PENDING: 'pending', PAID: 'paid', CANCELLED: 'cancelled' } as const;
export const MembershipStatus = {
  PENDING: 'pending',
  ACTIVE: 'active',
  FROZEN: 'frozen',
  EXPIRED: 'expired',
  CANCELLED: 'cancelled'
} as const;

export interface GymPackage {
  id: string;
  name: string;
  type: string;
  price: number;
  durationDays: number;
  trainerUid: string;
  isActive: boolean;
}

export interface Payment {
  id: string;
  uid: string;
  membershipId: string;
  amount: number;
  discount: number;
  method: string;
  status: string;
  createdAt: string;
  paidAt: string;
  /** millis; null với hoá đơn cũ (coi như không hết hạn). */
  expiresAt: number | null;
}

export interface Membership {
  id: string;
  uid: string;
  packageId: string;
  trainerUid: string;
  status: string;
  startDate: string;
  endDate: string;
}

export interface CheckIn {
  id: string;
  uid: string;
  checkInAt: number;
  checkOutAt: number;
}

export type ReconciliationReason =
  | 'no_code'
  | 'invoice_not_found'
  | 'invoice_not_pending'
  | 'invoice_expired'
  | 'underpaid'
  | 'overpaid'
  | 'activation_error';

/* ------------------------- Đọc dữ liệu thô từ Firebase ------------------------- */

type Raw = Record<string, unknown> | null | undefined;

const text = (raw: Raw, key: string) => {
  const value = raw?.[key];
  return value === undefined || value === null ? '' : String(value);
};
const num = (raw: Raw, key: string) => {
  const value = raw?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
};

export function toPackage(id: string, raw: Raw): GymPackage {
  return {
    id,
    name: text(raw, 'name'),
    type: text(raw, 'type') || 'membership',
    price: num(raw, 'price'),
    durationDays: num(raw, 'durationDays'),
    trainerUid: text(raw, 'trainerUid'),
    isActive: raw?.isActive === true
  };
}

export function toPayment(id: string, raw: Raw): Payment {
  const expiresAt = raw?.expiresAt;
  return {
    id,
    uid: text(raw, 'uid'),
    membershipId: text(raw, 'membershipId'),
    amount: num(raw, 'amount'),
    discount: num(raw, 'discount'),
    method: text(raw, 'method') || 'transfer',
    status: text(raw, 'status') || PaymentStatus.PENDING,
    createdAt: text(raw, 'createdAt'),
    paidAt: text(raw, 'paidAt'),
    expiresAt: typeof expiresAt === 'number' && expiresAt > 0 ? expiresAt : null
  };
}

export function toMembership(id: string, raw: Raw): Membership {
  return {
    id,
    uid: text(raw, 'uid'),
    packageId: text(raw, 'packageId'),
    trainerUid: text(raw, 'trainerUid'),
    status: text(raw, 'status') || MembershipStatus.PENDING,
    startDate: text(raw, 'startDate'),
    endDate: text(raw, 'endDate')
  };
}

export function toCheckIn(id: string, raw: Raw): CheckIn {
  return { id, uid: text(raw, 'uid'), checkInAt: num(raw, 'checkInAt'), checkOutAt: num(raw, 'checkOutAt') };
}

/** Chuyển node {key: value} thành danh sách, bỏ key bắt đầu bằng "_". */
export function entries<T>(node: unknown, map: (id: string, raw: Raw) => T): T[] {
  if (!node || typeof node !== 'object') return [];
  return Object.entries(node as Record<string, unknown>)
    .filter(([key, value]) => !key.startsWith('_') && value && typeof value === 'object')
    .map(([key, value]) => map(key, value as Raw));
}

/** Hoá đơn hết hạn: chỉ khi có expiresAt và đã qua. Hoá đơn cũ không có expiresAt thì không bao giờ hết hạn. */
export function isExpired(payment: Payment, nowMillis: number): boolean {
  return payment.expiresAt !== null && nowMillis > payment.expiresAt;
}
