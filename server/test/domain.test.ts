import { describe, expect, it } from 'vitest';
import { buildActivationUpdates, computePeriod } from '../src/domain/activation.js';
import { decideCheckIn } from '../src/domain/checkIn.js';
import { buildCode, sign, verifyCode, windowOf } from '../src/domain/checkInCode.js';
import {
  addDays,
  endOfVnDayMillis,
  formatDay,
  parseDay,
  startOfVnDayMillis,
  timestamp,
  vnDay
} from '../src/domain/dates.js';
import { toPackage, toPayment, type GymPackage, type Membership } from '../src/domain/models.js';
import {
  checkAmount,
  extractPaymentCode,
  formatVnd,
  generatePaymentId,
  isValidPaymentId
} from '../src/domain/paymentCode.js';
import { CHECKIN_SECRET, packages as pkgData } from './helpers.js';

describe('tách mã hoá đơn', () => {
  it('nội dung thật của ACB', () => {
    expect(extractPaymentCode('157504271219-01213704700-KGYMS KGAB23CDEF')).toBe('KGAB23CDEF');
  });
  it('chữ thường', () => {
    expect(extractPaymentCode('157504271219-01213704700-kgyms kgab23cdef')).toBe('KGAB23CDEF');
  });
  it('không có mã', () => {
    expect(extractPaymentCode('chuyen tien tap gym')).toBeNull();
    expect(extractPaymentCode('')).toBeNull();
    expect(extractPaymentCode(undefined)).toBeNull();
  });
  it('"...-KGYMS TEST" ra không có mã', () => {
    expect(extractPaymentCode('157504271219-01213704700-KGYMS TEST')).toBeNull();
  });
  it('không bắt nhầm chuỗi KGYMS dính liền chữ khác', () => {
    expect(extractPaymentCode('KGYMSTEST234')).toBeNull();
    expect(extractPaymentCode('KGYMS2ABCDEFG')).toBeNull();
  });
  it('mã có ký tự cấm (0, 1, I, O) không được nhận', () => {
    expect(extractPaymentCode('KGYMS KGAB23CDE0')).toBeNull();
    expect(extractPaymentCode('KGYMS KGAB1CDEFG')).toBeNull();
    expect(extractPaymentCode('KGYMS KGIBOCDEFG')).toBeNull();
  });
  it('ngân hàng bỏ khoảng trắng hoặc dùng gạch nối', () => {
    expect(extractPaymentCode('123-KGYMSKGAB23CDEF')).toBe('KGAB23CDEF');
    expect(extractPaymentCode('123-KGYMS-KGAB23CDEF-456')).toBe('KGAB23CDEF');
  });
  it('mã không đứng sau KGYMS vẫn tìm được', () => {
    expect(extractPaymentCode('thanh toan KGAB23CDEF')).toBe('KGAB23CDEF');
  });
  it('mã bắt đầu bằng KGYMS vẫn nhận nếu có tiền tố đầy đủ', () => {
    expect(extractPaymentCode('KGYMS KGYMS23ABC')).toBe('KGYMS23ABC');
  });
});

describe('sinh mã hoá đơn', () => {
  it('đúng định dạng KG + 8 ký tự và không bắt đầu bằng KGYMS', () => {
    let i = 0;
    // Lần đầu ra "KGYMS....." (bị bỏ), lần sau hợp lệ.
    const seq = [22, 11, 16, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8];
    const id = generatePaymentId(() => seq[i++ % seq.length]);
    expect(isValidPaymentId(id)).toBe(true);
    expect(id.startsWith('KGYMS')).toBe(false);
    expect(id).toBe('KGBCDEFGHJ');
  });
});

describe('so tiền', () => {
  it('bằng / thiếu / dư', () => {
    expect(checkAmount(1000000, 1000000)).toBe('ok');
    expect(checkAmount(999999, 1000000)).toBe('underpaid');
    expect(checkAmount(1000001, 1000000)).toBe('overpaid');
  });
  it('định dạng tiền như app', () => {
    expect(formatVnd(1000000)).toBe('1.000.000đ');
    expect(formatVnd(5000)).toBe('5.000đ');
    expect(formatVnd(399)).toBe('399đ');
  });
});

