<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function fill(username: string, password: string) {
  form.username = username
  form.password = password
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <h1>排策智演</h1>
        <p>车间 AGV 小车集群路径规划仿真与调度可视化系统</p>
      </div>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            show-password
            placeholder="请输入密码"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="quick">
        <span class="muted">演示账号：</span>
        <el-button link type="primary" @click="fill('admin', 'admin123')">管理员</el-button>
        <el-button link type="primary" @click="fill('dispatcher', 'dispatcher123')">调度人员</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 20% 20%, #1f3b73 0%, #0d1b33 60%, #060c18 100%);
}

.login-card {
  width: 400px;
  padding: 36px 32px 24px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}

.brand h1 {
  margin: 0 0 6px;
  font-size: 26px;
  letter-spacing: 2px;
  color: #1f3b73;
}

.brand p {
  margin: 0 0 24px;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.quick {
  margin-top: 16px;
  text-align: center;
}
</style>
