import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { buildCode, windowOf } from '../src/domain/checkInCode.js';
import { CHECKIN_SECRET, makeApp, NOW, packages, seedPendingInvoice } from './helpers.js';
import { MemoryDb } from './memoryDb.js';

const user = (uid: string) => ({ Authorization: `Bearer token-${uid}` });

describe('GET /health', () => {
  it('200 { ok: true }', async () => {
    const res = await request(makeApp(new MemoryDb()).app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ ok: true });
  });
});

describe('xác thực app', () => {
  it('thiếu hoặc sai token ⇒ 401', async () => {
    const { app } = makeApp(new MemoryDb({ packages }));
    expect((await request(app).post('/payments').send({ packageId: 'basic_1m' })).status).toBe(401);
    const bad = await request(app).post('/payments').set('Authorization', 'Bearer xyz').send({ packageId: 'basic_1m' });
    expect(bad.status).toBe(401);
    expect(bad.body.error).toBe('unauthorized');
  });
  it('route admin với user thường ⇒ 403', async () => {
    const db = new MemoryDb({ users: { u1: { role: 'user' } } });
    const res = await request(makeApp(db).app).post('/admin/reconciliation/1/resolve').set(user('u1')).send({ action: 'ignored' });
    expect(res.status).toBe(403);
  });
});

describe('POST /payments', () => {
  it('tạo hoá đơn với amount lấy từ giá gói trên Firebase', async () => {
    const db = new MemoryDb({ packages, users: { u1: { fullName: 'Kiệt V', role: 'user' } } });
    const res = await request(makeApp(db).app).post('/payments').set(user('u1')).send({ packageId: 'basic_1m', amount: 1 });
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ paymentId: 'KGAAAAAAAA', amount: 399000, expiresAt: NOW + 15 * 60_000, packageName: 'Basic 1 tháng' });
    const payment = db.read('payments/KGAAAAAAAA') as Record<string, unknown>;
    expect(payment).toMatchObject({ uid: 'u1', amount: 399000, discount: 0, method: 'transfer', status: 'pending', paidAt: '', createdAt: '10:00 19/11/2026' });
    expect(db.read(`memberships/${payment.membershipId}`)).toMatchObject({ uid: 'u1', packageId: 'basic_1m', status: 'pending', sessionsUsed: 0 });
    const logs = Object.values(db.read('activityLogs') as object);
    expect(logs).toContainEqual(expect.objectContaining({ title: 'Kiệt V tạo hoá đơn KGAAAAAAAA cho gói "Basic 1 tháng"', actorUid: 'u1' }));
  });

  it('đã có hoá đơn pending còn hạn cùng gói ⇒ trả lại, không tạo mới', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    const res = await request(makeApp(db).app).post('/payments').set(user('u1')).send({ packageId: 'premium_6m' });
    expect(res.body.paymentId).toBe('KGAB23CDEF');
    expect(Object.keys(db.read('payments') as object)).toHaveLength(1);
  });

  it('hoá đơn pending quá hạn ⇒ huỷ (cả membership) rồi tạo mới', async () => {
    const db = new MemoryDb(seedPendingInvoice({ expiresAt: NOW - 1 }));
    const res = await request(makeApp(db).app).post('/payments').set(user('u1')).send({ packageId: 'premium_6m' });
    expect(res.body.paymentId).not.toBe('KGAB23CDEF');
    expect(db.read('payments/KGAB23CDEF/status')).toBe('cancelled');
    expect(db.read('memberships/m1/status')).toBe('cancelled');
  });

  it('gói không tồn tại / ngừng bán / chưa có giá ⇒ 400', async () => {
    const { app } = makeApp(new MemoryDb({ packages }));
    for (const [packageId, code] of [['nope', 'package_not_found'], ['old_pkg', 'package_inactive'], ['free_pkg', 'package_no_price'], [123, 'invalid_package_id']]) {
      const res = await request(app).post('/payments').set(user('u1')).send({ packageId });
      expect(res.status).toBe(400);
      expect(res.body.error).toBe(code);
      expect(typeof res.body.message).toBe('string');
    }
  });
});

describe('POST /payments/:id/cancel', () => {
  it('huỷ hoá đơn pending của mình', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    const res = await request(makeApp(db).app).post('/payments/KGAB23CDEF/cancel').set(user('u1'));
    expect(res.status).toBe(200);
    expect(db.read('payments/KGAB23CDEF/status')).toBe('cancelled');
    expect(db.read('memberships/m1/status')).toBe('cancelled');
  });
  it('hoá đơn của người khác hoặc không còn pending ⇒ 400', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    expect((await request(makeApp(db).app).post('/payments/KGAB23CDEF/cancel').set(user('u2'))).status).toBe(400);
    const paid = new MemoryDb(seedPendingInvoice({ status: 'paid' }));
    const res = await request(makeApp(paid).app).post('/payments/KGAB23CDEF/cancel').set(user('u1'));
    expect(res.status).toBe(400);
    expect(res.body.error).toBe('payment_not_pending');
  });
});

