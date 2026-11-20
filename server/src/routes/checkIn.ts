import { Router, type RequestHandler } from 'express';
import { uidOf } from '../middleware/firebaseAuth.js';
import { handleCheckIn } from '../services/checkIn.js';
import type { Ctx } from '../services/context.js';

/** POST /check-in { code } (hội viên). */
export function checkInRouter(ctx: Ctx, auth: RequestHandler): Router {
  const router = Router();
  router.post('/check-in', auth, async (req, res) => {
    res.json(await handleCheckIn(ctx, uidOf(res.locals), req.body?.code));
  });
  return router;
}
