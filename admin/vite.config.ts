import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/** 同源开发代理只读取公开配置，不向浏览器暴露后端 .env 或凭证。 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'ADMIN_')
  return {
    plugins: [vue()],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.ADMIN_API_TARGET || 'http://127.0.0.1:8080',
          changeOrigin: true,
          ws: true,
        },
      },
    },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  }
})
