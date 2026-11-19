import type { AuthVerifier } from '../src/db.js';
import { createApp } from '../src/createApp.js';
import type { Ctx } from '../src/services/context.js';
import { MemoryDb } from './memoryDb.js';

export const API_KEY = 'k'.repeat(64);
export const CHECKIN_SECRET = 'test-secret-for-unit-tests-only';
export const BANK = '28899471';

/** 10:00 ngày 19/11/2026 giờ VN (= 03:00 UTC). */
export const NOW = Date.UTC(2026, 10, 19, 3, 0, 0);

export const silentLogger = { info() {}, warn() {}, error() {} };

/** Token giả: "token-<uid>" hợp lệ, còn lại bị từ chối. */
export const fakeAuth: AuthVerifier = {
  async verifyIdToken(token) {
    if (!token.startsWith('token-')) throw new Error('bad token');
    return { uid: token.slice('token-'.length) };
  }
};

export function makeCtx(db: MemoryDb, now = NOW): Ctx & { setNow(ms: number): void } {
  let current = now;
  let seq = 0;
  // Sinh mã hoá đơn tất định: KGAAAAAAAA, KGBBBBBBBB, ...
  return {
    db,
    config: { sepayApiKey: API_KEY, checkInSecret: CHECKIN_SECRET, bankAccountNo: BANK, paymentExpiryMinutes: 15 },
    now: () => current,
    randomInt: () => Math.floor(seq++ / 8) % 32,
    logger: silentLogger,
    setNow(ms: number) {
      current = ms;
    }
  };
}

export function makeApp(db: MemoryDb, now = NOW) {
  const ctx = makeCtx(db, now);
  return { app: createApp(ctx, fakeAuth), ctx };
}

export const packages = {
  basic_1m: { name: 'Basic 1 tháng', type: 'membership', price: 399000, durationDays: 30, trainerUid: '', isActive: true },
  premium_6m: { name: 'Premium 6 tháng', type: 'membership', price: 1000000, durationDays: 180, trainerUid: '', isActive: true },
  pt_strength_12: { name: 'PT 12 buổi', type: 'pt', price: 3600000, durationDays: 60, trainerUid: 'pt1', isActive: true },
  day_pass: { name: 'Vé ngày', type: 'membership', price: 5000, durationDays: 1, trainerUid: '', isActive: true },
  old_pkg: { name: 'Gói cũ', type: 'membership', price: 100000, durationDays: 30, trainerUid: '', isActive: false },
  free_pkg: { name: 'Gói 0đ', type: 'membership', price: 0, durationDays: 30, trainerUid: '', isActive: true }
};

/** Một hoá đơn pending + membership pending của user u1. */
export function seedPendingInvoice(extra: { amount?: number; expiresAt?: number | null; status?: string; packageId?: string } = {}) {
  const packageId = extra.packageId ?? 'premium_6m';
  const payment: Record<string, unknown> = {
    uid: 'u1',
    membershipId: 'm1',
    amount: extra.amount ?? 1000000,
    discount: 0,
    method: 'transfer',
    status: extra.status ?? 'pending',
    createdAt: '09:55 19/11/2026',
    paidAt: ''
  };
  if (extra.expiresAt !== null) payment.expiresAt = extra.expiresAt ?? NOW + 10 * 60_000;
  return {
    packages,
    users: { u1: { fullName: 'Kiệt V', email: 'kiet@example.com', role: 'user', isStaff: false } },
    payments: { KGAB23CDEF: payment },
    memberships: {
      m1: { uid: 'u1', packageId, trainerUid: '', status: 'pending', startDate: '', endDate: '', sessionsUsed: 0, createdAt: '09:55 19/11/2026' }
    }
  };
}

export function sepayBody(overrides: Record<string, unknown> = {}) {
  return {
    id: 100521933,
    gateway: 'ACB',
    transactionDate: '2026-11-19 10:00:00',
    accountNumber: BANK,
    subAccount: null,
    code: null,
    content: '157504271219-01213704700-KGYMS KGAB23CDEF',
    transferType: 'in',
    description: 'BankAPINotify 157504271219-01213704700-KGYMS KGAB23CDEF',
    transferAmount: 1000000,
    accumulated: 5000000,
    referenceCode: '2500',
    ...overrides
  };
}
