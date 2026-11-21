import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { categoryOf, pushData } from '../src/domain/push.js';
import { API_KEY, FakePush, makeApp, NOW, seedPendingInvoice, sepayBody } from './helpers.js';
import { MemoryDb } from './memoryDb.js';

const user = (uid: string) => ({ Authorization: `Bearer token-${uid}` });
const TOKEN_A = 'tokenA_'.padEnd(40, 'a');
const TOKEN_B = 'tokenB_'.padEnd(40, 'b');

describe('categoryOf', () => {
  it('khớp cách app lọc hộp thư', () => {
    expect(categoryOf({ type: 'reminder', broadcastId: '' })).toBe('reminders');
    expect(categoryOf({ type: 'promotion', broadcastId: '' })).toBe('gym');
    expect(categoryOf({ type: 'general', broadcastId: 'b1' })).toBe('gym');
    expect(categoryOf({ type: 'general', broadcastId: '' })).toBe('transactions');
  });
  it('payload FCM toàn chuỗi và cắt độ dài', () => {
    const data = pushData('u1', 'n1', { title: 't'.repeat(300), message: 'm', type: 'general', broadcastId: '' });
    expect(Object.values(data).every((v) => typeof v === 'string')).toBe(true);
    expect(data.title).toHaveLength(200);
    expect(data.category).toBe('transactions');
  });
});

describe('/push/token', () => {
  it('cần đăng nhập, token sai định dạng ⇒ 400', async () => {
    const { app } = makeApp(new MemoryDb());
    expect((await request(app).post('/push/token').send({ token: TOKEN_A })).status).toBe(401);
    const bad = await request(app).post('/push/token').set(user('u1')).send({ token: 'a/b.c' });
    expect(bad.status).toBe(400);
    expect(bad.body.error).toBe('invalid_token');
  });

  it('đăng ký rồi huỷ', async () => {
    const db = new MemoryDb();
    const { app } = makeApp(db);
    expect((await request(app).post('/push/token').set(user('u1')).send({ token: TOKEN_A })).status).toBe(200);
    expect(db.read(`fcmTokens/u1/${TOKEN_A}`)).toBe(NOW);
    expect(db.read(`fcmTokenOwners/${TOKEN_A}`)).toBe('u1');
    expect((await request(app).delete('/push/token').set(user('u1')).send({ token: TOKEN_A })).status).toBe(200);
    expect(db.read(`fcmTokens/u1/${TOKEN_A}`)).toBeNull();
    expect(db.read(`fcmTokenOwners/${TOKEN_A}`)).toBeNull();
  });

  it('huỷ bằng POST /push/token/remove (cho client Android)', async () => {
    const db = new MemoryDb();
    const { app } = makeApp(db);
    await request(app).post('/push/token').set(user('u1')).send({ token: TOKEN_A });
    expect((await request(app).post('/push/token/remove').set(user('u1')).send({ token: TOKEN_A })).status).toBe(200);
    expect(db.read(`fcmTokens/u1/${TOKEN_A}`)).toBeNull();
  });

  it('máy đổi tài khoản: token chuyển chủ, tài khoản cũ không còn nhận push', async () => {
    const db = new MemoryDb();
    const { app } = makeApp(db);
    await request(app).post('/push/token').set(user('u1')).send({ token: TOKEN_A });
    await request(app).post('/push/token').set(user('u2')).send({ token: TOKEN_A });
    expect(db.read(`fcmTokens/u1/${TOKEN_A}`)).toBeNull();
    expect(db.read(`fcmTokens/u2/${TOKEN_A}`)).toBe(NOW);
    expect(db.read(`fcmTokenOwners/${TOKEN_A}`)).toBe('u2');
  });

  it('người khác không huỷ được token của mình', async () => {
    const db = new MemoryDb();
    const { app } = makeApp(db);
    await request(app).post('/push/token').set(user('u1')).send({ token: TOKEN_A });
    await request(app).delete('/push/token').set(user('u2')).send({ token: TOKEN_A });
    expect(db.read(`fcmTokenOwners/${TOKEN_A}`)).toBe('u1');
    expect(db.read(`fcmTokens/u1/${TOKEN_A}`)).toBe(NOW);
  });
});

