<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RoadNetScene from '@/components/RoadNetScene.vue'
import type { AgvView } from '@/types'
import { getRoadNet, listDict, listRoadNets, type RoadNetPayload, type RoadNetSummary } from '@/api/roadnet'
import {
  listTaskSets,
  pauseSim,
  resetSim,
  resumeSim,
  setSpeed as setSimSpeed,
  simStatus,
  startSim,
  type AgvSnapshot,
  type SimEvent,
  type SimMetrics,
  type SimStatus,
  type TaskSetSummary
} from '@/api/sim'
import { taskSetLabel } from '@/utils/format'

const maps = ref<RoadNetSummary[]>([])
const taskSets = ref<TaskSetSummary[]>([])
const algorithms = ref<{ dictCode: string; dictValue: string }[]>([])

const selectedRoadnetId = ref<number | null>(null)
const selectedTaskSetId = ref<number | null>(null)
const algorithm = ref('nearest')
const agvCount = ref(3)
const seed = ref(20261107)

const mapTaskSets = computed(() =>
  taskSets.value.filter((set) => set.roadnetId === selectedRoadnetId.value)
)

const roadnet = ref<RoadNetPayload | null>(null)
const agvs = ref<AgvSnapshot[]>([])
const events = ref<SimEvent[]>([])
const metrics = ref<SimMetrics | null>(null)
const status = ref<SimStatus | null>(null)
const tick = ref(0)
const running = ref(false)
const finished = ref(false)
const paused = ref(false)
const speed = ref(1)
const socketState = ref<'closed' | 'open'>('closed')

let socket: WebSocket | null = null

const hasSim = computed(() => running.value || finished.value)

const simState = computed(() => {
  if (running.value) {
    return paused.value
      ? { label: '已暂停', type: 'warning' as const }
      : { label: '运行中', type: 'success' as const }
  }
  if (finished.value) {
    return status.value?.status === 'FAILED'
      ? { label: '仿真失败', type: 'danger' as const }
      : { label: '已完成', type: 'info' as const }
  }
  return null
})

const agvViews = computed<AgvView[]>(() =>
  agvs.value.map((a) => ({
    id: a.id,
    x: a.x,
    y: a.y,
    heading: a.heading,
    status: a.status,
    taskId: a.taskId
  }))
)

const progress = computed(() => {
  if (!metrics.value || metrics.value.totalTasks === 0) return 0
  return Math.round((metrics.value.completedTasks / metrics.value.totalTasks) * 100)
})

const eventTypeLabel: Record<string, string> = {
  TASK_ASSIGNED: '分配任务',
  LOADING: '装货',
  TASK_DONE: '任务完成',
  WAIT_CONFLICT: '冲突等待',
  REPLAN: '重规划',
  DEADLOCK_BREAK: '打破僵局',
  CHARGING: '回充',
  FINISHED: '结束'
}

