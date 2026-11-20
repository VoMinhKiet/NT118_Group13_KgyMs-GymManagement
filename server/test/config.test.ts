import { describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config.js';

const serviceAccount = Buffer.from(
  JSON.stringify({ project_id: 'p', client_email: 'e@p.iam.gserviceaccount.com', private_key: 'FAKE-PRIVATE-KEY-VALUE' })
).toString('base64');

const full = {
  FIREBASE_SERVICE_ACCOUNT_BASE64: serviceAccount,
  FIREBASE_DATABASE_URL: 'https://example.firebaseio.com',
  SEPAY_WEBHOOK_API_KEY: 'a'.repeat(64),
  CHECKIN_SECRET: 'super-secret-checkin-value',
  BANK_ACCOUNT_NO: '28899471'
};

describe('loadConfig', () => {
  it('đủ biến ⇒ dùng mặc định cho PAYMENT_EXPIRY_MINUTES và PORT', () => {
    const config = loadConfig(full);
    expect(config.paymentExpiryMinutes).toBe(15);
    expect(config.port).toBe(3000);
    expect(config.serviceAccount.project_id).toBe('p');
  });
  it('thiếu biến ⇒ báo tên, không lộ giá trị', () => {
    const { SEPAY_WEBHOOK_API_KEY: _a, CHECKIN_SECRET: _b, ...rest } = full;
    let message = '';
    try {
      loadConfig(rest);
    } catch (error) {
      message = (error as Error).message;
    }
    expect(message).toContain('SEPAY_WEBHOOK_API_KEY');
    expect(message).toContain('CHECKIN_SECRET');
    expect(message).not.toContain(serviceAccount);
    expect(message).not.toContain('FAKE-PRIVATE-KEY-VALUE');
  });
  it('service account không phải JSON ⇒ lỗi không chứa giá trị', () => {
    expect(() => loadConfig({ ...full, FIREBASE_SERVICE_ACCOUNT_BASE64: 'not-base64-json' })).toThrow(/FIREBASE_SERVICE_ACCOUNT_BASE64/);
  });
});
