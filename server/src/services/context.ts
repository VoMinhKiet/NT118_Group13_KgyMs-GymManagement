import type { Config } from '../config.js';
import type { Db } from '../db.js';
import type { PushSender } from './push.js';

/** Phụ thuộc dùng chung cho các service; [now] và [randomInt] tách ra để test. */
export interface Ctx {
  db: Db;
  /** Gửi FCM; bản thật dùng firebase-admin (firebase.ts), test dùng bản giả. */
  push: PushSender;
  config: Pick<Config, 'sepayApiKey' | 'checkInSecret' | 'bankAccountNo' | 'paymentExpiryMinutes'>;
  now: () => number;
  randomInt: (max: number) => number;
  logger: Pick<Console, 'info' | 'warn' | 'error'>;
}
