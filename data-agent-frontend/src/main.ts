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

import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import router from './router';
import { permission } from './directives/permission';
import App from './App.vue';
import { useUserStore } from './stores/user';
import './styles/theme.css';
import './styles/main.css';

const app = createApp(App);

const pinia = createPinia();
app.use(pinia);
app.directive('permission', permission);
// 启动时从 localStorage 恢复登录态（路由守卫据此决定是否放行）。
useUserStore(pinia).restore();
app.use(router);
app.use(ElementPlus, { locale: zhCn });

window.addEventListener('permissions-changed', async () => {
  const store = useUserStore(pinia);
  if (store.isLoggedIn) {
    try {
      await store.refreshPermissions();
      const current = router.currentRoute.value;
      await router.replace({
        path: current.path,
        query: current.query,
        hash: current.hash,
        force: true,
      });
    } catch {
      /* request handler displays errors */
    }
  }
});

app.mount('#app');
