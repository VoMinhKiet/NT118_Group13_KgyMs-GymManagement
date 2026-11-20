/**
 * Mã QR điểm danh xoay vòng, port từ CheckInCode.kt (phải khớp tool/checkin_qr.html).
 *
 * Nội dung mã: "KGYMS-CHECKIN:<window>:<sig>"
 * - window = floor(epochSeconds / 30)
 * - sig    = 8 ký tự hex đầu của HMAC-SHA256(key = secret, message = String(window))
 * Chấp nhận lệch ±1 chu kỳ.
 */
import { createHmac, timingSafeEqual } from 'node:crypto';

export const CHECKIN_PREFIX = 'KGYMS-CHECKIN';
export const PERIOD_SECONDS = 30;
const ALLOWED_DRIFT = 1;
const SIGNATURE_LENGTH = 8;

export function isCheckInCode(raw: unknown): raw is string {
  return typeof raw === 'string' && raw.trim().startsWith(`${CHECKIN_PREFIX}:`);
}

export function windowOf(nowMillis: number): number {
  return Math.floor(nowMillis / 1000 / PERIOD_SECONDS);
}

export function sign(secret: string, window: number): string {
  return createHmac('sha256', Buffer.from(secret, 'utf8'))
    .update(String(window), 'utf8')
    .digest('hex')
    .slice(0, SIGNATURE_LENGTH);
}

export function buildCode(secret: string, window: number): string {
  return `${CHECKIN_PREFIX}:${window}:${sign(secret, window)}`;
}

export function verifyCode(raw: string, secret: string, nowMillis: number): boolean {
  const parts = raw.trim().split(':');
  if (parts.length !== 3 || parts[0] !== CHECKIN_PREFIX || !secret.trim()) return false;
  if (!/^-?\d+$/.test(parts[1])) return false;
  const window = Number(parts[1]);
  if (!Number.isSafeInteger(window)) return false;
  if (Math.abs(window - windowOf(nowMillis)) > ALLOWED_DRIFT) return false;
  const expected = Buffer.from(sign(secret, window), 'utf8');
  const actual = Buffer.from(parts[2].toLowerCase(), 'utf8');
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}
