<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoadNet, listRoadNets, type RoadNetSummary } from '@/api/roadnet'
import {
  compareExperiment,
  experimentHistory,
  listExperimentEvents,
  listTaskSets,
  replayExperiment,
  type ExperimentEvent,
  type ExperimentReport,
  type TaskSetSummary
} from '@/api/sim'
import { formatDateTime, formatMetric, taskSetLabel } from '@/utils/format'
import {
  buildComparisonRows,
  buildComparisonSummary,
  comparisonSummaryType as comparisonSummaryLevel,
  COMPARISON_METRICS,
  formatTickSeconds
} from '@/utils/experimentCompare'
import MetricMiniChart from '@/components/MetricMiniChart.vue'

interface ComparisonMeta {
  roadnetId: number | null
  taskSetId: number | null
  roadnetName: string
  taskSetName: string
  agvCount: number | null
  seed: number | null
  createdAt: string
  source: 'live' | 'history' | 'restored'
}

const HISTORY_PAGE_SIZE = 10
const STORAGE_KEY = 'agv_experiment_last_compare_v1'
const ALGORITHM_COLORS = ['#1f3b73', '#409eff']

const statusMeta: Record<
  string,
  { label: string; type: 'success' | 'info' | 'warning' | 'danger' }
> = {
  RUNNING: { label: '运行中', type: 'warning' },
  FINISHED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  DEADLOCK: { label: '死锁', type: 'danger' },
  ABORTED: { label: '已中断', type: 'info' }
}

function statusLabel(value: string) {
  return statusMeta[value]?.label ?? value
}

function statusTagType(value: string) {
  return statusMeta[value]?.type ?? 'info'
}

const maps = ref<RoadNetSummary[]>([])
const taskSets = ref<TaskSetSummary[]>([])
const selectedRoadnetId = ref<number | null>(null)
const selectedTaskSetId = ref<number | null>(null)
const mapTaskSets = computed(() =>
  taskSets.value.filter((set) => set.roadnetId === selectedRoadnetId.value)
)
const mapNameById = computed(() => new Map(maps.value.map((map) => [map.id, map.name])))
const taskSetById = computed(() => new Map(taskSets.value.map((set) => [set.id, set])))
const selectedRoadnetName = computed(() =>
  selectedRoadnetId.value == null ? '' : roadnetName(selectedRoadnetId.value)
)
const selectedTaskSetName = computed(() =>
  selectedTaskSetId.value == null ? '' : taskSetName(selectedTaskSetId.value)
)

const agvCount = ref(3)
const maxAgv = ref(12)
const seed = ref(20261107)

const results = ref<ExperimentReport[]>([])
const comparisonMeta = ref<ComparisonMeta | null>(null)
const restoredFromStorage = ref(false)
const running = ref(false)

