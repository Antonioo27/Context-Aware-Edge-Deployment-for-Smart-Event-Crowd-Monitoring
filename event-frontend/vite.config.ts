import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Carica le variabili d'ambiente dalla root del progetto
  const env = loadEnv(mode, path.resolve(__dirname, '..'), '');
  
  // Usa la variabile VITE_PROXY_TARGET se definita, altrimenti il default
  const target = env.VITE_PROXY_TARGET || 'http://192.168.58.2:30080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: target,
          changeOrigin: true,
          secure: false,
        },
      },
    },
  };
});