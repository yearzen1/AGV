<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RoadNetScene from '@/components/RoadNetScene.vue'
import type { AgvView } from '@/types'
import { isSceneQuality, loadQuality, saveQuality, type SceneQuality } from '@/three/quality'
import { getRoadNet, listDict, listRoadNets, type RoadNetPayload, type RoadNetSummary } from '@/api/roadnet'
import {
  getTaskSet,
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
  type TaskDto,
  type TaskSetSummary
} from '@/api/sim'
import { formatTicks, taskSetLabel } from '@/utils/format'
import { simSocketUrl } from '@/utils/ws'

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
const tasks = ref<TaskDto[]>([])
const agvs = ref<AgvSnapshot[]>([])
const events = ref<SimEvent[]>([])
const metrics = ref<SimMetrics | null>(null)
const status = ref<SimStatus | null>(null)
const tick = ref(0)
const running = ref(false)
const finished = ref(false)
const deadlocked = ref(false)
const paused = ref(false)
/** 已发出启动请求但还没拿到响应：此期间的帧仍属于本次运行，不能丢。 */
const starting = ref(false)
const speed = ref(1)
const socketState = ref<'closed' | 'open' | 'failed'>('closed')

/** 自动重连上限与退避间隔；耗尽后停止，等待用户手动重试。 */
const MAX_RECONNECT_ATTEMPTS = 5
const RECONNECT_DELAYS = [2000, 4000, 8000, 16000, 30000]

const quality = ref<SceneQuality>(loadQuality())
const showLabels = ref(true)
const topView = ref(false)
const selectedAgvId = ref<string | null>(null)
const highlightNodeId = ref<string | null>(null)
const sideTab = ref<'agv' | 'task' | 'node' | 'log'>('agv')
const eventTypeFilter = ref<string[]>([])

/** 单个任务的分配/完成时刻，来源是本轮推送的事件；不落库，刷新后从当前帧重算。 */
interface TaskTiming {
  assigned: number | null
  done: number | null
}
const taskTimings = ref<Record<number, TaskTiming>>({})

interface SceneHandle {
  fitView: () => void
  setTopView: (on: boolean) => void
  focus: (id: string | null) => void
  highlightNode: (id: string | null) => void
}

const sceneRef = ref<SceneHandle | null>(null)

let socket: WebSocket | null = null
let reconnectTimer: number | null = null
let reconnectAttempts = 0
let disposed = false

const hasSim = computed(() => running.value || finished.value)

const simState = computed(() => {
  if (running.value) {
    return paused.value
      ? { label: '已暂停', type: 'warning' as const }
      : { label: '运行中', type: 'success' as const }
  }
  if (finished.value) {
    if (deadlocked.value) {
      return { label: '死锁 / 未完成', type: 'danger' as const }
    }
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
    taskId: a.taskId,
    arcId: a.arcId
  }))
)

const progress = computed(() => {
  if (!metrics.value || metrics.value.totalTasks === 0) return 0
  return Math.round((metrics.value.completedTasks / metrics.value.totalTasks) * 100)
})

/** 平均等待：总等待 tick 摊到每台车上。不落库，前后端都不单独存这一列。 */
const avgWaitTick = computed(() => {
  const count = status.value?.agvCount ?? agvCount.value ?? 1
  return Math.round((metrics.value?.totalWaitTick ?? 0) / Math.max(1, count))
})

const taskById = computed(() => new Map(tasks.value.map((task) => [task.id, task])))

const nodeCapacity = computed(
  () => new Map((roadnet.value?.nodes ?? []).map((node) => [node.id, node.capacity ?? 1]))
)

const nodeTypes = computed(
  () => new Map((roadnet.value?.nodes ?? []).map((node) => [node.id, node.type]))
)

/** 弧编号带方向后缀（E2+ / E2-），长度按物理路段取。 */
const edgeLengths = computed(
  () => new Map((roadnet.value?.edges ?? []).map((edge) => [edge.id, edge.length]))
)

const nodeTypeLabel: Record<string, string> = {
  WORKSTATION: '工位',
  CHARGER: '充电位',
  BUFFER: '缓存位',
  JUNCTION: '路口'
}

