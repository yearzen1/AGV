<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { listRoadNets, type RoadNetSummary } from '@/api/roadnet'
import {
  compareExperiment,
  experimentHistory,
  listTaskSets,
  replayExperiment,
  type ExperimentReport,
  type TaskSetSummary
} from '@/api/sim'
import { formatDateTime, formatMetric, taskSetLabel } from '@/utils/format'
import AppIcon from '@/components/AppIcon.vue'

const maps = ref<RoadNetSummary[]>([])
const taskSets = ref<TaskSetSummary[]>([])
const selectedRoadnetId = ref<number | null>(null)
const selectedTaskSetId = ref<number | null>(null)
const mapTaskSets = computed(() =>
  taskSets.value.filter((set) => set.roadnetId === selectedRoadnetId.value)
)
const agvCount = ref(3)
const seed = ref(20261107)

const results = ref<ExperimentReport[]>([])
const history = ref<ExperimentReport[]>([])
const historyTotal = ref(0)
const historyPage = ref(0)
const historySize = 10
const historyLoading = ref(false)
const replayingId = ref<number | null>(null)
const running = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let chartObserver: ResizeObserver | null = null

/** 对比结果出来后，用最快的完成时间和最少冲突给用户一个结论入口。 */
const fastest = computed(() => {
  if (results.value.length === 0) return null
  return results.value.reduce((best, item) =>
    (item.makespan ?? Number.POSITIVE_INFINITY) < (best.makespan ?? Number.POSITIVE_INFINITY) ? item : best
  )
})
const calmest = computed(() => {
  if (results.value.length === 0) return null
  return results.value.reduce((best, item) =>
    (item.conflictCount ?? Number.POSITIVE_INFINITY) < (best.conflictCount ?? Number.POSITIVE_INFINITY) ? item : best
  )
})

async function loadOptions() {
  try {
    maps.value = await listRoadNets()
    taskSets.value = await listTaskSets()
    if (maps.value.length > 0) selectedRoadnetId.value = maps.value[0].id
    selectDefaultTaskSet()
    await loadHistory(0)
  } catch (error: any) {
    ElMessage.error(error.message || '加载参数失败')
  }
}

async function loadHistory(page = historyPage.value) {
  historyLoading.value = true
  try {
    const data = await experimentHistory(page, historySize)
    history.value = data.records
    historyTotal.value = data.total
    historyPage.value = data.page
  } finally {
    historyLoading.value = false
  }
}

function onHistoryPageChange(page: number) {
  loadHistory(page - 1).catch((error: any) => {
    ElMessage.error(error.message || '加载历史实验失败')
  })
}

async function onReplay(row: ExperimentReport) {
  replayingId.value = row.id
  try {
    const result = await replayExperiment(row.id)
    if (result.consistent) {
      ElMessage.success(`回放完成，四项指标与实验 #${row.id} 一致`)
    } else {
      ElMessage.warning(`回放完成，但指标与实验 #${row.id} 不一致，请检查输入快照`)
    }
    await loadHistory(0)
  } catch (error: any) {
    ElMessage.error(error.message || '回放失败')
  } finally {
    replayingId.value = null
  }
}

function selectDefaultTaskSet() {
  const candidates = mapTaskSets.value
  if (!candidates.some((set) => set.id === selectedTaskSetId.value)) {
    selectedTaskSetId.value = candidates[0]?.id ?? null
  }
}

function onMapChange() {
  selectDefaultTaskSet()
}

async function runCompare() {
  if (!selectedRoadnetId.value || !selectedTaskSetId.value) {
    ElMessage.warning('请选择路网与任务集')
    return
  }
  running.value = true
  try {
    results.value = await compareExperiment({
      roadnetId: selectedRoadnetId.value,
      taskSetId: selectedTaskSetId.value,
      agvCount: agvCount.value,
      seed: seed.value
    })
    await loadHistory(0)
    await nextTick()
    renderChart()
    ElMessage.success('对比实验完成')
  } catch (error: any) {
    ElMessage.error(error.message || '实验失败')
  } finally {
    running.value = false
  }
}

