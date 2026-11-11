import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  // 开发期代理目标：默认指向本地后端。
  // 走代理后前端请求是同源的，开发期不需要 CORS。
  const devApiTarget = env.VITE_DEV_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: devApiTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
