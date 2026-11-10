<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
const invalidTaskId = ref<number | null>(null)
const form = reactive<{ name: string; mapId: number | null; tasks: TaskDto[] }>({
  name: '',
  mapId: null,
  tasks: []
})
let lastMapId: number | null = null

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

async function loadDialogMap(mapId: number | null) {
  dialogRoadnet.value = null
  if (!mapId) return
  mapLoading.value = true
  try {
    dialogRoadnet.value = await getRoadNet(mapId)
  } catch (error: any) {
    ElMessage.error(error.message || '加载路网失败')
  } finally {
    mapLoading.value = false
  }
}

function defaultTasks(): TaskDto[] {
  if (stationNodes.value.length < 2) return []
  return [{ id: 1, from: stationNodes.value[0].id, to: stationNodes.value[1].id }]
}

async function openCreate() {
  if (maps.value.length === 0) {
    ElMessage.warning('请先在「车间地图」中创建路网')
    return
  }
  editingId.value = null
  invalidTaskId.value = null
  form.name = '新任务集'
  form.mapId = maps.value[0].id
  lastMapId = form.mapId
  await loadDialogMap(form.mapId)
  form.tasks = defaultTasks()
  dialogVisible.value = true
}

async function openEdit(row: TaskSetSummary) {
  try {
    const payload = await getTaskSet(row.id)
    editingId.value = row.id
    invalidTaskId.value = null
    form.name = row.name
    form.mapId = row.mapId ?? maps.value[0]?.id ?? null
    lastMapId = form.mapId
    await loadDialogMap(form.mapId)
    form.tasks = payload.tasks.map((task) => ({ ...task }))
    dialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '读取任务集失败')
  }
}

async function onMapChange(mapId: number) {
  if (form.tasks.length > 0) {
    try {
      await ElMessageBox.confirm(
        '切换路网会清空当前任务行，因为节点编号只在单张路网内唯一。确定切换吗？',
        '切换路网',
        { type: 'warning', confirmButtonText: '切换并清空', cancelButtonText: '取消' }
      )
    } catch {
      form.mapId = lastMapId
      return
    }
  }
  lastMapId = mapId
  await loadDialogMap(mapId)
  form.tasks = defaultTasks()
  invalidTaskId.value = null
}

function addTask() {
  if (stationNodes.value.length < 2) {
    ElMessage.warning('该路网至少需要两个工位才能创建任务')
    return
  }
  const nextId = form.tasks.length === 0 ? 1 : Math.max(...form.tasks.map((task) => task.id)) + 1
  form.tasks.push({
    id: nextId,
    from: stationNodes.value[0].id,
    to: stationNodes.value[1].id
  })
}

function removeTask(index: number) {
  form.tasks.splice(index, 1)
}

function fillDemo() {
  const stations = stationNodes.value
  if (stations.length < 2) {
    ElMessage.warning('该路网至少需要两个工位才能填充演示数据')
    return
  }
  form.tasks = Array.from({ length: 20 }, (_, i) => {
    const from = stations[i % stations.length].id
    let to = stations[(i + 1 + Math.floor(i / 3)) % stations.length].id
    if (to === from) {
      to = stations[(i + 2) % stations.length].id
    }
    return { id: i + 1, from, to }
  })
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
}

function closePicker() {
  pickerVisible.value = false
  pickerIndex.value = null
}

function rowClassName({ row }: { row: TaskDto }) {
  return invalidTaskId.value === row.id ? 'invalid-row' : ''
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写任务集名称')
    return
  }
  if (!form.mapId) {
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
  invalidTaskId.value = null
  try {
    const payload = { name: form.name, mapId: form.mapId, taskSet: { tasks: form.tasks } }
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
    const matched = /任务 (\d+)/.exec(message)
    invalidTaskId.value = matched ? Number(matched[1]) : null
    ElMessage.error(message)
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
            <el-tag v-if="row.mapId" size="small">{{ row.mapName || `路网 ${row.mapId}` }}</el-tag>
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
            v-model="form.mapId"
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

      <div class="tools">
        <el-button size="small" @click="addTask">加一条</el-button>
        <el-button size="small" @click="fillDemo">填充 20 单演示数据</el-button>
        <span class="muted">共 {{ form.tasks.length }} 条</span>
        <span v-if="stationNodes.length < 2" class="warning">
          当前路网只有 {{ stationNodes.length }} 个工位，至少需要两个
        </span>
      </div>

      <el-table :data="form.tasks" height="360" size="small" :row-class-name="rowClassName">
        <el-table-column prop="id" label="序号" width="70" />
        <el-table-column label="起点" min-width="190">
          <template #default="{ row, $index }">
            <div class="station-cell">
              <el-select v-model="row.from" filterable size="small" style="width: 100%">
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
              <el-select v-model="row.to" filterable size="small" style="width: 100%">
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
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
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
