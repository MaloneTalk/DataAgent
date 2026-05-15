import { createRouter, createWebHistory } from "vue-router";
import type { RouteRecordRaw } from "vue-router";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    redirect: "/data-source",
  },
  {
    path: "/data-source",
    name: "DataSource",
    component: () => import("@/views/data-source/DataSource.vue"),
    meta: { title: "数据源管理" },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || "Data Agent"}`;
  next();
});

export default router;