async function loadOptions() {
  try {
    maps.value = await listRoadNets()
    taskSets.value = await listTaskSets()
    algorithms.value = await listDict('algorithm')
    if (maps.value.length > 0) {
      selectedRoadnetId.value = maps.value[0].id
      await loadRoadNet(maps.value[0].id)
      selectDefaultTaskSet()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载参数失败')
  }
}

/** 任务集绑定路网，切换路网后只保留匹配的任务集。 */
function selectDefaultTaskSet() {
  const candidates = mapTaskSets.value
  if (!candidates.some((set) => set.id === selectedTaskSetId.value)) {
    selectedTaskSetId.value = candidates[0]?.id ?? null
  }
}

async function loadRoadNet(id: number) {
  try {
    roadnet.value = await getRoadNet(id)
  } catch (error: any) {
    ElMessage.error(error.message || '加载路网失败')
  }
}

async function onMapChange(id: number) {
  await loadRoadNet(id)
  selectDefaultTaskSet()
}

function connectSocket() {
  if (socket && socket.readyState === WebSocket.OPEN) return
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const ws = new WebSocket(`${protocol}://${location.host}/ws/sim`)
  socket = ws
  ws.onopen = () => {
    socketState.value = 'open'
  }
  ws.onclose = () => {
    if (socket === ws) socket = null
    socketState.value = 'closed'
  }
  ws.onerror = () => {
    socketState.value = 'closed'
  }
  ws.onmessage = (messageEvent) => {
    try {
      const payload = JSON.parse(String(messageEvent.data))
      if (payload.type === 'frame') {
        const frame = payload.frame
        agvs.value = frame.agvs
        metrics.value = frame.metrics
        tick.value = frame.tick
        if (frame.events?.length) {
          events.value = [...frame.events, ...events.value].slice(0, 200)
        }
        if (frame.finished) {
          running.value = false
          finished.value = true
        }
      } else if (payload.type === 'status' || payload.type === 'connected') {
        if (payload.type === 'status') {
          status.value = payload as SimStatus
          paused.value = !!payload.paused
          if (payload.status === 'FINISHED' || payload.status === 'FAILED') {
            running.value = false
            finished.value = true
          }
        }
      }
    } catch {
      // 忽略非 JSON 消息
    }
  }
}

/**
 * 刷新页面或切换菜单后，后端可能仍在运行仿真。
 * 用 /api/sim/status 把运行状态、参数和指标同步回页面，避免显示成「未开始」。
 */
async function syncStatus() {
  try {
    const current = await simStatus()
    if (current.status !== 'RUNNING' && current.status !== 'FINISHED' && current.status !== 'FAILED') {
      return
    }
    status.value = current
    running.value = current.status === 'RUNNING'
    finished.value = current.status !== 'RUNNING'
    paused.value = current.paused
    speed.value = current.speed
    tick.value = current.tick
    metrics.value = current.metrics
    if (current.roadnetId) {
      selectedRoadnetId.value = current.roadnetId
      await loadRoadNet(current.roadnetId)
    }
    if (current.taskSetId) selectedTaskSetId.value = current.taskSetId
    selectDefaultTaskSet()
    if (current.algorithm) algorithm.value = current.algorithm
    if (current.agvCount) agvCount.value = current.agvCount
    if (current.seed !== null && current.seed !== undefined) seed.value = current.seed
  } catch {
    // 状态恢复失败不阻断手动启动
  }
}

async function start() {
  if (!selectedRoadnetId.value || !selectedTaskSetId.value) {
    ElMessage.warning('请先选择路网与任务集')
    return
  }
  connectSocket()
  try {
    const result = await startSim({
      roadnetId: selectedRoadnetId.value,
      taskSetId: selectedTaskSetId.value,
      algorithm: algorithm.value,
      agvCount: agvCount.value,
      seed: seed.value
    })
    status.value = result
    events.value = []
    agvs.value = []
    metrics.value = result.metrics ?? null
    tick.value = result.tick ?? 0
    running.value = true
    finished.value = false
    paused.value = false
    speed.value = result.speed ?? 1
    ElMessage.success('仿真已启动')
  } catch (error: any) {
    ElMessage.error(error.message || '启动失败')
  }
}

async function pause() {
  try {
    await pauseSim()
    paused.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '暂停失败')
  }
}

async function resume() {
  try {
    await resumeSim()
    paused.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '继续失败')
  }
}

async function reset() {
  try {
    await resetSim()
    running.value = false
    finished.value = false
    paused.value = false
    agvs.value = []
    events.value = []
    metrics.value = null
    tick.value = 0
    status.value = null
  } catch (error: any) {
    ElMessage.error(error.message || '重置失败')
  }
}

async function changeSpeed(value: number) {
  const previous = speed.value
  speed.value = value
  try {
    await setSimSpeed(value)
  } catch (error: any) {
    speed.value = previous
    ElMessage.error(error.message || '调速失败')
  }
}

function onSpeedChange(value: string | number | boolean | undefined) {
  void changeSpeed(Number(value))
}

function statusText(value: string) {
  return (
    {
      IDLE: '空闲',
      MOVING: '行驶',
      WAITING: '等待',
      LOADING: '装卸',
      CHARGING: '充电',
      DONE: '完成'
    }[value] || value
  )
}

function statusTagType(value: string) {
  return (
    {
      MOVING: 'primary',
      WAITING: 'danger',
      LOADING: 'warning',
      CHARGING: 'success',
      IDLE: 'info',
      DONE: 'info'
    }[value] || 'info'
  )
}

onMounted(async () => {
  await loadOptions()
  await syncStatus()
  connectSocket()
})
</script>

