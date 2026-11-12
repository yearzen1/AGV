<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRoadNet,
  listRoadNets,
  type NetNode,
  type RoadNetPayload,
  type RoadNetSummary
} from '@/api/roadnet'
import {
  createTaskSet,
  deleteTaskSet,
  getTaskSet,
  listTaskSets,
  updateTaskSet,
  type TaskDto,
  type TaskSetSummary
} from '@/api/sim'
import { formatDateTime } from '@/utils/format'
import StationPicker from '@/components/StationPicker.vue'

const sets = ref<TaskSetSummary[]>([])
const maps = ref<RoadNetSummary[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const mapLoading = ref(false)
const dialogRoadnet = ref<RoadNetPayload | null>(null)
const invalidTaskIds = ref<Set<number>>(new Set())
const formErrors = ref<string[]>([])
const saving = ref(false)
const form = reactive<{ name: string; roadnetId: number | null; tasks: TaskDto[] }>({
  name: '',
  roadnetId: null,
  tasks: []
})
let lastRoadnetId: number | null = null

const pickerVisible = ref(false)
const pickerIndex = ref<number | null>(null)
const pickerField = ref<'from' | 'to'>('from')

const stationNodes = computed<NetNode[]>(() =>
  (dialogRoadnet.value?.nodes ?? []).filter((node) => node.type === 'WORKSTATION')
)

const pickerValue = computed(() => {
  if (pickerIndex.value === null) return null
  return form.tasks[pickerIndex.value]?.[pickerField.value] ?? null
})

const pickerTitle = computed(() => {
  if (pickerIndex.value === null) return '选择工位'
  return `第 ${pickerIndex.value + 1} 行 · ${pickerField.value === 'from' ? '起点' : '终点'}`
})

async function load() {
  loading.value = true
  try {
    sets.value = await listTaskSets()
    maps.value = await listRoadNets()
  } catch (error: any) {
    ElMessage.error(error.message || '加载任务集失败')
  } finally {
    loading.value = false
  }
}

async function loadDialogMap(roadnetId: number | null) {
  dialogRoadnet.value = null
  if (!roadnetId) return
  mapLoading.value = true
  try {
    dialogRoadnet.value = await getRoadNet(roadnetId)
  } catch (error: any) {
    ElMessage.error(error.message || '加载路网失败')
  } finally {
    mapLoading.value = false
  }
}

function clearErrors() {
  invalidTaskIds.value = new Set()
  formErrors.value = []
}

/** 任务编号与行号保持一致，删除后不会留下空洞。 */
function renumberTasks() {
  form.tasks.forEach((task, index) => {
    task.id = index + 1
  })
}

function defaultTasks(): TaskDto[] {
  if (stationNodes.value.length < 2) return []
  return [{ id: 1, from: stationNodes.value[0].id, to: stationNodes.value[1].id }]
}

/** 新行优先选一组没出现过的起终点，避免连续点击得到重复任务。 */
function nextTaskPair(): { from: string; to: string } | null {
  const stations = stationNodes.value
  if (stations.length < 2) return null
  const used = new Set(form.tasks.map((task) => `${task.from}->${task.to}`))
  for (const from of stations) {
    for (const to of stations) {
      if (from.id === to.id) continue
      if (!used.has(`${from.id}->${to.id}`)) {
        return { from: from.id, to: to.id }
      }
    }
  }
  return { from: stations[0].id, to: stations[1].id }
}

async function openCreate() {
  if (maps.value.length === 0) {
    ElMessage.warning('请先在「车间地图」中创建路网')
    return
  }
  editingId.value = null
  clearErrors()
  form.name = '新任务集'
  form.roadnetId = maps.value[0].id
  lastRoadnetId = form.roadnetId
  await loadDialogMap(form.roadnetId)
  form.tasks = defaultTasks()
  dialogVisible.value = true
}

async function openEdit(row: TaskSetSummary) {
  try {
    const payload = await getTaskSet(row.id)
    editingId.value = row.id
    clearErrors()
    form.name = row.name
    form.roadnetId = row.roadnetId ?? maps.value[0]?.id ?? null
    lastRoadnetId = form.roadnetId
    await loadDialogMap(form.roadnetId)
    form.tasks = payload.tasks.map((task) => ({ ...task }))
    renumberTasks()
    dialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '读取任务集失败')
  }
}

