import { defineConfig } from 'vite';

// base './' — нужно, чтобы сборка работала внутри Tauri (файловый протокол)
export default defineConfig({
  base: './',
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    strictPort: true,
    allowedHosts: true,
  },
});
