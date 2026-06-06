/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import eslint from 'vite-plugin-eslint';
import { fileURLToPath, URL } from 'node:url';

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
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // Development server configuration
  server: {
    port: 3000,
    // API proxy: forward /api requests to backend server
    // This solves cross-origin issues during frontend-backend local development
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
