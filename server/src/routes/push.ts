import { Router, type RequestHandler } from 'express';
import { badRequest } from '../errors.js';
import { uidOf } from '../middleware/firebaseAuth.js';
import type { Ctx } from '../services/context.js';
import { pushStoredNotifications, registerToken, unregisterToken, type PushTarget } from '../services/push.js';

const KEY = /^[A-Za-z0-9_-]{1,64}$/;
const MAX_TARGETS = 500;

/**
 * POST /push/token, DELETE /push/token (hoặc POST /push/token/remove cho client không gửi được body DELETE):
 * đăng ký / huỷ token FCM của thiết bị (người dùng đăng nhập).
 * POST /admin/push (admin): push cho các thông báo admin vừa ghi vào notifications/{uid}/{id}.
 */
export function pushRouter(ctx: Ctx, auth: RequestHandler, admin: RequestHandler): Router {
  const router = Router();

  router.post('/push/token', auth, async (req, res) => {
    await registerToken(ctx, uidOf(res.locals), req.body?.token);
    res.json({ registered: true });
  });

  const remove: RequestHandler = async (req, res) => {
    await unregisterToken(ctx, uidOf(res.locals), req.body?.token);
    res.json({ registered: false });
  };
  router.delete('/push/token', auth, remove);
  router.post('/push/token/remove', auth, remove);

  router.post('/admin/push', auth, admin, async (req, res) => {
    const items: unknown = req.body?.notifications;
    if (!Array.isArray(items) || items.length === 0 || items.length > MAX_TARGETS) {
      throw badRequest('invalid_notifications', `Cần từ 1 đến ${MAX_TARGETS} thông báo.`);
    }
    const targets: PushTarget[] = items.map((item) => {
      const uid = (item as { uid?: unknown })?.uid;
      const id = (item as { id?: unknown })?.id;
      if (typeof uid !== 'string' || typeof id !== 'string' || !KEY.test(uid) || !KEY.test(id)) {
        throw badRequest('invalid_notifications', 'uid hoặc id thông báo không hợp lệ.');
      }
      return { uid, id };
    });
    res.json(await pushStoredNotifications(ctx, targets));
  });

  return router;
}
