import { Router } from 'express';
import { sepayAuth } from '../middleware/sepayAuth.js';
import type { Ctx } from '../services/context.js';
import { handleSepayWebhook } from '../services/webhook.js';

/** POST /webhooks/sepay — SePay gọi khi có giao dịch. Thành công: 200 { success: true }. */
export function webhookRouter(ctx: Ctx): Router {
  const router = Router();
  router.post('/webhooks/sepay', sepayAuth(ctx.config.sepayApiKey, ctx.logger), async (req, res) => {
    const outcome = await handleSepayWebhook(ctx, req.body);
    if (outcome.kind === 'invalid') {
      ctx.logger.warn(`[webhook] Payload không hợp lệ: ${outcome.message}`);
      res.status(400).json({ success: false, error: 'invalid_payload', message: outcome.message });
      return;
    }
    if (outcome.kind === 'duplicate') ctx.logger.info('[webhook] Giao dịch đã xử lý trước đó, bỏ qua.');
    res.status(200).json({ success: true });
  });
  return router;
}
