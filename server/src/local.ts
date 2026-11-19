/** Chạy server trên máy: `npm run dev`. Vercel không dùng file này. */
import 'dotenv/config';

try {
  const { default: app, config } = await import('./app.js');
  app.listen(config.port, () => {
    console.log(`KgyMs server đang chạy: http://localhost:${config.port}/health`);
  });
} catch (error) {
  console.error(`Không khởi động được server: ${(error as Error).message}`);
  process.exit(1);
}
