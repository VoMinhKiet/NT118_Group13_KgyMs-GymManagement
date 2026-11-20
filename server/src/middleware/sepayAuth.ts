import { createHash, timingSafeEqual } from 'node:crypto';
import type { RequestHandler } from 'express';

const digest = (value: string) => createHash('sha256').update(value, 'utf8').digest();

type Logger = Pick<Console, 'warn'>;

/**
 * SePay → server: header "Authorization: Apikey <API_KEY>", so sánh hằng thời gian. Sai ⇒ 401.
 * Chữ "Apikey" không phân biệt hoa/thường. Khi từ chối chỉ ghi lý do (không ghi key).
 */
export function sepayAuth(apiKey: string, logger?: Logger): RequestHandler {
  const expected = digest(apiKey);
  return (req, res, next) => {
    const header = (req.get('authorization') ?? '').trim();
    const match = /^(\S+)\s+(\S+)$/.exec(header);
    const scheme = match?.[1] ?? '';
    const key = match?.[2] ?? '';
    // So sánh băm SHA-256 để hai bên luôn cùng độ dài (timingSafeEqual yêu cầu vậy).
    const keyOk = timingSafeEqual(digest(key), expected);
    if (scheme.toLowerCase() !== 'apikey' || !keyOk) {
      logger?.warn(`[sepayAuth] Từ chối webhook: ${describe(header, scheme, key, keyOk)}`);
      res.status(401).json({ error: 'unauthorized', message: 'Sai API key.' });
      return;
    }
    next();
  };
}

/** Mô tả lý do từ chối, không bao giờ chứa key. */
function describe(header: string, scheme: string, key: string, keyOk: boolean): string {
  if (!header) return 'không có header Authorization';
  if (!scheme) return `header không có dạng "Apikey <key>" (một từ, dài ${header.length} ký tự)`;
  const schemeText = scheme.length <= 12 ? `"${scheme}"` : `<dài ${scheme.length} ký tự>`;
  if (scheme.toLowerCase() !== 'apikey') return `tiền tố ${schemeText} không phải "Apikey"`;
  return keyOk ? 'không rõ' : `key không khớp (key nhận được dài ${key.length} ký tự)`;
}
