import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vite 配置。
 *
 * - dev server 固定 5173 端口；
 * - /api 代理到后端 http://localhost:8080，开发期免跨域（生产由部署层处理）；
 * - @ 别名指向 src，简化导入路径。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 前端所有 /api 请求转发到后端，避免开发期跨域
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
