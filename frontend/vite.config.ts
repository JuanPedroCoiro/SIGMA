import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { msw } from 'msw/vite'

export default defineConfig({
  // msw sirve el service worker de la API simulada; solo se usa en `npm run dev:simulado`
  // y no entra en el build (ver src/main.tsx).
  plugins: [react(), msw()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