const historyAll = ref<ExperimentReport[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyStatus = ref('')
const historyAlgorithm = ref('')
const historyRoadnetId = ref<number | null>(null)
const historyTaskSetId = ref<number | null>(null)
const historyRange = ref<'all' | '7' | '30'>('all')
const historyKeyword = ref('')
const comparePicks = ref<ExperimentReport[]>([])

const replayingId = ref<number | null>(null)
const eventDialogVisible = ref(false)
const eventLoading = ref(false)
const eventRows = ref<ExperimentEvent[]>([])
const eventTruncated = ref(false)
const eventExperimentId = ref<number | null>(null)
const detailVisible = ref(false)
const detailRow = ref<ExperimentReport | null>(null)
const metricsPanelRef = ref<HTMLElement | null>(null)
const metricChartRefs: Array<{ getDataUrl: () => string }> = []

function setMetricChartRef(element: any, index: number) {
  if (element) metricChartRefs[index] = element
}

const eventTypeLabel: Record<string, string> = {
  TASK_ASSIGNED: '分配任务',
  LOADING: '装货',
  UNLOADING: '卸货',
  UNLOADED: '卸货完成',
  TASK_DONE: '任务完成',
  WAIT_CONFLICT: '冲突等待',
  WAIT_RESOLVED: '等待结束',
  REPLAN: '重规划',
  DEADLOCK_BREAK: '打破僵局',
  DEADLOCK: '死锁',
  CO_LOCATION: '位置重合',
  CO_LOCATION_END: '重合结束',
  CHARGING: '充电',
  FINISHED: '结束'
}

function roadnetName(id: number | null | undefined) {
  if (id == null) return '-'
  return mapNameById.value.get(id) ?? `路网 #${id}（已删除）`
}

function taskSetName(id: number | null | undefined) {
  if (id == null) return '-'
  const set = taskSetById.value.get(id)
  return set ? taskSetLabel(set.name, set.taskCount) : `任务集 #${id}（已删除）`
}

function shortId(id: number | null | undefined) {
  if (id == null) return '-'
  const text = String(id)
  return text.length > 6 ? `…${text.slice(-6)}` : text
}

function nowText() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const historyStatusOptions = computed(() =>
  Object.entries(statusMeta).map(([value, meta]) => ({ value, label: meta.label }))
)

const historyAlgorithmOptions = computed(() => {
  const map = new Map<string, string>()
  for (const row of historyAll.value) map.set(row.algorithm, row.algorithmName)
  return [...map.entries()].map(([value, label]) => ({ value, label }))
})

function withinHistoryRange(row: ExperimentReport) {
  if (historyRange.value === 'all') return true
  const time = new Date(row.createTime.replace(' ', 'T')).getTime()
  if (!Number.isFinite(time)) return true
  return Date.now() - time <= Number(historyRange.value) * 24 * 60 * 60 * 1000
}

const historyFiltered = computed(() =>
  historyAll.value.filter((row) => {
    if (historyStatus.value && row.status !== historyStatus.value) return false
    if (historyAlgorithm.value && row.algorithm !== historyAlgorithm.value) return false
    if (historyRoadnetId.value != null && row.roadnetId !== historyRoadnetId.value) return false
    if (historyTaskSetId.value != null && row.taskSetId !== historyTaskSetId.value) return false
    if (!withinHistoryRange(row)) return false
    const keyword = historyKeyword.value.trim().toLowerCase()
    if (!keyword) return true
    const haystack =
      `${row.id} ${row.algorithmName} ${roadnetName(row.roadnetId)} ${taskSetName(row.taskSetId)}`
        .toLowerCase()
    return haystack.includes(keyword)
  })
)

const maxHistoryPage = computed(() =>
  Math.max(1, Math.ceil(historyFiltered.value.length / HISTORY_PAGE_SIZE))
)

const historyPaged = computed(() => {
  const start = (historyPage.value - 1) * HISTORY_PAGE_SIZE
  return historyFiltered.value.slice(start, start + HISTORY_PAGE_SIZE)
})

const historyCountLabel = computed(() => {
  const loaded = historyAll.value.length
  const filtered = historyFiltered.value.length
  if (filtered !== loaded) return `筛选 ${filtered} / 已加载 ${loaded} 条`
  return loaded < historyTotal.value
    ? `已加载 ${loaded} / 共 ${historyTotal.value} 条`
    : `共 ${historyTotal.value} 条`
})

watch(
  [historyStatus, historyAlgorithm, historyRoadnetId, historyTaskSetId, historyRange, historyKeyword],
  () => {
    historyPage.value = 1
  }
)

watch(historyFiltered, () => {
  if (historyPage.value > maxHistoryPage.value) historyPage.value = maxHistoryPage.value
})

function resetHistoryFilters() {
  historyStatus.value = ''
  historyAlgorithm.value = ''
  historyRoadnetId.value = null
  historyTaskSetId.value = null
  historyRange.value = 'all'
  historyKeyword.value = ''
  historyPage.value = 1
}

function selectDefaultTaskSet() {
  const candidates = mapTaskSets.value
  if (!candidates.some((set) => set.id === selectedTaskSetId.value)) {
    selectedTaskSetId.value = candidates[0]?.id ?? null
  }
}

async function refreshMaxAgv() {
  if (selectedRoadnetId.value == null) {
    maxAgv.value = 12
    return
  }
  try {
    const detail = await getRoadNet(selectedRoadnetId.value)
    const total = (detail.nodes ?? [])
      .filter((node) => node.type === 'CHARGER')
      .reduce((sum, node) => sum + Math.max(1, node.capacity ?? 1), 0)
    maxAgv.value = total > 0 ? total : 12
  } catch {
    maxAgv.value = 12
  }
  if (agvCount.value > maxAgv.value) agvCount.value = maxAgv.value
}

async function loadAllHistory() {
  const size = 100
  const first = await experimentHistory(0, size)
  const records = [...first.records]
  const pages = Math.min(5, Math.ceil(first.total / size))
  for (let page = 1; page < pages; page++) {
    const next = await experimentHistory(page, size)
    records.push(...next.records)
  }
  historyAll.value = records
  historyTotal.value = first.total
  if (historyPage.value > maxHistoryPage.value) historyPage.value = maxHistoryPage.value
}

async function loadOptions() {
  try {
    maps.value = await listRoadNets()
    taskSets.value = await listTaskSets()
    if (maps.value.length > 0) selectedRoadnetId.value = maps.value[0].id
    selectDefaultTaskSet()
    await refreshMaxAgv()
    await loadAllHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '加载参数失败')
  }
}

async function onMapChange() {
  selectDefaultTaskSet()
  await refreshMaxAgv()
}

