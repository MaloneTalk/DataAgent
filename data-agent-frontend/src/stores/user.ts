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

import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('');
  const userInfo = ref<{
    id: string;
    username: string;
    nickname: string;
    avatar?: string;
  } | null>(null);

  const setToken = (newToken: string) => {
    token.value = newToken;
    localStorage.setItem('token', newToken);
  };

  const setUserInfo = (info: typeof userInfo.value) => {
    userInfo.value = info;
  };

  const logout = () => {
    token.value = '';
    userInfo.value = null;
    localStorage.removeItem('token');
  };

  const initToken = () => {
    const savedToken = localStorage.getItem('token');
    if (savedToken) {
      token.value = savedToken;
    }
  };

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    logout,
    initToken,
  };
});
