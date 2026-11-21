/** Dựng Express app từ các phụ thuộc (Firebase thật hoặc bản giả trong test). */
import express, { type ErrorRequestHandler, type Express } from 'express';
import type { AuthVerifier } from './db.js';
import { HttpError } from './errors.js';
import { firebaseAuth, requireAdmin } from './middleware/firebaseAuth.js';
import { adminRouter } from './routes/admin.js';
import { checkInRouter } from './routes/checkIn.js';
import { healthRouter } from './routes/health.js';
import { paymentsRouter } from './routes/payments.js';
import { pushRouter } from './routes/push.js';
import { webhookRouter } from './routes/webhook.js';
import type { Ctx } from './services/context.js';

export function createApp(ctx: Ctx, auth: AuthVerifier): Express {
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '100kb' }));

  const requireUser = firebaseAuth(auth);
  app.use(healthRouter());
  app.use(webhookRouter(ctx));
  app.use(paymentsRouter(ctx, requireUser));
  app.use(checkInRouter(ctx, requireUser));
  app.use(adminRouter(ctx, requireUser, requireAdmin(ctx.db)));
  app.use(pushRouter(ctx, requireUser, requireAdmin(ctx.db)));

  app.use((_req, res) => {
    res.status(404).json({ error: 'not_found', message: 'Không tìm thấy đường dẫn.' });
  });

  const onError: ErrorRequestHandler = (error, _req, res, _next) => {
    if (error instanceof HttpError) {
      res.status(error.status).json({ error: error.code, message: error.message });
      return;
    }
    if (error?.type === 'entity.parse.failed' || error?.type === 'entity.too.large') {
      res.status(400).json({ error: 'invalid_json', message: 'Body không phải JSON hợp lệ.' });
      return;
    }
    // Chỉ ghi thông điệp lỗi, không ghi body / header (có thể chứa token).
    ctx.logger.error(`[error] ${error?.message ?? error}`);
    res.status(500).json({ error: 'internal', message: 'Lỗi hệ thống, vui lòng thử lại.' });
  };
  app.use(onError);

  return app;
}
