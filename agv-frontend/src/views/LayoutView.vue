<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
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
const initial = computed(() => (auth.displayName || auth.user?.username || 'U').slice(0, 1).toUpperCase())
const roleLabel = computed(() => (auth.isAdmin ? '管理员' : '调度人员'))

const mobileOpen = ref(false)
const userMenuOpen = ref(false)
const mobileClose = ref<HTMLButtonElement | null>(null)

watch(
  () => route.path,
  () => {
    mobileOpen.value = false
    userMenuOpen.value = false
  }
)

watch(mobileOpen, async (open) => {
  if (open) {
    await nextTick()
    mobileClose.value?.focus()
  }
})

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  if (userMenuOpen.value) userMenuOpen.value = false
  if (mobileOpen.value) mobileOpen.value = false
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function logout() {
  userMenuOpen.value = false
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>

  <div class="flex h-full min-h-0 bg-background">
    <aside class="hidden w-[76px] shrink-0 flex-col border-r border-border bg-white md:flex lg:w-[232px]">
      <div class="flex h-16 items-center justify-center gap-2.5 border-b border-border px-4 lg:justify-start">
        <span
          class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-sm font-bold text-white shadow-sm"
          aria-hidden="true"
        >
          排
        </span>
        <span class="hidden text-[17px] font-semibold tracking-wide text-slate-900 lg:inline">排策智演</span>
      </div>

      <NavMenu :menus="menus" />

      <div class="border-t border-border p-3">
        <div class="flex items-center justify-center gap-2 rounded-xl bg-emerald-50 px-3 py-2.5 text-xs font-semibold text-emerald-700 lg:justify-start">
          <span class="h-1.5 w-1.5 rounded-full bg-emerald-500" aria-hidden="true"></span>
          <span class="hidden lg:inline">系统在线</span>
        </div>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-16 shrink-0 items-center gap-3 border-b border-border bg-white/85 px-4 backdrop-blur md:px-6">
        <button
          type="button"
          class="grid h-9 w-9 place-items-center rounded-xl border border-border text-slate-600 transition hover:bg-slate-50 md:hidden"
          aria-label="打开菜单"
          @click="mobileOpen = true"
        >
          <AppIcon name="menu" :size="20" />
        </button>

        <h1 class="flex min-w-0 items-baseline gap-2 text-[17px] font-semibold tracking-tight text-slate-900">
          <span class="truncate">{{ currentTitle }}</span>
          <span v-if="currentSubtitle" class="header-subtitle hidden text-xs font-normal text-slate-500 sm:inline">
            {{ currentSubtitle }}
          </span>
        </h1>

        <div class="ml-auto flex items-center gap-2 sm:gap-3">
          <span class="hidden items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 sm:inline-flex">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald-500" aria-hidden="true"></span>
            实时通道在线
          </span>

          <span class="hidden rounded-full bg-primary-soft px-2.5 py-1 text-xs font-semibold text-indigo-700 sm:inline">
            {{ roleLabel }}
          </span>

          <div class="relative">
            <button
              type="button"
              class="flex items-center gap-2 rounded-full p-0.5 pr-2 transition hover:bg-slate-50"
              :aria-expanded="userMenuOpen"
              aria-haspopup="menu"
              aria-label="用户菜单"
              @click="userMenuOpen = !userMenuOpen"
            >
              <span class="grid h-8 w-8 place-items-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-xs font-bold text-white">
                {{ initial }}
              </span>
              <span class="hidden text-sm font-medium text-slate-700 sm:inline">{{ auth.displayName }}</span>
              <AppIcon name="arrow" :size="14" class="rotate-90 text-slate-400" />
            </button>

            <div v-if="userMenuOpen" class="fixed inset-0 z-40" @click="userMenuOpen = false"></div>
            <div
              v-if="userMenuOpen"
              class="absolute right-0 top-full z-50 mt-2 w-48 rounded-xl border border-border bg-white p-1.5 shadow-float"
              role="menu"
            >
              <div class="px-3 py-2">
                <p class="text-sm font-semibold text-slate-900">{{ auth.displayName }}</p>
                <p class="mt-0.5 text-xs text-slate-500">{{ roleLabel }}</p>
              </div>
              <div class="my-1 h-px bg-border"></div>
              <button
                type="button"
                class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-red-600 transition hover:bg-red-50"
                role="menuitem"
                @click="logout"
              >
                <AppIcon name="logout" :size="16" />
                退出登录
              </button>
            </div>
          </div>
        </div>
      </header>

      <main id="main-content" class="el-main flex-1 overflow-auto bg-background p-4 md:p-6" tabindex="-1">
        <router-view />
      </main>
    </div>

    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0"
        leave-active-class="transition duration-150 ease-in"
        leave-to-class="opacity-0"
      >
        <div v-if="mobileOpen" class="fixed inset-0 z-[60] md:hidden">
          <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="mobileOpen = false"></div>
          <aside
            class="absolute inset-y-0 left-0 flex w-72 max-w-[86vw] flex-col bg-white shadow-float"
            role="dialog"
            aria-modal="true"
            aria-label="导航菜单"
          >
            <div class="flex h-16 items-center gap-2.5 border-b border-border px-4">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-sm font-bold text-white">
                排
              </span>
              <span class="text-[17px] font-semibold tracking-wide text-slate-900">排策智演</span>
              <button
                ref="mobileClose"
                type="button"
                class="ml-auto grid h-9 w-9 place-items-center rounded-xl text-slate-500 transition hover:bg-slate-50"
                aria-label="关闭菜单"
                @click="mobileOpen = false"
              >
                <AppIcon name="close" :size="18" />
              </button>
            </div>
            <NavMenu :menus="menus" always-show-labels @select="mobileOpen = false" />
          </aside>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
