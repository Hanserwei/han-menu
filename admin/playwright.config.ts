import { defineConfig } from '@playwright/test'

/** 端到端测试启动专用后端及 Vite，永不复用用户正在运行的开发服务。 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  use: {
    baseURL: 'http://127.0.0.1:15173',
    viewport: { width: 1440, height: 1024 },
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'node scripts/e2e-server.mjs',
      url: 'http://127.0.0.1:18081/actuator/health',
      reuseExistingServer: false,
      timeout: 120000,
    },
    {
      command: 'pnpm dev --port 15173',
      url: 'http://127.0.0.1:15173',
      env: { ADMIN_API_TARGET: 'http://127.0.0.1:18081' },
      reuseExistingServer: false,
    },
  ],
})
