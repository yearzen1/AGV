<script setup lang="ts">
import { computed, ref } from 'vue'
import type { NetEdge, NetNode, RoadNetPayload, ValidationError } from '@/api/roadnet'

const props = defineProps<{
  initial: RoadNetPayload
  errors?: ValidationError[]
}>()

const SCALE = 20 // 1 米 = 20 像素
const WIDTH = 50 // 米
const HEIGHT = 34 // 米
const SNAP = 0.5 // 米

const nodes = ref<NetNode[]>(props.initial.nodes.map((n) => ({ ...n })))
const edges = ref<NetEdge[]>(props.initial.edges.map((e) => ({ ...e })))

const svgRef = ref<SVGSVGElement | null>(null)
const tool = ref<'select' | 'WORKSTATION' | 'CHARGER' | 'BUFFER' | 'JUNCTION' | 'connect'>(
  'select'
)
const selected = ref<{ kind: 'node' | 'edge'; id: string } | null>(null)
const connectFrom = ref<string | null>(null)
const dragging = ref<string | null>(null)

const errorNodeIds = computed(
  () => new Set((props.errors ?? []).map((e) => e.nodeId).filter(Boolean) as string[])
)
const errorEdgeIds = computed(
  () => new Set((props.errors ?? []).map((e) => e.edgeId).filter(Boolean) as string[])
)

const selectedNode = computed(() =>
  selected.value?.kind === 'node' ? nodes.value.find((n) => n.id === selected.value!.id) ?? null : null
)
const selectedEdge = computed(() =>
  selected.value?.kind === 'edge' ? edges.value.find((e) => e.id === selected.value!.id) ?? null : null
)

const stats = computed(() => ({
  nodes: nodes.value.length,
  edges: edges.value.length,
  length: edges.value.reduce((sum, e) => sum + (Number(e.length) || 0), 0),
  chargers: nodes.value.filter((n) => n.type === 'CHARGER').length
}))

const nodeMap = computed(() => new Map(nodes.value.map((n) => [n.id, n])))

const PREFIX: Record<string, string> = {
  WORKSTATION: 'W',
  CHARGER: 'CH',
  BUFFER: 'B',
  JUNCTION: 'J'
}

const TYPE_LABEL: Record<string, string> = {
  WORKSTATION: '工位',
  CHARGER: '充电位',
  BUFFER: '缓存位',
  JUNCTION: '路口'
}

const TYPE_COLOR: Record<string, string> = {
  WORKSTATION: '#409eff',
  CHARGER: '#67c23a',
  BUFFER: '#e6a23c',
  JUNCTION: '#909399'
}

function px(value: number) {
  return value * SCALE
}

function nextNodeId(type: string) {
  const prefix = PREFIX[type] ?? 'N'
  let index = 1
  while (nodes.value.some((n) => n.id === `${prefix}${index}`)) {
    index++
  }
  return `${prefix}${index}`
}

function nextEdgeId() {
  let index = 1
  while (edges.value.some((e) => e.id === `E${index}`)) {
    index++
  }
  return `E${index}`
}

function toWorld(event: MouseEvent) {
  const svg = svgRef.value
  if (!svg) return { x: 0, y: 0 }
  const point = svg.createSVGPoint()
  point.x = event.clientX
  point.y = event.clientY
  const transformed = point.matrixTransform(svg.getScreenCTM()!.inverse())
  return {
    x: Math.round((transformed.x / SCALE) / SNAP) * SNAP,
    y: Math.round((transformed.y / SCALE) / SNAP) * SNAP
  }
}

function onCanvasClick(event: MouseEvent) {
  const type = tool.value
  if (type === 'select' || type === 'connect') {
    if (type === 'connect' && !connectFrom.value) {
      selected.value = null
    }
    return
  }
  const point = toWorld(event)
  if (point.x < 0 || point.y < 0 || point.x > WIDTH || point.y > HEIGHT) return
  const node: NetNode = {
    id: nextNodeId(type),
    x: point.x,
    y: point.y,
    type: type as NetNode['type']
  }
  nodes.value.push(node)
  selected.value = { kind: 'node', id: node.id }
}

function onNodeClick(event: MouseEvent, node: NetNode) {
  event.stopPropagation()
  if (tool.value === 'connect') {
    if (!connectFrom.value) {
      connectFrom.value = node.id
      selected.value = { kind: 'node', id: node.id }
      return
    }
    if (connectFrom.value === node.id) {
      connectFrom.value = null
      return
    }
    const duplicate = edges.value.some(
      (e) =>
        (e.from === connectFrom.value && e.to === node.id) ||
        (e.from === node.id && e.to === connectFrom.value)
    )
    if (duplicate) {
      connectFrom.value = null
      return
    }
    const from = nodeMap.value.get(connectFrom.value)!
    const length = Math.round(Math.hypot(node.x - from.x, node.y - from.y) * 10) / 10
    const edge: NetEdge = {
      id: nextEdgeId(),
      from: from.id,
      to: node.id,
      length: length > 0 ? length : 1,
      direction: 'TWO_WAY',
      speed: 1,
      capacity: 1
    }
    edges.value.push(edge)
    selected.value = { kind: 'edge', id: edge.id }
    connectFrom.value = null
    return
  }
  selected.value = { kind: 'node', id: node.id }
}

