/**
 * Mã hoá đơn: "KG" + 8 ký tự từ [PAYMENT_ID_ALPHABET] (giống PaymentRepository.newPaymentId()).
 * Nội dung chuyển khoản: "KGYMS <paymentId>". ACB chèn chuỗi số phía trước, ví dụ
 * "157504271219-01213704700-KGYMS KGAB23CDEF", nên phải tự tìm mã trong nội dung.
 */

/** Bỏ 0/O/1/I để đọc không nhầm. */
export const PAYMENT_ID_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
export const TRANSFER_PREFIX = 'KGYMS';

const CODE = 'KG[A-HJ-NP-Z2-9]{8}';
/** Mã đứng ngay sau "KGYMS" (có thể có khoảng trắng, "-", "_" hoặc "." xen giữa). */
const AFTER_PREFIX = new RegExp(`${TRANSFER_PREFIX}[\\s\\-_.]*(${CODE})`);
const CODE_ANYWHERE = new RegExp(CODE, 'g');

export function isValidPaymentId(value: string): boolean {
  return new RegExp(`^${CODE}$`).test(value);
}

/**
 * Sinh mã hoá đơn mới. [randomInt] nhận max (không gồm) và trả số nguyên ngẫu nhiên an toàn
 * (vd crypto.randomInt). Bỏ các mã bắt đầu bằng "KGYMS" để không lẫn với tiền tố nội dung.
 */
export function generatePaymentId(randomInt: (max: number) => number): string {
  for (;;) {
    let id = 'KG';
    for (let i = 0; i < 8; i++) id += PAYMENT_ID_ALPHABET[randomInt(PAYMENT_ID_ALPHABET.length)];
    if (!id.startsWith(TRANSFER_PREFIX)) return id;
  }
}

/**
 * Tách mã hoá đơn từ nội dung chuyển khoản; null nếu không có.
 * 1. Ưu tiên mã đứng ngay sau "KGYMS".
 * 2. Không có thì tìm mã ở bất kỳ đâu, bỏ qua các vị trí bắt đầu bằng "KGYMS"
 *    (để "KGYMS TEST" hay "KGYMSTEST1234" không bị bắt nhầm).
 */
export function extractPaymentCode(content: unknown): string | null {
  if (typeof content !== 'string') return null;
  const text = content.toUpperCase();

  const afterPrefix = AFTER_PREFIX.exec(text);
  if (afterPrefix) return afterPrefix[1];

  for (let i = 0; i < text.length; i++) {
    if (text.startsWith(TRANSFER_PREFIX, i)) continue;
    CODE_ANYWHERE.lastIndex = i;
    const match = CODE_ANYWHERE.exec(text);
    if (!match) return null;
    if (match.index === i) return match[0];
    i = match.index - 1;
  }
  return null;
}

export type AmountCheck = 'ok' | 'underpaid' | 'overpaid';

export function checkAmount(received: number, expected: number): AmountCheck {
  if (received < expected) return 'underpaid';
  if (received > expected) return 'overpaid';
  return 'ok';
}

/** 1000000 → "1.000.000đ" (giống formatVnd trong app). */
export function formatVnd(amount: number): string {
  const sign = amount < 0 ? '-' : '';
  return sign + String(Math.abs(Math.trunc(amount))).replace(/\B(?=(\d{3})+(?!\d))/g, '.') + 'đ';
}