describe('ngày giờ Việt Nam (máy chạy UTC)', () => {
  it('ranh giới nửa đêm', () => {
    // 16:59 UTC 18/11 = 23:59 VN 18/11; 17:00 UTC 18/11 = 00:00 VN 19/11.
    expect(formatDay(vnDay(Date.UTC(2026, 10, 18, 16, 59)))).toBe('18/11/2026');
    expect(formatDay(vnDay(Date.UTC(2026, 10, 18, 17, 0)))).toBe('19/11/2026');
    expect(timestamp(Date.UTC(2026, 10, 18, 17, 5))).toBe('00:05 19/11/2026');
  });
  it('đầu và cuối ngày VN dạng millis', () => {
    const t = Date.UTC(2026, 10, 19, 3, 0);
    expect(startOfVnDayMillis(t)).toBe(Date.UTC(2026, 10, 18, 17, 0));
    expect(endOfVnDayMillis(t)).toBe(Date.UTC(2026, 10, 19, 16, 59));
  });
  it('parseDay chặt chẽ', () => {
    expect(formatDay(parseDay('31/12/2026')!)).toBe('31/12/2026');
    expect(parseDay('31/02/2026')).toBeNull();
    expect(parseDay('1/2/2026')).toBeNull();
    expect(parseDay('')).toBeNull();
  });
  it('cộng ngày qua tháng/năm', () => {
    expect(formatDay(addDays(parseDay('31/12/2026')!, 1))).toBe('01/01/2027');
  });
});

describe('tính thời hạn gói', () => {
  const packages = new Map<string, GymPackage>(Object.entries(pkgData).map(([id, raw]) => [id, toPackage(id, raw)]));
  const premium = packages.get('premium_6m')!;
  const today = parseDay('19/11/2026')!;
  const m = (id: string, packageId: string, status: string, endDate: string): Membership => ({
    id, uid: 'u1', packageId, trainerUid: '', status, startDate: '01/11/2026', endDate
  });

  it('không có gói cũ: bắt đầu hôm nay', () => {
    const p = computePeriod({ membershipId: 'new', pkg: premium, userMemberships: [], packages, today });
    expect([formatDay(p.start), formatDay(p.end)]).toEqual(['19/11/2026', '17/05/2027']);
  });
  it('nối tiếp gói cùng loại đang chạy (lấy endDate muộn nhất)', () => {
    const p = computePeriod({
      membershipId: 'new',
      pkg: premium,
      userMemberships: [m('a', 'basic_1m', 'active', '30/11/2026'), m('b', 'day_pass', 'active', '05/12/2026')],
      packages,
      today
    });
    expect(formatDay(p.start)).toBe('06/12/2026');
  });
  it('bỏ qua gói khác loại', () => {
    const p = computePeriod({
      membershipId: 'new', pkg: premium, userMemberships: [m('a', 'pt_strength_12', 'active', '30/12/2026')], packages, today
    });
    expect(formatDay(p.start)).toBe('19/11/2026');
  });
  it('bỏ qua gói cũ đã hết hạn và gói không active, và chính membership đang kích hoạt', () => {
    const p = computePeriod({
      membershipId: 'new',
      pkg: premium,
      userMemberships: [
        m('a', 'basic_1m', 'active', '18/11/2026'),
        m('b', 'basic_1m', 'cancelled', '30/12/2026'),
        m('new', 'premium_6m', 'active', '30/12/2026')
      ],
      packages,
      today
    });
    expect(formatDay(p.start)).toBe('19/11/2026');
  });
  it('gói hết hạn đúng hôm nay vẫn được nối tiếp', () => {
    const p = computePeriod({ membershipId: 'new', pkg: premium, userMemberships: [m('a', 'basic_1m', 'active', '19/11/2026')], packages, today });
    expect(formatDay(p.start)).toBe('20/11/2026');
  });
  it('durationDays 1 (vé ngày) và 0 đều ra 1 ngày', () => {
    for (const days of [1, 0]) {
      const p = computePeriod({ membershipId: 'n', pkg: { ...premium, durationDays: days }, userMemberships: [], packages, today });
      expect(formatDay(p.end)).toBe('19/11/2026');
    }
  });
  it('ranh giới nửa đêm: 23:30 VN 18/11 và 00:30 VN 19/11 ra hai ngày khác nhau', () => {
    const before = vnDay(Date.UTC(2026, 10, 18, 16, 30));
    const after = vnDay(Date.UTC(2026, 10, 18, 17, 30));
    const pb = computePeriod({ membershipId: 'n', pkg: premium, userMemberships: [], packages, today: before });
    const pa = computePeriod({ membershipId: 'n', pkg: premium, userMemberships: [], packages, today: after });
    expect(formatDay(pb.start)).toBe('18/11/2026');
    expect(formatDay(pa.start)).toBe('19/11/2026');
  });
  it('thông báo đúng mẫu', () => {
    const payment = toPayment('KGAB23CDEF', { uid: 'u1', membershipId: 'm1', amount: 1000000, status: 'pending' });
    const period = computePeriod({ membershipId: 'm1', pkg: premium, userMemberships: [], packages, today });
    const updates = buildActivationUpdates({
      payment,
      membership: { id: 'm1', uid: 'u1', packageId: 'premium_6m', trainerUid: '', status: 'pending', startDate: '', endDate: '' },
      pkg: premium,
      period,
      nowStamp: '10:00 19/11/2026',
      paidVia: 'sepay',
      bankTransactionId: '100521933',
      notificationId: 'n1'
    });
    expect(updates['notifications/u1/n1']).toEqual({
      title: 'Thanh toán thành công',
      message: 'KgyMs đã nhận 1.000.000đ cho hoá đơn KGAB23CDEF (Premium 6 tháng). Thời hạn: 19/11/2026 – 17/05/2027.',
      type: 'general',
      isRead: false,
      createdAt: '10:00 19/11/2026'
    });
  });
});

