import type { Config } from '../config.js';
import type { Db } from '../db.js';

/** Phụ thuộc dùng chung cho các service; [now] và [randomInt] tách ra để test. */
export interface Ctx {
  db: Db;
  config: Pick<Config, 'sepayApiKey' | 'checkInSecret' | 'bankAccountNo' | 'paymentExpiryMinutes'>;
  now: () => number;
  randomInt: (max: number) => number;
  logger: Pick<Console, 'info' | 'warn' | 'error'>;
}
