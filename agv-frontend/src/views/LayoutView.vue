<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const menus = computed(() => {
  const base = [
    { path: '/dashboard', title: '概览' },
    { path: '/roadnet', title: '车间地图' },
    { path: '/tasks', title: '任务集' },
    { path: '/simulation', title: '仿真运行' },
    { path: '/experiments', title: '对比实验' }
  ]
  if (auth.isAdmin) {
    base.push({ path: '/admin/users', title: '用户管理' })
    base.push({ path: '/admin/dict', title: '数字字典' })
  }
  return base
})

const currentTitle = computed(() => (route.meta.title as string) || '')

function logout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">排策智演</div>
      <el-menu :default-active="route.path" router class="menu">
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
          {{ menu.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ currentTitle }}</div>
        <div class="header-right">
          <el-tag size="small" :type="auth.isAdmin ? 'danger' : 'success'">
            {{ auth.isAdmin ? '管理员' : '调度人员' }}
          </el-tag>
          <span class="username">{{ auth.displayName }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #101c33;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  letter-spacing: 3px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.menu {
  border-right: none;
  background: transparent;
  flex: 1;
}

.menu :deep(.el-menu-item) {
  color: #b8c4d9;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}

.menu :deep(.el-menu-item.is-active) {
  background: #1f3b73;
  color: #fff;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-size: 14px;
  color: #606266;
}

.main {
  padding: 20px;
  scrollbar-gutter: stable;
}
</style>
