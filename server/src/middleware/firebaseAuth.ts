import type { RequestHandler } from 'express';
import type { AuthVerifier, Db } from '../db.js';

/** uid của người gọi, do [firebaseAuth] gắn vào res.locals. */
export const uidOf = (locals: Record<string, unknown>) => locals.uid as string;

/** App → server: "Authorization: Bearer <Firebase ID token>". Sai ⇒ 401. */
export function firebaseAuth(auth: AuthVerifier): RequestHandler {
  return async (req, res, next) => {
    const match = /^Bearer\s+(\S+)$/i.exec(req.get('authorization') ?? '');
    if (!match) {
      res.status(401).json({ error: 'unauthorized', message: 'Bạn cần đăng nhập.' });
      return;
    }
    try {
      const { uid } = await auth.verifyIdToken(match[1]);
      res.locals.uid = uid;
    } catch {
      res.status(401).json({ error: 'unauthorized', message: 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn.' });
      return;
    }
    next();
  };
}

/** Chỉ admin (users/{uid}/role == "admin"). Không phải ⇒ 403. Dùng sau [firebaseAuth]. */
export function requireAdmin(db: Db): RequestHandler {
  return async (_req, res, next) => {
    const role = await db.get(`users/${uidOf(res.locals)}/role`);
    if (role !== 'admin') {
      res.status(403).json({ error: 'forbidden', message: 'Chỉ admin được thực hiện thao tác này.' });
      return;
    }
    next();
  };
}
