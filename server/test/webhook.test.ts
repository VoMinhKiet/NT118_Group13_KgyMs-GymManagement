import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { API_KEY, BANK, makeApp, NOW, seedPendingInvoice, sepayBody } from './helpers.js';
import { MemoryDb } from './memoryDb.js';

const AUTH = `Apikey ${API_KEY}`;

async function send(db: MemoryDb, body: unknown, auth = AUTH, now = NOW) {
  const { app } = makeApp(db, now);
  return request(app).post('/webhooks/sepay').set('Authorization', auth).send(body as object);
}

describe('POST /webhooks/sepay', () => {
  it('sai API key ⇒ 401, không ghi gì', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    for (const auth of ['Apikey sai', `Bearer ${API_KEY}`, '']) {
      const res = await send(db, sepayBody(), auth);
      expect(res.status).toBe(401);
    }
    expect(db.read('bankTransactions')).toBeNull();
  });

  it('chữ "Apikey" không phân biệt hoa/thường', async () => {
    for (const scheme of ['ApiKey', 'APIKEY', 'apikey']) {
      const db = new MemoryDb(seedPendingInvoice());
      const res = await send(db, sepayBody(), `${scheme} ${API_KEY}`);
      expect(res.status).toBe(200);
    }
  });

  it('tiền ra hoặc tài khoản khác ⇒ 200 success, bỏ qua', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    const out = await send(db, sepayBody({ transferType: 'out' }));
    expect(out.status).toBe(200);
    expect(out.body).toEqual({ success: true });
    const other = await send(db, sepayBody({ accountNumber: '9793704700' }));
    expect(other.status).toBe(200);
    expect(db.read('bankTransactions')).toBeNull();
    expect(db.read('payments/KGAB23CDEF/status')).toBe('pending');
  });

  it('thiếu id ⇒ 400', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    const res = await send(db, sepayBody({ id: undefined }));
    expect(res.status).toBe(400);
  });

  it('thành công: hoá đơn paid, gói active, thông báo, bankTransactions matched, log', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    const res = await send(db, sepayBody());
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ success: true });

    expect(db.read('payments/KGAB23CDEF')).toMatchObject({
      status: 'paid',
      paidAt: '10:00 19/11/2026',
      paidVia: 'sepay',
      bankTransactionId: '100521933'
    });
    expect(db.read('memberships/m1')).toMatchObject({ status: 'active', startDate: '19/11/2026', endDate: '17/05/2027' });
    expect(db.read('bankTransactions/100521933')).toMatchObject({
      amount: 1000000,
      content: '157504271219-01213704700-KGYMS KGAB23CDEF',
      referenceCode: '2500',
      transactionDate: '2026-11-19 10:00:00',
      status: 'matched',
      paymentId: 'KGAB23CDEF'
    });
    const notifications = Object.values(db.read('notifications/u1') as object);
    expect(notifications).toHaveLength(1);
    expect(notifications[0]).toMatchObject({
      title: 'Thanh toán thành công',
      message: 'KgyMs đã nhận 1.000.000đ cho hoá đơn KGAB23CDEF (Premium 6 tháng). Thời hạn: 19/11/2026 – 17/05/2027.'
    });
    const logs = Object.values(db.read('activityLogs') as object);
    expect(logs).toContainEqual({
      title: 'Hệ thống xác nhận thanh toán hoá đơn KGAB23CDEF (Premium 6 tháng)',
      actorUid: 'system',
      action: 'payment',
      createdAt: '10:00 19/11/2026'
    });
    expect(db.read('reconciliation')).toBeNull();
  });

  it('nối tiếp gói cùng loại đang chạy', async () => {
    const seed = seedPendingInvoice();
    (seed.memberships as Record<string, unknown>).old = {
      uid: 'u1', packageId: 'basic_1m', trainerUid: '', status: 'active', startDate: '01/11/2026', endDate: '30/11/2026'
    };
    const db = new MemoryDb(seed);
    await send(db, sepayBody());
    expect(db.read('memberships/m1')).toMatchObject({ startDate: '01/12/2026', endDate: '29/05/2027' });
  });

  it('SePay gửi lặp cùng id ⇒ chỉ xử lý một lần', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    await send(db, sepayBody());
    const again = await send(db, sepayBody());
    expect(again.status).toBe(200);
    expect(again.body).toEqual({ success: true });
    expect(Object.keys(db.read('notifications/u1') as object)).toHaveLength(1);
    expect(db.read('reconciliation')).toBeNull();
  });

  it('hai giao dịch khác id cho cùng hoá đơn ⇒ giao dịch thứ hai vào đối soát invoice_not_pending', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    await send(db, sepayBody());
    await send(db, sepayBody({ id: 100521999 }));
    expect(db.read('reconciliation/100521999')).toMatchObject({ reason: 'invoice_not_pending', status: 'open' });
    expect(Object.keys(db.read('notifications/u1') as object)).toHaveLength(1);
  });

  const reasons: Array<[string, () => MemoryDb, Record<string, unknown>, Record<string, unknown>]> = [
    ['no_code', () => new MemoryDb(seedPendingInvoice()), { content: '157504271219-01213704700-KGYMS TEST' }, { paymentId: '', expectedAmount: 0 }],
    ['invoice_not_found', () => new MemoryDb(seedPendingInvoice()), { content: '123-KGYMS KGZZZZZZZZ' }, { paymentId: 'KGZZZZZZZZ' }],
    ['invoice_not_pending', () => new MemoryDb(seedPendingInvoice({ status: 'cancelled' })), {}, { paymentId: 'KGAB23CDEF', expectedAmount: 1000000 }],
    ['invoice_expired', () => new MemoryDb(seedPendingInvoice({ expiresAt: NOW - 1 })), {}, { paymentId: 'KGAB23CDEF' }],
    ['underpaid', () => new MemoryDb(seedPendingInvoice()), { transferAmount: 999000 }, { amount: 999000, expectedAmount: 1000000 }],
    ['overpaid', () => new MemoryDb(seedPendingInvoice()), { transferAmount: 1500000 }, { amount: 1500000, expectedAmount: 1000000 }]
  ];

  for (const [reason, makeDb, overrides, expected] of reasons) {
    it(`đối soát ${reason} ⇒ 200, ghi reconciliation, không kích hoạt`, async () => {
      const db = makeDb();
      const before = db.read('payments/KGAB23CDEF/status');
      const res = await send(db, sepayBody(overrides));
      expect(res.status).toBe(200);
      expect(res.body).toEqual({ success: true });
      expect(db.read('reconciliation/100521933')).toMatchObject({
        reason,
        status: 'open',
        resolution: '',
        transactionDate: '2026-11-19 10:00:00',
        createdAt: '10:00 19/11/2026',
        ...expected
      });
      expect(db.read('bankTransactions/100521933/status')).toBe('reconciliation');
      expect(db.read('payments/KGAB23CDEF/status')).toBe(before);
      expect(db.read('memberships/m1/status')).toBe('pending');
      expect(db.read('notifications')).toBeNull();
      const logs = Object.values(db.read('activityLogs') as object) as Array<{ title: string }>;
      expect(logs.some((l) => l.title.startsWith('Hệ thống chuyển giao dịch ngân hàng 100521933 sang đối soát'))).toBe(true);
    });
  }

  it('hoá đơn cũ không có expiresAt ⇒ không hết hạn, vẫn kích hoạt', async () => {
    const db = new MemoryDb(seedPendingInvoice({ expiresAt: null }));
    await send(db, sepayBody(), AUTH, NOW + 30 * 86_400_000);
    expect(db.read('payments/KGAB23CDEF/status')).toBe('paid');
  });

  it('lỗi ghi multi-path sau khi đã chuyển paid ⇒ đối soát activation_error, vẫn 200', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    db.failUpdate = (u) => Object.keys(u).some((p) => p.startsWith('memberships/'));
    const res = await send(db, sepayBody());
    expect(res.status).toBe(200);
    expect(db.read('reconciliation/100521933')).toMatchObject({ reason: 'activation_error', paymentId: 'KGAB23CDEF' });
    expect(db.read('payments/KGAB23CDEF')).toMatchObject({ status: 'paid', paidAt: '' });
  });

  it('lỗi hệ thống bất ngờ ⇒ xoá bankTransactions vừa tạo và trả 500 để SePay gửi lại', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    db.failUpdate = () => true; // ghi đối soát cũng lỗi
    const res = await send(db, sepayBody({ content: 'khong co ma' }));
    expect(res.status).toBe(500);
    expect(db.read('bankTransactions/100521933')).toBeNull();

    // SePay gửi lại khi Firebase đã ổn ⇒ được xử lý.
    db.failUpdate = null;
    const retry = await send(db, sepayBody({ content: 'khong co ma' }));
    expect(retry.status).toBe(200);
    expect(db.read('reconciliation/100521933/reason')).toBe('no_code');
  });

  it('tài khoản nhận tiền khớp cả khi SePay gửi kèm khoảng trắng', async () => {
    const db = new MemoryDb(seedPendingInvoice());
    await send(db, sepayBody({ accountNumber: ` ${BANK} ` }));
    expect(db.read('payments/KGAB23CDEF/status')).toBe('paid');
  });
});
