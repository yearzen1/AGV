<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
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
  type TaskSetSummary
} from '@/api/sim'
import { formatDateTime } from '@/utils/format'
import TaskMapPicker from '@/components/TaskMapPicker.vue'

interface EditableTask {
  id: number
  from: string | null
  to: string | null
}

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
const form = reactive<{ name: string; roadnetId: number | null; tasks: EditableTask[] }>({
  name: '',
  roadnetId: null,
  tasks: []
})
let lastRoadnetId: number | null = null

const activeIndex = ref(-1)
const activeField = ref<'from' | 'to' | null>('from')
const mapNotice = ref('')
let noticeTimer = 0

const stationNodes = computed<NetNode[]>(() =>
  (dialogRoadnet.value?.nodes ?? []).filter((node) => node.type === 'WORKSTATION')
)

const activeTask = computed(() => form.tasks[activeIndex.value] ?? null)

const pickModeText = computed(() => {
  if (!dialogRoadnet.value) return mapLoading.value ? '正在加载路网…' : '请选择所属路网'
  if (stationNodes.value.length < 2) return '当前路网至少需要两个工位'
  if (!activeTask.value) return '选择或新增一条任务'
  if (activeField.value === 'from') return '点击工位设置起点'
  if (activeField.value === 'to') return '点击工位设置终点'
  return '当前任务已完成 · 点起点/终点可重选'
})

const pickModeTone = computed(() =>
  !dialogRoadnet.value || stationNodes.value.length < 2
    ? 'danger'
    : activeField.value === 'from' || activeField.value === 'to'
      ? 'warning'
      : 'success'
)

