<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
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

const typeHint = computed(() =>
  dictType.value === 'metric'
    ? '评价指标字典暂未接入实验页显示，修改不影响现有页面'
    : '编码与后端算法实现绑定，此处只调整显示名、排序与启停'
)

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

function openEdit(row: DictItem) {
  editing.value = row
  form.dictCode = row.dictCode
  form.dictValue = row.dictValue
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
}

async function submit() {
  if (!editing.value) return
  const payload = {
    dictType: dictType.value,
    dictCode: form.dictCode,
    dictValue: form.dictValue,
    sort: form.sort,
    status: form.status
  }
  try {
    await updateDict(editing.value.id, payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function remove(row: DictItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除字典项「${row.dictValue}」吗？删除后不可恢复，建议优先使用「停用」。`,
      '删除确认',
      { type: 'warning' }
    )
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
    <p class="page-lead">维护下拉字典的显示名、排序与启停；编码由后端实现绑定，界面不提供新增或改编码。</p>

    <div class="panel">
      <div class="section-head">
        <h2 class="panel-title">字典项</h2>
        <el-space wrap>
          <el-select v-model="dictType" style="width: 180px" aria-label="字典类型" @change="load">
            <el-option v-for="option in typeOptions" :key="option.value" :value="option.value" :label="option.label" />
          </el-select>
          <span class="muted">{{ typeHint }}</span>
        </el-space>
      </div>

      <el-table v-loading="loading" :data="items">
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

    <el-dialog v-model="dialogVisible" title="编辑字典项" width="460px">
      <el-form label-width="90px">
        <el-form-item label="字典类型">
          <el-input :model-value="typeOptions.find((o) => o.value === dictType)?.label" disabled />
        </el-form-item>
        <el-form-item label="编码">
          <el-input :model-value="form.dictCode" disabled />
          <div class="muted hint">编码与后端实现绑定，不可修改</div>
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

<style scoped>
.hint {
  margin-top: 4px;
  line-height: 1.4;
}
</style>
