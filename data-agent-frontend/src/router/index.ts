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
    redirect: '/semantic/domain',
  },
  {
    path: '/semantic/domain',
    name: 'DomainManage',
    component: () => import('@/views/semantic/components/DomainManage.vue'),
    meta: { title: '数据领域' },
  },
  {
    path: '/semantic/table',
    name: 'TableSemanticManage',
    component: () => import('@/views/semantic/components/TableSemanticManage.vue'),
    meta: { title: '表语义' },
  },
  {
    path: '/semantic/metric',
    name: 'MetricManage',
    component: () => import('@/views/metric/MetricManage.vue'),
    meta: { title: '指标口径' },
  },
  {
    path: '/semantic/relation',
    name: 'RelationManage',
    component: () => import('@/views/semantic/components/RelationManage.vue'),
    meta: { title: '逻辑外键' },
  },
  {
    path: '/asset',
    redirect: '/asset/report',
  },
  {
    path: '/asset/report',
    name: 'ReportList',
    component: () => import('@/views/report/ReportList.vue'),
    meta: { title: '报告管理' },
  },
  {
    path: '/asset/table-export',
    name: 'TableExportList',
    component: () => import('@/views/table-export/TableExportList.vue'),
    meta: { title: '表格导出' },
  },
  {
    path: '/system',
    redirect: '/system/user',
  },
  {
    path: '/system/user',
    name: 'UserManage',
    component: () => import('@/views/sys-user/UserManage.vue'),
    meta: { title: '用户管理' },
  },
  {
    path: '/system/role',
    name: 'RoleManage',
    component: () => import('@/views/sys-role/RoleManage.vue'),
    meta: { title: '角色管理' },
  },
  {
    path: '/metric',
    redirect: '/semantic/metric',
  },
  {
    path: '/sys-user',
    redirect: '/system/user',
  },
  {
    path: '/sys-role',
    redirect: '/system/role',
  },
  {
    path: '/report',
    redirect: '/asset/report',
  },
  {
    path: '/table-export',
    redirect: '/asset/table-export',
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
