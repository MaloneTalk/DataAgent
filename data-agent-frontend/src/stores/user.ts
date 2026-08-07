/*
 * Copyright (C) 2026 github.com/MaloneTalk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * limitations under the License.
 */

import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { login as loginApi, type LoginRequest, type UserInfoResponse } from '@/api/auth';

const TOKEN_KEY = 'token';
const USER_KEY = 'userInfo';

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('');
  const userInfo = ref<UserInfoResponse | null>(null);

  const isLoggedIn = computed(() => Boolean(token.value));

  function setToken(newToken: string) {
    token.value = newToken;
    localStorage.setItem(TOKEN_KEY, newToken);
  }

  function setUserInfo(info: UserInfoResponse | null) {
    userInfo.value = info;
    if (info) {
      localStorage.setItem(USER_KEY, JSON.stringify(info));
    } else {
      localStorage.removeItem(USER_KEY);
    }
  }

  async function login(payload: LoginRequest) {
    const result = await loginApi(payload);
    setToken(result.token);
    setUserInfo(result.user);
    return result;
  }

  function logout() {
    token.value = '';
    userInfo.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  /** 启动时从 localStorage 恢复 token/用户信息（不校验有效性，401 时由请求层清空并跳登录）。 */
  function restore() {
    const savedToken = localStorage.getItem(TOKEN_KEY);
    if (savedToken) {
      token.value = savedToken;
    }
    const savedUser = localStorage.getItem(USER_KEY);
    if (savedUser) {
      try {
        userInfo.value = JSON.parse(savedUser) as UserInfoResponse;
      } catch {
        localStorage.removeItem(USER_KEY);
      }
    }
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    setToken,
    setUserInfo,
    login,
    logout,
    restore,
  };
});
