/** Kết nối firebase-admin và cài đặt [Db] / [AuthVerifier] thật. */
import { cert, getApps, initializeApp, type App } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getDatabase } from 'firebase-admin/database';
import { getMessaging } from 'firebase-admin/messaging';
import type { Config } from './config.js';
import type { AuthVerifier, Db } from './db.js';
import type { PushSender } from './services/push.js';

function firebaseApp(config: Config): App {
  const existing = getApps().find((app) => app.name === 'kgyms-server');
  if (existing) return existing;
  return initializeApp(
    {
      credential: cert({
        projectId: config.serviceAccount.project_id,
        clientEmail: config.serviceAccount.client_email,
        privateKey: config.serviceAccount.private_key
      }),
      databaseURL: config.databaseUrl
    },
    'kgyms-server'
  );
}

const DEAD_TOKEN_CODES = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-registration-token']);

export function createFirebaseDeps(config: Config): { db: Db; auth: AuthVerifier; push: PushSender } {
  const app = firebaseApp(config);
  const database = getDatabase(app);
  const auth = getAuth(app);

  const db: Db = {
    async get(path) {
      const snapshot = await database.ref(path).get();
      return snapshot.exists() ? snapshot.val() : null;
    },
    async queryByChild(path, child, value) {
      const snapshot = await database.ref(path).orderByChild(child).equalTo(value).get();
      return (snapshot.val() as Record<string, unknown> | null) ?? {};
    },
    async update(updates) {
      await database.ref().update(updates);
    },
    async transaction(path, apply) {
      const result = await database.ref(path).transaction(apply, undefined, false);
      return { committed: result.committed, value: result.snapshot.val() };
    },
    async remove(path) {
      await database.ref(path).remove();
    },
    pushKey(path) {
      const key = database.ref(path).push().key;
      if (!key) throw new Error(`Không tạo được key cho ${path}`);
      return key;
    }
  };

  const verifier: AuthVerifier = {
    async verifyIdToken(token) {
      const decoded = await auth.verifyIdToken(token);
      return { uid: decoded.uid };
    }
  };

  const messaging = getMessaging(app);
  const push: PushSender = {
    async send(tokens, data) {
      const invalidTokens: string[] = [];
      for (let i = 0; i < tokens.length; i += 500) {
        const batch = tokens.slice(i, i + 500);
        // Data-only + priority high: app (KgyMsMessagingService) tự hiện thông báo theo công tắc của người dùng.
        const response = await messaging.sendEachForMulticast({
          tokens: batch,
          data,
          android: { priority: 'high', ttl: 24 * 60 * 60 * 1000 }
        });
        response.responses.forEach((r, index) => {
          if (!r.success && DEAD_TOKEN_CODES.has(r.error?.code ?? '')) invalidTokens.push(batch[index]);
        });
      }
      return { invalidTokens };
    }
  };

  return { db, auth: verifier, push };
}
