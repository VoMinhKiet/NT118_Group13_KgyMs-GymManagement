import { Router, type RequestHandler } from 'express';
import { badRequest } from '../errors.js';
import { uidOf } from '../middleware/firebaseAuth.js';
import type { Ctx } from '../services/context.js';
import { cancelInvoice, createInvoice } from '../services/payments.js';

/** POST /payments, POST /payments/:id/cancel (hội viên, cần [auth] chạy trước). */
export function paymentsRouter(ctx: Ctx, auth: RequestHandler): Router {
  const router = Router();

  router.post('/payments', auth, async (req, res) => {
    const result = await createInvoice(ctx, uidOf(res.locals), req.body?.packageId);
    res.json(result);
  });

  router.post('/payments/:id/cancel', auth, async (req, res) => {
    const id = String(req.params.id);
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(id)) throw badRequest('invalid_payment_id', 'Mã hoá đơn không hợp lệ.');
    await cancelInvoice(ctx, uidOf(res.locals), id);
    res.json({ paymentId: id, status: 'cancelled' });
  });

  return router;
}
