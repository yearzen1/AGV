<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { formatMetric } from '@/utils/format'

interface ChartItem {
  label: string
  value: number | null | undefined
  color: string
}

const props = withDefaults(
  defineProps<{
    title: string
    unit: string
    items: ChartItem[]
    precision?: number
    height?: number
  }>(),
  { precision: 0, height: 150 }
)

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

const hasData = computed(() =>
  props.items.some((item) => item.value !== null && item.value !== undefined)
)

function render() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const data = props.items.map((item) => ({
    value:
      item.value === null || item.value === undefined
        ? null
        : Number(Number(item.value).toFixed(props.precision)),
    itemStyle: { color: item.color, borderRadius: [4, 4, 0, 0] }
  }))

  chart.setOption(
    {
      animationDuration: 300,
      grid: { left: 4, right: 8, top: 24, bottom: 4, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: unknown) => {
          const list = Array.isArray(params) ? params : [params]
          return list
            .map((item: any) => `${item.marker ?? ''}${item.name}：${formatMetric(item.value, props.precision)} ${props.unit}`)
            .join('<br/>')
        }
      },
      xAxis: {
        type: 'category',
        data: props.items.map((item) => item.label),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#dcdfe6' } },
        axisLabel: {
          color: '#606266',
          fontSize: 11,
          interval: 0,
          width: 84,
          overflow: 'truncate'
        }
      },
      yAxis: { type: 'value', show: false, splitLine: { show: false } },
      series: [
        {
          type: 'bar',
          barMaxWidth: 34,
          data,
          label: {
            show: true,
            position: 'top',
            color: '#303133',
            fontSize: 11,
            fontWeight: 600,
            formatter: (item: any) => formatMetric(item.value, props.precision)
          }
        }
      ]
    },
    true
  )
}

onMounted(() => {
  render()
  if (chartEl.value) {
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(chartEl.value)
  }
})

watch(
  [() => props.items, () => props.precision, () => props.unit],
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})

defineExpose({
  getDataUrl: () =>
    chart?.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff' }) ?? ''
})
</script>

<template>
  <div class="metric-card">
    <div class="metric-card-head">
      <span class="metric-card-title">{{ title }}</span>
      <span class="metric-card-unit">{{ unit }}</span>
    </div>
    <div class="metric-card-body" :style="{ height: `${height}px` }">
      <div ref="chartEl" class="metric-chart-canvas" :aria-label="title" />
      <div v-if="!hasData" class="metric-chart-empty">无完整指标</div>
    </div>
  </div>
</template>

<style scoped>
.metric-card {
  min-width: 0;
  padding: 12px 14px 6px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fff;
}

.metric-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.metric-card-title {
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.metric-card-unit {
  color: #909399;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.metric-card-body {
  position: relative;
  width: 100%;
  min-width: 0;
}

.metric-chart-canvas {
  width: 100%;
  height: 100%;
}

.metric-chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.82);
}
</style>