function metaFromForm(): ComparisonMeta {
  return {
    roadnetId: selectedRoadnetId.value,
    taskSetId: selectedTaskSetId.value,
    roadnetName: selectedRoadnetName.value,
    taskSetName: selectedTaskSetName.value,
    agvCount: agvCount.value,
    seed: seed.value,
    createdAt: nowText(),
    source: 'live'
  }
}

function metaFromRows(rows: ExperimentReport[]): ComparisonMeta {
  const first = rows[0]
  return {
    roadnetId: first.roadnetId,
    taskSetId: first.taskSetId,
    roadnetName: roadnetName(first.roadnetId),
    taskSetName: taskSetName(first.taskSetId),
    agvCount: first.agvCount,
    seed: first.seed,
    createdAt: formatDateTime(first.createTime),
    source: 'history'
  }
}

function persistComparison() {
  if (results.value.length === 0 || !comparisonMeta.value) return
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ rows: results.value, meta: comparisonMeta.value })
    )
  } catch {
    // 隐私模式或存储满时忽略，页面仍可正常使用
  }
}

function restoreComparison() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const stored = JSON.parse(raw) as { rows?: ExperimentReport[]; meta?: ComparisonMeta }
    if (!Array.isArray(stored.rows) || stored.rows.length < 2) {
      localStorage.removeItem(STORAGE_KEY)
      return
    }
    results.value = stored.rows.slice(0, 2)
    comparisonMeta.value = stored.meta ?? metaFromRows(stored.rows)
    comparisonMeta.value.source = 'restored'
    restoredFromStorage.value = true
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }
}

function clearComparison() {
  results.value = []
  comparisonMeta.value = null
  restoredFromStorage.value = false
  localStorage.removeItem(STORAGE_KEY)
}

