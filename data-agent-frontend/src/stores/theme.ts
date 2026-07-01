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

import { ref, watch } from 'vue';
import { defineStore } from 'pinia';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'dataagent-theme';
const THEME_ATTR = 'data-theme';

function applyTheme(mode: ThemeMode) {
  globalThis.document.documentElement.setAttribute(THEME_ATTR, mode);
}

function getSystemPreference(): ThemeMode {
  if (globalThis.matchMedia?.('(prefers-color-scheme: dark)').matches) {
    return 'dark';
  }
  return 'light';
}

function loadStoredTheme(): ThemeMode {
  try {
    const raw = globalThis.localStorage.getItem(STORAGE_KEY);
    if (raw === 'light' || raw === 'dark') return raw;
  } catch {
    // ignore
  }
  return getSystemPreference();
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(loadStoredTheme());

  applyTheme(mode.value);

  watch(mode, val => {
    applyTheme(val);
    try {
      globalThis.localStorage.setItem(STORAGE_KEY, val);
    } catch {
      // ignore
    }
  });

  function toggle() {
    mode.value = mode.value === 'light' ? 'dark' : 'light';
  }

  function setMode(next: ThemeMode) {
    mode.value = next;
  }

  return { mode, toggle, setMode };
});
