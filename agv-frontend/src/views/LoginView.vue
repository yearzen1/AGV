<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)
const error = ref('')
const invalidField = ref<'username' | 'password' | ''>('')
const capsLock = ref(false)
const passwordVisible = ref(false)
const usernameRef = ref<HTMLInputElement | null>(null)
const passwordRef = ref<HTMLInputElement | null>(null)

onMounted(() => {
  usernameRef.value?.focus()
})

function clearError() {
  error.value = ''
  invalidField.value = ''
}

function checkCapsLock(event: KeyboardEvent) {
  capsLock.value = event.getModifierState?.('CapsLock') ?? false
}

async function submit() {
  if (!form.username.trim()) {
    error.value = '请输入用户名'
    invalidField.value = 'username'
    usernameRef.value?.focus()
    return
  }
  if (!form.password) {
    error.value = '请输入密码'
    invalidField.value = 'password'
    passwordRef.value?.focus()
    return
  }

  error.value = ''
  invalidField.value = ''
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (exception: any) {
    error.value = exception.message || '登录失败，请稍后重试'
    invalidField.value = 'password'
    passwordRef.value?.focus()
  } finally {
    loading.value = false
  }
}

function fill(username: string, password: string) {
  form.username = username
  form.password = password
  clearError()
}
</script>

