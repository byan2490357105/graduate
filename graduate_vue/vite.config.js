import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/getBZoneRegion': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/stopCrawler': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/isCrawlerRunning': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/batchGetZoneComment': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/getComment': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/tag': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/comment': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