describe('POST /check-in', () => {
  const code = (now = NOW, offset = 0) => buildCode(CHECKIN_SECRET, windowOf(now) + offset);
  const seed = () => ({
    packages,
    users: { u1: { fullName: 'Kiệt V', role: 'user', isStaff: false }, pt: { role: 'user', isStaff: true }, admin: { role: 'admin' } },
    memberships: { m1: { uid: 'u1', packageId: 'basic_1m', status: 'active', startDate: '01/11/2026', endDate: '30/11/2026' } }
  });

  it('các kết quả: not_checkin_code, invalid_code, staff_not_required', async () => {
    const { app } = makeApp(new MemoryDb(seed()));
    expect((await request(app).post('/check-in').set(user('u1')).send({ code: 'https://x.com' })).body).toEqual({ result: 'not_checkin_code' });
    expect((await request(app).post('/check-in').set(user('u1')).send({ code: code(NOW, 2) })).body).toEqual({ result: 'invalid_code' });
    expect((await request(app).post('/check-in').set(user('pt')).send({ code: code() })).body).toEqual({ result: 'staff_not_required' });
    expect((await request(app).post('/check-in').set(user('admin')).send({ code: code() })).body).toEqual({ result: 'staff_not_required' });
  });

  it('check-in rồi check-out', async () => {
    const db = new MemoryDb(seed());
    const { app, ctx } = makeApp(db);
    const first = await request(app).post('/check-in').set(user('u1')).send({ code: code() });
    expect(first.body).toEqual({ result: 'checked_in', at: NOW, packageName: 'Basic 1 tháng' });
    ctx.setNow(NOW + 3600_000);
    const second = await request(app).post('/check-in').set(user('u1')).send({ code: code(NOW + 3600_000) });
    expect(second.body).toEqual({ result: 'checked_out', checkInAt: NOW, checkOutAt: NOW + 3600_000 });
  });

  it('không có gói ⇒ no_active_membership', async () => {
    const db = new MemoryDb({ ...seed(), memberships: {} });
    const res = await request(makeApp(db).app).post('/check-in').set(user('u1')).send({ code: code() });
    expect(res.body).toEqual({ result: 'no_active_membership' });
    expect(db.read('checkIns')).toBeNull();
  });
});

describe('POST /admin/reconciliation/:id/resolve', () => {
  const seedWith = (reason: string, paymentOverrides = {}) => {
    const s = seedPendingInvoice(paymentOverrides) as Record<string, any>;
    s.users.admin = { fullName: 'Kiệt', role: 'admin' };
    s.bankTransactions = { '555': { amount: 1000000, content: 'x', status: 'reconciliation', paymentId: 'KGAB23CDEF' } };
    s.reconciliation = { '555': { reason, amount: 1000000, paymentId: 'KGAB23CDEF', expectedAmount: 1000000, status: 'open', resolution: '' } };
    return s;
  };
  const admin = { Authorization: 'Bearer token-admin' };

  it('activate hoá đơn đã hết hạn ⇒ paid qua admin, đóng đối soát', async () => {
    const db = new MemoryDb(seedWith('invoice_expired', { expiresAt: NOW - 1 }));
    const res = await request(makeApp(db).app).post('/admin/reconciliation/555/resolve').set(admin).send({ action: 'activate', note: 'khách chuyển trễ' });
    expect(res.status).toBe(200);
    expect(db.read('payments/KGAB23CDEF')).toMatchObject({ status: 'paid', paidVia: 'admin', bankTransactionId: '555' });
    expect(db.read('memberships/m1/status')).toBe('active');
    expect(db.read('reconciliation/555')).toMatchObject({ status: 'resolved', resolution: 'activate', resolvedBy: 'admin', note: 'khách chuyển trễ', resolvedAt: '10:00 19/11/2026' });
    expect(db.read('bankTransactions/555/status')).toBe('matched');
  });

  it('activate hoá đơn đã bị tự huỷ ⇒ vẫn kích hoạt được', async () => {
    const db = new MemoryDb(seedWith('invoice_not_pending', { status: 'cancelled' }));
    const res = await request(makeApp(db).app).post('/admin/reconciliation/555/resolve').set(admin).send({ action: 'activate' });
    expect(res.status).toBe(200);
    expect(db.read('payments/KGAB23CDEF/status')).toBe('paid');
  });

  it('activate sau activation_error (paid nhưng chưa có paidAt) ⇒ hoàn tất', async () => {
    const db = new MemoryDb(seedWith('activation_error', { status: 'paid' }));
    const res = await request(makeApp(db).app).post('/admin/reconciliation/555/resolve').set(admin).send({ action: 'activate' });
    expect(res.status).toBe(200);
    expect(db.read('memberships/m1/status')).toBe('active');
  });

  it('refunded / ignored chỉ đóng đối soát', async () => {
    const db = new MemoryDb(seedWith('overpaid'));
    const res = await request(makeApp(db).app).post('/admin/reconciliation/555/resolve').set(admin).send({ action: 'refunded' });
    expect(res.status).toBe(200);
    expect(db.read('reconciliation/555')).toMatchObject({ status: 'resolved', resolution: 'refunded' });
    expect(db.read('payments/KGAB23CDEF/status')).toBe('pending');
    const again = await request(makeApp(db).app).post('/admin/reconciliation/555/resolve').set(admin).send({ action: 'ignored' });
    expect(again.status).toBe(400);
  });

  it('action sai / không tìm thấy', async () => {
    const db = new MemoryDb(seedWith('no_code'));
    expect((await request(makeApp(db).app).post('/admin/reconciliation/555/resolve').set(admin).send({ action: 'x' })).status).toBe(400);
    expect((await request(makeApp(db).app).post('/admin/reconciliation/999/resolve').set(admin).send({ action: 'ignored' })).status).toBe(404);
  });
});
