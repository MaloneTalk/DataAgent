<script setup lang="ts">
import { ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const isCollapse = ref(false);

const menuItems = [
  { path: "/data-source", title: "数据源管理", icon: "Connection" },
];

const activeMenu = computed(() => route.path);

const handleMenuSelect = (path: string) => {
  router.push(path);
};

const toggleSidebar = () => {
  isCollapse.value = !isCollapse.value;
};
</script>

<template>
  <aside class="app-sidebar" :class="{ collapsed: isCollapse }">
    <el-menu
      :default-active="activeMenu"
      :collapse="isCollapse"
      router
      @select="handleMenuSelect"
    >
      <el-menu-item
        v-for="item in menuItems"
        :key="item.path"
        :index="item.path"
      >
        <span>{{ item.title }}</span>
      </el-menu-item>
    </el-menu>
    <div class="sidebar-toggle" @click="toggleSidebar">
      <span>{{ isCollapse ? "»" : "«" }}</span>
    </div>
  </aside>
</template>

<style scoped>
.app-sidebar {
  width: 200px;
  background-color: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  position: relative;
}

.app-sidebar.collapsed {
  width: 64px;
}

.app-sidebar :deep(.el-menu) {
  border-right: none;
}

.sidebar-toggle {
  position: absolute;
  bottom: 20px;
  right: 0;
  width: 100%;
  text-align: center;
  cursor: pointer;
  color: #909399;
  font-size: 16px;
  transform: translateX(-50%);
}
</style>