const mapSummary = computed(() => {
  if (!activeTask.value) return '尚未选中任务'
  const from = activeTask.value.from ?? '待选'
  const to = activeTask.value.to ?? '待选'
  return `任务 ${activeIndex.value + 1}：${from} → ${to}`
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

function defaultTasks(): EditableTask[] {
  if (stationNodes.value.length < 2) return []
  return [{ id: 1, from: null, to: null }]
}

function resetActive(tasks: EditableTask[]) {
  activeIndex.value = tasks.length > 0 ? 0 : -1
  const task = tasks[0]
  activeField.value = !task ? 'from' : !task.from ? 'from' : !task.to ? 'to' : null
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
  resetActive(form.tasks)
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
    resetActive(form.tasks)
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
  resetActive(form.tasks)
  clearErrors()
}

function selectTask(index: number) {
  activeIndex.value = index
  mapNotice.value = ''
  const task = form.tasks[index]
  activeField.value = !task ? 'from' : !task.from ? 'from' : !task.to ? 'to' : null
}

function pickField(index: number, field: 'from' | 'to') {
  activeIndex.value = index
  activeField.value = field
  mapNotice.value = ''
}

function onMapNotice(message: string) {
  mapNotice.value = message
  window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => {
    mapNotice.value = ''
  }, 2200)
}

/** 地图点选工位：写回当前行，起点完成后自动进入终点。 */
function onMapPick(nodeId: string) {
  const task = activeTask.value
  if (!task || (!activeField.value && task.from && task.to)) return
  const field = activeField.value ?? (!task.from ? 'from' : 'to')
  task[field] = nodeId
  mapNotice.value = ''
  clearErrors()
  activeField.value = field === 'from' && !task.to ? 'to' : null
}

function addTask() {
  if (stationNodes.value.length < 2) {
    ElMessage.warning('该路网至少需要两个工位才能创建任务')
    return
  }
  const id = form.tasks.length + 1
  form.tasks.push({ id, from: null, to: null })
  activeIndex.value = form.tasks.length - 1
  activeField.value = 'from'
  clearErrors()
  nextTick(() => {
    document.querySelector('.task-row.active')?.scrollIntoView({ block: 'nearest' })
  })
}

function removeTask(index: number) {
  form.tasks.splice(index, 1)
  renumberTasks()
  const nextIndex =
    form.tasks.length === 0
      ? -1
      : index < activeIndex.value
        ? activeIndex.value - 1
        : Math.min(activeIndex.value, form.tasks.length - 1)
  activeIndex.value = nextIndex
  const task = form.tasks[nextIndex]
  activeField.value = !task ? 'from' : !task.from ? 'from' : !task.to ? 'to' : null
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
  activeIndex.value = -1
  activeField.value = 'from'
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
  activeIndex.value = 0
  activeField.value = null
  clearErrors()
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
    const tasks = form.tasks.map((task) => ({
      id: task.id,
      from: task.from as string,
      to: task.to as string
    }))
    const payload = { name: form.name, roadnetId: form.roadnetId, taskSet: { tasks } }
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
      document.querySelector('.el-dialog .task-row.invalid-row')?.scrollIntoView({ block: 'center' })
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

onBeforeUnmount(() => window.clearTimeout(noticeTimer))
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑任务集' : '新建任务集'"
      width="min(1180px, 96vw)"
      top="5vh"
      class="taskset-dialog"
    >
      <el-form label-width="70px" class="editor-form">
        <div class="form-row">
          <el-form-item label="名称">
            <el-input v-model="form.name" style="width: 220px" />
          </el-form-item>
          <el-form-item label="所属路网">
            <el-select
              v-model="form.roadnetId"
              :loading="mapLoading"
              placeholder="选择路网"
              style="width: 260px"
              @change="onMapChange"
            >
              <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
            </el-select>
          </el-form-item>
          <span class="hint">起终点只能选该路网的工位</span>
        </div>
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

      <div class="editor-body">
        <section class="map-panel">
          <div class="map-toolbar">
            <span class="mode-badge" :class="`is-${pickModeTone}`">{{ pickModeText }}</span>
            <span class="map-note">蓝色工位可点，灰色节点不可选</span>
          </div>
          <div class="map-wrap">
            <TaskMapPicker
              :roadnet="dialogRoadnet"
              :from="activeTask?.from ?? null"
              :to="activeTask?.to ?? null"
              :active-field="activeField"
              @pick="onMapPick"
              @notice="onMapNotice"
            />
          </div>
          <div class="map-foot">
            <span :class="{ 'map-notice': mapNotice }">{{ mapNotice || mapSummary }}</span>
            <span class="muted">地图只负责选点，实际路径由后端 A* 计算</span>
          </div>
        </section>

        <aside class="task-panel">
          <div class="list-tools">
            <el-button type="primary" size="small" @click="addTask">
              + 在地图上新增任务
            </el-button>
            <el-button size="small" :disabled="form.tasks.length === 0" @click="fillDemo">
              填充 20 单演示数据
            </el-button>
            <el-button size="small" :disabled="form.tasks.length === 0" @click="clearTasks">
              清空
            </el-button>
            <span class="muted">共 {{ form.tasks.length }} 条</span>
          </div>

          <div class="task-list">
            <div v-if="form.tasks.length === 0" class="task-empty">
              暂无任务，点「+ 在地图上新增任务」或「填充 20 单演示数据」
            </div>
            <div
              v-for="(task, index) in form.tasks"
              :key="task.id"
              class="task-row"
              :class="{ active: index === activeIndex, 'invalid-row': invalidTaskIds.has(task.id) }"
              @click="selectTask(index)"
            >
              <span class="task-seq">{{ index + 1 }}</span>
              <div class="task-route">
                <button
                  type="button"
                  class="station-chip"
                  :class="{
                    empty: !task.from,
                    picking: index === activeIndex && activeField === 'from'
                  }"
                  @click.stop="pickField(index, 'from')"
                >
                  {{ task.from || '选起点' }}
                </button>
                <span class="route-arrow">→</span>
                <button
                  type="button"
                  class="station-chip"
                  :class="{
                    empty: !task.to,
                    picking: index === activeIndex && activeField === 'to'
                  }"
                  @click.stop="pickField(index, 'to')"
                >
                  {{ task.to || '选终点' }}
                </button>
              </div>
              <button type="button" class="row-del" title="删除" @click.stop="removeTask(index)">
                ×
              </button>
            </div>
          </div>

          <div class="list-foot">
            <span v-if="stationNodes.length < 2" class="warning">
              当前路网只有 {{ stationNodes.length }} 个工位，至少需要两个
            </span>
            <span v-else>点行定位地图 · 点起点/终点重选 · 起点选完自动进入终点</span>
          </div>
        </aside>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <span class="muted">共 {{ form.tasks.length }} 条任务</span>
          <div class="footer-actions">
            <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.editor-form {
  margin-bottom: 6px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.editor-form :deep(.el-form-item) {
  margin-bottom: 6px;
}

.hint {
  color: #909399;
  font-size: 12px;
}

.warning {
  color: #e6a23c;
  font-size: 12px;
}

.form-errors {
  margin: 4px 0 12px;
}

.error-list {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.7;
}

.editor-body {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(340px, 1fr);
  gap: 14px;
  height: 500px;
}

.map-panel,
.task-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.map-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafcff;
}

.mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.mode-badge::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.mode-badge.is-warning {
  color: #b88230;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
}

.mode-badge.is-success {
  color: #529b2e;
  background: #f0f9eb;
  border: 1px solid #d7f0c4;
}

.mode-badge.is-danger {
  color: #c45656;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
}

.map-note {
  color: #909399;
  font-size: 12px;
}

.map-wrap {
  flex: 1;
  min-height: 0;
}

.map-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border-top: 1px solid #ebeef5;
  background: #fafcff;
  color: #606266;
  font-size: 12px;
}

.map-notice {
  color: #e6a23c;
  font-weight: 600;
}

.list-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafcff;
}

.list-tools .muted {
  margin-left: auto;
  font-size: 12px;
}

.task-list {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.task-row {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 26px;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.task-row:hover {
  background: #f7f9fc;
}

.task-row.active {
  background: #ecf5ff;
  border-color: #c6e2ff;
}

.task-row.invalid-row {
  background: #fef0f0;
  border-color: #fbc4c4;
}

.task-seq {
  width: 24px;
  height: 24px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: #f0f4fa;
  color: #606266;
  font-size: 12px;
  font-weight: 600;
}

.task-row.active .task-seq {
  background: #409eff;
  color: #fff;
}

.task-route {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.station-chip {
  min-width: 58px;
  padding: 4px 9px;
  border: 1px solid #c6e2ff;
  border-radius: 6px;
  background: #fff;
  color: #409eff;
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.station-chip:hover {
  background: #ecf5ff;
}

.station-chip.empty {
  border-style: dashed;
  border-color: #cfd8e5;
  color: #909399;
  font-weight: 500;
}

.station-chip.picking {
  border-color: #e6a23c;
  background: #fdf6ec;
  color: #b88230;
  animation: chip-blink 1.2s ease-in-out infinite;
}

@keyframes chip-blink {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(230, 162, 60, 0.25);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(230, 162, 60, 0.12);
  }
}

.route-arrow {
  color: #c0c4cc;
  font-size: 12px;
}

.row-del {
  border: none;
  background: transparent;
  color: #909399;
  font-size: 17px;
  cursor: pointer;
  padding: 0;
}

.row-del:hover {
  color: #f56c6c;
}

.task-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: #909399;
  font-size: 12px;
  text-align: center;
  line-height: 1.7;
}

.list-foot {
  padding: 8px 12px;
  border-top: 1px solid #ebeef5;
  background: #fafcff;
  color: #909399;
  font-size: 11px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 1100px) {
  .editor-body {
    grid-template-columns: 1fr;
    height: auto;
  }

  .map-panel {
    min-height: 340px;
  }

  .task-panel {
    height: 340px;
  }
}
</style>
