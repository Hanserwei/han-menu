import { mergeConfig, defineConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

/** 单元测试不依赖真实浏览器；端到端测试使用独立入口。 */
export default mergeConfig(
  typeof viteConfig === 'function' ? viteConfig({ command: 'serve', mode: 'test' }) : viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      environmentOptions: { jsdom: { url: 'http://localhost/' } },
      include: ['src/**/*.spec.ts'],
      restoreMocks: true,
    },
  }),
)
