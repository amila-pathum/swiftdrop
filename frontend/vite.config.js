import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Port 3000 matches the API Gateway's default FRONTEND_ORIGIN (CORS).
// strictPort fails fast instead of silently moving to a port the Gateway rejects.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
  },
  preview: {
    port: 3000,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/setupTests.js',
    // Tests must not depend on a developer's .env.local; tests that need
    // Google sign-in enable it themselves.
    env: {
      VITE_GOOGLE_LOGIN_ENABLED: 'false',
    },
  },
})