function onNodeMouseDown(event: MouseEvent, node: NetNode) {
  if (tool.value !== 'select') return
  event.stopPropagation()
  selected.value = { kind: 'node', id: node.id }
  dragging.value = node.id
  const svg = svgRef.value
  if (svg) svg.setPointerCapture((event as PointerEvent).pointerId)
}

function onCanvasMouseMove(event: MouseEvent) {
  if (!dragging.value) return
  const point = toWorld(event)
  const node = nodes.value.find((n) => n.id === dragging.value)
  if (!node) return
  node.x = Math.max(0, Math.min(WIDTH, point.x))
  node.y = Math.max(0, Math.min(HEIGHT, point.y))
  for (const edge of edges.value) {
    if (edge.from === node.id || edge.to === node.id) {
      const a = nodeMap.value.get(edge.from)
      const b = nodeMap.value.get(edge.to)
      if (a && b) {
        edge.length = Math.max(0.1, Math.round(Math.hypot(b.x - a.x, b.y - a.y) * 10) / 10)
      }
    }
  }
}

function onCanvasMouseUp() {
  dragging.value = null
}

function selectEdge(event: MouseEvent, edge: NetEdge) {
  event.stopPropagation()
  selected.value = { kind: 'edge', id: edge.id }
}

function deleteSelected() {
  if (!selected.value) return
  if (selected.value.kind === 'node') {
    const id = selected.value.id
    nodes.value = nodes.value.filter((n) => n.id !== id)
    edges.value = edges.value.filter((e) => e.from !== id && e.to !== id)
  } else {
    edges.value = edges.value.filter((e) => e.id !== selected.value!.id)
  }
  selected.value = null
}

function edgeMid(edge: NetEdge) {
  const a = nodeMap.value.get(edge.from)
  const b = nodeMap.value.get(edge.to)
  if (!a || !b) return { x: 0, y: 0 }
  return { x: ((a.x + b.x) / 2) * SCALE, y: ((a.y + b.y) / 2) * SCALE }
}

function edgeAngle(edge: NetEdge) {
  const a = nodeMap.value.get(edge.from)
  const b = nodeMap.value.get(edge.to)
  if (!a || !b) return 0
  return (Math.atan2(b.y - a.y, b.x - a.x) * 180) / Math.PI
}

function setPayload(payload: RoadNetPayload) {
  nodes.value = payload.nodes.map((n) => ({ ...n }))
  edges.value = payload.edges.map((e) => ({ ...e }))
  selected.value = null
  connectFrom.value = null
}

function getPayload(): RoadNetPayload {
  return {
    nodes: nodes.value.map((n) => ({ ...n })),
    edges: edges.value.map((e) => ({ ...e }))
  }
}

defineExpose({ getPayload, setPayload })
</script>