async function onMapChange(roadnetId: number) {
  if (form.tasks.length > 0) {
    try {
      await ElMessageBox.confirm(
        '切换路网会清空当前任务行，因为节点编号只在单张路网内唯一。确定切换吗？',
        '切换路网',
        { type: 'warning', confirmButtonText: '切换并清空', cancelButtonText: '取消' }
      )
    } catch {
      form.roadnetId = lastRoadnetId
      return
    }
  }
  lastRoadnetId = roadnetId
  await loadDialogMap(roadnetId)
  form.tasks = defaultTasks()
  clearErrors()
}

function addTask() {
  const pair = nextTaskPair()
  if (!pair) {
    ElMessage.warning('该路网至少需要两个工位才能创建任务')
    return
  }
  form.tasks.push({ id: form.tasks.length + 1, ...pair })
  clearErrors()
}

function removeTask(index: number) {
  form.tasks.splice(index, 1)
  renumberTasks()
  clearErrors()
}

async function clearTasks() {
  if (form.tasks.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定清空当前 ${form.tasks.length} 条任务吗？`,
      '清空任务',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  form.tasks = []
  clearErrors()
}

async function fillDemo() {
  const stations = stationNodes.value
  if (stations.length < 2) {
    ElMessage.warning('该路网至少需要两个工位才能填充演示数据')
    return
  }
  if (form.tasks.length > 0) {
    try {
      await ElMessageBox.confirm(
        `填充演示数据会覆盖当前 ${form.tasks.length} 条任务，确定继续吗？`,
        '填充演示数据',
        { type: 'warning', confirmButtonText: '覆盖并填充', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  form.tasks = Array.from({ length: 20 }, (_, i) => {
    const from = stations[i % stations.length].id
    let to = stations[(i + 1 + Math.floor(i / 3)) % stations.length].id
    if (to === from) {
      to = stations[(i + 2) % stations.length].id
    }
    return { id: i + 1, from, to }
  })
  clearErrors()
}

function openPicker(index: number, field: 'from' | 'to') {
  pickerIndex.value = index
  pickerField.value = field
  pickerVisible.value = true
}

function applyPickedNode(nodeId: string) {
  if (pickerIndex.value === null) return
  const task = form.tasks[pickerIndex.value]
  if (!task) return
  task[pickerField.value] = nodeId
  clearErrors()
}

function closePicker() {
  pickerVisible.value = false
  pickerIndex.value = null
}

function rowClassName({ row }: { row: TaskDto }) {
  return invalidTaskIds.value.has(row.id) ? 'invalid-row' : ''
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写任务集名称')
    return
  }
  if (!form.roadnetId) {
    ElMessage.warning('请选择所属路网')
    return
  }
  if (form.tasks.length === 0) {
    ElMessage.warning('至少需要一条任务')
    return
  }
  if (form.tasks.some((task) => !task.from || !task.to)) {
    ElMessage.warning('请为每条任务选择起点与终点')
    return
  }
  if (form.tasks.some((task) => task.from === task.to)) {
    ElMessage.warning('起点与终点不能相同')
    return
  }
  clearErrors()
  saving.value = true
  try {
    const payload = { name: form.name, roadnetId: form.roadnetId, taskSet: { tasks: form.tasks } }
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
    const message = error.message || '保存失败'
    const errors = message
      .split(/[；;]/)
      .map((item: string) => item.trim())
      .filter(Boolean)
    formErrors.value = errors.length > 0 ? errors : [message]
    const ids = new Set<number>()
    for (const item of formErrors.value) {
      const matched = /任务\s*(\d+)/g
      let current = matched.exec(item)
      while (current) {
        ids.add(Number(current[1]))
        current = matched.exec(item)
      }
    }
    invalidTaskIds.value = ids
    if (ids.size > 0) {
      ElMessage.error('保存失败，请检查标红的任务行')
      await nextTick()
      document.querySelector('.el-dialog .invalid-row')?.scrollIntoView({ block: 'center' })
    } else {
      ElMessage.error(message)
    }
  } finally {
    saving.value = false
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
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column label="所属路网" min-width="180">
          <template #default="{ row }">
            <el-tag v-if="row.roadnetId" size="small">{{ row.roadnetName || `路网 ${row.roadnetId}` }}</el-tag>
            <el-tag v-else type="danger" size="small">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskCount" label="任务数" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="180"
          :formatter="(row: TaskSetSummary) => formatDateTime(row.createTime)" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑任务集' : '新建任务集'" width="860px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="所属路网">
          <el-select
            v-model="form.roadnetId"
            :loading="mapLoading"
            placeholder="选择路网"
            style="width: 320px"
            @change="onMapChange"
          >
            <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
          </el-select>
          <span class="hint">起终点只能选该路网的工位</span>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="formErrors.length > 0"
        class="form-errors"
        type="error"
        :closable="true"
        show-icon
        @close="clearErrors"
      >
        <template #title>保存失败，共 {{ formErrors.length }} 个问题</template>
        <ul class="error-list">
          <li v-for="(message, index) in formErrors" :key="index">{{ message }}</li>
        </ul>
      </el-alert>

      <div class="tools">
        <el-button size="small" @click="addTask">加一条</el-button>
        <el-button size="small" :disabled="form.tasks.length === 0" @click="fillDemo">
          填充 20 单演示数据
        </el-button>
        <el-button size="small" :disabled="form.tasks.length === 0" @click="clearTasks">清空</el-button>
        <span class="muted">共 {{ form.tasks.length }} 条</span>
        <span v-if="stationNodes.length < 2" class="warning">
          当前路网只有 {{ stationNodes.length }} 个工位，至少需要两个
        </span>
      </div>

      <el-table
        :data="form.tasks"
        row-key="id"
        height="360"
        size="small"
        empty-text="暂无任务，点「加一条」或「填充 20 单演示数据」"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="id" label="序号" width="70" />
        <el-table-column label="起点" min-width="190">
          <template #default="{ row, $index }">
            <div class="station-cell">
              <el-select
                v-model="row.from"
                filterable
                size="small"
                style="width: 100%"
                :aria-label="`第 ${$index + 1} 行起点`"
                @change="clearErrors"
              >
                <el-option
                  v-for="node in stationNodes"
                  :key="node.id"
                  :label="`${node.id} · (${node.x}, ${node.y})`"
                  :value="node.id"
                />
              </el-select>
              <el-button link type="primary" size="small" @click="openPicker($index, 'from')">
                选点
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="终点" min-width="190">
          <template #default="{ row, $index }">
            <div class="station-cell">
              <el-select
                v-model="row.to"
                filterable
                size="small"
                style="width: 100%"
                :aria-label="`第 ${$index + 1} 行终点`"
                @change="clearErrors"
              >
                <el-option
                  v-for="node in stationNodes"
                  :key="node.id"
                  :label="`${node.id} · (${node.x}, ${node.y})`"
                  :value="node.id"
                />
              </el-select>
              <el-button link type="primary" size="small" @click="openPicker($index, 'to')">
                选点
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="removeTask($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pickerVisible" :title="pickerTitle" width="680px" append-to-body @closed="closePicker">
      <StationPicker
        :roadnet="dialogRoadnet"
        :model-value="pickerValue"
        @update:model-value="applyPickedNode"
        @close="closePicker"
      />
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

.form-errors {
  margin-bottom: 12px;
}

.error-list {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.7;
}

.station-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}

.warning {
  color: #e6a23c;
  font-size: 12px;
}

:deep(.invalid-row td) {
  background: #fef0f0;
}
</style>