function renderChart() {
  if (!chartRef.value || results.value.length === 0) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    chartObserver = new ResizeObserver(() => chart?.resize())
    chartObserver.observe(chartRef.value)
  }
  const algorithms = results.value.map((r) => r.algorithmName)
  const series = [
    { name: '完成时间(tick)', key: 'makespan' as const },
    { name: '总路径长(m)', key: 'totalPathLen' as const },
    { name: '总等待(tick)', key: 'totalWaitTick' as const },
    { name: '冲突次数', key: 'conflictCount' as const }
  ]
  chart.setOption(
    {
      aria: {
        enabled: true,
        label: { description: '两种调度策略在完成时间、总路径长、总等待和冲突次数上的柱状对比' }
      },
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis' },
      legend: { top: 0, textStyle: { color: '#606266' } },
      grid: { left: 50, right: 20, top: 50, bottom: 36 },
      xAxis: { type: 'category', data: algorithms, axisLine: { lineStyle: { color: '#c0c4cc' } } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: '#ebeef5' } } },
      series: series.map((item, index) => ({
        name: item.name,
        type: 'bar',
        barMaxWidth: 42,
        data: results.value.map((r) => Number(Number(r[item.key] ?? 0).toFixed(1))),
        itemStyle: { color: ['#4f46e5', '#6366f1', '#f59e0b', '#ef4444'][index] },
        label: { show: true, position: 'top', fontSize: 11 }
      }))
    },
    true
  )
}

const statusMeta: Record<string, { label: string; type: 'success' | 'info' | 'warning' | 'danger' }> = {
  RUNNING: { label: '运行中', type: 'warning' },
  FINISHED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  ABORTED: { label: '已中断', type: 'info' }
}

function statusLabel(value: string) {
  return statusMeta[value]?.label ?? value
}

function statusTagType(value: string) {
  return statusMeta[value]?.type ?? 'info'
}

function resizeChart() {
  chart?.resize()
}