const eventTypeLabel: Record<string, string> = {
  TASK_ASSIGNED: '分配任务',
  LOADING: '装货',
  TASK_DONE: '任务完成',
  WAIT_CONFLICT: '冲突等待',
  YIELD_BUFFER: '让位缓存',
  REPLAN: '重规划',
  DEADLOCK_BREAK: '打破僵局',
  CHARGING: '回充',
  FINISHED: '结束'
}

const eventTypeOptions = Object.entries(eventTypeLabel).map(([value, label]) => ({ value, label }))

const filteredEvents = computed(() => {
  if (eventTypeFilter.value.length === 0) return events.value
  return events.value.filter((event) => eventTypeFilter.value.includes(event.type))
})

/** 任务清单：已完成 / 进行中 / 未分配，用时来自事件时刻。 */
const taskRows = computed(() => {
  const holders = new Map<number, string>()
  for (const agv of agvs.value) {
    if (agv.taskId > 0) holders.set(agv.taskId, agv.id)
  }
  const sorted = [...tasks.value].sort((a, b) => a.id - b.id)
  return sorted.map((task) => {
    const timing = taskTimings.value[task.id]
    const holder = holders.get(task.id) ?? null
    const done = timing?.done !== null && timing?.done !== undefined
    const assigned = timing?.assigned !== null && timing?.assigned !== undefined
    const state = done ? 'done' : holder || assigned ? 'running' : 'pending'
    const duration = done && assigned ? timing!.done! - timing!.assigned! : null
    return { ...task, holder, state, duration }
  })
})

const taskCounts = computed(() => {
  const done = metrics.value?.completedTasks ?? 0
  const runningCount = taskRows.value.filter((row) => row.state === 'running').length
  const total = tasks.value.length
  return { done, running: runningCount, pending: Math.max(0, total - done - runningCount), total }
})

/** 本页面进入前就已完成、没有分配到/完成事件的任务数；用时不可追溯。 */
const untrackedDone = computed(
  () => Math.max(0, taskCounts.value.done - taskRows.value.filter((row) => row.state === 'done').length)
)

/** 节点占用：区分「预约占用」与「让位等待」，前者才是容量约束的对象。 */
const nodeRows = computed(() => {
  const cars = new Map<string, AgvSnapshot[]>()
  for (const agv of agvs.value) {
    if (agv.arcId || !agv.nodeId) continue
    const list = cars.get(agv.nodeId) ?? []
    list.push(agv)
    cars.set(agv.nodeId, list)
  }
  return [...cars.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([id, list]) => {
      const capacity = nodeCapacity.value.get(id) ?? 1
      const type = nodeTypes.value.get(id) ?? ''
      // 路口等待时保持预约；非路口等待时已让出预约（引擎为避免等待环主动释放）。
      const reserved = list.filter((agv) => agv.status !== 'WAITING' || type === 'JUNCTION').length
      const yielded = list.length - reserved
      return {
        id,
        reserved,
        yielded,
        capacity,
        type: nodeTypeLabel[type] ?? '节点',
        full: reserved >= capacity
      }
    })
})

/** 场景光环只看预约，和表格的红色判定保持一致。 */
const nodeOccupancy = computed<Record<string, number>>(() => {
  const result: Record<string, number> = {}
  for (const row of nodeRows.value) result[row.id] = row.reserved
  return result
})

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

async function loadTasks(id: number | null) {
  if (!id) {
    tasks.value = []
    return
  }
  try {
    const payload = await getTaskSet(id)
    tasks.value = payload.tasks ?? []
  } catch {
    tasks.value = []
  }
}

async function onMapChange(id: number) {
  await loadRoadNet(id)
  selectDefaultTaskSet()
}

