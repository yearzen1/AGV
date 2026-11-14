<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import NavMenu from '@/components/NavMenu.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const menus = computed(() => {
  const base = [
    { path: '/dashboard', title: '概览', icon: 'dashboard' },
    { path: '/roadnet', title: '车间地图', icon: 'map' },
    { path: '/tasks', title: '任务集', icon: 'tasks' },
    { path: '/simulation', title: '仿真运行', icon: 'simulation' },
    { path: '/experiments', title: '对比实验', icon: 'chart' }
  ]
  if (auth.isAdmin) {
    base.push({ path: '/admin/users', title: '用户管理', icon: 'users' })
    base.push({ path: '/admin/dict', title: '数字字典', icon: 'dict' })
  }
  return base
})

const currentTitle = computed(() => (route.meta.title as string) || '')
const currentSubtitle = computed(() => (route.meta.subtitle as string) || '')

/** ≤900px 时侧边栏收进抽屉，避免窄屏被固定 220px 导航挤占主内容。 */
const isCompact = ref(false)
const drawerVisible = ref(false)
let mediaQuery: MediaQueryList | null = null

function syncCompact(event: MediaQueryList | MediaQueryListEvent) {
  isCompact.value = event.matches
  if (!event.matches) drawerVisible.value = false
}

onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 900px)')
  syncCompact(mediaQuery)
  mediaQuery.addEventListener('change', syncCompact)
})

onBeforeUnmount(() => {
  mediaQuery?.removeEventListener('change', syncCompact)
})

watch(
  () => route.path,
  () => {
    drawerVisible.value = false
  }
)

function logout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>

  <el-container class="layout">
    <el-aside v-if="!isCompact" width="220px" class="aside">
      <div class="logo">
        <span class="logo-mark" aria-hidden="true">排</span>
        <span class="logo-text">排策智演</span>
      </div>
      <NavMenu :menus="menus" />
      <div class="aside-foot">
        <span>AGV 调度仿真</span>
        <span class="aside-version">v1.0</span>
      </div>
    </el-aside>

    <el-container class="content">
      <el-header class="header">
        <div class="header-left">
          <button
            v-if="isCompact"
            type="button"
            class="icon-button"
            aria-label="打开菜单"
            aria-controls="nav-drawer"
            :aria-expanded="drawerVisible"
            @click="drawerVisible = true"
          >
            <AppIcon name="menu" :size="20" />
          </button>
          <h1 class="header-title">
            {{ currentTitle }}
            <span v-if="currentSubtitle" class="header-subtitle">{{ currentSubtitle }}</span>
          </h1>
        </div>
        <div class="header-right">
          <el-tag size="small" :type="auth.isAdmin ? 'danger' : 'success'">
            {{ auth.isAdmin ? '管理员' : '调度人员' }}
          </el-tag>
          <span class="username">{{ auth.displayName }}</span>
          <el-button link type="primary" @click="logout">
            <AppIcon name="logout" :size="16" />
            <span class="logout-text">退出</span>
          </el-button>
        </div>
      </el-header>

      <el-main id="main-content" class="main" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>

    <el-drawer
      id="nav-drawer"
      v-model="drawerVisible"
      direction="ltr"
      size="248px"
      :with-header="false"
      class="nav-drawer"
    >
      <div class="drawer-head">
        <div class="logo">
          <span class="logo-mark" aria-hidden="true">排</span>
          <span class="logo-text">排策智演</span>
        </div>
        <button type="button" class="icon-button drawer-close" aria-label="关闭菜单" @click="drawerVisible = false">
          <AppIcon name="close" :size="18" />
        </button>
      </div>
      <NavMenu :menus="menus" @select="drawerVisible = false" />
    </el-drawer>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
  background: var(--agv-surface-muted);
}

.aside {
  display: flex;
  flex-direction: column;
  background: var(--agv-brand-strong);
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 62px;
  padding: 0 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--agv-brand);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.logo-text {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 2px;
}

.aside-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  color: #7f8ca3;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.aside-version {
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  font-family: Consolas, Monaco, monospace;
}

.content {
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 62px;
  background: var(--agv-surface);
  border-bottom: 1px solid var(--agv-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  white-space: nowrap;
}

.header-subtitle {
  color: var(--agv-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  color: #4b5563;
  font-size: 14px;
}

.icon-button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  color: var(--agv-brand);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
}

.icon-button:hover {
  background: var(--agv-surface-muted);
  border-color: var(--agv-border);
}

.main {
  padding: 20px;
  scrollbar-gutter: stable;
  outline: none;
}

@media (max-width: 900px) {
  .header {
    padding-inline: 12px;
  }

  .main {
    padding: 12px;
  }

  .username {
    display: none;
  }
}

@media (max-width: 560px) {
  .logout-text {
    display: none;
  }
}

/* Drawer 被 teleport 到 body，样式需要全局作用域 */
:global(.nav-drawer) {
  background: var(--agv-brand-strong);
}

:global(.nav-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  padding: 0;
  background: var(--agv-brand-strong);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline-end: 10px;
}

.drawer-close {
  color: #b8c4d9;
}
</style>
