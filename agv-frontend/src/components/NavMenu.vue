<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'

defineProps<{
  menus: Array<{ path: string; title: string; icon: string }>
}>()

const emit = defineEmits<{ select: [] }>()
const route = useRoute()
</script>

<template>
  <nav aria-label="主导航" class="nav">
    <el-menu :default-active="route.path" router class="menu" @select="emit('select')">
      <el-menu-item
        v-for="menu in menus"
        :key="menu.path"
        :index="menu.path"
        :aria-current="route.path === menu.path ? 'page' : undefined"
      >
        <AppIcon :name="menu.icon" />
        <span class="label">{{ menu.title }}</span>
      </el-menu-item>
    </el-menu>
  </nav>
</template>

<style scoped>
.nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.menu {
  border-right: none;
  background: transparent;
  padding: 8px;
}

.menu :deep(.el-menu-item) {
  height: 44px;
  margin-bottom: 2px;
  border-radius: 8px;
  color: #b8c4d9;
  gap: 10px;
  transition: background 0.16s ease, color 0.16s ease;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-menu-item:focus-visible) {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--agv-brand);
  color: #fff;
  font-weight: 600;
}

.menu :deep(.el-menu-item.is-active)::after {
  content: '';
  position: absolute;
  inset-inline-end: 8px;
  inline-size: 4px;
  block-size: 18px;
  border-radius: 999px;
  background: #8fb4ff;
}

.label {
  line-height: 1.2;
}
</style>
