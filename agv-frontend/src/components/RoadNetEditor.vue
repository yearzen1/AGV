<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { NetEdge, NetNode, RoadNetPayload, ValidationError } from '@/api/roadnet'

const props = defineProps<{
  initial: RoadNetPayload
  errors?: ValidationError[]
}>()

const SCALE = 20 // 1 米 = 20 像素
const WIDTH = 50 // 米
const HEIGHT = 34 // 米
const SNAP = 0.5 // 米
const MIN_SCALE = 0.4
const MAX_SCALE = 4

const nodes = ref<NetNode[]>(props.initial.nodes.map((n) => ({ ...n })))
const edges = ref<NetEdge[]>(props.initial.edges.map((e) => ({ ...e })))

const svgRef = ref<SVGSVGElement | null>(null)
const contentRef = ref<SVGGElement | null>(null)

type Tool = 'select' | 'WORKSTATION' | 'CHARGER' | 'BUFFER' | 'JUNCTION' | 'connect'
const tool = ref<Tool>('select')

const selectedNodeIds = ref<string[]>([])
const selectedEdgeIds = ref<string[]>([])
const connectFrom = ref<string | null>(null)

/** 视图变换：内容像素 * scale + (tx, ty)，只影响显示，不影响导出的坐标 */
const view = reactive({ scale: 1, tx: 0, ty: 0 })
const spacePressed = ref(false)
const panning = ref(false)
const hovering = ref(false)
const suppressClick = ref(false)

const marquee = ref<{ x1: number; y1: number; x2: number; y2: number } | null>(null)
const marqueeAdditive = ref(false)

const dragState = ref<{
  start: { x: number; y: number }
  base: Map<string, { x: number; y: number }>
  moved: boolean
} | null>(null)

const panState = ref<{
  start: { x: number; y: number }
  tx: number
  ty: number
  moved: boolean
} | null>(null)

const errors = computed(() => props.errors ?? [])
const errorNodeIds = computed(
  () => new Set(errors.value.map((e) => e.nodeId).filter(Boolean) as string[])
)
const errorEdgeIds = computed(
  () => new Set(errors.value.map((e) => e.edgeId).filter(Boolean) as string[])
)

const selectedNode = computed(() => {
  if (selectedNodeIds.value.length !== 1 || selectedEdgeIds.value.length !== 0) return null
  return nodes.value.find((n) => n.id === selectedNodeIds.value[0]) ?? null
})
const selectedEdge = computed(() => {
  if (selectedEdgeIds.value.length !== 1 || selectedNodeIds.value.length !== 0) return null
  return edges.value.find((e) => e.id === selectedEdgeIds.value[0]) ?? null
})
const selectedCount = computed(() => selectedNodeIds.value.length + selectedEdgeIds.value.length)

const stats = computed(() => ({
  nodes: nodes.value.length,
  edges: edges.value.length,
  length: edges.value.reduce((sum, e) => sum + (Number(e.length) || 0), 0),
  chargers: nodes.value.filter((n) => n.type === 'CHARGER').length
}))

const nodeMap = computed(() => new Map(nodes.value.map((n) => [n.id, n])))

const transform = computed(() => `translate(${view.tx} ${view.ty}) scale(${view.scale})`)
const zoomPercent = computed(() => Math.round(view.scale * 100))
const canvasClass = computed(() => ({
  'is-drawing': tool.value !== 'select',
  'is-dragging': !!dragState.value,
  'is-panning': panning.value,
  'can-pan': spacePressed.value
}))

