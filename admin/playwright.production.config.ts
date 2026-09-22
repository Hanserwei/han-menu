import { defineConfig } from '@playwright/test'
/** 第二轮独立schema验收生产dist、TLS和Nginx，不启动Vite，不关闭正式应用的证书验证。 */
export default defineConfig({
  testDir: './e2e-production',
  outputDir: 'test-results/production',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  use: {
    baseURL: 'https://127.0.0.1:15174',
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1024 },
    trace: 'off',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'node scripts/e2e-server.mjs',
      url: 'http://127.0.0.1:18081/actuator/health',
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 30000 },
      timeout: 120000,
    },
    {
      command: 'node scripts/nginx-test-server.mjs',
      url: 'https://127.0.0.1:15174',
      ignoreHTTPSErrors: true,
      reuseExistingServer: false,
      gracefulShutdown: { signal: 'SIGTERM', timeout: 30000 },
      timeout: 120000,
    },
  ],
})
