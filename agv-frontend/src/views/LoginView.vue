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
const usernameRef = ref<{ focus: () => void } | null>(null)
const passwordRef = ref<{ focus: () => void } | null>(null)

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
  <div class="login-page">
    <div class="login-shell">
      <section class="brand-panel">
        <div class="brand-top">
          <span class="brand-mark" aria-hidden="true">排</span>
          <span class="brand-name">排策智演</span>
        </div>

        <h1 class="brand-title">
          车间 AGV 集群<br />
          路径规划与调度仿真
        </h1>
        <p class="brand-desc">拓扑路网 · A* 寻路 · 时间窗避障 · 双算法对比</p>

        <ul class="brand-points">
          <li>
            <AppIcon name="map" :size="18" />
            <span>三维路网与实时车辆态势</span>
          </li>
          <li>
            <AppIcon name="bolt" :size="18" />
            <span>冲突等待、重规划与死锁事件追踪</span>
          </li>
          <li>
            <AppIcon name="chart" :size="18" />
            <span>两种调度策略的四项指标对比</span>
          </li>
        </ul>

        <p class="brand-foot">毕业设计 · 选题 11</p>
      </section>

      <section class="form-panel" aria-labelledby="login-heading">
        <div class="form-head">
          <h2 id="login-heading">登录</h2>
          <p>使用演示账号或自己的账号进入系统</p>
        </div>

        <form class="login-form" novalidate @submit.prevent="submit">
          <div class="field">
            <label class="field-label" for="login-username">用户名</label>
            <el-input
              id="login-username"
              ref="usernameRef"
              v-model="form.username"
              name="username"
              size="large"
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

          <div class="field">
            <label class="field-label" for="login-password">密码</label>
            <el-input
              id="login-password"
              ref="passwordRef"
              v-model="form.password"
              name="password"
              size="large"
              :type="passwordVisible ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
              enterkeyhint="done"
              :aria-invalid="invalidField === 'password'"
              :aria-describedby="error ? 'login-error' : undefined"
              @input="clearError"
              @keydown="checkCapsLock"
              @keyup="checkCapsLock"
            >
              <template #suffix>
                <button
                  type="button"
                  class="password-toggle"
                  :aria-pressed="passwordVisible"
                  :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                  @click="passwordVisible = !passwordVisible"
                >
                  <AppIcon :name="passwordVisible ? 'eyeOff' : 'eye'" :size="18" />
                </button>
              </template>
            </el-input>
            <p v-if="capsLock" class="caps-hint" role="status">大写锁定已开启</p>
          </div>

          <p v-if="error" id="login-error" class="login-error" role="alert">{{ error }}</p>

          <el-button
            type="primary"
            native-type="submit"
            size="large"
            class="submit"
            :loading="loading"
            :disabled="loading"
          >
            登录
          </el-button>
        </form>

        <div class="demo">
          <span class="demo-label">演示账号</span>
          <div class="demo-list">
            <button type="button" class="demo-card" @click="fill('admin', 'admin123')">
              <strong>管理员</strong>
              <small>全部模块 + 用户管理</small>
            </button>
            <button type="button" class="demo-card" @click="fill('dispatcher', 'dispatcher123')">
              <strong>调度人员</strong>
              <small>业务五个菜单</small>
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  min-height: 100%;
  padding: 32px 20px;
  overflow: auto;
  background:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px) 0 0 / 48px 48px,
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px) 0 0 / 48px 48px,
    radial-gradient(circle at 18% 18%, #244d8f 0%, #10203d 52%, #070d18 100%);
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  width: min(1040px, 100%);
  overflow: hidden;
  background: var(--agv-surface);
  border-radius: 20px;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.45);
}

.brand-panel {
  display: flex;
  flex-direction: column;
  padding: 44px 44px 36px;
  color: #fff;
  background:
    radial-gradient(circle at 82% 12%, rgba(120, 170, 255, 0.35), transparent 42%),
    linear-gradient(150deg, #1c3a70 0%, #12213d 58%, #0c172b 100%);
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 16px;
  font-weight: 700;
}

.brand-name {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 3px;
}

.brand-title {
  margin: 56px 0 14px;
  font-size: clamp(26px, 3vw, 34px);
  line-height: 1.28;
  letter-spacing: 1px;
}

.brand-desc {
  margin: 0;
  color: #a9bce0;
  font-size: 13px;
  line-height: 1.7;
}

.brand-points {
  display: grid;
  gap: 14px;
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
  color: #d6e1f6;
  font-size: 13px;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-points :deep(svg) {
  color: #8fb4ff;
}

.brand-foot {
  margin: auto 0 0;
  padding-top: 36px;
  color: #6f83a6;
  font-size: 12px;
}

.form-panel {
  display: flex;
  flex-direction: column;
  padding: 40px 36px 32px;
}

.form-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
  color: var(--agv-brand);
}

.form-head p {
  margin: 0 0 26px;
  color: var(--agv-text-muted);
  font-size: 13px;
}

.login-form {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 6px;
}

.field-label {
  color: #4b5563;
  font-size: 13px;
  font-weight: 500;
}

.password-toggle {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--agv-text-muted);
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.password-toggle:hover {
  color: var(--agv-brand);
  background: var(--agv-surface-muted);
}

.caps-hint {
  margin: 0;
  color: var(--agv-warning);
  font-size: 12px;
}

.login-error {
  margin: 0;
  padding: 10px 12px;
  color: var(--agv-danger);
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--agv-radius);
  font-size: 13px;
}

.submit {
  width: 100%;
  margin-top: 4px;
}

.demo {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px dashed var(--agv-border);
}

.demo-label {
  display: block;
  margin-bottom: 10px;
  color: var(--agv-text-muted);
  font-size: 12px;
}

.demo-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.demo-card {
  display: grid;
  gap: 2px;
  padding: 10px 12px;
  text-align: start;
  color: var(--agv-text);
  background: #f8fafc;
  border: 1px solid var(--agv-border);
  border-radius: var(--agv-radius);
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, transform 0.16s ease;
}

.demo-card:hover {
  background: #eef4ff;
  border-color: #b9cdf5;
}

.demo-card:active {
  transform: translateY(1px);
}

.demo-card strong {
  color: var(--agv-brand);
  font-size: 13px;
  font-weight: 600;
}

.demo-card small {
  color: var(--agv-text-muted);
  font-size: 11px;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    width: min(460px, 100%);
  }

  .brand-panel {
    padding: 26px 26px 22px;
  }

  .brand-title {
    margin-top: 22px;
    font-size: 22px;
  }

  .brand-points,
  .brand-foot {
    display: none;
  }
}

@media (max-width: 520px) {
  .login-page {
    padding: 16px 12px;
  }

  .form-panel {
    padding: 28px 22px 24px;
  }
}
</style>
