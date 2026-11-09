<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createUser,
  deleteUser,
  listUsers,
  updateUser,
  type UserInfo
} from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'

const auth = useAuthStore()
const users = ref<UserInfo[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<UserInfo | null>(null)
const form = reactive({ username: '', password: '', realName: '', role: 'dispatcher' })

async function load() {
  loading.value = true
  try {
    users.value = await listUsers()
  } catch (error: any) {
    ElMessage.error(error.message || '加载用户失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.username = ''
  form.password = ''
  form.realName = ''
  form.role = 'dispatcher'
  dialogVisible.value = true
}

function openEdit(row: UserInfo) {
  editing.value = row
  form.username = row.username
  form.password = ''
  form.realName = row.realName || ''
  form.role = row.role
  dialogVisible.value = true
}

async function submit() {
  try {
    if (editing.value) {
      await updateUser(editing.value.id, {
        realName: form.realName,
        role: form.role,
        password: form.password || undefined
      })
      ElMessage.success('已保存')
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        realName: form.realName,
        role: form.role
      })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function toggleStatus(row: UserInfo) {
  try {
    await updateUser(row.id, { status: row.status })
    ElMessage.success(row.status === 1 ? '已启用' : '已停用')
  } catch (error: any) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error(error.message || '操作失败')
  }
}

async function remove(row: UserInfo) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除确认', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="page-title">用户管理</h2>

    <div class="panel">
      <el-button type="primary" @click="openCreate">新建用户</el-button>

      <el-table v-loading="loading" :data="users" style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'success'" size="small">
              {{ row.role === 'admin' ? '管理员' : '调度人员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :disabled="row.username === auth.user?.username"
              @change="toggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180"
          :formatter="(row: UserInfo) => formatDateTime(row.createTime)" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              :disabled="row.username === auth.user?.username"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新建用户'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!editing" />
        </el-form-item>
        <el-form-item :label="editing ? '重置密码' : '密码'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editing ? '留空则不修改' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="form.role">
            <el-radio value="dispatcher">调度人员</el-radio>
            <el-radio value="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
