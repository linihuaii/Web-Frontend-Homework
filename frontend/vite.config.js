import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const host = '127.0.0.1'
const proxy = { '/api': process.env.API_TARGET || 'http://127.0.0.1:8080' }

export default defineConfig({
  plugins: [vue()],
  server: {
    host,
    port: 5173,
    strictPort: true,
    proxy,
  },
  preview: {
    host,
    proxy,
  },
})
