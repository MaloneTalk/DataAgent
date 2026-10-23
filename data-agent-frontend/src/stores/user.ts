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
import { fetchMe, login as loginApi, type LoginRequest, type UserInfoResponse } from '@/api/auth';

const TOKEN_KEY = 'token';

export const useUserStore = defineStore('user', () => {
  const token = ref('');
  const userInfo = ref<UserInfoResponse | null>(null);
  const isLoggedIn = computed(() => Boolean(token.value));

  function hasPermission(code: string) {
    return Boolean(userInfo.value?.superAdmin || userInfo.value?.permissions.includes(code));
  }

  async function refreshPermissions() {
    userInfo.value = await fetchMe();
  }

  async function login(payload: LoginRequest) {
    const result = await loginApi(payload);
    token.value = result.token;
    localStorage.setItem(TOKEN_KEY, result.token);
    userInfo.value = result.user;
    return result;
  }

  function logout() {
    token.value = '';
    userInfo.value = null;
    localStorage.removeItem(TOKEN_KEY);
  }

  /** 仅恢复凭证，用户与权限在首次路由访问时从服务器加载。 */
  function restore() {
    token.value = localStorage.getItem(TOKEN_KEY) ?? '';
    localStorage.removeItem('userInfo'); // 清理旧版本保存的权限快照。
  }

  return { token, userInfo, isLoggedIn, hasPermission, refreshPermissions, login, logout, restore };
});