onMounted(async () => {
  await loadOptions()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chartObserver?.disconnect()
  chartObserver = null
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="experiment">
    <p class="page-lead">
      同一路网、同一任务集、同一随机种子下依次跑两种调度策略，比较完成时间、路径、等待与冲突。
    </p>

    <section class="panel" aria-label="实验参数">
      <div class="exp-toolbar">
        <div class="field">
          <span class="field-label">路网</span>
          <el-select v-model="selectedRoadnetId" class="roadnet-select" aria-label="路网" @change="onMapChange">
            <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
          </el-select>
        </div>
        <div class="field">
          <span class="field-label">任务集</span>
          <el-select v-model="selectedTaskSetId" class="taskset-select" aria-label="任务集">
            <el-option
              v-for="set in mapTaskSets"
              :key="set.id"
              :label="taskSetLabel(set.name, set.taskCount)"
              :value="set.id"
            />
          </el-select>
        </div>
        <div class="field">
          <span class="field-label">AGV 数量</span>
          <el-input-number v-model="agvCount" :min="1" :max="12" style="width: 120px" aria-label="AGV 数量" />
        </div>
        <div class="field">
          <span class="field-label">随机种子</span>
          <el-input-number
            v-model="seed"
            :min="0"
            :controls="false"
            style="width: 150px"
            aria-label="随机种子"
          />
        </div>
        <el-button
          class="run-button"
          type="primary"
          :loading="running"
          :disabled="!selectedTaskSetId"
          @click="runCompare"
        >
          运行对比（两种算法）
        </el-button>
      </div>
      <p v-if="selectedRoadnetId && mapTaskSets.length === 0" class="muted hint">
        该路网还没有任务集，先到「任务集」页面为它创建任务。
      </p>
      <p v-else class="muted hint">
        指标口径见实验报告；运行结束后可在下方历史实验中回放同一输入。
      </p>
    </section>

    <section v-if="results.length === 0" class="panel empty-result" aria-live="polite">
      <span class="empty-icon"><AppIcon name="chart" :size="26" /></span>
      <h2 class="panel-title">还没有对比结果</h2>
      <p class="muted">选择路网与任务集后点击「运行对比（两种算法）」，这里会显示指标图表和明细。</p>
    </section>

    <section v-else class="panel" aria-labelledby="result-heading">
      <div class="section-head">
        <h2 id="result-heading" class="panel-title">指标对比</h2>
        <div class="verdicts">
          <span v-if="fastest" class="verdict">最快：{{ fastest.algorithmName }}</span>
          <span v-if="calmest" class="verdict">冲突最少：{{ calmest.algorithmName }}</span>
        </div>
      </div>
      <div
        ref="chartRef"
        class="chart"
        role="img"
        aria-label="两种调度策略在完成时间、总路径长、总等待和冲突次数上的柱状对比"
      ></div>
      <el-table :data="results" style="margin-top: 12px">
        <el-table-column prop="algorithmName" label="调度算法" width="140" />
        <el-table-column prop="agvCount" label="AGV" width="80" />
        <el-table-column prop="seed" label="种子" width="120" />
        <el-table-column prop="makespan" label="完成时间(tick)" width="140"
          :formatter="(row: ExperimentReport) => formatMetric(row.makespan)" />
        <el-table-column prop="totalPathLen" label="总路径长(m)" width="140"
          :formatter="(row: ExperimentReport) => formatMetric(row.totalPathLen, 1)" />
        <el-table-column prop="totalWaitTick" label="总等待(tick)" width="140"
          :formatter="(row: ExperimentReport) => formatMetric(row.totalWaitTick)" />
        <el-table-column prop="conflictCount" label="冲突次数" width="110"
          :formatter="(row: ExperimentReport) => formatMetric(row.conflictCount)" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel" aria-labelledby="history-heading">
      <div class="section-head">
        <h2 id="history-heading" class="panel-title">我的历史实验</h2>
        <span class="muted">共 {{ historyTotal }} 条</span>
      </div>
      <el-table v-loading="historyLoading" :data="history" size="small" empty-text="还没有实验记录">
        <el-table-column prop="id" label="ID" width="140" />
        <el-table-column prop="algorithmName" label="算法" width="120" />
        <el-table-column prop="roadnetId" label="路网" width="80" />
        <el-table-column prop="taskSetId" label="任务集" width="90" />
        <el-table-column prop="agvCount" label="AGV" width="80" />
        <el-table-column prop="seed" label="种子" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="makespan" label="完成时间" width="110"
          :formatter="(row: ExperimentReport) => formatMetric(row.makespan)" />
        <el-table-column prop="totalPathLen" label="总路径长" width="110"
          :formatter="(row: ExperimentReport) => formatMetric(row.totalPathLen, 1)" />
        <el-table-column prop="totalWaitTick" label="总等待" width="100"
          :formatter="(row: ExperimentReport) => formatMetric(row.totalWaitTick)" />
        <el-table-column prop="conflictCount" label="冲突" width="80"
          :formatter="(row: ExperimentReport) => formatMetric(row.conflictCount)" />
        <el-table-column prop="createTime" label="时间" min-width="170"
          :formatter="(row: ExperimentReport) => formatDateTime(row.createTime)" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="replayingId === row.id"
              @click="onReplay(row)"
            >回放</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          layout="total, prev, pager, next"
          :total="historyTotal"
          :page-size="historySize"
          :current-page="historyPage + 1"
          @current-change="onHistoryPageChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.experiment {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}

.exp-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px 16px;
}

.field {
  display: grid;
  gap: 4px;
}

.field-label {
  color: var(--agv-text-muted);
  font-size: 12px;
}

.roadnet-select {
  width: 210px;
}

.taskset-select {
  width: 200px;
}

.run-button {
  margin-inline-start: auto;
}

.hint {
  margin: 12px 0 0;
}

.empty-result {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 40px 20px;
  text-align: center;
}

.empty-result .panel-title {
  margin: 0;
}

.empty-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 4px;
  color: var(--agv-brand);
  background: #eef2ff;
  border-radius: 14px;
}

.verdicts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.verdict {
  padding: 3px 10px;
  color: var(--agv-brand);
  background: #eef2ff;
  border-radius: 999px;
  font-size: 12px;
}

.chart {
  width: 100%;
  height: 360px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 720px) {
  .roadnet-select,
  .taskset-select {
    width: 100%;
  }

  .field {
    width: 100%;
  }

  .run-button {
    width: 100%;
    margin-inline-start: 0;
  }

  .chart {
    height: 300px;
  }
}
</style>
