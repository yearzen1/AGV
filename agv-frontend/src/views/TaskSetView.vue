<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoadNet, listRoadNets, type RoadNetPayload } from '@/api/roadnet'
import {
  createTaskSet,
  deleteTaskSet,
  getTaskSet,
  listTaskSets,
  updateTaskSet,
  type TaskDto,
  type TaskSetSummary
} from '@/api/sim'

const sets = ref<TaskSetSummary[]>([])
const loading = ref(false)
const roadnet = ref<RoadNetPayload | null>(null)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<{ name: string; tasks: TaskDto[] }>({ name: '', tasks: [] })

const stationIds = computed(() =>
  (roadnet.value?.nodes ?? [])
    .filter((n) => n.type === 'WORKSTATION' || n.type === 'BUFFER' || n.type === 'CHARGER')
    .map((n) => n.id)
)

async function load() {
  loading.value = true
  try {
    sets.value = await listTaskSets()
    const maps = await listRoadNets()
    if (maps.length > 0) {
      roadnet.value = await getRoadNet(maps[0].id)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载任务集失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = '新任务集'
  form.tasks = [{ id: 1, from: stationIds.value[0] ?? '', to: stationIds.value[1] ?? '' }]
  dialogVisible.value = true
}

async function openEdit(row: TaskSetSummary) {
  try {
    const payload = await getTaskSet(row.id)
    editingId.value = row.id
    form.name = row.name
    form.tasks = payload.tasks.map((t) => ({ ...t }))
    dialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '读取任务集失败')
  }
}

function addTask() {
  const nextId = form.tasks.length === 0 ? 1 : Math.max(...form.tasks.map((t) => t.id)) + 1
  form.tasks.push({
    id: nextId,
    from: stationIds.value[0] ?? '',
    to: stationIds.value[1] ?? ''
  })
}

function removeTask(index: number) {
  form.tasks.splice(index, 1)
}

function fillDemo() {
  const stations = stationIds.value.length >= 3 ? stationIds.value.slice(0, 3) : stationIds.value
  if (stations.length === 0) return
  form.tasks = Array.from({ length: 20 }, (_, i) => {
    const from = stations[i % stations.length]
    let to = stations[(i + 1 + Math.floor(i / 3)) % stations.length]
    if (to === from) {
      to = stations[(i + 2) % stations.length] ?? from
    }
    return { id: i + 1, from, to }
  })
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写任务集名称')
    return
  }
  if (form.tasks.length === 0) {
    ElMessage.warning('至少需要一条任务')
    return
  }
  try {
    const payload = { name: form.name, taskSet: { tasks: form.tasks } }
    if (editingId.value) {
      await updateTaskSet(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createTaskSet(payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function remove(row: TaskSetSummary) {
  try {
    await ElMessageBox.confirm(`确定删除任务集「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteTaskSet(row.id)
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
    <h2 class="page-title">任务集</h2>

    <div class="panel">
      <el-button type="primary" @click="openCreate">新建任务集</el-button>

      <el-table v-loading="loading" :data="sets" style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="220" />
        <el-table-column prop="taskCount" label="任务数" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑任务集' : '新建任务集'" width="720px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
      </el-form>

      <div class="tools">
        <el-button size="small" @click="addTask">加一条</el-button>
        <el-button size="small" @click="fillDemo">填充 20 单演示数据</el-button>
        <span class="muted">共 {{ form.tasks.length }} 条</span>
      </div>

      <el-table :data="form.tasks" height="360" size="small">
        <el-table-column prop="id" label="序号" width="70" />
        <el-table-column label="起点" min-width="120">
          <template #default="{ row }">
            <el-select v-model="row.from" filterable allow-create size="small" style="width: 100%">
              <el-option v-for="id in stationIds" :key="id" :label="id" :value="id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="终点" min-width="120">
          <template #default="{ row }">
            <el-select v-model="row.to" filterable allow-create size="small" style="width: 100%">
              <el-option v-for="id in stationIds" :key="id" :label="id" :value="id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeTask($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tools {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
</style>
