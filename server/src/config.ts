/**
 * Đọc biến môi trường. Thiếu biến bắt buộc ⇒ ném lỗi kèm **tên** biến thiếu (không bao giờ in giá trị).
 */
export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

export interface Config {
  serviceAccount: ServiceAccount;
  databaseUrl: string;
  sepayApiKey: string;
  checkInSecret: string;
  bankAccountNo: string;
  paymentExpiryMinutes: number;
  port: number;
}

const REQUIRED = [
  'FIREBASE_SERVICE_ACCOUNT_BASE64',
  'FIREBASE_DATABASE_URL',
  'SEPAY_WEBHOOK_API_KEY',
  'CHECKIN_SECRET',
  'BANK_ACCOUNT_NO'
] as const;

export class ConfigError extends Error {}

export function loadConfig(env: NodeJS.ProcessEnv): Config {
  const missing = REQUIRED.filter((name) => !env[name]?.trim());
  if (missing.length > 0) {
    throw new ConfigError(`Thiếu biến môi trường bắt buộc: ${missing.join(', ')}`);
  }

  let serviceAccount: ServiceAccount;
  try {
    const json = Buffer.from(env.FIREBASE_SERVICE_ACCOUNT_BASE64!.trim(), 'base64').toString('utf8');
    serviceAccount = JSON.parse(json);
  } catch {
    throw new ConfigError('FIREBASE_SERVICE_ACCOUNT_BASE64 không phải base64 của một file JSON hợp lệ.');
  }
  if (!serviceAccount?.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
    throw new ConfigError('FIREBASE_SERVICE_ACCOUNT_BASE64 thiếu project_id / client_email / private_key.');
  }

  const sepayApiKey = env.SEPAY_WEBHOOK_API_KEY!.trim();
  if (sepayApiKey.length < 32) throw new ConfigError('SEPAY_WEBHOOK_API_KEY quá ngắn (cần ít nhất 32 ký tự).');

  const checkInSecret = env.CHECKIN_SECRET!.trim();
  if (checkInSecret.length < 16) throw new ConfigError('CHECKIN_SECRET quá ngắn (cần ít nhất 16 ký tự).');

  const expiry = Number(env.PAYMENT_EXPIRY_MINUTES?.trim() || '15');
  if (!Number.isInteger(expiry) || expiry <= 0) throw new ConfigError('PAYMENT_EXPIRY_MINUTES phải là số nguyên dương.');

  const port = Number(env.PORT?.trim() || '3000');
  if (!Number.isInteger(port) || port <= 0) throw new ConfigError('PORT không hợp lệ.');

  return {
    serviceAccount,
    databaseUrl: env.FIREBASE_DATABASE_URL!.trim(),
    sepayApiKey,
    checkInSecret,
    bankAccountNo: env.BANK_ACCOUNT_NO!.trim(),
    paymentExpiryMinutes: expiry,
    port
  };
}
