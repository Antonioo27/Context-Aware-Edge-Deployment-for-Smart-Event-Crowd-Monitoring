import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // Sostituisci con l'IP restituito da "minikube -p edge-cluster ip"
        target: 'http://192.168.58.2:30080',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});