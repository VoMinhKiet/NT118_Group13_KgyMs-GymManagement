/**
 * Ngày giờ theo giờ Việt Nam, tính tường minh bằng Intl (Vercel chạy UTC, không dựa vào biến TZ).
 *
 * Định dạng trên Firebase (giống AppDates.kt):
 * - thời điểm: "HH:mm dd/MM/yyyy"
 * - ngày:      "dd/MM/yyyy"
 *
 * "Ngày" (DayValue) trong file này là millis lúc 00:00 UTC của **ngày lịch Việt Nam** tương ứng,
 * chỉ dùng để so sánh/cộng ngày; không phải một thời điểm thật.
 */

export const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';
const DAY_MS = 86_400_000;

export type DayValue = number;

const formatter = new Intl.DateTimeFormat('en-GB', {
  timeZone: VN_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23'
});

interface VnParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function vnParts(millis: number): VnParts {
  const parts: Record<string, string> = {};
  for (const part of formatter.formatToParts(new Date(millis))) parts[part.type] = part.value;
  return {
    year: Number(parts.year),
    month: Number(parts.month),
    day: Number(parts.day),
    hour: Number(parts.hour),
    minute: Number(parts.minute),
    second: Number(parts.second)
  };
}

const pad = (value: number, length = 2) => String(value).padStart(length, '0');

/** "HH:mm dd/MM/yyyy" theo giờ Việt Nam. */
export function timestamp(millis: number): string {
  const p = vnParts(millis);
  return `${pad(p.hour)}:${pad(p.minute)} ${pad(p.day)}/${pad(p.month)}/${pad(p.year, 4)}`;
}

/** Ngày lịch Việt Nam chứa thời điểm [millis]. */
export function vnDay(millis: number): DayValue {
  const p = vnParts(millis);
  return Date.UTC(p.year, p.month - 1, p.day);
}

/** DayValue → "dd/MM/yyyy". */
export function formatDay(day: DayValue): string {
  const d = new Date(day);
  return `${pad(d.getUTCDate())}/${pad(d.getUTCMonth() + 1)}/${pad(d.getUTCFullYear(), 4)}`;
}

/** "dd/MM/yyyy" → DayValue; null nếu sai định dạng hoặc ngày không tồn tại. */
export function parseDay(text: unknown): DayValue | null {
  if (typeof text !== 'string') return null;
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text.trim());
  if (!match) return null;
  const [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const value = Date.UTC(year, month - 1, day);
  const d = new Date(value);
  if (d.getUTCFullYear() !== year || d.getUTCMonth() !== month - 1 || d.getUTCDate() !== day) return null;
  return value;
}

export function addDays(day: DayValue, days: number): DayValue {
  return day + days * DAY_MS;
}

/** Thời điểm thật (millis) của giờ [hour]:[minute] ngày lịch VN [day]. */
function vnWallTimeToMillis(day: DayValue, hour: number, minute: number): number {
  const wall = day + (hour * 60 + minute) * 60_000;
  // Độ lệch của múi giờ VN tại thời điểm đó (VN không có giờ mùa hè, nhưng vẫn tính thay vì gán cứng).
  const p = vnParts(wall);
  const offset = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second) - wall;
  return wall - offset;
}

/** 00:00 giờ VN của ngày chứa [millis], dạng millis thật (dùng với checkIns). */
export function startOfVnDayMillis(millis: number): number {
  return vnWallTimeToMillis(vnDay(millis), 0, 0);
}

/** 23:59:00 giờ VN của ngày chứa [millis], dạng millis thật (đóng lượt quên check-out). */
export function endOfVnDayMillis(millis: number): number {
  return vnWallTimeToMillis(vnDay(millis), 23, 59);
}
