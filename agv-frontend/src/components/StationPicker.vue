<script setup lang="ts">
import { computed } from 'vue'
import type { RoadNetPayload } from '@/api/roadnet'

const props = defineProps<{
  roadnet: RoadNetPayload | null
  modelValue: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  close: []
}>()

const nodes = computed(() =>
  (props.roadnet?.nodes ?? []).map((node) => ({
    id: node.id,
    x: node.x,
    y: -node.y,
    station: node.type === 'WORKSTATION',
    selected: node.id === props.modelValue
  }))
)

const edges = computed(() => {
  const byId = new Map(nodes.value.map((node) => [node.id, node]))
  const result: { id: string; x1: number; y1: number; x2: number; y2: number }[] = []
  for (const edge of props.roadnet?.edges ?? []) {
    const from = byId.get(edge.from)
    const to = byId.get(edge.to)
    if (from && to) {
      result.push({ id: edge.id, x1: from.x, y1: from.y, x2: to.x, y2: to.y })
    }
  }
  return result
})

const viewBox = computed(() => {
  const points = nodes.value
  if (points.length === 0) {
    return '0 -34 50 34'
  }
  const xs = points.map((node) => node.x)
  const ys = points.map((node) => node.y)
  const minX = Math.min(...xs) - 2
  const maxX = Math.max(...xs) + 2
  const minY = Math.min(...ys) - 2
  const maxY = Math.max(...ys) + 2
  return `${minX} ${minY} ${Math.max(1, maxX - minX)} ${Math.max(1, maxY - minY)}`
})

function choose(node: { id: string; station: boolean }) {
  if (!node.station) return
  emit('update:modelValue', node.id)
  emit('close')
}
</script>

<template>
  <div class="station-picker">
    <div v-if="nodes.length === 0" class="empty">该路网还没有节点</div>
    <svg v-else :viewBox="viewBox" preserveAspectRatio="xMidYMid meet">
      <line
        v-for="edge in edges"
        :key="edge.id"
        :x1="edge.x1"
        :y1="edge.y1"
        :x2="edge.x2"
        :y2="edge.y2"
        class="edge"
      />
      <g
        v-for="node in nodes"
        :key="node.id"
        class="node"
        :class="{ station: node.station, selected: node.selected }"
        @click="choose(node)"
      >
        <circle :cx="node.x" :cy="node.y" r="0.7" />
        <text :x="node.x" :y="node.y - 1" text-anchor="middle">{{ node.id }}</text>
      </g>
    </svg>
    <div class="legend">
      <span class="dot station"></span>工位（可点击）
      <span class="dot"></span>其他节点
    </div>
  </div>
</template>

<style scoped>
.station-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

svg {
  width: 100%;
  height: 340px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.edge {
  stroke: #c0c4cc;
  stroke-width: 0.12;
}

.node circle {
  fill: #c0c4cc;
}

.node.station circle {
  fill: #409eff;
  cursor: pointer;
}

.node.station:hover circle {
  fill: #66b1ff;
}

.node.selected circle {
  fill: #f56c6c;
  stroke: #ffffff;
  stroke-width: 0.15;
}

.node text {
  font-size: 1.1px;
  fill: #303133;
  pointer-events: none;
  user-select: none;
}

.empty {
  height: 340px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #c0c4cc;
  display: inline-block;
}

.dot.station {
  background: #409eff;
}
</style>