const marqueeBox = computed(() => {
  const m = marquee.value
  if (!m) return { x: 0, y: 0, width: 0, height: 0 }
  return {
    x: Math.min(m.x1, m.x2),
    y: Math.min(m.y1, m.y2),
    width: Math.abs(m.x2 - m.x1),
    height: Math.abs(m.y2 - m.y1)
  }
})

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

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function isEditableTarget(target: EventTarget | null) {
  const el = target as HTMLElement | null
  if (!el) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

function isSelectedNode(id: string) {
  return selectedNodeIds.value.includes(id)
}

function isSelectedEdge(id: string) {
  return selectedEdgeIds.value.includes(id)
}

function clearSelection() {
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
}

function selectAll() {
  selectedNodeIds.value = nodes.value.map((n) => n.id)
  selectedEdgeIds.value = edges.value.map((e) => e.id)
}

function toggleNodeSelection(id: string) {
  selectedNodeIds.value = isSelectedNode(id)
    ? selectedNodeIds.value.filter((item) => item !== id)
    : [...selectedNodeIds.value, id]
}

function toggleEdgeSelection(id: string) {
  selectedEdgeIds.value = isSelectedEdge(id)
    ? selectedEdgeIds.value.filter((item) => item !== id)
    : [...selectedEdgeIds.value, id]
}

function clientToView(event: MouseEvent) {
  const svg = svgRef.value
  if (!svg) return { x: 0, y: 0 }
  const point = svg.createSVGPoint()
  point.x = event.clientX
  point.y = event.clientY
  return point.matrixTransform(svg.getScreenCTM()!.inverse())
}

function toWorldRaw(event: MouseEvent) {
  const svg = svgRef.value
  const content = contentRef.value
  if (!svg || !content) return { x: 0, y: 0 }
  const point = svg.createSVGPoint()
  point.x = event.clientX
  point.y = event.clientY
  const transformed = point.matrixTransform(content.getScreenCTM()!.inverse())
  return { x: transformed.x / SCALE, y: transformed.y / SCALE }
}

function toWorld(event: MouseEvent) {
  const point = toWorldRaw(event)
  return {
    x: Math.round(point.x / SNAP) * SNAP,
    y: Math.round(point.y / SNAP) * SNAP
  }
}

function setScale(next: number, anchor?: { x: number; y: number }) {
  const scale = clamp(next, MIN_SCALE, MAX_SCALE)
  if (Math.abs(scale - view.scale) < 1e-6) return
  const point = anchor ?? { x: (WIDTH * SCALE) / 2, y: (HEIGHT * SCALE) / 2 }
  const contentX = (point.x - view.tx) / view.scale
  const contentY = (point.y - view.ty) / view.scale
  view.scale = scale
  view.tx = point.x - contentX * scale
  view.ty = point.y - contentY * scale
}

function zoomIn() {
  setScale(view.scale * 1.2)
}

function zoomOut() {
  setScale(view.scale / 1.2)
}

function resetView() {
  view.scale = 1
  view.tx = 0
  view.ty = 0
}

function fitView() {
  if (nodes.value.length === 0) {
    resetView()
    return
  }
  const xs = nodes.value.map((n) => n.x * SCALE)
  const ys = nodes.value.map((n) => n.y * SCALE)
  const margin = 60
  const minX = Math.min(...xs) - margin
  const maxX = Math.max(...xs) + margin
  const minY = Math.min(...ys) - margin
  const maxY = Math.max(...ys) + margin
  const viewWidth = WIDTH * SCALE
  const viewHeight = HEIGHT * SCALE
  const scale = clamp(
    Math.min(viewWidth / Math.max(maxX - minX, 1), viewHeight / Math.max(maxY - minY, 1)),
    MIN_SCALE,
    MAX_SCALE
  )
  view.scale = scale
  view.tx = viewWidth / 2 - ((minX + maxX) / 2) * scale
  view.ty = viewHeight / 2 - ((minY + maxY) / 2) * scale
}

function onWheel(event: WheelEvent) {
  if (dragState.value || panState.value) return
  setScale(view.scale * (event.deltaY < 0 ? 1.1 : 1 / 1.1), clientToView(event))
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

function refreshEdgeLengths(nodeIds: Set<string>) {
  for (const edge of edges.value) {
    if (!nodeIds.has(edge.from) && !nodeIds.has(edge.to)) continue
    const a = nodeMap.value.get(edge.from)
    const b = nodeMap.value.get(edge.to)
    if (a && b) {
      edge.length = Math.max(0.1, Math.round(Math.hypot(b.x - a.x, b.y - a.y) * 10) / 10)
    }
  }
}

function onCanvasClick(event: MouseEvent) {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  const type = tool.value
  if (type === 'select' || type === 'connect') {
    if (type === 'connect' && !connectFrom.value) {
      clearSelection()
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
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []
}

function movingNodeIds() {
  const ids = new Set(selectedNodeIds.value)
  for (const edge of edges.value) {
    if (isSelectedEdge(edge.id)) {
      ids.add(edge.from)
      ids.add(edge.to)
    }
  }
  return [...ids]
}

function beginDrag(event: MouseEvent, nodeIds: string[]) {
  if (nodeIds.length === 0) return
  const base = new Map<string, { x: number; y: number }>()
  for (const id of nodeIds) {
    const node = nodes.value.find((n) => n.id === id)
    if (node) base.set(id, { x: node.x, y: node.y })
  }
  if (base.size === 0) return
  dragState.value = { start: toWorldRaw(event), base, moved: false }
}

function onNodeClick(event: MouseEvent, node: NetNode) {
  if (tool.value !== 'connect') return
  event.stopPropagation()
  if (!connectFrom.value) {
    connectFrom.value = node.id
    selectedNodeIds.value = [node.id]
    selectedEdgeIds.value = []
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
  selectedEdgeIds.value = [edge.id]
  selectedNodeIds.value = []
  connectFrom.value = null
}

function onNodeMouseDown(event: MouseEvent, node: NetNode) {
  if (event.button === 1 || spacePressed.value) return
  if (tool.value !== 'select') return
  event.stopPropagation()
  const additive = event.shiftKey || event.ctrlKey || event.metaKey
  if (additive) {
    toggleNodeSelection(node.id)
  } else if (!isSelectedNode(node.id)) {
    selectedNodeIds.value = [node.id]
    selectedEdgeIds.value = []
  }
  if (!isSelectedNode(node.id)) return
  beginDrag(event, movingNodeIds())
}

function onEdgeMouseDown(event: MouseEvent, edge: NetEdge) {
  if (event.button === 1 || spacePressed.value) return
  if (tool.value !== 'select') return
  event.stopPropagation()
  const additive = event.shiftKey || event.ctrlKey || event.metaKey
  if (additive) {
    toggleEdgeSelection(edge.id)
  } else if (!isSelectedEdge(edge.id)) {
    selectedEdgeIds.value = [edge.id]
    selectedNodeIds.value = []
  }
  if (!isSelectedEdge(edge.id)) return
  beginDrag(event, movingNodeIds())
}

function updateDrag(event: MouseEvent) {
  const state = dragState.value
  if (!state) return
  const point = toWorldRaw(event)
  let dx = Math.round((point.x - state.start.x) / SNAP) * SNAP
  let dy = Math.round((point.y - state.start.y) / SNAP) * SNAP

  const xs = [...state.base.values()].map((p) => p.x)
  const ys = [...state.base.values()].map((p) => p.y)
  dx = clamp(dx, -Math.min(...xs), WIDTH - Math.max(...xs))
  dy = clamp(dy, -Math.min(...ys), HEIGHT - Math.max(...ys))

  if (!state.moved && (dx !== 0 || dy !== 0)) state.moved = true

  for (const [id, origin] of state.base) {
    const node = nodes.value.find((n) => n.id === id)
    if (node) {
      node.x = origin.x + dx
      node.y = origin.y + dy
    }
  }
  refreshEdgeLengths(new Set(state.base.keys()))
}

function onCanvasMouseDown(event: MouseEvent) {
  suppressClick.value = false
  const isPanTrigger = event.button === 1 || (event.button === 0 && spacePressed.value)
  if (isPanTrigger) {
    event.preventDefault()
    panState.value = {
      start: clientToView(event),
      tx: view.tx,
      ty: view.ty,
      moved: false
    }
    panning.value = true
    return
  }
  if (tool.value !== 'select' || event.button !== 0) return
  const point = clientToView(event)
  marquee.value = { x1: point.x, y1: point.y, x2: point.x, y2: point.y }
  marqueeAdditive.value = event.shiftKey || event.ctrlKey || event.metaKey
}

function onCanvasMouseMove(event: MouseEvent) {
  if (panState.value) {
    const point = clientToView(event)
    view.tx = panState.value.tx + (point.x - panState.value.start.x)
    view.ty = panState.value.ty + (point.y - panState.value.start.y)
    if (
      Math.abs(point.x - panState.value.start.x) > 2 ||
      Math.abs(point.y - panState.value.start.y) > 2
    ) {
      panState.value.moved = true
    }
    return
  }
  if (marquee.value) {
    const point = clientToView(event)
    marquee.value.x2 = point.x
    marquee.value.y2 = point.y
    return
  }
  updateDrag(event)
}

function finalizeMarquee() {
  const box = marqueeBox.value
  if (box.width < 3 && box.height < 3) {
    if (!marqueeAdditive.value) clearSelection()
    return
  }
  const x1 = (box.x - view.tx) / view.scale / SCALE
  const y1 = (box.y - view.ty) / view.scale / SCALE
  const x2 = (box.x + box.width - view.tx) / view.scale / SCALE
  const y2 = (box.y + box.height - view.ty) / view.scale / SCALE
  const nodeIds = nodes.value
    .filter((n) => n.x >= x1 && n.x <= x2 && n.y >= y1 && n.y <= y2)
    .map((n) => n.id)
  const nodeSet = new Set(nodeIds)
  const edgeIds = edges.value
    .filter((e) => nodeSet.has(e.from) && nodeSet.has(e.to))
    .map((e) => e.id)

  if (marqueeAdditive.value) {
    selectedNodeIds.value = [...new Set([...selectedNodeIds.value, ...nodeIds])]
    selectedEdgeIds.value = [...new Set([...selectedEdgeIds.value, ...edgeIds])]
  } else {
    selectedNodeIds.value = nodeIds
    selectedEdgeIds.value = edgeIds
  }
  suppressClick.value = true
}

function onCanvasMouseUp() {
  if (panState.value) {
    if (panState.value.moved) suppressClick.value = true
    panState.value = null
    panning.value = false
  }
  if (marquee.value) {
    finalizeMarquee()
    marquee.value = null
  }
  dragState.value = null
}

function onCanvasLeave() {
  hovering.value = false
  onCanvasMouseUp()
}

function deleteSelected() {
  if (selectedCount.value === 0) return
  const nodeIds = new Set(selectedNodeIds.value)
  const edgeIds = new Set(selectedEdgeIds.value)
  nodes.value = nodes.value.filter((n) => !nodeIds.has(n.id))
  edges.value = edges.value.filter(
    (e) => !edgeIds.has(e.id) && !nodeIds.has(e.from) && !nodeIds.has(e.to)
  )
  clearSelection()
}

function onKeyDown(event: KeyboardEvent) {
  if (!hovering.value || isEditableTarget(event.target)) return
  if (event.code === 'Space') {
    spacePressed.value = true
    event.preventDefault()
    return
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a') {
    event.preventDefault()
    selectAll()
    return
  }
  if (event.key === 'Escape') {
    clearSelection()
    connectFrom.value = null
    return
  }
  if (event.key === 'Delete' || event.key === 'Backspace') {
    if (selectedCount.value > 0) {
      event.preventDefault()
      deleteSelected()
    }
  }
}

function onKeyUp(event: KeyboardEvent) {
  if (event.code === 'Space') {
    spacePressed.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
})

function edgeMid(edge: NetEdge) {
  const a = nodeMap.value.get(edge.from)
  const b = nodeMap.value.get(edge.to)
  if (!a || !b) return { x: 0, y: 0 }
  return { x: ((a.x + b.x) / 2) * SCALE, y: ((a.y + b.y) / 2) * SCALE }
}

function setPayload(payload: RoadNetPayload) {
  nodes.value = payload.nodes.map((n) => ({ ...n }))
  edges.value = payload.edges.map((e) => ({ ...e }))
  clearSelection()
  connectFrom.value = null
  resetView()
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
      <el-button size="small" :disabled="selectedCount === 0" @click="deleteSelected">
        删除选中
      </el-button>
      <el-button size="small" @click="selectAll">全选</el-button>
      <el-button size="small" :disabled="selectedCount === 0" @click="clearSelection">
        清空
      </el-button>
      <el-divider direction="vertical" />
      <el-button-group>
        <el-button size="small" title="缩小" @click="zoomOut">−</el-button>
        <el-button size="small" title="重置为 100%" @click="resetView">{{ zoomPercent }}%</el-button>
        <el-button size="small" title="放大" @click="zoomIn">+</el-button>
      </el-button-group>
      <el-button size="small" @click="fitView">适应窗口</el-button>
      <span class="hint">滚轮缩放 · 空格/中键拖动画布 · 拖空白框选</span>
    </div>

    <div class="workspace">
      <div class="canvas-wrap">
        <svg
          ref="svgRef"
          :viewBox="`0 0 ${WIDTH * SCALE} ${HEIGHT * SCALE}`"
          class="canvas"
          :class="canvasClass"
          @click="onCanvasClick"
          @wheel.prevent="onWheel"
          @mousedown="onCanvasMouseDown"
          @mousemove="onCanvasMouseMove"
          @mouseup="onCanvasMouseUp"
          @mouseenter="hovering = true"
          @mouseleave="onCanvasLeave"
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

          <g ref="contentRef" :transform="transform">
            <rect :width="WIDTH * SCALE" :height="HEIGHT * SCALE" fill="url(#grid)" />

            <g v-for="edge in edges" :key="edge.id">
              <line
                :x1="px(nodeMap.get(edge.from)?.x ?? 0)"
                :y1="px(nodeMap.get(edge.from)?.y ?? 0)"
                :x2="px(nodeMap.get(edge.to)?.x ?? 0)"
                :y2="px(nodeMap.get(edge.to)?.y ?? 0)"
                :stroke="
                  errorEdgeIds.has(edge.id)
                    ? '#f56c6c'
                    : isSelectedEdge(edge.id)
                      ? '#1f3b73'
                      : '#8fa3bd'
                "
                :stroke-width="isSelectedEdge(edge.id) ? 4 : 3"
                class="edge-line"
                :class="{ selected: isSelectedEdge(edge.id) }"
                @mousedown="onEdgeMouseDown($event, edge)"
              />
              <text
                :x="edgeMid(edge).x"
                :y="edgeMid(edge).y - 6"
                text-anchor="middle"
                font-size="10"
                fill="#7a8aa3"
                pointer-events="none"
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
                    : isSelectedNode(node.id)
                      ? '#1f3b73'
                      : '#ffffff'
                "
                :stroke-width="connectFrom === node.id || isSelectedNode(node.id) ? 4 : 2"
                class="node-dot"
                :class="{ selected: isSelectedNode(node.id) }"
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
          </g>

          <rect
            v-if="marquee"
            :x="marqueeBox.x"
            :y="marqueeBox.y"
            :width="marqueeBox.width"
            :height="marqueeBox.height"
            class="marquee"
          />
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
          <div v-else-if="selectedCount > 0" class="muted">
            已选 {{ selectedNodeIds.length }} 个节点、{{ selectedEdgeIds.length }} 条路段，拖动任意选中元素可整体移动
          </div>
          <div v-else class="muted">选中节点或路段后可编辑属性</div>
        </div>

        <div class="side-block">
          <div class="side-title">统计</div>
          <div class="stat-row"><span>节点</span><b>{{ stats.nodes }}</b></div>
          <div class="stat-row"><span>路段</span><b>{{ stats.edges }}</b></div>
          <div class="stat-row"><span>总长度</span><b>{{ stats.length.toFixed(1) }} m</b></div>
          <div class="stat-row"><span>充电位</span><b>{{ stats.chargers }}</b></div>
          <div class="stat-row"><span>选中</span><b>{{ selectedCount }}</b></div>
        </div>

        <div class="side-block">
          <div class="side-title">校验</div>
          <div v-if="errors.length === 0" class="muted">未发现问题</div>
          <div v-for="(error, index) in errors" :key="index" class="error-row">
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

.hint {
  font-size: 12px;
  color: #909399;
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
  cursor: default;
  user-select: none;
}

.canvas.is-drawing {
  cursor: crosshair;
}

.canvas.is-drawing .node-dot,
.canvas.is-drawing .edge-line {
  cursor: crosshair;
}

.canvas.can-pan {
  cursor: grab;
}

.canvas.is-panning {
  cursor: grabbing;
}

.canvas.is-dragging {
  cursor: grabbing;
}

.node-dot {
  cursor: grab;
}

.canvas.is-dragging .node-dot {
  cursor: grabbing;
}

.edge-line {
  cursor: pointer;
}

.marquee {
  fill: rgba(31, 59, 115, 0.08);
  stroke: #1f3b73;
  stroke-width: 1;
  stroke-dasharray: 4 3;
  pointer-events: none;
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
