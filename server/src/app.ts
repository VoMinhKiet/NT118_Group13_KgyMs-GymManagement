/**
 * Điểm vào cho Vercel (tự nhận src/app.ts có default export là Express app).
 * Thiếu biến môi trường ⇒ loadConfig ném lỗi kèm tên biến, function không khởi động.
 */
import { randomInt } from 'node:crypto';
import express from 'express';
import { loadConfig } from './config.js';
import { createApp } from './createApp.js';
import { createFirebaseDeps } from './firebase.js';

const config = loadConfig(process.env);
const { db, auth } = createFirebaseDeps(config);

const app = express();
app.disable('x-powered-by');
app.use(
  createApp(
    {
      db,
      config,
      now: () => Date.now(),
      randomInt: (max) => randomInt(max),
      logger: console
    },
    auth
  )
);

export { config };
export default app;