<template>
  <div class="sim-page">
    <div class="toolbar panel">
      <el-select v-model="selectedRoadnetId" style="width: 200px" placeholder="路网" @change="onMapChange">
        <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
      </el-select>
      <el-select v-model="selectedTaskSetId" style="width: 190px" placeholder="任务集">
        <el-option
          v-for="set in mapTaskSets"
          :key="set.id"
          :label="taskSetLabel(set.name, set.taskCount)"
          :value="set.id"
        />
      </el-select>
      <span v-if="selectedRoadnetId && mapTaskSets.length === 0" class="muted">该路网还没有任务集</span>
      <el-select v-model="algorithm" style="width: 150px">
        <el-option v-for="item in algorithms" :key="item.dictCode" :label="item.dictValue" :value="item.dictCode" />
      </el-select>
      <el-input-number v-model="agvCount" :min="1" :max="12" style="width: 120px" />
      <el-input-number v-model="seed" :min="0" :controls="false" style="width: 140px" />
      <el-button type="primary" :disabled="running || !selectedTaskSetId" @click="start">开始</el-button>
      <el-button :disabled="!running || paused" @click="pause">暂停</el-button>
      <el-button :disabled="!running || !paused" @click="resume">继续</el-button>
      <el-button :disabled="!hasSim" @click="reset">重置</el-button>
      <el-radio-group :model-value="speed" size="small" @change="onSpeedChange">
        <el-radio-button :value="0.5">0.5x</el-radio-button>
        <el-radio-button :value="1">1x</el-radio-button>
        <el-radio-button :value="2">2x</el-radio-button>
        <el-radio-button :value="4">4x</el-radio-button>
      </el-radio-group>
      <el-tag v-if="simState" :type="simState.type" size="small">{{ simState.label }}</el-tag>
      <el-tag :type="socketState === 'open' ? 'success' : 'info'" size="small">
        {{ socketState === 'open' ? '实时通道已连接' : '实时通道未连接' }}
      </el-tag>
      <el-button v-if="socketState !== 'open'" size="small" @click="connectSocket">重连</el-button>
    </div>

    <div class="body">
      <div class="panel scene-panel">
        <RoadNetScene :roadnet="roadnet" :agvs="agvViews" />
      </div>

      <div class="side">
        <div class="panel">
          <div class="metric-grid">
            <div class="metric">
              <div class="metric-label">tick</div>
              <div class="metric-value">{{ tick }}</div>
            </div>
            <div class="metric">
              <div class="metric-label">完成</div>
              <div class="metric-value">
                {{ metrics?.completedTasks ?? 0 }}/{{ metrics?.totalTasks ?? 0 }}
              </div>
            </div>
            <div class="metric">
              <div class="metric-label">完成时间</div>
              <div class="metric-value">{{ metrics?.makespan ?? 0 }}</div>
            </div>
            <div class="metric">
              <div class="metric-label">冲突</div>
              <div class="metric-value">{{ metrics?.conflictCount ?? 0 }}</div>
            </div>
            <div class="metric">
              <div class="metric-label">总里程</div>
              <div class="metric-value">{{ (metrics?.totalPathLen ?? 0).toFixed(1) }} m</div>
            </div>
            <div class="metric">
              <div class="metric-label">等待</div>
              <div class="metric-value">{{ metrics?.totalWaitTick ?? 0 }}</div>
            </div>
          </div>
          <el-progress :percentage="progress" :stroke-width="8" style="margin-top: 12px" />
        </div>

        <div class="panel agv-panel">
          <div class="panel-title">车辆状态</div>
          <div v-if="agvs.length === 0" class="muted">
            {{ finished ? '仿真已结束，车辆位置只在运行中可见' : '尚未启动仿真' }}
          </div>
          <div v-for="agv in agvs" :key="agv.id" class="agv-row">
            <span class="agv-id">{{ agv.id }}</span>
            <el-tag size="small" :type="statusTagType(agv.status)">{{ statusText(agv.status) }}</el-tag>
            <span class="muted">任务 {{ agv.taskId > 0 ? agv.taskId : '-' }}</span>
            <span class="muted">{{ agv.arcId ?? '节点' }} {{ agv.s.toFixed(1) }}m</span>
          </div>
        </div>

        <div class="panel log-panel">
          <div class="panel-title">事件日志</div>
          <div v-if="events.length === 0" class="muted">
            {{ finished ? '仿真已结束；事件只在运行期间推送，刷新后不保留' : '等待冲突、任务分配等事件会显示在这里' }}
          </div>
          <div v-for="(event, index) in events" :key="index" class="log-row">
            <span class="log-tick">{{ event.tick }}</span>
            <el-tag size="small" :type="event.type === 'WAIT_CONFLICT' ? 'danger' : 'info'">
              {{ eventTypeLabel[event.type] || event.type }}
            </el-tag>
            <span class="log-detail">{{ event.agvId }} {{ event.detail }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sim-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
}

.body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  min-height: 620px;
}

.scene-panel {
  padding: 0;
  overflow: hidden;
  min-height: 620px;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metric-label {
  font-size: 12px;
  color: #909399;
}

.metric-value {
  font-size: 18px;
  font-weight: 600;
  color: #1f3b73;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.agv-panel {
  max-height: 240px;
  overflow: auto;
}

.agv-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}

.agv-id {
  font-weight: 600;
  width: 56px;
}

.log-panel {
  flex: 1;
  min-height: 160px;
  max-height: 320px;
  overflow: auto;
}

.log-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  border-bottom: 1px dashed #f0f2f5;
}

.log-tick {
  color: #909399;
  width: 44px;
  text-align: right;
  font-family: Consolas, monospace;
}

.log-detail {
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .body {
    grid-template-columns: 1fr;
  }
}
</style>