function scrollToMetrics() {
  nextTick(() => {
    metricsPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function showComparison(rows: ExperimentReport[], source: 'live' | 'history') {
  if (rows.length < 2) {
    ElMessage.warning('请选择两条实验再开始对比')
    return
  }
  results.value = rows.slice(0, 2)
  comparisonMeta.value = source === 'history' ? metaFromRows(rows) : metaFromForm()
  restoredFromStorage.value = false
  comparePicks.value = []
  persistComparison()
  scrollToMetrics()
}

async function runCompare() {
  if (!selectedRoadnetId.value || !selectedTaskSetId.value) {
    ElMessage.warning('请选择路网与任务集')
    return
  }
  running.value = true
  try {
    const rows = await compareExperiment({
      roadnetId: selectedRoadnetId.value,
      taskSetId: selectedTaskSetId.value,
      agvCount: agvCount.value,
      seed: seed.value
    })
    showComparison(rows, 'live')
    await loadAllHistory()
    ElMessage.success('对比实验完成')
  } catch (error: any) {
    ElMessage.error(error.message || '实验失败')
  } finally {
    running.value = false
  }
}

const comparisonSeries = computed(() => {
  const names = results.value.map((row) => row.algorithmName)
  const hasDuplicate = names.some((name, index) => names.indexOf(name) !== index)
  return results.value.map((row, index) => ({
    row,
    label: hasDuplicate ? `${row.algorithmName} #${shortId(row.id)}` : row.algorithmName,
    color: ALGORITHM_COLORS[index] ?? '#909399'
  }))
})

const metricCharts = computed(() =>
  COMPARISON_METRICS.map((metric) => ({
    ...metric,
    items: comparisonSeries.value.map((item) => ({
      label: item.label,
      value: item.row[metric.key],
      color: item.color
    }))
  }))
)

const comparisonRows = computed(() => buildComparisonRows(results.value))
const comparisonSummary = computed(() =>
  buildComparisonSummary(results.value, comparisonRows.value, statusLabel)
)
const comparisonSummaryType = computed(() =>
  comparisonSummaryLevel(comparisonRows.value)
)

const comparisonMetaLabel = computed(() => {
  const meta = comparisonMeta.value
  if (!meta) return ''
  return [
    meta.roadnetName || `路网 #${meta.roadnetId}`,
    meta.taskSetName || `任务集 #${meta.taskSetId}`,
    meta.agvCount != null ? `${meta.agvCount} 台 AGV` : '',
    meta.seed != null ? `seed ${meta.seed}` : '',
    meta.createdAt
  ]
    .filter(Boolean)
    .join(' · ')
})

const comparisonSourceLabel = computed(() => {
  if (restoredFromStorage.value) return '上次查看'
  if (comparisonMeta.value?.source === 'history') return '历史实验'
  return '本次运行'
})

const staleResults = computed(() => {
  const meta = comparisonMeta.value
  if (!meta || results.value.length === 0) return false
  return (
    meta.roadnetId !== selectedRoadnetId.value ||
    meta.taskSetId !== selectedTaskSetId.value ||
    meta.agvCount !== agvCount.value ||
    meta.seed !== seed.value
  )
})

const mixedParams = computed(() => {
  if (results.value.length < 2) return false
  const [first, second] = results.value
  return (
    first.roadnetId !== second.roadnetId ||
    first.taskSetId !== second.taskSetId ||
    first.agvCount !== second.agvCount ||
    first.seed !== second.seed
  )
})

function resultLabel(index: number) {
  return comparisonSeries.value[index]?.label ?? '-'
}

function isPicked(row: ExperimentReport) {
  return comparePicks.value.some((item) => item.id === row.id)
}

function formatPick(row: ExperimentReport) {
  return `${row.algorithmName} #${shortId(row.id)}`
}

function toggleComparePick(row: ExperimentReport) {
  const index = comparePicks.value.findIndex((item) => item.id === row.id)
  if (index >= 0) {
    comparePicks.value.splice(index, 1)
    return
  }
  if (comparePicks.value.length >= 2) {
    comparePicks.value = [comparePicks.value[1], row]
    ElMessage.info('已替换较早的选择')
  } else {
    comparePicks.value.push(row)
    if (comparePicks.value.length === 1) ElMessage.info('已选择第一条，请再选择一条')
  }
}

function clearComparePicks() {
  comparePicks.value = []
}

function addDetailToCompare() {
  if (!detailRow.value) return
  toggleComparePick(detailRow.value)
  detailVisible.value = false
}

function comparePickedHistory() {
  if (comparePicks.value.length !== 2) return
  showComparison([...comparePicks.value], 'history')
  comparePicks.value = []
}

function canReplay(row: ExperimentReport) {
  return (
    row.status === 'FINISHED' &&
    row.makespan != null &&
    row.totalPathLen != null &&
    row.totalWaitTick != null &&
    row.conflictCount != null
  )
}

function openDetail(row: ExperimentReport) {
  detailRow.value = row
  detailVisible.value = true
}

async function onReplay(row: ExperimentReport) {
  if (!canReplay(row)) return
  replayingId.value = row.id
  try {
    const result = await replayExperiment(row.id)
    if (result.consistent) {
      ElMessage.success(`回放完成，四项指标与实验 #${row.id} 一致`)
    } else if (!result.versionMatched) {
      ElMessage.warning(result.note || `回放完成，但实验 #${row.id} 的规则版本不同，指标不可直接比较`)
    } else {
      ElMessage.warning(`回放完成，但指标与实验 #${row.id} 不一致，请检查输入快照`)
    }
    detailVisible.value = false
    await loadAllHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '回放失败')
  } finally {
    replayingId.value = null
  }
}

async function onOpenEvents(row: ExperimentReport) {
  detailVisible.value = false
  eventExperimentId.value = row.id
  eventDialogVisible.value = true
  eventLoading.value = true
  eventRows.value = []
  eventTruncated.value = false
  try {
    const result = await listExperimentEvents(row.id)
    eventRows.value = result.events
    eventTruncated.value = result.truncated
  } catch (error: any) {
    ElMessage.error(error.message || '加载实验事件失败')
  } finally {
    eventLoading.value = false
  }
}

function downloadCsv(filename: string, header: string[], rows: string[][]) {
  const escape = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const text = [header, ...rows].map((row) => row.map(escape).join(',')).join('\n')
  const blob = new Blob(['\uFEFF' + text], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function exportComparisonCsv() {
  if (results.value.length < 2) return
  downloadCsv(
    `experiment-compare-${Date.now()}.csv`,
    ['指标', resultLabel(0), resultLabel(1), '差异（B - A）', '更优'],
    comparisonRows.value.map((row) => [row.metric, row.first, row.second, row.delta, row.winnerLabel])
  )
}

async function exportChartsPng() {
  const urls = metricChartRefs
    .slice(0, metricCharts.value.length)
    .map((item) => item?.getDataUrl())
    .filter((url): url is string => Boolean(url))
  if (urls.length === 0) {
    ElMessage.warning('图表还没准备好，请稍后再试')
    return
  }
  try {
    const images = await Promise.all(
      urls.map(
        (url) =>
          new Promise<HTMLImageElement>((resolve, reject) => {
            const image = new Image()
            image.onload = () => resolve(image)
            image.onerror = reject
            image.src = url
          })
      )
    )
    const width = Math.max(...images.map((image) => image.naturalWidth))
    const height = Math.max(...images.map((image) => image.naturalHeight))
    const gap = 24
    const titleHeight = 30
    const canvas = document.createElement('canvas')
    canvas.width = width * 2 + gap * 3
    canvas.height = (height + titleHeight) * 2 + gap * 3
    const context = canvas.getContext('2d')
    if (!context) return
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.fillStyle = '#303133'
    context.font = '600 20px "Microsoft YaHei", sans-serif'
    context.textBaseline = 'middle'
    images.forEach((image, index) => {
      const column = index % 2
      const row = Math.floor(index / 2)
      const x = gap + column * (width + gap)
      const y = gap + row * (height + titleHeight + gap)
      const metric = metricCharts.value[index]
      context.fillText(
        `${metric?.title ?? ''}${metric?.unit ? `（${metric.unit}）` : ''}`,
        x,
        y + titleHeight / 2
      )
      context.drawImage(image, x, y + titleHeight, image.naturalWidth, image.naturalHeight)
    })
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
    if (!blob) return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `experiment-compare-${Date.now()}.png`
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出图表失败')
  }
}

function exportEvents() {
  if (eventRows.value.length === 0) return
  const header = ['seq', 'tick', 'agvId', 'type', 'place', 'detail']
  const escape = (value: unknown) => `"${String(value ?? '').replace(/"/g, '""')}"`
  const lines = [
    header.join(','),
    ...eventRows.value.map((row) =>
      [row.seq, row.tick, row.agvId, row.type, row.place, row.detail].map(escape).join(',')
    )
  ]
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `experiment-${eventExperimentId.value ?? 'events'}-events.csv`
  link.click()
  URL.revokeObjectURL(url)
}

function onHistoryPageChange(page: number) {
  historyPage.value = page
}

onMounted(async () => {
  await loadOptions()
  restoreComparison()
})
</script>

<template>
  <div class="experiment-page">
    <div class="page-head">
      <h2 class="page-title">对比实验</h2>
      <span class="page-subtitle">同一路网、同一任务集、同一组参数下比较两种调度策略</span>
    </div>

    <div class="panel form-panel">
      <div class="form-row">
        <div class="field">
          <span class="field-label">路网</span>
          <el-select v-model="selectedRoadnetId" style="width: 210px" @change="onMapChange">
            <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
          </el-select>
        </div>
        <div class="field">
          <span class="field-label">任务集</span>
          <el-select v-model="selectedTaskSetId" style="width: 210px">
            <el-option
              v-for="set in mapTaskSets"
              :key="set.id"
              :label="taskSetLabel(set.name, set.taskCount)"
              :value="set.id"
            />
          </el-select>
        </div>
        <div class="field">
          <span class="field-label">
            AGV 数量
            <span class="field-hint">最多 {{ maxAgv }} 台</span>
          </span>
          <el-input-number v-model="agvCount" :min="1" :max="maxAgv" style="width: 130px" />
        </div>
        <div class="field">
          <el-tooltip
            placement="top"
            content="当前两种调度算法均为确定性实现，种子只用于记录与复现；相同参数结果一致"
          >
            <span class="field-label">随机种子 <span class="info-dot">?</span></span>
          </el-tooltip>
          <el-input-number v-model="seed" :min="0" :controls="false" style="width: 150px" />
        </div>
        <el-button
          type="primary"
          :loading="running"
          :disabled="!selectedTaskSetId"
          @click="runCompare"
        >
          运行对比（两种算法）
        </el-button>
      </div>
      <div class="form-hint">
        <span>同一路网、同一任务集、同一随机种子下依次跑就近分配与贪心 ETA。</span>
        <el-popover placement="bottom-start" :width="430" trigger="click">
          <template #reference>
            <el-button link type="primary" size="small">指标说明</el-button>
          </template>
          <div class="metric-help">
            <p><b>完成时间（tick）</b>：最后一件任务完成的 tick；1 tick = 100ms。</p>
            <p><b>总路径长（m）</b>：全部 AGV 实走弧长之和，含空驶。</p>
            <p><b>总等待（tick）</b>：处于等待状态的（车 × tick）之和。</p>
            <p><b>冲突次数</b>：预约失败导致停车的事件次数，一次连续等待记 1 次。</p>
            <p class="muted">死锁/失败/中断的实验没有完整指标，显示为「-」，不参与图表。</p>
          </div>
        </el-popover>
      </div>
      <div v-if="selectedRoadnetId && mapTaskSets.length === 0" class="form-hint warning">
        该路网还没有任务集，请先去「任务集」页面创建或绑定。
      </div>
    </div>

    <div ref="metricsPanelRef" class="panel metrics-panel">
      <div class="section-head">
        <div>
          <h3>指标对比</h3>
          <div v-if="results.length > 0" class="comparison-meta">{{ comparisonMetaLabel }}</div>
        </div>
        <div class="section-actions">
          <el-tag v-if="results.length > 0" size="small" type="info">
            {{ comparisonSourceLabel }}
          </el-tag>
          <el-button
            v-if="results.length > 0"
            link
            type="primary"
            size="small"
            @click="exportChartsPng"
          >
            导出图表
          </el-button>
          <el-button
            v-if="results.length > 0"
            link
            type="primary"
            size="small"
            @click="exportComparisonCsv"
          >
            导出 CSV
          </el-button>
          <el-button
            v-if="results.length > 0"
            link
            type="primary"
            size="small"
            @click="clearComparison"
          >
            清除
          </el-button>
        </div>
      </div>

      <template v-if="results.length > 0">
        <el-alert
          v-if="staleResults"
          type="warning"
          :closable="false"
          show-icon
          title="参数已变更，以下为上一次查看的结果"
          class="board-alert"
        />
        <el-alert
          v-if="mixedParams"
          type="warning"
          :closable="false"
          show-icon
          title="两条实验的参数不同，差异不能直接归因于调度算法"
          class="board-alert"
        />

        <div class="summary" :class="`is-${comparisonSummaryType}`">{{ comparisonSummary }}</div>

        <div class="chart-legend">
          <span v-for="item in comparisonSeries" :key="item.row.id" class="legend-item">
            <i :style="{ background: item.color }" />{{ item.label }}
            <el-button link type="primary" size="small" @click="openDetail(item.row)">详情</el-button>
            <el-button link type="primary" size="small" @click="onOpenEvents(item.row)">事件</el-button>
          </span>
        </div>
        <div class="metric-grid">
          <MetricMiniChart
            v-for="(chart, index) in metricCharts"
            :key="chart.key"
            :ref="(element) => setMetricChartRef(element, index)"
            :title="chart.title"
            :unit="chart.unit"
            :precision="chart.precision"
            :items="chart.items"
          />
        </div>

        <el-table :data="comparisonRows" size="small" class="comparison-table">
          <el-table-column prop="metric" label="指标" width="170" />
          <el-table-column prop="first" :label="resultLabel(0)" width="160" align="right" />
          <el-table-column prop="second" :label="resultLabel(1)" width="160" align="right" />
          <el-table-column prop="delta" label="差异（B − A）" width="180" align="right" />
          <el-table-column label="更优" width="120">
            <template #default="{ row }">
              <el-tag :type="row.winner >= 0 ? 'success' : 'info'" size="small">
                {{ row.winnerLabel }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="metric-note">差异 = 第二列 − 第一列；负数表示第二列更优。</div>
        <div class="metric-note">
          死锁 / 失败 / 中断的实验没有完整指标，显示为「-」，不参与图表。
        </div>
      </template>

      <div v-else class="empty-state">
        <div class="empty-title">还没有对比结果</div>
        <div class="empty-hint">
          选择路网、任务集和 AGV 数量后点「运行对比」，或在下方历史实验里选两条记录进行对比。
        </div>
      </div>
    </div>

    <div class="panel history-panel">
      <div class="section-head">
        <div>
          <h3>我的历史实验</h3>
          <div class="history-count">{{ historyCountLabel }}</div>
          <div class="history-note">
            历史里同时包含单算法运行、对比实验和回放记录；可用筛选缩小范围，或点两条记录的「对比」重新查看。
          </div>
        </div>
        <el-button size="small" @click="loadAllHistory">刷新</el-button>
      </div>

      <div class="history-filters">
        <el-select v-model="historyStatus" placeholder="状态" clearable size="small" class="filter-select">
          <el-option
            v-for="item in historyStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="historyAlgorithm"
          placeholder="算法"
          clearable
          size="small"
          class="filter-select"
        >
          <el-option
            v-for="item in historyAlgorithmOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="historyRoadnetId"
          placeholder="路网"
          clearable
          size="small"
          class="filter-select wide"
        >
          <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
        </el-select>
        <el-select
          v-model="historyTaskSetId"
          placeholder="任务集"
          clearable
          size="small"
          class="filter-select wide"
        >
          <el-option
            v-for="set in taskSets"
            :key="set.id"
            :label="taskSetLabel(set.name, set.taskCount)"
            :value="set.id"
          />
        </el-select>
        <el-select v-model="historyRange" size="small" class="filter-select">
          <el-option label="全部时间" value="all" />
          <el-option label="最近 7 天" value="7" />
          <el-option label="最近 30 天" value="30" />
        </el-select>
        <el-input
          v-model="historyKeyword"
          placeholder="搜索算法 / 路网 / 任务集 / 实验 ID"
          clearable
          size="small"
          class="filter-input"
        />
        <el-button size="small" @click="resetHistoryFilters">重置</el-button>
      </div>

      <div v-if="comparePicks.length > 0" class="compare-bar">
        <span class="compare-picks">
          已选 {{ comparePicks.length }}/2：{{ comparePicks.map(formatPick).join('、') }}
        </span>
        <el-button
          size="small"
          type="primary"
          :disabled="comparePicks.length !== 2"
          @click="comparePickedHistory"
        >
          开始对比
        </el-button>
        <el-button size="small" @click="clearComparePicks">清空</el-button>
      </div>

      <div class="history-table-wrap">
        <el-table :data="historyPaged" size="small" row-key="id" empty-text="没有匹配的实验">
          <el-table-column label="时间" width="135">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="场景" width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="scenario-cell">
                <div class="scenario-main" :title="roadnetName(row.roadnetId)">
                  {{ roadnetName(row.roadnetId) }}
                </div>
                <div class="scenario-sub" :title="taskSetName(row.taskSetId)">
                  {{ taskSetName(row.taskSetId) }}
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="algorithmName" label="算法" width="95" show-overflow-tooltip />
          <el-table-column prop="agvCount" label="AGV" width="50" align="right" />
          <el-table-column label="状态" width="75">
            <template #default="{ row }">
              <el-tooltip
                :disabled="row.status !== 'DEADLOCK'"
                content="死锁：点「事件」查看等待环或等待车列表"
                placement="top"
              >
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="完成时间 (tick)" width="100" align="right">
            <template #default="{ row }">
              <div class="metric-value">{{ formatMetric(row.makespan) }}</div>
              <div v-if="row.makespan != null" class="metric-sub">
                {{ formatTickSeconds(row.makespan) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="总路径长 (m)" width="95" align="right">
            <template #default="{ row }">{{ formatMetric(row.totalPathLen, 1) }}</template>
          </el-table-column>
          <el-table-column label="总等待 (tick)" width="95" align="right">
            <template #default="{ row }">
              <div class="metric-value">{{ formatMetric(row.totalWaitTick) }}</div>
              <div v-if="row.totalWaitTick != null" class="metric-sub">
                {{ formatTickSeconds(row.totalWaitTick) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="冲突" width="60" align="right">
            <template #default="{ row }">{{ formatMetric(row.conflictCount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="145">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              <el-button
                link
                :type="isPicked(row) ? 'success' : 'primary'"
                size="small"
                @click="toggleComparePick(row)"
              >
                {{ isPicked(row) ? '已选' : '对比' }}
              </el-button>
              <el-button link type="primary" size="small" @click="onOpenEvents(row)">事件</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination-row">
        <span class="muted">第 {{ historyPage }} / {{ maxHistoryPage }} 页</span>
        <el-pagination
          layout="prev, pager, next"
          :total="historyFiltered.length"
          :page-size="HISTORY_PAGE_SIZE"
          :current-page="historyPage"
          @current-change="onHistoryPageChange"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="实验详情" size="480px">
      <template v-if="detailRow">
        <div class="detail-head">
          <div class="detail-title">
            {{ detailRow.algorithmName }}
            <span class="muted">#{{ shortId(detailRow.id) }}</span>
          </div>
          <el-tag :type="statusTagType(detailRow.status)" size="small">
            {{ statusLabel(detailRow.status) }}
          </el-tag>
        </div>

        <el-descriptions :column="1" border size="small" class="detail-desc">
          <el-descriptions-item label="实验 ID">{{ detailRow.id }}</el-descriptions-item>
          <el-descriptions-item label="路网">{{ roadnetName(detailRow.roadnetId) }}</el-descriptions-item>
          <el-descriptions-item label="任务集">{{ taskSetName(detailRow.taskSetId) }}</el-descriptions-item>
          <el-descriptions-item label="AGV 数量">{{ detailRow.agvCount }} 台</el-descriptions-item>
          <el-descriptions-item label="随机种子">{{ detailRow.seed }}</el-descriptions-item>
          <el-descriptions-item label="规则版本">{{ detailRow.engineVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(detailRow.createTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-metrics">
          <div v-for="metric in COMPARISON_METRICS" :key="metric.key" class="detail-metric">
            <div class="detail-metric-label">
              {{ metric.title }} <span>{{ metric.unit }}</span>
            </div>
            <div class="detail-metric-value">
              {{ formatMetric(detailRow[metric.key], metric.precision) }}
            </div>
            <div v-if="metric.key === 'makespan' && detailRow.makespan != null" class="metric-sub">
              {{ formatTickSeconds(detailRow.makespan) }}
            </div>
            <div
              v-if="metric.key === 'totalWaitTick' && detailRow.totalWaitTick != null"
              class="metric-sub"
            >
              {{ formatTickSeconds(detailRow.totalWaitTick) }}
            </div>
          </div>
        </div>

        <div class="detail-actions">
          <el-button size="small" @click="onOpenEvents(detailRow)">事件</el-button>
          <el-tooltip
            :disabled="canReplay(detailRow)"
            content="该实验没有完整指标，无法回放比对"
            placement="top"
          >
            <span>
              <el-button
                size="small"
                :loading="replayingId === detailRow.id"
                :disabled="!canReplay(detailRow)"
                @click="onReplay(detailRow)"
              >
                回放
              </el-button>
            </span>
          </el-tooltip>
          <el-button size="small" type="primary" @click="addDetailToCompare">
            加入对比
          </el-button>
          <el-button size="small" @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="eventDialogVisible"
      :title="eventExperimentId ? `实验事件 #${eventExperimentId}` : '实验事件'"
      width="900px"
    >
      <div v-if="eventTruncated" style="margin-bottom: 8px; color: #e6a23c; font-size: 12px">
        仅保留前 5000 条事件
      </div>
      <el-table v-loading="eventLoading" :data="eventRows" height="460" size="small">
        <el-table-column prop="seq" label="#" width="60" />
        <el-table-column prop="tick" label="tick" width="80" />
        <el-table-column prop="type" label="类型" width="110">
          <template #default="{ row }">{{ eventTypeLabel[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column prop="agvId" label="车辆" width="90" />
        <el-table-column prop="place" label="位置" width="90" />
        <el-table-column prop="detail" label="说明" min-width="360">
          <template #default="{ row }">
            <span class="event-detail">{{ row.detail }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="eventDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="eventRows.length === 0" @click="exportEvents">
          导出 CSV
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.experiment-page {
  min-width: 0;
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 16px;
}

.page-subtitle {
  color: #909399;
  font-size: 12px;
}

.form-panel {
  margin-bottom: 16px;
}

.form-row {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  color: #606266;
  font-size: 12px;
  line-height: 1.4;
}

.field-hint {
  color: #909399;
  font-size: 11px;
  margin-left: 4px;
}

.info-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  margin-left: 2px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  font-size: 10px;
  line-height: 1;
  cursor: help;
}

.form-hint {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.form-hint.warning {
  color: #e6a23c;
}

.metrics-panel,
.history-panel {
  margin-top: 16px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comparison-meta {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.board-alert {
  margin-bottom: 8px;
}

.summary {
  margin: 8px 0 12px;
  padding: 10px 12px;
  border-left: 3px solid #909399;
  border-radius: 6px;
  background: #f5f7fa;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}

.summary.is-success {
  border-left-color: #67c23a;
  background: #f0f9eb;
  color: #3f6b2a;
}

.summary.is-info {
  border-left-color: #409eff;
  background: #f4f8ff;
  color: #1f3b73;
}

.summary.is-warning {
  border-left-color: #e6a23c;
  background: #fdf6ec;
  color: #8a5a1f;
}

.chart-legend {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 16px;
  margin: 4px 0 8px;
  color: #606266;
  font-size: 11px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.chart-legend i {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.comparison-table {
  margin-top: 14px;
}

.metric-note {
  margin-top: 8px;
  color: #909399;
  font-size: 11px;
  line-height: 1.5;
}

.empty-state {
  padding: 24px 16px;
  border: 1px dashed #e4e9f0;
  border-radius: 8px;
  background: #fbfdff;
  text-align: center;
}

.empty-title {
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}

.empty-hint {
  max-width: 480px;
  margin: 6px auto 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

.history-count {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
}

.history-note {
  margin-top: 2px;
  color: #c0c4cc;
  font-size: 11px;
  line-height: 1.5;
}

.history-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.filter-select {
  width: 110px;
}

.filter-select.wide {
  width: 150px;
}

.filter-input {
  width: 230px;
}

.compare-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 10px;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  background: #f4f8ff;
}

.compare-picks {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #1f3b73;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-table-wrap {
  overflow-x: auto;
}

.history-table-wrap :deep(.el-table) {
  min-width: 1030px;
}

.history-table-wrap :deep(.el-table .cell) {
  line-height: 1.4;
}

.scenario-cell {
  min-width: 0;
  line-height: 1.35;
}

.scenario-main,
.scenario-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-main {
  color: #303133;
  font-size: 12px;
}

.scenario-sub {
  color: #909399;
  font-size: 11px;
}

.metric-value {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.metric-sub {
  color: #909399;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-title {
  color: #303133;
  font-size: 16px;
  font-weight: 600;
}

.detail-desc {
  margin-bottom: 16px;
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 16px;
}

.detail-metric {
  padding: 10px;
  border: 1px solid #eef2f7;
  border-radius: 6px;
  background: #fafcff;
}

.detail-metric-label {
  color: #909399;
  font-size: 11px;
}

.detail-metric-label span {
  color: #c0c4cc;
}

.detail-metric-value {
  margin-top: 4px;
  color: #303133;
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.metric-help {
  color: #606266;
  font-size: 12px;
  line-height: 1.7;
}

.metric-help p {
  margin: 0 0 6px;
}

.metric-help b {
  color: #303133;
}

.event-detail {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .field {
    flex: 1 1 160px;
  }

  .history-table-wrap :deep(.el-table) {
    min-width: 1000px;
  }
}
</style>