function connectSocket() {
  if (disposed) return
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return
  const ws = new WebSocket(simSocketUrl())
  socket = ws
  ws.onopen = () => {
    socketState.value = 'open'
    reconnectAttempts = 0
  }
  ws.onclose = () => {
    if (socket === ws) socket = null
    if (socketState.value !== 'failed') socketState.value = 'closed'
    scheduleReconnect()
  }
  ws.onerror = () => {
    if (socketState.value !== 'failed') socketState.value = 'closed'
  }
  ws.onmessage = (messageEvent) => {
    try {
      const payload = JSON.parse(String(messageEvent.data))
      if (payload.type === 'frame') {
        // 重置后仍可能收到停止瞬间的在途帧，忽略它，避免已清空的指标和车辆列表回跳；
        // 启动请求在途时（starting）必须收帧，否则最早的分配事件会被丢掉。
        if (!running.value && !finished.value && !starting.value) return
        const frame = payload.frame
        agvs.value = frame.agvs
        metrics.value = frame.metrics
        tick.value = frame.tick
        if (frame.events?.length) {
          recordTaskTimings(frame.events)
          events.value = [...frame.events, ...events.value].slice(0, 200)
        }
        if (frame.finished) {
          running.value = false
          finished.value = true
          deadlocked.value = !!frame.deadlocked
        }
      } else if (payload.type === 'status' || payload.type === 'connected') {
        if (payload.type === 'status') {
          status.value = payload as SimStatus
          paused.value = !!payload.paused
          deadlocked.value = !!payload.deadlocked
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

/** 按任务聚合事件时刻，任务清单据此算状态与用时。 */
function recordTaskTimings(frameEvents: SimEvent[]) {
  const next = { ...taskTimings.value }
  let changed = false
  for (const event of frameEvents) {
    const taskId = event.taskId ?? -1
    if (taskId < 0) continue
    const current = next[taskId] ?? { assigned: null, done: null }
    if (event.type === 'TASK_ASSIGNED') {
      next[taskId] = { ...current, assigned: event.tick }
      changed = true
    } else if (event.type === 'TASK_DONE') {
      next[taskId] = { ...current, done: event.tick }
      changed = true
    }
  }
  if (changed) taskTimings.value = next
}

/** 连接意外断开时按退避间隔重试；达到上限后停止，离开页面后不再重连。 */
function scheduleReconnect() {
  if (disposed || reconnectTimer !== null || socketState.value === 'failed') return
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    socketState.value = 'failed'
    ElMessage.warning('实时通道连接失败，请检查网络或登录状态后点击「重试」')
    return
  }
  const delay = RECONNECT_DELAYS[reconnectAttempts]
  reconnectAttempts += 1
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectSocket()
  }, delay)
}

/** 手动重连或重新开始仿真：清掉待执行定时器并重置重试次数。 */
function retrySocket() {
  if (reconnectTimer !== null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  reconnectAttempts = 0
  if (socketState.value === 'failed') socketState.value = 'closed'
  connectSocket()
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
    deadlocked.value = !!current.deadlocked
    paused.value = current.paused
    speed.value = current.speed
    tick.value = current.tick
    metrics.value = current.metrics
    agvs.value = current.agvs ?? []
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
  retrySocket()
  // 先清空再发请求：请求在途时已经开始收帧，若在响应后清空会把最早的分配事件抹掉。
  starting.value = true
  events.value = []
  taskTimings.value = {}
  agvs.value = []
  metrics.value = null
  tick.value = 0
  finished.value = false
  deadlocked.value = false
  paused.value = false
  selectedAgvId.value = null
  highlightNodeId.value = null
  sceneRef.value?.focus(null)
  try {
    const result = await startSim({
      roadnetId: selectedRoadnetId.value,
      taskSetId: selectedTaskSetId.value,
      algorithm: algorithm.value,
      agvCount: agvCount.value,
      seed: seed.value
    })
    status.value = result
    running.value = true
    speed.value = result.speed ?? 1
    ElMessage.success('仿真已启动')
  } catch (error: any) {
    ElMessage.error(error.message || '启动失败')
    await syncStatus()
  } finally {
    starting.value = false
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
    deadlocked.value = false
    paused.value = false
    agvs.value = []
    events.value = []
    taskTimings.value = {}
    metrics.value = null
    tick.value = 0
    speed.value = 1
    status.value = null
    selectedAgvId.value = null
    highlightNodeId.value = null
    sceneRef.value?.focus(null)
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

/** 车辆当前任务的可读描述；没有任务时按状态给一句。 */
function agvTaskText(agv: AgvSnapshot) {
  if (agv.taskId > 0) {
    const task = taskById.value.get(agv.taskId)
    return task ? `#${task.id} ${task.from}→${task.to}` : `#${agv.taskId}`
  }
  if (agv.status === 'CHARGING') return '回充'
  if (agv.status === 'DONE') return '已完成'
  return '待命'
}

/** 在弧上的行程百分比；弧编号去掉方向后缀查物理路段长度。 */
function arcProgress(agv: AgvSnapshot) {
  if (!agv.arcId) return null
  const length = edgeLengths.value.get(agv.arcId.replace(/[+-]$/, ''))
  if (!length) return null
  return Math.min(100, Math.round((agv.s / length) * 100))
}

function arcTitle(agv: AgvSnapshot) {
  if (!agv.arcId) return agv.nodeId ? `节点 ${agv.nodeId}` : ''
  return `弧 ${agv.arcId} ${agv.s.toFixed(1)}m`
}

const taskStateLabel: Record<string, string> = {
  pending: '未分配',
  running: '进行中',
  done: '已完成'
}

const taskStateTag: Record<string, 'info' | 'warning' | 'success'> = {
  pending: 'info',
  running: 'warning',
  done: 'success'
}

function onQualityChange(value: string | number | boolean | undefined) {
  const next = String(value)
  if (!isSceneQuality(next)) return
  quality.value = next
  saveQuality(next)
}

function toggleTopView() {
  sceneRef.value?.setTopView(!topView.value)
}

function onViewMode(top: boolean) {
  topView.value = top
}

function onSelectAgv(id: string | null) {
  selectedAgvId.value = id
}

function focusAgv(id: string) {
  selectedAgvId.value = id
  sceneRef.value?.focus(id)
}

/** 点节点表：切换白色高亮圈，便于在 3D 里定位。 */
function onSelectNode(id: string) {
  highlightNodeId.value = highlightNodeId.value === id ? null : id
  sceneRef.value?.highlightNode(highlightNodeId.value)
}

function onKeydown(event: KeyboardEvent) {
  if (event.ctrlKey || event.metaKey || event.altKey) return
  const target = event.target as HTMLElement | null
  if (
    target &&
    (target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.tagName === 'SELECT' ||
      target.isContentEditable)
  ) {
    return
  }
  const key = event.key.toLowerCase()
  if (key === 'f' && selectedAgvId.value) {
    sceneRef.value?.focus(selectedAgvId.value)
  } else if (key === 't') {
    toggleTopView()
  } else if (key === 'r') {
    sceneRef.value?.fitView()
  }
}

watch(selectedTaskSetId, (id) => void loadTasks(id), { immediate: true })

onMounted(async () => {
  await loadOptions()
  await syncStatus()
  connectSocket()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  disposed = true
  if (reconnectTimer !== null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  socket?.close()
  socket = null
})
</script>

<template>
  <div class="sim-page">
    <div class="toolbar panel">
      <span class="field-label">路网</span>
      <el-select
        v-model="selectedRoadnetId"
        class="param-select"
        style="width: 200px"
        placeholder="路网"
        :disabled="running"
        @change="onMapChange"
      >
        <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
      </el-select>
      <span class="field-label">任务集</span>
      <el-select
        v-model="selectedTaskSetId"
        class="param-select"
        style="width: 190px"
        placeholder="任务集"
        :disabled="running"
      >
        <el-option
          v-for="set in mapTaskSets"
          :key="set.id"
          :label="taskSetLabel(set.name, set.taskCount)"
          :value="set.id"
        />
      </el-select>
      <span v-if="selectedRoadnetId && mapTaskSets.length === 0" class="muted">该路网还没有任务集</span>
      <span class="field-label">算法</span>
      <el-select v-model="algorithm" class="param-select" style="width: 150px" :disabled="running">
        <el-option v-for="item in algorithms" :key="item.dictCode" :label="item.dictValue" :value="item.dictCode" />
      </el-select>
      <span class="field-label">AGV 数量</span>
      <el-input-number v-model="agvCount" :min="1" :max="12" style="width: 120px" :disabled="running" />
      <span class="field-label">随机种子</span>
      <el-input-number v-model="seed" :min="0" :controls="false" style="width: 140px" :disabled="running" />
      <el-button type="primary" :disabled="running || !selectedTaskSetId" @click="start">开始</el-button>
      <el-button :disabled="!running || paused" @click="pause">暂停</el-button>
      <el-button :disabled="!running || !paused" @click="resume">继续</el-button>
      <el-button :disabled="!hasSim" @click="reset">重置</el-button>
      <el-radio-group
        class="speed-group"
        :model-value="speed"
        size="small"
        :disabled="!running"
        @change="onSpeedChange"
      >
        <el-radio-button :value="0.5">0.5x</el-radio-button>
        <el-radio-button :value="1">1x</el-radio-button>
        <el-radio-button :value="2">2x</el-radio-button>
        <el-radio-button :value="4">4x</el-radio-button>
      </el-radio-group>
      <el-select v-model="quality" size="small" style="width: 108px" @change="onQualityChange">
        <el-option label="低画质" value="low" />
        <el-option label="中画质" value="medium" />
        <el-option label="高画质" value="high" />
      </el-select>
      <el-checkbox v-model="showLabels" size="small">标签</el-checkbox>
      <el-button size="small" :type="topView ? 'primary' : ''" @click="toggleTopView">俯视</el-button>
      <el-tag v-if="simState" :type="simState.type" size="small">{{ simState.label }}</el-tag>
      <el-tag
        :type="socketState === 'open' ? 'success' : socketState === 'failed' ? 'danger' : 'info'"
        size="small"
      >
        {{
          socketState === 'open'
            ? '实时通道已连接'
            : socketState === 'failed'
              ? '实时通道已断开'
              : '实时通道未连接'
        }}
      </el-tag>
      <el-button v-if="socketState !== 'open'" size="small" @click="retrySocket">
        {{ socketState === 'failed' ? '重试' : '重连' }}
      </el-button>
    </div>

    <div class="body">
      <div class="panel scene-panel">
        <RoadNetScene
          ref="sceneRef"
          :roadnet="roadnet"
          :agvs="agvViews"
          :tick="tick"
          :quality="quality"
          :show-labels="showLabels"
          :selected-id="selectedAgvId"
          :node-occupancy="nodeOccupancy"
          :highlight-node-id="highlightNodeId"
          @select="onSelectAgv"
          @view-mode="onViewMode"
        />
        <div class="scene-legend">
          <span><i class="dot partial"></i>节点有车</span>
          <span><i class="dot full"></i>节点满员</span>
          <span><i class="dot selected"></i>选中节点</span>
        </div>
      </div>

      <div class="side">
        <div class="panel metrics-panel" :data-tick="tick">
          <div class="metric-grid">
            <div class="metric">
              <div class="metric-label">运行时长</div>
              <div class="metric-value">{{ formatTicks(tick) }}</div>
              <div class="metric-sub">{{ tick }} tick</div>
            </div>
            <div class="metric">
              <div class="metric-label">完成</div>
              <div class="metric-value">
                {{ metrics?.completedTasks ?? 0 }}/{{ metrics?.totalTasks ?? 0 }}
              </div>
              <div class="metric-sub">{{ progress }}%</div>
            </div>
            <div class="metric">
              <div class="metric-label">完成时间</div>
              <div class="metric-value">{{ formatTicks(metrics?.makespan) }}</div>
              <div class="metric-sub">最后一件完成</div>
            </div>
            <div class="metric">
              <div class="metric-label">单均配送</div>
              <div class="metric-value">{{ formatTicks(metrics?.avgDeliveryTick) }}</div>
              <div class="metric-sub">分配 → 完成</div>
            </div>
            <div class="metric">
              <div class="metric-label">平均等待</div>
              <div class="metric-value">{{ formatTicks(avgWaitTick) }}</div>
              <div class="metric-sub">每台车</div>
            </div>
            <div class="metric">
              <div class="metric-label">冲突</div>
              <div class="metric-value">{{ metrics?.conflictCount ?? 0 }}</div>
              <div class="metric-sub">{{ metrics?.totalWaitTick ?? 0 }} tick 等待</div>
            </div>
            <div class="metric">
              <div class="metric-label">总里程</div>
              <div class="metric-value">{{ (metrics?.totalPathLen ?? 0).toFixed(1) }}</div>
              <div class="metric-sub">米（含空驶）</div>
            </div>
            <div class="metric">
              <div class="metric-label">完成率</div>
              <div class="metric-value">{{ progress }}%</div>
              <div class="metric-sub">{{ taskCounts.done }} / {{ taskCounts.total }} 单</div>
            </div>
          </div>
          <el-progress :percentage="progress" :stroke-width="8" style="margin-top: 12px" />
        </div>

        <div class="panel tabs-panel">
          <el-tabs v-model="sideTab" class="side-tabs">
            <el-tab-pane name="agv">
              <template #label>车辆 {{ agvs.length }}</template>
              <div class="agv-panel">
                <div v-if="agvs.length === 0" class="muted">
                  {{
                    running
                      ? '正在等待第一帧车辆数据…'
                      : finished
                        ? '仿真已结束，车辆位置只在运行中可见'
                        : '尚未启动仿真'
                  }}
                </div>
                <div
                  v-for="agv in agvs"
                  :key="agv.id"
                  class="agv-row"
                  :class="{ selected: selectedAgvId === agv.id }"
                  :title="arcTitle(agv)"
                  @click="focusAgv(agv.id)"
                >
                  <span class="agv-id">{{ agv.id }}</span>
                  <el-tag size="small" :type="statusTagType(agv.status)">{{ statusText(agv.status) }}</el-tag>
                  <span class="agv-task">{{ agvTaskText(agv) }}</span>
                  <span v-if="arcProgress(agv) !== null" class="agv-progress">
                    {{ arcProgress(agv) }}%
                  </span>
                  <span v-if="agv.waitTicks > 0" class="agv-wait">等待 {{ agv.waitTicks }}</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane name="task">
              <template #label>
                任务 {{ taskCounts.done }}/{{ taskCounts.total }}
              </template>
              <div class="list-panel task-panel">
                <div v-if="taskRows.length === 0" class="muted">该任务集没有任务</div>
                <div class="task-summary muted">
                  未分配 {{ taskCounts.pending }} · 进行中 {{ taskCounts.running }} · 已完成 {{ taskCounts.done }}
                </div>
                <div v-if="untrackedDone > 0" class="task-note muted">
                  其中 {{ untrackedDone }} 单在进入本页面之前已完成，用时不可追溯
                </div>
                <div v-for="row in taskRows" :key="row.id" class="task-row">
                  <span class="task-id">#{{ row.id }}</span>
                  <span class="task-path">{{ row.from }} → {{ row.to }}</span>
                  <el-tag size="small" :type="taskStateTag[row.state]">
                    {{ taskStateLabel[row.state] }}
                  </el-tag>
                  <span v-if="row.state === 'running' && row.holder" class="muted">{{ row.holder }}</span>
                  <span v-else-if="row.duration !== null" class="muted">{{ formatTicks(row.duration) }}</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane name="node">
              <template #label>节点 {{ nodeRows.length }}</template>
              <div class="list-panel node-panel">
                <div v-if="nodeRows.length === 0" class="muted">
                  当前没有车停靠在节点上；在弧上行驶的车不计入节点占用
                </div>
                <div class="node-hint muted">「预约 / 容量」超过容量才是异常；「让位」是已让出预约、但还没挪走的车</div>
                <div v-for="row in nodeRows" :key="row.id" class="node-row"
                  :class="{ selected: highlightNodeId === row.id }" @click="onSelectNode(row.id)">
                  <span class="node-id">{{ row.id }}</span>
                  <span class="muted">{{ row.type }}</span>
                  <span v-if="row.yielded > 0" class="node-yielded">让位 {{ row.yielded }}</span>
                  <span class="node-usage" :class="{ full: row.full }">
                    {{ row.reserved }} / {{ row.capacity }}
                  </span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane name="log">
              <template #label>事件 {{ events.length }}</template>
              <div class="list-panel log-panel">
                <el-select
                  v-model="eventTypeFilter"
                  multiple
                  collapse-tags
                  clearable
                  size="small"
                  placeholder="全部类型"
                  class="log-filter"
                >
                  <el-option
                    v-for="item in eventTypeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <div v-if="filteredEvents.length === 0" class="muted">
                  {{ finished ? '仿真已结束；事件只在运行期间推送，刷新后不保留' : '等待冲突、任务分配等事件会显示在这里' }}
                </div>
                <div v-for="(event, index) in filteredEvents" :key="index" class="log-row">
                  <span class="log-tick">{{ event.tick }}</span>
                  <el-tag size="small" :type="event.type === 'WAIT_CONFLICT' ? 'danger' : 'info'">
                    {{ eventTypeLabel[event.type] || event.type }}
                  </el-tag>
                  <span class="log-detail">
                    {{ event.agvId }}<span v-if="(event.taskId ?? -1) >= 0"> #{{ event.taskId }}</span>
                    {{ event.detail }}
                  </span>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
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
  height: 100%;
  min-height: 0;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  flex-shrink: 0;
}

.field-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  grid-template-rows: minmax(0, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.scene-panel {
  padding: 0;
  overflow: hidden;
  min-height: 0;
  position: relative;
}

.scene-legend {
  position: absolute;
  left: 14px;
  bottom: 12px;
  display: flex;
  gap: 14px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(8, 17, 30, 0.72);
  color: #dbe6f5;
  font-size: 12px;
  pointer-events: none;
}

.scene-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 5px;
  border-radius: 50%;
}

.scene-legend .dot.partial {
  background: #e6a23c;
}

.scene-legend .dot.full {
  background: #f56c6c;
}

.scene-legend .dot.selected {
  background: #ffffff;
}

/* 场景组件自身保留 420px 最小高度，仿真页跟随窗口缩短时需放开来 */
.scene-panel :deep(.scene) {
  min-height: 0;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

.metrics-panel {
  flex-shrink: 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 12px;
}

.metric-label {
  font-size: 12px;
  color: #909399;
}

.metric-value {
  font-size: 17px;
  font-weight: 600;
  color: #1f3b73;
  line-height: 1.3;
}

.metric-sub {
  font-size: 11px;
  color: #b1b6bf;
  line-height: 1.2;
}

.tabs-panel {
  flex: 1;
  min-height: 0;
  padding: 8px 14px 12px;
  display: flex;
  flex-direction: column;
}

.side-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.side-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
  flex-shrink: 0;
}

.side-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.side-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.agv-panel,
.list-panel {
  min-height: 0;
}

.agv-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
  cursor: pointer;
}

.agv-row.selected {
  background: #ecf5ff;
}

.agv-id {
  font-weight: 600;
  width: 52px;
  flex-shrink: 0;
}

.agv-task {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.agv-progress {
  color: #409eff;
  font-family: Consolas, monospace;
  font-size: 12px;
}

.agv-wait {
  color: #f56c6c;
  font-size: 12px;
}

.task-summary {
  font-size: 12px;
  margin-bottom: 6px;
}

.task-note {
  font-size: 12px;
  margin-bottom: 6px;
  color: #e6a23c;
}

.task-row,
.node-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px dashed #f0f2f5;
  font-size: 12px;
}

.task-id {
  font-weight: 600;
  width: 48px;
  flex-shrink: 0;
}

.task-path {
  flex: 1;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-id {
  font-weight: 600;
  width: 64px;
}

.node-usage {
  margin-left: auto;
  font-family: Consolas, monospace;
  color: #606266;
}

.node-yielded {
  margin-left: auto;
  padding: 0 6px;
  border-radius: 8px;
  background: #fdf6ec;
  color: #e6a23c;
  font-size: 11px;
}

.node-hint {
  font-size: 11px;
  line-height: 1.4;
  margin-bottom: 6px;
}

.node-usage.full {
  color: #f56c6c;
  font-weight: 600;
}

.node-row {
  cursor: pointer;
}

.node-row.selected {
  background: #ecf5ff;
}

.log-filter {
  width: 100%;
  margin-bottom: 8px;
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
  flex-shrink: 0;
}

.log-detail {
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .sim-page {
    height: auto;
  }

  .body {
    flex: none;
    grid-template-columns: 1fr;
    grid-template-rows: 420px auto;
  }

  .side {
    overflow: visible;
  }

  .tabs-panel {
    min-height: 320px;
  }
}
</style>
