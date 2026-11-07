<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { listRoadNets, type RoadNetSummary } from '@/api/roadnet'
import {
  compareExperiment,
  experimentHistory,
  listTaskSets,
  type ExperimentReport,
  type TaskSetSummary
} from '@/api/sim'

const maps = ref<RoadNetSummary[]>([])
const taskSets = ref<TaskSetSummary[]>([])
const selectedMapId = ref<number | null>(null)
const selectedTaskSetId = ref<number | null>(null)
const agvCount = ref(3)
const seed = ref(20261107)

const results = ref<ExperimentReport[]>([])
const history = ref<ExperimentReport[]>([])
const running = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

async function loadOptions() {
  try {
    maps.value = await listRoadNets()
    taskSets.value = await listTaskSets()
    if (maps.value.length > 0) selectedMapId.value = maps.value[0].id
    if (taskSets.value.length > 0) selectedTaskSetId.value = taskSets.value[0].id
    history.value = await experimentHistory()
  } catch (error: any) {
    ElMessage.error(error.message || '加载参数失败')
  }
}

async function runCompare() {
  if (!selectedMapId.value || !selectedTaskSetId.value) {
    ElMessage.warning('请选择路网与任务集')
    return
  }
  running.value = true
  try {
    results.value = await compareExperiment({
      mapId: selectedMapId.value,
      taskSetId: selectedTaskSetId.value,
      agvCount: agvCount.value,
      seed: seed.value
    })
    history.value = await experimentHistory()
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
        data: results.value.map((r) => Number(r[item.key] ?? 0)),
        itemStyle: { color: ['#1f3b73', '#409eff', '#e6a23c', '#f56c6c'][index] },
        label: { show: true, position: 'top', fontSize: 11 }
      }))
    },
    true
  )
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
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div>
    <h2 class="page-title">对比实验</h2>

    <div class="panel">
      <el-space wrap>
        <el-select v-model="selectedMapId" style="width: 210px">
          <el-option v-for="map in maps" :key="map.id" :label="map.name" :value="map.id" />
        </el-select>
        <el-select v-model="selectedTaskSetId" style="width: 200px">
          <el-option
            v-for="set in taskSets"
            :key="set.id"
            :label="`${set.name}（${set.taskCount} 单）`"
            :value="set.id"
          />
        </el-select>
        <span class="muted">AGV 数量</span>
        <el-input-number v-model="agvCount" :min="1" :max="12" style="width: 120px" />
        <span class="muted">随机种子</span>
        <el-input-number v-model="seed" :min="0" :controls="false" style="width: 150px" />
        <el-button type="primary" :loading="running" @click="runCompare">运行对比（两种算法）</el-button>
      </el-space>
      <div class="muted" style="margin-top: 8px">
        同一路网、同一任务集、同一随机种子下依次跑就近分配与贪心 ETA，指标口径见实验报告。
      </div>
    </div>

    <div v-if="results.length > 0" class="panel" style="margin-top: 16px">
      <h3 style="margin-top: 0">指标对比</h3>
      <div ref="chartRef" style="width: 100%; height: 360px"></div>
      <el-table :data="results" style="margin-top: 12px">
        <el-table-column prop="algorithmName" label="调度算法" width="140" />
        <el-table-column prop="agvCount" label="AGV" width="80" />
        <el-table-column prop="seed" label="种子" width="120" />
        <el-table-column prop="makespan" label="完成时间(tick)" width="140" />
        <el-table-column prop="totalPathLen" label="总路径长(m)" width="140" />
        <el-table-column prop="totalWaitTick" label="总等待(tick)" width="140" />
        <el-table-column prop="conflictCount" label="冲突次数" width="110" />
        <el-table-column prop="status" label="状态" width="110" />
      </el-table>
    </div>

    <div class="panel" style="margin-top: 16px">
      <h3 style="margin-top: 0">我的历史实验</h3>
      <el-table :data="history" size="small" empty-text="还没有实验记录">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="algorithmName" label="算法" width="120" />
        <el-table-column prop="mapId" label="路网" width="80" />
        <el-table-column prop="taskSetId" label="任务集" width="90" />
        <el-table-column prop="agvCount" label="AGV" width="80" />
        <el-table-column prop="seed" label="种子" width="120" />
        <el-table-column prop="makespan" label="完成时间" width="110" />
        <el-table-column prop="totalPathLen" label="总路径长" width="110" />
        <el-table-column prop="totalWaitTick" label="总等待" width="100" />
        <el-table-column prop="conflictCount" label="冲突" width="80" />
        <el-table-column prop="createTime" label="时间" min-width="170" />
      </el-table>
    </div>
  </div>
</template>
