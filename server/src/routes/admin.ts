import { Router, type RequestHandler } from 'express';
import { badRequest } from '../errors.js';
import { uidOf } from '../middleware/firebaseAuth.js';
import type { Ctx } from '../services/context.js';
import { resolveReconciliation } from '../services/reconciliation.js';

/** POST /admin/reconciliation/:id/resolve (admin). */
export function adminRouter(ctx: Ctx, auth: RequestHandler, admin: RequestHandler): Router {
  const router = Router();
  router.post('/admin/reconciliation/:id/resolve', auth, admin, async (req, res) => {
    const id = String(req.params.id);
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(id)) throw badRequest('invalid_id', 'Mã giao dịch không hợp lệ.');
    res.json(await resolveReconciliation(ctx, uidOf(res.locals), id, req.body));
  });
  return router;
}