describe('push khi thanh toán qua webhook SePay', () => {
  it('gửi push "Thanh toán thành công" cho mọi thiết bị của hội viên', async () => {
    const push = new FakePush();
    const db = new MemoryDb({
      ...seedPendingInvoice(),
      fcmTokens: { u1: { [TOKEN_A]: 1, [TOKEN_B]: 2 } }
    });
    const { app } = makeApp(db, NOW, push);
    const res = await request(app).post('/webhooks/sepay').set('Authorization', `Apikey ${API_KEY}`).send(sepayBody());
    expect(res.status).toBe(200);

    expect(push.sent).toHaveLength(1);
    expect(push.sent[0].tokens.sort()).toEqual([TOKEN_A, TOKEN_B].sort());
    expect(push.sent[0].data).toMatchObject({ uid: 'u1', title: 'Thanh toán thành công', category: 'transactions' });
    const notifId = push.sent[0].data.notificationId;
    expect(db.read(`notifications/u1/${notifId}/pushedAt`)).toBe(NOW);
  });

  it('hội viên chưa đăng ký token: không gửi, thanh toán vẫn thành công', async () => {
    const push = new FakePush();
    const db = new MemoryDb(seedPendingInvoice());
    const { app } = makeApp(db, NOW, push);
    const res = await request(app).post('/webhooks/sepay').set('Authorization', `Apikey ${API_KEY}`).send(sepayBody());
    expect(res.status).toBe(200);
    expect(push.sent).toHaveLength(0);
    expect(db.read('payments/KGAB23CDEF/status')).toBe('paid');
  });

  it('FCM lỗi bất ngờ không làm hỏng thanh toán', async () => {
    const failing = { send: async () => { throw new Error('fcm down'); } };
    const db = new MemoryDb({ ...seedPendingInvoice(), fcmTokens: { u1: { [TOKEN_A]: 1 } } });
    const { app } = makeApp(db, NOW, failing);
    const res = await request(app).post('/webhooks/sepay').set('Authorization', `Apikey ${API_KEY}`).send(sepayBody());
    expect(res.status).toBe(200);
    expect(db.read('payments/KGAB23CDEF/status')).toBe('paid');
  });

  it('token chết bị dọn khỏi database', async () => {
    const push = new FakePush();
    push.dead.add(TOKEN_B);
    const db = new MemoryDb({ ...seedPendingInvoice(), fcmTokens: { u1: { [TOKEN_A]: 1, [TOKEN_B]: 2 } }, fcmTokenOwners: { [TOKEN_B]: 'u1' } });
    const { app } = makeApp(db, NOW, push);
    await request(app).post('/webhooks/sepay').set('Authorization', `Apikey ${API_KEY}`).send(sepayBody());
    expect(db.read(`fcmTokens/u1/${TOKEN_B}`)).toBeNull();
    expect(db.read(`fcmTokenOwners/${TOKEN_B}`)).toBeNull();
    expect(db.read(`fcmTokens/u1/${TOKEN_A}`)).toBe(1);
  });
});

describe('POST /admin/push', () => {
  const notification = (extra: Record<string, unknown> = {}) => ({
    title: 'Ưu đãi tháng 12', message: 'Giảm 20% gói Premium', type: 'promotion', isRead: false, createdAt: '10:00 19/11/2026', broadcastId: 'b1', ...extra
  });
  const base = () => ({
    users: { admin1: { role: 'admin' }, u1: { role: 'user' } },
    notifications: { u1: { n1: notification() } },
    fcmTokens: { u1: { [TOKEN_A]: 1 } }
  });

  it('chỉ admin được gọi', async () => {
    const { app } = makeApp(new MemoryDb(base()));
    const res = await request(app).post('/admin/push').set(user('u1')).send({ notifications: [{ uid: 'u1', id: 'n1' }] });
    expect(res.status).toBe(403);
  });

  it('body sai ⇒ 400', async () => {
    const { app } = makeApp(new MemoryDb(base()));
    for (const body of [{}, { notifications: [] }, { notifications: [{ uid: 'a/b', id: 'n1' }] }, { notifications: [{ uid: 'u1' }] }]) {
      const res = await request(app).post('/admin/push').set(user('admin1')).send(body);
      expect(res.status).toBe(400);
    }
  });

  it('gửi push cho thông báo đã lưu, category gym, và không gửi trùng lần hai', async () => {
    const push = new FakePush();
    const db = new MemoryDb(base());
    const { app } = makeApp(db, NOW, push);
    const first = await request(app).post('/admin/push').set(user('admin1')).send({ notifications: [{ uid: 'u1', id: 'n1' }] });
    expect(first.body).toEqual({ sent: 1, skipped: 0 });
    expect(push.sent[0].data).toMatchObject({ uid: 'u1', notificationId: 'n1', title: 'Ưu đãi tháng 12', category: 'gym' });

    const again = await request(app).post('/admin/push').set(user('admin1')).send({ notifications: [{ uid: 'u1', id: 'n1' }] });
    expect(again.body).toEqual({ sent: 0, skipped: 1 });
    expect(push.sent).toHaveLength(1);
  });

  it('thông báo không tồn tại bị bỏ qua (admin không thể push nội dung tự bịa)', async () => {
    const push = new FakePush();
    const { app } = makeApp(new MemoryDb(base()), NOW, push);
    const res = await request(app).post('/admin/push').set(user('admin1')).send({ notifications: [{ uid: 'u1', id: 'khongco' }] });
    expect(res.body).toEqual({ sent: 0, skipped: 1 });
    expect(push.sent).toHaveLength(0);
  });
});
