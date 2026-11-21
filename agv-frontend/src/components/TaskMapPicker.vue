<script setup lang="ts">
import { computed } from 'vue'
import type { RoadNetPayload } from '@/api/roadnet'
import { mapZones, zoneExtents } from '@/utils/mapZones'

const props = defineProps<{
  roadnet: RoadNetPayload | null
  from: string | null
  to: string | null
  activeField: 'from' | 'to' | null
}>()

const emit = defineEmits<{
  pick: [nodeId: string]
  notice: [message: string]
}>()

const nodes = computed(() =>
  (props.roadnet?.nodes ?? []).map((node) => ({
    id: node.id,
    x: node.x,
    y: node.y,
    type: node.type,
    station: node.type === 'WORKSTATION',
    isFrom: node.id === props.from,
    isTo: node.id === props.to
  }))
)

const edges = computed(() => {
  const byId = new Map(nodes.value.map((node) => [node.id, node]))
  const result: Array<{ id: string; x1: number; y1: number; x2: number; y2: number }> = []
  for (const edge of props.roadnet?.edges ?? []) {
    const from = byId.get(edge.from)
    const to = byId.get(edge.to)
    if (from && to) {
      result.push({ id: edge.id, x1: from.x, y1: from.y, x2: to.x, y2: to.y })
    }
  }
  return result
})

const bounds = computed(() => {
  const list = nodes.value
  if (list.length === 0) return { x: 0, y: 0, width: 50, height: 34 }

  const xs = list.map((node) => node.x)
  const ys = list.map((node) => node.y)
  let minX = Math.min(...xs)
  let maxX = Math.max(...xs)
  let minY = Math.min(...ys)
  let maxY = Math.max(...ys)
  const extents = zoneExtents(props.roadnet?.zones)
  if (extents) {
    minX = Math.min(minX, extents.minX)
    maxX = Math.max(maxX, extents.maxX)
    minY = Math.min(minY, extents.minY)
    maxY = Math.max(maxY, extents.maxY)
  }

  const span = Math.max(maxX - minX, maxY - minY, 4)
  const padding = Math.max(1.5, span * 0.12)
  return {
    x: minX - padding,
    y: minY - padding,
    width: maxX - minX + padding * 2,
    height: maxY - minY + padding * 2
  }
})

const viewBox = computed(
  () => `${bounds.value.x} ${bounds.value.y} ${bounds.value.width} ${bounds.value.height}`
)

const unit = computed(() => Math.max(bounds.value.width, bounds.value.height))
const dotRadius = computed(() => clamp(unit.value / 70, 0.28, 1.1))
const stationRadius = computed(() => dotRadius.value * 1.35)
const labelSize = computed(() => clamp(unit.value / 36, 0.55, 1.8))
const zoneLabelSize = computed(() => clamp(unit.value / 32, 0.5, 1.4))
const zones = computed(() => mapZones(props.roadnet?.zones, unit.value))
const roadWidth = computed(() => clamp(unit.value / 26, 0.5, 2.2))
const lineWidth = computed(() => clamp(unit.value / 260, 0.05, 0.16))
const gridSize = computed(() => Math.max(1, Math.round(unit.value / 12)))

const fromNode = computed(() => nodes.value.find((node) => node.isFrom) ?? null)
const toNode = computed(() => nodes.value.find((node) => node.isTo) ?? null)

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function chooseNode(node: { id: string; station: boolean }) {
  if (!node.station) {
    emit('notice', `${node.id} 不是工位，不能作为任务端点`)
    return
  }
  if (!props.activeField) {
    emit('notice', '先在右侧点「起点」或「终点」按钮，再在地图上选点')
    return
  }
  emit('pick', node.id)
}

function nodeTitle(node: { id: string; station: boolean }) {
  if (!node.station) return `${node.id} 不是工位，不能作为任务端点`
  if (!props.activeField) return `工位 ${node.id}：先点右侧起点/终点按钮`
  return `工位 ${node.id}：点击设为${props.activeField === 'from' ? '起点' : '终点'}`
}
</script>

