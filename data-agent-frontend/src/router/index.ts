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

import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useUserStore } from '@/stores/user';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    redirect: '/chat',
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/chat/ChatView.vue'),
    meta: { title: 'AI 智能分析' },
  },
  {
    path: '/chat/:sessionId',
    name: 'ChatSession',
    component: () => import('@/views/chat/ChatView.vue'),
    meta: { title: 'AI 智能分析' },
  },
  {
    path: '/data-source',
    name: 'DataSource',
    component: () => import('@/views/data-source/DataSource.vue'),
    meta: { title: '数据源管理' },
  },
  {
    path: '/semantic',
    name: 'SemanticManage',
    component: () => import('@/views/semantic/SemanticManage.vue'),
    meta: { title: '语义管理' },
  },
  {
    path: '/report',
    name: 'ReportList',
    component: () => import('@/views/report/ReportList.vue'),
    meta: { title: '报告管理' },
  },
  {
    path: '/metric',
    name: 'MetricManage',
    component: () => import('@/views/metric/MetricManage.vue'),
    meta: { title: '指标口径管理' },
  },
  {
    path: '/scheduled-task',
    name: 'ScheduledTaskManage',
    component: () => import('@/views/scheduled-task/ScheduledTaskManage.vue'),
    meta: { title: '定时任务' },
  },
  {
    path: '/sys-user',
    name: 'UserManage',
    component: () => import('@/views/sys-user/UserManage.vue'),
    meta: { title: '用户管理' },
  },
  {
    path: '/sys-role',
    name: 'RoleManage',
    component: () => import('@/views/sys-role/RoleManage.vue'),
    meta: { title: '角色管理' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'Data Agent'}`;
  const userStore = useUserStore();
  if (!userStore.isLoggedIn && to.path !== '/login') {
    next('/login');
    return;
  }
  if (userStore.isLoggedIn && to.path === '/login') {
    next('/chat');
    return;
  }
  next();
});

export default router;
