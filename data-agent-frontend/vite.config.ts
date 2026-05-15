import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import eslint from "vite-plugin-eslint";
import { resolve } from "path";

/**
 * Vite configuration file
 * Similar to application.properties/application.yml in Spring Boot
 * Defines build, dev server, and plugin settings
 */
export default defineConfig({
  // Enable Vue 3 support
  plugins: [vue(), eslint()],
  // Path alias configuration: '@' -> 'src' directory
  // Allows importing like: import xxx from '@/components/xxx'
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
    },
  },
  // Development server configuration
  server: {
    port: 3000,
    // API proxy: forward /api requests to backend server
    // This solves cross-origin issues during frontend-backend local development
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