<template>
  <div class="task-map">
    <div v-if="nodes.length === 0" class="map-empty">
      {{ roadnet ? '该路网还没有节点' : '正在加载路网…' }}
    </div>
    <svg
      v-else
      class="map-svg"
      :viewBox="viewBox"
      preserveAspectRatio="xMidYMid meet"
      role="img"
      aria-label="任务端点选择地图"
    >
      <defs>
        <pattern
          id="task-map-grid"
          :width="gridSize"
          :height="gridSize"
          patternUnits="userSpaceOnUse"
        >
          <path
            :d="`M ${gridSize} 0 L 0 0 0 ${gridSize}`"
            fill="none"
            stroke="#e8eef6"
            :stroke-width="lineWidth"
          />
        </pattern>
      </defs>

      <rect
        :x="bounds.x"
        :y="bounds.y"
        :width="bounds.width"
        :height="bounds.height"
        fill="url(#task-map-grid)"
      />

      <g v-if="zones.length > 0" class="zone-shapes">
        <rect
          v-for="zone in zones"
          :key="`zone-${zone.id}`"
          class="zone-rect"
          :x="zone.x"
          :y="zone.y"
          :width="zone.width"
          :height="zone.height"
          :rx="dotRadius * 0.8"
          :fill="zone.color"
          :stroke="zone.color"
          :stroke-width="lineWidth * 2"
        />
      </g>

      <g class="edges">
        <line
          v-for="edge in edges"
          :key="`road-${edge.id}`"
          class="edge-road"
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          :stroke-width="roadWidth"
        />
        <line
          v-for="edge in edges"
          :key="`line-${edge.id}`"
          class="edge-line"
          :x1="edge.x1"
          :y1="edge.y1"
          :x2="edge.x2"
          :y2="edge.y2"
          :stroke-width="lineWidth"
        />
      </g>

      <g v-if="zones.length > 0" class="zone-labels">
        <text
          v-for="zone in zones"
          v-show="zone.showLabel"
          :key="`zone-label-${zone.id}`"
          :x="zone.x + zoneLabelSize * 0.5"
          :y="zone.y + zone.height - zoneLabelSize * 0.5"
          :fill="zone.color"
          :font-size="zoneLabelSize"
          :stroke-width="zoneLabelSize * 0.18"
          class="zone-label"
        >
          {{ zone.name }}
        </text>
      </g>

      <g
        v-for="node in nodes"
        :key="node.id"
        class="node"
        :class="{
          station: node.station,
          'selected-from': node.isFrom,
          'selected-to': node.isTo,
          'pick-from': activeField === 'from' && node.station,
          'pick-to': activeField === 'to' && node.station
        }"
        :data-id="node.id"
        :transform="`translate(${node.x},${node.y})`"
        @click="chooseNode(node)"
      >
        <title>{{ nodeTitle(node) }}</title>
        <circle class="halo" :r="node.station ? stationRadius * 2.6 : dotRadius * 2.2" />
        <circle
          class="ring"
          :r="node.station ? stationRadius * 1.5 : dotRadius * 1.4"
          :stroke-width="lineWidth * 2.5"
        />
        <circle class="dot" :r="node.station ? stationRadius : dotRadius * 0.78" />
        <text
          :y="node.station ? -stationRadius * 2.1 : -dotRadius * 2.1"
          :font-size="labelSize"
        >
          {{ node.id }}
        </text>
      </g>

      <g
        v-if="fromNode"
        class="marker start"
        :transform="`translate(${fromNode.x + stationRadius * 2.7},${fromNode.y + stationRadius * 1.9})`"
      >
        <circle :r="stationRadius * 0.95" />
        <text :font-size="labelSize * 0.78">起</text>
      </g>
      <g
        v-if="toNode"
        class="marker end"
        :transform="`translate(${toNode.x + stationRadius * 2.7},${toNode.y + stationRadius * 1.9})`"
      >
        <circle :r="stationRadius * 0.95" />
        <text :font-size="labelSize * 0.78">终</text>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.task-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 300px;
  background: #f7f9fc;
  overflow: hidden;
}

.map-svg {
  width: 100%;
  height: 100%;
  display: block;
}

.zone-shapes,
.zone-labels {
  pointer-events: none;
}

.zone-rect {
  fill-opacity: 0.16;
  stroke-opacity: 0.55;
}

.zone-label {
  font-weight: 700;
  text-anchor: start;
  fill-opacity: 0.92;
  stroke: #ffffff;
  stroke-opacity: 0.9;
  paint-order: stroke;
  pointer-events: none;
  user-select: none;
}

.edge-road {
  stroke: #dfe7f1;
  stroke-linecap: round;
}

.edge-line {
  stroke: #c4d2e3;
  stroke-dasharray: 4 3;
}

.node .dot {
  fill: #c7d0dc;
}

.node.station {
  cursor: pointer;
}

.node.station .dot {
  fill: #409eff;
}

.node.station .halo {
  fill: #409eff;
  opacity: 0.1;
}

.node.station:hover .dot {
  fill: #66b1ff;
}

.node.station:hover .halo {
  opacity: 0.2;
}

.node .ring {
  fill: none;
  stroke: #ffffff;
}

.node .halo {
  fill: #8fa3bd;
  opacity: 0.12;
}

.node:not(.station) {
  cursor: default;
}

.node text {
  fill: #4b5b70;
  font-weight: 700;
  text-anchor: middle;
  pointer-events: none;
  user-select: none;
}

.node.station.selected-from .dot {
  fill: #2f9e64;
}

.node.station.selected-from .halo {
  fill: #2f9e64;
  opacity: 0.24;
}

.node.station.selected-to .dot {
  fill: #e6822e;
}

.node.station.selected-to .halo {
  fill: #e6822e;
  opacity: 0.24;
}

.marker circle {
  stroke: #ffffff;
  stroke-width: 0.08;
}

.marker text {
  fill: #ffffff;
  font-weight: 700;
  text-anchor: middle;
  dominant-baseline: middle;
  pointer-events: none;
}

.marker.start circle {
  fill: #2f9e64;
}

.marker.end circle {
  fill: #e6822e;
}

.map-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
}
</style>