describe('HMAC check-in (khớp CheckInCode.kt)', () => {
  it('chữ ký khớp vector tính độc lập bằng Python hmac', () => {
    // python: hmac.new(secret, str(window).encode(), sha256).hexdigest()[:8]
    expect(buildCode(CHECKIN_SECRET, 59612345)).toBe('KGYMS-CHECKIN:59612345:9cf7c99a');
    expect(sign(CHECKIN_SECRET, 60000000)).toBe('8c95664e');
  });
  it('±1 chu kỳ chấp nhận, ±2 từ chối', () => {
    const now = 1_800_000_000_000;
    const w = windowOf(now);
    expect(w).toBe(60000000);
    expect(verifyCode(buildCode(CHECKIN_SECRET, w), CHECKIN_SECRET, now)).toBe(true);
    expect(verifyCode(buildCode(CHECKIN_SECRET, w - 1), CHECKIN_SECRET, now)).toBe(true);
    expect(verifyCode(buildCode(CHECKIN_SECRET, w + 1), CHECKIN_SECRET, now)).toBe(true);
    expect(verifyCode(buildCode(CHECKIN_SECRET, w - 2), CHECKIN_SECRET, now)).toBe(false);
    expect(verifyCode(buildCode(CHECKIN_SECRET, w + 2), CHECKIN_SECRET, now)).toBe(false);
  });
  it('sai secret, sai định dạng, chữ hoa', () => {
    const now = 1_800_000_000_000;
    const w = windowOf(now);
    expect(verifyCode(buildCode('another-secret-value', w), CHECKIN_SECRET, now)).toBe(false);
    expect(verifyCode('KGYMS-CHECKIN:abc:1234', CHECKIN_SECRET, now)).toBe(false);
    expect(verifyCode(buildCode(CHECKIN_SECRET, w).toUpperCase(), CHECKIN_SECRET, now)).toBe(true);
  });
});

describe('quyết định check-in', () => {
  const now = Date.UTC(2026, 10, 19, 3, 0); // 10:00 VN 19/11
  const active: Membership = { id: 'm1', uid: 'u1', packageId: 'basic_1m', trainerUid: '', status: 'active', startDate: '01/11/2026', endDate: '30/11/2026' };
  let n = 0;
  const newId = () => `c${++n}`;

  it('check-in mới khi có gói còn hạn, đóng lượt quên hôm trước lúc 23:59 VN', () => {
    const yesterday = Date.UTC(2026, 10, 18, 12, 0); // 19:00 VN 18/11
    const d = decideCheckIn({
      uid: 'u1',
      checkIns: [{ id: 'old', uid: 'u1', checkInAt: yesterday, checkOutAt: 0 }],
      memberships: [active],
      nowMillis: now,
      newCheckInId: newId
    });
    expect(d.result).toBe('checked_in');
    expect(d.updates['checkIns/old/checkOutAt']).toBe(Date.UTC(2026, 10, 18, 16, 59));
  });
  it('lượt mở trong hôm nay ⇒ check-out', () => {
    const d = decideCheckIn({
      uid: 'u1', checkIns: [{ id: 'c', uid: 'u1', checkInAt: now - 3600_000, checkOutAt: 0 }], memberships: [], nowMillis: now, newCheckInId: newId
    });
    expect(d.result).toBe('checked_out');
    expect(d.updates['checkIns/c/checkOutAt']).toBe(now);
  });
  it('lượt mở lúc 23:30 VN hôm qua không bị coi là hôm nay', () => {
    const d = decideCheckIn({
      uid: 'u1',
      checkIns: [{ id: 'c', uid: 'u1', checkInAt: Date.UTC(2026, 10, 18, 16, 30), checkOutAt: 0 }],
      memberships: [],
      nowMillis: Date.UTC(2026, 10, 18, 17, 10), // 00:10 VN 19/11
      newCheckInId: newId
    });
    expect(d.result).toBe('no_active_membership');
    expect(d.updates['checkIns/c/checkOutAt']).toBe(Date.UTC(2026, 10, 18, 16, 59));
  });
  it('gói chưa bắt đầu hoặc đã hết hạn ⇒ không có gói', () => {
    const d = decideCheckIn({
      uid: 'u1',
      checkIns: [],
      memberships: [{ ...active, startDate: '20/11/2026' }, { ...active, id: 'm2', endDate: '18/11/2026' }],
      nowMillis: now,
      newCheckInId: newId
    });
    expect(d.result).toBe('no_active_membership');
  });
});