<template>
  <div class="editor">
    <div class="toolbar">
      <el-radio-group v-model="tool" size="small">
        <el-radio-button value="select">选择/拖动</el-radio-button>
        <el-radio-button value="connect">连路段</el-radio-button>
      </el-radio-group>
      <el-divider direction="vertical" />
      <el-radio-group v-model="tool" size="small">
        <el-radio-button value="WORKSTATION">工位</el-radio-button>
        <el-radio-button value="CHARGER">充电位</el-radio-button>
        <el-radio-button value="BUFFER">缓存位</el-radio-button>
        <el-radio-button value="JUNCTION">路口</el-radio-button>
      </el-radio-group>
      <el-divider direction="vertical" />
      <el-button size="small" :disabled="!selected" @click="deleteSelected">删除选中</el-button>
    </div>

    <div class="workspace">
      <div class="canvas-wrap">
        <svg
          ref="svgRef"
          :viewBox="`0 0 ${WIDTH * SCALE} ${HEIGHT * SCALE}`"
          class="canvas"
          @click="onCanvasClick"
          @mousemove="onCanvasMouseMove"
          @mouseup="onCanvasMouseUp"
          @mouseleave="onCanvasMouseUp"
        >
          <defs>
            <pattern id="smallGrid" :width="SNAP * SCALE" :height="SNAP * SCALE" patternUnits="userSpaceOnUse">
              <path
                :d="`M ${SNAP * SCALE} 0 L 0 0 0 ${SNAP * SCALE}`"
                fill="none"
                stroke="#eef2f7"
                stroke-width="1"
              />
            </pattern>
            <pattern id="grid" :width="SCALE * 5" :height="SCALE * 5" patternUnits="userSpaceOnUse">
              <rect :width="SCALE * 5" :height="SCALE * 5" fill="url(#smallGrid)" />
              <path
                :d="`M ${SCALE * 5} 0 L 0 0 0 ${SCALE * 5}`"
                fill="none"
                stroke="#dde5ef"
                stroke-width="1"
              />
            </pattern>
          </defs>

          <rect :width="WIDTH * SCALE" :height="HEIGHT * SCALE" fill="url(#grid)" />

          <g v-for="edge in edges" :key="edge.id">
            <line
              :x1="px(nodeMap.get(edge.from)?.x ?? 0)"
              :y1="px(nodeMap.get(edge.from)?.y ?? 0)"
              :x2="px(nodeMap.get(edge.to)?.x ?? 0)"
              :y2="px(nodeMap.get(edge.to)?.y ?? 0)"
              :stroke="errorEdgeIds.has(edge.id) ? '#f56c6c' : selected?.kind === 'edge' && selected.id === edge.id ? '#1f3b73' : '#8fa3bd'"
              :stroke-width="selected?.kind === 'edge' && selected.id === edge.id ? 4 : 3"
              class="edge-line"
              @click="selectEdge($event, edge)"
            />
            <text
              :x="edgeMid(edge).x"
              :y="edgeMid(edge).y - 6"
              text-anchor="middle"
              font-size="10"
              fill="#7a8aa3"
            >
              {{ edge.id }} · {{ edge.length }}m
            </text>
          </g>

          <g v-for="node in nodes" :key="node.id">
            <circle
              :cx="px(node.x)"
              :cy="px(node.y)"
              r="9"
              :fill="errorNodeIds.has(node.id) ? '#f56c6c' : TYPE_COLOR[node.type]"
              :stroke="
                connectFrom === node.id
                  ? '#ffd666'
                  : selected?.kind === 'node' && selected.id === node.id
                    ? '#1f3b73'
                    : '#ffffff'
              "
              :stroke-width="connectFrom === node.id || (selected?.kind === 'node' && selected.id === node.id) ? 4 : 2"
              class="node-dot"
              @click="onNodeClick($event, node)"
              @mousedown="onNodeMouseDown($event, node)"
            />
            <text
              :x="px(node.x) + 12"
              :y="px(node.y) + 4"
              font-size="11"
              fill="#606266"
              pointer-events="none"
            >
              {{ node.id }}
            </text>
          </g>
        </svg>
      </div>

      <div class="side">
        <div class="side-block">
          <div class="side-title">属性</div>
          <template v-if="selectedNode">
            <el-form label-width="64px" size="small">
              <el-form-item label="编号">
                <el-input v-model="selectedNode.id" />
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="selectedNode.type" style="width: 100%">
                  <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
                </el-select>
              </el-form-item>
              <el-form-item label="X(m)">
                <el-input-number v-model="selectedNode.x" :step="0.5" :min="0" style="width: 100%" />
              </el-form-item>
              <el-form-item label="Y(m)">
                <el-input-number v-model="selectedNode.y" :step="0.5" :min="0" style="width: 100%" />
              </el-form-item>
            </el-form>
          </template>
          <template v-else-if="selectedEdge">
            <el-form label-width="64px" size="small">
              <el-form-item label="编号">
                <el-input v-model="selectedEdge.id" />
              </el-form-item>
              <el-form-item label="方向">
                <el-select v-model="selectedEdge.direction" style="width: 100%">
                  <el-option label="双向" value="TWO_WAY" />
                  <el-option label="单向" value="ONE_WAY" />
                </el-select>
              </el-form-item>
              <el-form-item label="长度(m)">
                <el-input-number v-model="selectedEdge.length" :step="0.5" :min="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="速度">
                <el-input-number v-model="selectedEdge.speed" :step="0.1" :min="0.1" style="width: 100%" />
              </el-form-item>
              <el-form-item label="容量">
                <el-input-number v-model="selectedEdge.capacity" :min="1" style="width: 100%" />
              </el-form-item>
            </el-form>
          </template>
          <div v-else class="muted">选中节点或路段后可编辑属性</div>
        </div>

        <div class="side-block">
          <div class="side-title">统计</div>
          <div class="stat-row"><span>节点</span><b>{{ stats.nodes }}</b></div>
          <div class="stat-row"><span>路段</span><b>{{ stats.edges }}</b></div>
          <div class="stat-row"><span>总长度</span><b>{{ stats.length.toFixed(1) }} m</b></div>
          <div class="stat-row"><span>充电位</span><b>{{ stats.chargers }}</b></div>
        </div>

        <div class="side-block">
          <div class="side-title">校验</div>
          <div v-if="!props.errors || props.errors.length === 0" class="muted">未发现问题</div>
          <div v-for="(error, index) in props.errors" :key="index" class="error-row">
            <el-tag size="small" type="danger">{{ error.code }}</el-tag>
            <span>{{ error.message }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 12px;
  flex: 1;
  min-height: 540px;
}

.canvas-wrap {
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdff;
}

.canvas {
  width: 100%;
  height: 100%;
  display: block;
  cursor: crosshair;
  user-select: none;
}

.node-dot {
  cursor: pointer;
}

.edge-line {
  cursor: pointer;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-block {
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.side-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #1f3b73;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
  color: #606266;
}

.error-row {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  color: #606266;
  padding: 4px 0;
  line-height: 1.4;
}
</style>
