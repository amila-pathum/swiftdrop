import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
export default defineConfig(({ mode }) => {
  const env=loadEnv(mode,'..','VITE_')
  return { plugins:[react()],server:{host:'127.0.0.1',port:3001,strictPort:true,
    proxy:{'/api':{target:env.VITE_STYLECART_BACKEND_URL||'https://127.0.0.1:8090',changeOrigin:true,secure:false}}},
    test:{environment:'jsdom',setupFiles:'./src/setupTests.js'},
  }
})
