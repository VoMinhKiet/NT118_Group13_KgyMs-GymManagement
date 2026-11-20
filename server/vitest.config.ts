import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['test/**/*.test.ts'],
    // Chạy test như trên Vercel (UTC) để bắt lỗi múi giờ.
    env: { TZ: 'UTC' }
  }
});
