<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createDict,
  deleteDict,
  listDictAdmin,
  updateDict,
  type DictItem
} from '@/api/admin'

const dictType = ref('algorithm')
const items = ref<DictItem[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<DictItem | null>(null)
const form = reactive({ dictCode: '', dictValue: '', sort: 0, status: 1 })

const typeOptions = [
  { value: 'algorithm', label: '调度算法' },
  { value: 'metric', label: '评价指标' }
]

async function load() {
  loading.value = true
  try {
    items.value = await listDictAdmin(dictType.value)
  } catch (error: any) {
    ElMessage.error(error.message || '加载字典失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.dictCode = ''
  form.dictValue = ''
  form.sort = 0
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: DictItem) {
  editing.value = row
  form.dictCode = row.dictCode
  form.dictValue = row.dictValue
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
}

async function submit() {
  const payload = {
    dictType: dictType.value,
    dictCode: form.dictCode,
    dictValue: form.dictValue,
    sort: form.sort,
    status: form.status
  }
  try {
    if (editing.value) {
      await updateDict(editing.value.id, payload)
      ElMessage.success('已保存')
    } else {
      await createDict(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function remove(row: DictItem) {
  try {
    await ElMessageBox.confirm(`确定删除字典项「${row.dictValue}」吗？`, '删除确认', { type: 'warning' })
    await deleteDict(row.id)
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
    <h2 class="page-title">数字字典</h2>

    <div class="panel">
      <el-space wrap>
        <el-select v-model="dictType" style="width: 180px" @change="load">
          <el-option v-for="option in typeOptions" :key="option.value" :value="option.value" :label="option.label" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增字典项</el-button>
        <span class="muted">本期只维护调度算法与评价指标两类</span>
      </el-space>

      <el-table v-loading="loading" :data="items" style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="dictCode" label="编码" min-width="160" />
        <el-table-column prop="dictValue" label="显示名" min-width="160" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑字典项' : '新增字典项'" width="460px">
      <el-form label-width="90px">
        <el-form-item label="字典类型">
          <el-input :model-value="typeOptions.find((o) => o.value === dictType)?.label" disabled />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.dictCode" placeholder="如 nearest" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.dictValue" placeholder="如 就近分配" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
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