<template>
  <div class="relative min-h-screen overflow-auto bg-[#f7f8fa] px-4 py-8 sm:px-6">
    <div class="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      <div class="absolute -left-24 -top-32 h-80 w-80 rounded-full bg-indigo-200/50 blur-3xl"></div>
      <div class="absolute -bottom-32 right-0 h-96 w-96 rounded-full bg-violet-200/50 blur-3xl"></div>
      <div
        class="absolute inset-0 opacity-[0.35]"
        style="background-image: linear-gradient(#e2e8f0 1px, transparent 1px), linear-gradient(90deg, #e2e8f0 1px, transparent 1px); background-size: 56px 56px"
      ></div>
    </div>

    <div class="relative mx-auto flex min-h-[calc(100vh-4rem)] w-full max-w-[1060px] items-center justify-center">
      <div class="w-full overflow-hidden rounded-[28px] border border-white bg-white shadow-[0_24px_80px_rgba(15,23,42,0.14)] lg:grid lg:grid-cols-[1.05fr_1fr]">
        <section class="relative hidden flex-col overflow-hidden bg-gradient-to-br from-indigo-600 via-indigo-700 to-violet-800 p-10 text-white lg:flex">
          <div
            class="pointer-events-none absolute inset-0 opacity-20"
            style="background-image: radial-gradient(circle at 1px 1px, #fff 1px, transparent 0); background-size: 22px 22px"
            aria-hidden="true"
          ></div>
          <div class="pointer-events-none absolute -right-20 top-24 h-64 w-64 rounded-full bg-white/10 blur-2xl" aria-hidden="true"></div>

          <div class="relative flex items-center gap-2.5">
            <span class="grid h-10 w-10 place-items-center rounded-xl bg-white/15 text-base font-bold backdrop-blur">排</span>
            <span class="text-lg font-semibold tracking-[0.18em]">排策智演</span>
          </div>

          <div class="relative mt-16">
            <p class="text-xs font-semibold uppercase tracking-[0.22em] text-indigo-200">AGV Scheduling Studio</p>
            <h1 class="mt-4 text-[34px] font-semibold leading-[1.25] tracking-tight">
              车间 AGV 集群<br />路径规划与调度仿真
            </h1>
            <p class="mt-4 max-w-sm text-sm leading-7 text-indigo-100">
              从拓扑路网到时间窗避障，在同一个工作台里完成建模、仿真与策略对比。
            </p>
          </div>

          <ul class="relative mt-10 space-y-4 text-sm text-indigo-50">
            <li class="flex items-center gap-3">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-white/10"><AppIcon name="map" :size="18" /></span>
              三维路网与实时车辆态势
            </li>
            <li class="flex items-center gap-3">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-white/10"><AppIcon name="bolt" :size="18" /></span>
              冲突等待、重规划与死锁追踪
            </li>
            <li class="flex items-center gap-3">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-white/10"><AppIcon name="chart" :size="18" /></span>
              两种调度策略的四项指标对比
            </li>
          </ul>

          <div class="relative mt-auto flex items-center gap-3 pt-10 text-xs text-indigo-200">
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
              <span class="h-1.5 w-1.5 rounded-full bg-emerald-300"></span>
              实时通道
            </span>
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1.5">
              <span class="h-1.5 w-1.5 rounded-full bg-amber-300"></span>
              A* + 时间窗
            </span>
          </div>
        </section>

        <section class="flex flex-col justify-center p-7 sm:p-10" aria-labelledby="login-heading">
          <div class="mb-8 flex items-center gap-2.5 lg:hidden">
            <span class="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-sm font-bold text-white">排</span>
            <span class="text-base font-semibold tracking-wide text-slate-900">排策智演</span>
          </div>

          <div>
            <h2 id="login-heading" class="text-[26px] font-semibold tracking-tight text-slate-900">欢迎回来</h2>
            <p class="mt-1.5 text-sm text-slate-500">使用演示账号或自己的账号进入调度工作台。</p>
          </div>

          <form class="mt-8 space-y-5" novalidate @submit.prevent="submit">
            <div>
              <label for="login-username" class="text-sm font-medium text-slate-700">用户名</label>
              <div class="relative mt-1.5">
                <span class="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-400">
                  <AppIcon name="users" :size="17" />
                </span>
                <input
                  id="login-username"
                  ref="usernameRef"
                  v-model="form.username"
                  name="username"
                  type="text"
                  class="h-11 w-full rounded-xl border border-border bg-white pl-10 pr-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                  placeholder="请输入用户名"
                  autocomplete="username"
                  autocorrect="off"
                  autocapitalize="none"
                  spellcheck="false"
                  enterkeyhint="next"
                  :aria-invalid="invalidField === 'username'"
                  :aria-describedby="error ? 'login-error' : undefined"
                  @input="clearError"
                />
              </div>
            </div>

            <div>
              <label for="login-password" class="text-sm font-medium text-slate-700">密码</label>
              <div class="relative mt-1.5">
                <span class="pointer-events-none absolute inset-y-0 left-3 flex items-center text-slate-400">
                  <AppIcon name="lock" :size="17" />
                </span>
                <input
                  id="login-password"
                  ref="passwordRef"
                  v-model="form.password"
                  name="password"
                  :type="passwordVisible ? 'text' : 'password'"
                  class="h-11 w-full rounded-xl border border-border bg-white pl-10 pr-11 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                  enterkeyhint="done"
                  :aria-invalid="invalidField === 'password'"
                  :aria-describedby="error ? 'login-error' : undefined"
                  @input="clearError"
                  @keydown="checkCapsLock"
                  @keyup="checkCapsLock"
                />
                <button
                  type="button"
                  class="absolute inset-y-0 right-2 my-auto grid h-8 w-8 place-items-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
                  :aria-pressed="passwordVisible"
                  :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                  @click="passwordVisible = !passwordVisible"
                >
                  <AppIcon :name="passwordVisible ? 'eyeOff' : 'eye'" :size="17" />
                </button>
              </div>
              <p v-if="capsLock" class="mt-1.5 text-xs text-amber-600" role="status">大写锁定已开启</p>
            </div>

            <p
              v-if="error"
              id="login-error"
              class="rounded-xl border border-red-100 bg-red-50 px-3 py-2.5 text-sm text-red-600"
              role="alert"
            >
              {{ error }}
            </p>

            <button
              type="submit"
              class="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(79,70,229,0.28)] transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="loading"
            >
              <span
                v-if="loading"
                class="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
                aria-hidden="true"
              ></span>
              {{ loading ? '登录中…' : '登录' }}
            </button>
          </form>

          <div class="mt-8 border-t border-border pt-6">
            <p class="text-xs font-medium text-slate-500">演示账号</p>
            <div class="mt-3 grid grid-cols-2 gap-2.5">
              <button
                type="button"
                class="rounded-xl border border-border bg-white px-3 py-2.5 text-left transition hover:border-indigo-200 hover:bg-indigo-50/40"
                @click="fill('admin', 'admin123')"
              >
                <span class="block text-sm font-semibold text-slate-800">管理员</span>
                <span class="mt-0.5 block text-[11px] text-slate-500">全部模块 + 用户管理</span>
              </button>
              <button
                type="button"
                class="rounded-xl border border-border bg-white px-3 py-2.5 text-left transition hover:border-indigo-200 hover:bg-indigo-50/40"
                @click="fill('dispatcher', 'dispatcher123')"
              >
                <span class="block text-sm font-semibold text-slate-800">调度人员</span>
                <span class="mt-0.5 block text-[11px] text-slate-500">业务五个菜单</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
