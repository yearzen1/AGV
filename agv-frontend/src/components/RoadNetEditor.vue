<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { NetEdge, NetNode, RoadNetPayload, ValidationError } from '@/api/roadnet'

const props = defineProps<{
  initial: RoadNetPayload
  errors?: ValidationError[]
  readonly?: boolean
  tasks?: { id: number; from: string; to: string }[]
  name?: string
}>()

const emit = defineEmits<{
  (e: 'change'): void
  (e: 'save'): void
}>()

const SCALE = 20 // 1 米 = 20 像素
const SNAP = 0.5 // 米
const MIN_SCALE = 0.4
const MAX_SCALE = 4
const MIN_SIZE = 10
const MAX_SIZE = 200
const HISTORY_LIMIT = 60

type Tool = 'select' | 'pan' | 'WORKSTATION' | 'CHARGER' | 'BUFFER' | 'JUNCTION' | 'connect'

const NODE_TOOLS: Tool[] = ['WORKSTATION', 'CHARGER', 'BUFFER', 'JUNCTION']

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

/** 工具栏内联图标，避免额外依赖。 */
const ICONS: Record<string, string> = {
  undo: 'M9 14 4 9l5-5M4 9h9a7 7 0 0 1 0 14h-3',
  redo: 'M15 14l5-5-5-5M20 9h-9a7 7 0 0 0 0 14h3',
  copy: 'M9 9h10v12H9zM5 15H3V3h12v2',
  paste: 'M9 4h6v3H9zM7 6H4v15h16V6h-3',
  trash: 'M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14M10 11v6M14 11v6',
  align: 'M4 4v16M8 8h9M8 16h6',
  merge: 'M3 7h6l5 5-5 5H3M14 12h7',
  reverse: 'M4 8h12l-3-3M20 16H8l3 3',
  fit: 'M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z'
}

const PREFIX: Record<string, string> = {
  WORKSTATION: 'W',
  CHARGER: 'CH',
  BUFFER: 'B',
  JUNCTION: 'J'
}

const mapWidth = ref(50)
const mapHeight = ref(34)
const nodes = ref<NetNode[]>([])
const edges = ref<NetEdge[]>([])

const svgRef = ref<SVGSVGElement | null>(null)
const contentRef = ref<SVGGElement | null>(null)
const canvasWrap = ref<HTMLDivElement | null>(null)
const miniRef = ref<SVGSVGElement | null>(null)

const tool = ref<Tool>('select')
const selectedNodeIds = ref<string[]>([])
const selectedEdgeIds = ref<string[]>([])
const connectFrom = ref<string | null>(null)

const view = reactive({ scale: 1, tx: 0, ty: 0 })
const spacePressed = ref(false)
const panning = ref(false)
const hovering = ref(false)
const suppressClick = ref(false)

const marquee = ref<{ x1: number; y1: number; x2: number; y2: number } | null>(null)
const marqueeAdditive = ref(false)

const miniDragging = ref(false)
const isFullscreen = ref(false)

const showNodeLabels = ref(true)
const showEdgeLabels = ref(true)
const showGrid = ref(true)
const showTasks = ref(true)

function readUiPref(key: string, fallback: boolean) {
  try {
    const raw = localStorage.getItem(key)
    return raw === null ? fallback : raw === '1'
  } catch {
    return fallback
  }
}

function writeUiPref(key: string, value: boolean) {
  try {
    localStorage.setItem(key, value ? '1' : '0')
  } catch {
    // 隐私模式下忽略
  }
}

/** 窄窗口默认收起悬浮层，避免遮挡地图。 */
const narrowWindow = typeof window !== 'undefined' && window.innerWidth < 1600
const minimapCollapsed = ref(readUiPref('agv_roadnet_minimap_collapsed', narrowWindow))
const legendCollapsed = ref(readUiPref('agv_roadnet_legend_collapsed', narrowWindow))
const sideTab = ref<'props' | 'stats' | 'errors'>('props')
const isMaximized = ref(false)

function toggleMinimap() {
  minimapCollapsed.value = !minimapCollapsed.value
  writeUiPref('agv_roadnet_minimap_collapsed', minimapCollapsed.value)
}

function toggleLegend() {
  legendCollapsed.value = !legendCollapsed.value
  writeUiPref('agv_roadnet_legend_collapsed', legendCollapsed.value)
}

const cursorWorld = ref<{ x: number; y: number } | null>(null)
const hover = ref<{ kind: 'node' | 'edge'; id: string; x: number; y: number } | null>(null)
const guides = ref<{ x?: number; y?: number }>({})

const searchQuery = ref('')
const searchIndex = ref(0)
const helpVisible = ref(false)

const contextMenu = ref<{
  x: number
  y: number
  kind: 'canvas' | 'node' | 'edge'
  id?: string
  world?: { x: number; y: number }
} | null>(null)

const batchNodeType = ref('WORKSTATION')
const batchNodeCapacity = ref(1)
const batchEdgeDirection = ref<'ONE_WAY' | 'TWO_WAY'>('TWO_WAY')
const batchEdgeSpeed = ref(1)
const batchEdgeCapacity = ref(1)
const batchEdgeLaneMode = ref<'SINGLE' | 'DUAL'>('SINGLE')

const editable = computed(() => !props.readonly)
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
const selectedNodes = computed(() =>
  nodes.value.filter((n) => selectedNodeIds.value.includes(n.id))
)
const selectedCount = computed(() => selectedNodeIds.value.length + selectedEdgeIds.value.length)
const hasSelectedOneWay = computed(() =>
  edges.value.some((e) => selectedEdgeIds.value.includes(e.id) && e.direction === 'ONE_WAY')
)

const nodeMap = computed(() => new Map(nodes.value.map((n) => [n.id, n])))

const stats = computed(() => ({
  nodes: nodes.value.length,
  edges: edges.value.length,
  length: edges.value.reduce((sum, e) => sum + (Number(e.length) || 0), 0),
  chargers: nodes.value.filter((n) => n.type === 'CHARGER').length
}))

const transform = computed(() => `translate(${view.tx} ${view.ty}) scale(${view.scale})`)
const zoomPercent = computed(() => Math.round(view.scale * 100))
const canvasClass = computed(() => ({
  'is-drawing': tool.value !== 'select' && tool.value !== 'pan',
  'is-pan-tool': tool.value === 'pan',
  'is-readonly': !editable.value,
  'is-dragging': !!dragState.value,
  'is-panning': panning.value,
  'can-pan': spacePressed.value
}))

const tickStep = 10
const xTicks = computed(() => {
  const result: number[] = []
  for (let value = 0; value <= mapWidth.value; value += tickStep) result.push(value)
  return result
})
const yTicks = computed(() => {
  const result: number[] = []
  for (let value = 0; value <= mapHeight.value; value += tickStep) result.push(value)
  return result
})

const scaleBarMeters = computed(() => (view.scale >= 1.2 ? 5 : 10))
const scaleBarPx = computed(() => scaleBarMeters.value * SCALE * view.scale)

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

const hoverInfo = computed(() => {
  if (!hover.value) return null
  if (hover.value.kind === 'node') {
    const node = nodeMap.value.get(hover.value.id)
    if (!node) return null
    return {
      title: `${node.id} · ${TYPE_LABEL[node.type] ?? node.type}`,
      rows: [
        `坐标：X ${node.x.toFixed(1)} m，Y ${node.y.toFixed(1)} m`,
        `容量：${node.capacity ?? 1} 辆`
      ]
    }
  }
  const edge = edges.value.find((e) => e.id === hover.value!.id)
  if (!edge) return null
  return {
    title: `${edge.id} · ${edge.from} → ${edge.to}`,
    rows: [
      `长度：${edge.length.toFixed(1)} m`,
      `方向：${edge.direction === 'ONE_WAY' ? '单向' : '双向'}`,
      `速度：${edge.speed} · 容量：${edge.capacity}`
    ]
  }
})

const searchResults = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) return [] as { kind: 'node' | 'edge'; id: string }[]
  const result: { kind: 'node' | 'edge'; id: string }[] = []
  for (const node of nodes.value) {
    if (node.id.toLowerCase().includes(query) || TYPE_LABEL[node.type]?.includes(query)) {
      result.push({ kind: 'node', id: node.id })
    }
  }
  for (const edge of edges.value) {
    if (edge.id.toLowerCase().includes(query)) result.push({ kind: 'edge', id: edge.id })
  }
  return result
})

const searchHitNodeIds = computed(
  () => new Set(searchResults.value.filter((item) => item.kind === 'node').map((item) => item.id))
)
const searchHitEdgeIds = computed(
  () => new Set(searchResults.value.filter((item) => item.kind === 'edge').map((item) => item.id))
)
const searchLabel = computed(() => {
  if (!searchQuery.value.trim()) return '查找'
  if (searchResults.value.length === 0) return '0'
  return `${searchIndex.value + 1}/${searchResults.value.length}`
})

const miniWidth = 140
const miniHeight = computed(() =>
  Math.max(84, Math.round((miniWidth * mapHeight.value) / Math.max(mapWidth.value, 1)))
)
const miniScale = computed(() =>
  Math.min(miniWidth / (mapWidth.value * SCALE), miniHeight.value / (mapHeight.value * SCALE))
)
const miniViewport = computed(() => {
  const k = miniScale.value
  const x = (-view.tx / view.scale) * k
  const y = (-view.ty / view.scale) * k
  const width = ((mapWidth.value * SCALE) / view.scale) * k
  const height = ((mapHeight.value * SCALE) / view.scale) * k
  return {
    x: Math.max(0, Math.min(x, miniWidth)),
    y: Math.max(0, Math.min(y, miniHeight.value)),
    width: Math.max(2, Math.min(width, miniWidth)),
    height: Math.max(2, Math.min(height, miniHeight.value))
  }
})

const taskFlows = computed(() => {
  if (!props.tasks?.length) return []
  const groups = new Map<string, { from: string; to: string; count: number }>()
  for (const task of props.tasks) {
    if (!nodeMap.value.has(task.from) || !nodeMap.value.has(task.to)) continue
    const key = `${task.from}->${task.to}`
    const group = groups.get(key) ?? { from: task.from, to: task.to, count: 0 }
    group.count += 1
    groups.set(key, group)
  }
  const list = [...groups.values()]
  return list.map((group, index) => {
    const from = nodeMap.value.get(group.from)!
    const to = nodeMap.value.get(group.to)!
    const x1 = from.x * SCALE
    const y1 = from.y * SCALE
    const x2 = to.x * SCALE
    const y2 = to.y * SCALE
    const dx = x2 - x1
    const dy = y2 - y1
    const len = Math.hypot(dx, dy) || 1
    const offset = list.length > 1 ? ((index % 5) - 2) * 7 : 0
    const mx = (x1 + x2) / 2 + (-dy / len) * offset
    const my = (y1 + y2) / 2 + (dx / len) * offset
    const labelX = 0.25 * x1 + 0.5 * mx + 0.25 * x2
    const labelY = 0.25 * y1 + 0.5 * my + 0.25 * y2
    return {
      key: `${group.from}->${group.to}`,
      d: `M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`,
      count: group.count,
      labelX,
      labelY
    }
  })
})

const taskBadges = computed(() => {
  if (!props.tasks?.length) return []
  const counts = new Map<string, { from: number; to: number }>()
  for (const task of props.tasks) {
    const from = counts.get(task.from) ?? { from: 0, to: 0 }
    from.from += 1
    counts.set(task.from, from)
    const to = counts.get(task.to) ?? { from: 0, to: 0 }
    to.to += 1
    counts.set(task.to, to)
  }
  return [...counts.entries()]
    .filter(([id]) => nodeMap.value.has(id))
    .map(([id, count]) => {
      const node = nodeMap.value.get(id)!
      return { id, ...count, x: node.x * SCALE + 14, y: node.y * SCALE - 16 }
    })
})

interface Snapshot {
  nodes: NetNode[]
  edges: NetEdge[]
  width: number
  height: number
  nodeIds: string[]
  edgeIds: string[]
}

const past = ref<Snapshot[]>([])
const future = ref<Snapshot[]>([])
let lastSnapshot: Snapshot = emptySnapshot()
let historyLock = false
let historyTimer: number | undefined
let baseline = ''
const canUndo = computed(() => past.value.length > 0)
const canRedo = computed(() => future.value.length > 0)

interface DragState {
  start: { x: number; y: number }
  base: Map<string, { x: number; y: number }>
  before: Snapshot
  moved: boolean
  historyPushed: boolean
}

const dragState = ref<DragState | null>(null)

const panState = ref<{
  start: { x: number; y: number }
  tx: number
  ty: number
  moved: boolean
} | null>(null)

const clipboard = ref<{ nodes: NetNode[]; edges: NetEdge[] } | null>(null)

function emptySnapshot(): Snapshot {
  return { nodes: [], edges: [], width: 50, height: 34, nodeIds: [], edgeIds: [] }
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function sizeValue(value: number | undefined, fallback: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? clamp(Math.round(parsed), MIN_SIZE, MAX_SIZE) : fallback
}

function cloneNodes(list: NetNode[]) {
  return list.map((node) => ({
    ...node,
    capacity: typeof node.capacity === 'number' && node.capacity >= 1 ? node.capacity : 1
  }))
}

function cloneEdges(list: NetEdge[]) {
  return list.map((edge): NetEdge => {
    const laneMode: NetEdge['laneMode'] =
      edge.direction === 'TWO_WAY' && edge.laneMode === 'DUAL' ? 'DUAL' : 'SINGLE'
    return { ...edge, laneMode }
  })
}

function capture(): Snapshot {
  return {
    nodes: cloneNodes(nodes.value),
    edges: cloneEdges(edges.value),
    width: mapWidth.value,
    height: mapHeight.value,
    nodeIds: [...selectedNodeIds.value],
    edgeIds: [...selectedEdgeIds.value]
  }
}

function signature(snapshot = capture()) {
  return JSON.stringify({
    nodes: snapshot.nodes,
    edges: snapshot.edges,
    width: snapshot.width,
    height: snapshot.height
  })
}

function applySnapshot(snapshot: Snapshot) {
  window.clearTimeout(historyTimer)
  historyLock = true
  nodes.value = cloneNodes(snapshot.nodes)
  edges.value = cloneEdges(snapshot.edges)
  mapWidth.value = snapshot.width
  mapHeight.value = snapshot.height
  selectedNodeIds.value = [...snapshot.nodeIds]
  selectedEdgeIds.value = [...snapshot.edgeIds]
  connectFrom.value = null
  guides.value = {}
  lastSnapshot = capture()
  nextTick(() => {
    historyLock = false
  })
}

function commitHistory() {
  window.clearTimeout(historyTimer)
  const next = capture()
  if (signature(next) === signature(lastSnapshot)) return
  past.value.push(lastSnapshot)
  if (past.value.length > HISTORY_LIMIT) past.value.shift()
  future.value = []
  lastSnapshot = next
}

function flushHistory() {
  commitHistory()
}

function pauseHistory() {
  flushHistory()
  historyLock = true
}

function resumeHistory() {
  historyLock = false
  lastSnapshot = capture()
}

function undo() {
  if (!editable.value) return
  commitHistory()
  if (past.value.length === 0) return
  window.clearTimeout(historyTimer)
  const target = past.value.pop()!
  future.value.push(capture())
  applySnapshot(target)
}

function redo() {
  if (!editable.value || future.value.length === 0) return
  window.clearTimeout(historyTimer)
  const target = future.value.pop()!
  past.value.push(capture())
  applySnapshot(target)
}

function refreshEdgeLengths(nodeIds: Set<string>) {
  for (const edge of edges.value) {
    if (!nodeIds.has(edge.from) && !nodeIds.has(edge.to)) continue
    const from = nodeMap.value.get(edge.from)
    const to = nodeMap.value.get(edge.to)
    if (from && to) {
      edge.length = Math.max(0.1, Math.round(Math.hypot(to.x - from.x, to.y - from.y) * 10) / 10)
    }
  }
}

function refreshAllEdgeLengths() {
  refreshEdgeLengths(new Set(nodes.value.map((node) => node.id)))
}

function px(value: number) {
  return value * SCALE
}

const LANE_OFFSET_PX = 5

interface RenderLane {
  key: string
  x1: number
  y1: number
  x2: number
  y2: number
}

interface RenderArrow {
  key: string
  x1: number
  y1: number
  x2: number
  y2: number
  markerEnd: string
}

function edgeLanes(edge: NetEdge): RenderLane[] {
  const from = nodeMap.value.get(edge.from)
  const to = nodeMap.value.get(edge.to)
  if (!from || !to) return []
  const x1 = px(from.x)
  const y1 = px(from.y)
  const x2 = px(to.x)
  const y2 = px(to.y)

  if (edge.direction === 'ONE_WAY' || edge.laneMode !== 'DUAL') {
    return [{ key: 'single', x1, y1, x2, y2 }]
  }

  const dx = x2 - x1
  const dy = y2 - y1
  const length = Math.hypot(dx, dy) || 1
  const ox = (-dy / length) * LANE_OFFSET_PX
  const oy = (dx / length) * LANE_OFFSET_PX
  return [
    { key: 'forward', x1: x1 + ox, y1: y1 + oy, x2: x2 + ox, y2: y2 + oy },
    { key: 'reverse', x1: x2 - ox, y1: y2 - oy, x2: x1 - ox, y2: y1 - oy }
  ]
}

/** 箭头画在路段上，而不是端点，避免被节点圆点遮住。 */
function arrowAt(
  key: string,
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  t: number,
  markerEnd: string
): RenderArrow {
  const dx = x2 - x1
  const dy = y2 - y1
  const length = Math.hypot(dx, dy) || 1
  const ux = dx / length
  const uy = dy / length
  const half = Math.min(6, length * 0.2)
  const cx = x1 + dx * t
  const cy = y1 + dy * t
  return {
    key,
    x1: cx - ux * half,
    y1: cy - uy * half,
    x2: cx + ux * half,
    y2: cy + uy * half,
    markerEnd
  }
}

function edgeArrows(edge: NetEdge): RenderArrow[] {
  const from = nodeMap.value.get(edge.from)
  const to = nodeMap.value.get(edge.to)
  if (!from || !to) return []
  const x1 = px(from.x)
  const y1 = px(from.y)
  const x2 = px(to.x)
  const y2 = px(to.y)
  const marker = edgeMarker(edge)

  if (edge.direction === 'ONE_WAY' || edge.laneMode !== 'DUAL') {
    const arrows = [arrowAt('forward', x1, y1, x2, y2, 1 / 3, marker)]
    if (edge.direction === 'TWO_WAY') {
      arrows.push(arrowAt('reverse', x2, y2, x1, y1, 1 / 3, marker))
    }
    return arrows
  }

  const dx = x2 - x1
  const dy = y2 - y1
  const length = Math.hypot(dx, dy) || 1
  const ox = (-dy / length) * LANE_OFFSET_PX
  const oy = (dx / length) * LANE_OFFSET_PX
  return [
    arrowAt('forward', x1 + ox, y1 + oy, x2 + ox, y2 + oy, 1 / 3, marker),
    arrowAt('reverse', x2 - ox, y2 - oy, x1 - ox, y1 - oy, 1 / 3, marker)
  ]
}

function isEditableTarget(target: EventTarget | null) {
  const el = target as HTMLElement | null
  if (!el) return false
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
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
  const point = anchor ?? { x: (mapWidth.value * SCALE) / 2, y: (mapHeight.value * SCALE) / 2 }
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

function panBy(dx: number, dy: number) {
  view.tx += dx
  view.ty += dy
}

let panHoldTimer: number | undefined

function startPanRepeat(dx: number, dy: number) {
  panBy(dx, dy)
  window.clearInterval(panHoldTimer)
  panHoldTimer = window.setInterval(() => panBy(dx, dy), 90)
}

function stopPanRepeat() {
  window.clearInterval(panHoldTimer)
  panHoldTimer = undefined
}

let previewNoticeAt = 0

function notifyPreviewReadonly() {
  const now = Date.now()
  if (now - previewNoticeAt < 2000) return
  previewNoticeAt = now
  ElMessage.info('预览模式：点右上角「编辑」后可拖动')
}

function fitNodes(list: NetNode[], maxScale = MAX_SCALE) {
  if (list.length === 0) {
    resetView()
    return
  }
  const xs = list.map((node) => node.x * SCALE)
  const ys = list.map((node) => node.y * SCALE)
  const margin = 60
  const minX = Math.min(...xs) - margin
  const maxX = Math.max(...xs) + margin
  const minY = Math.min(...ys) - margin
  const maxY = Math.max(...ys) + margin
  const viewWidth = mapWidth.value * SCALE
  const viewHeight = mapHeight.value * SCALE
  const scale = clamp(
    Math.min(viewWidth / Math.max(maxX - minX, 1), viewHeight / Math.max(maxY - minY, 1)),
    MIN_SCALE,
    maxScale
  )
  view.scale = scale
  view.tx = viewWidth / 2 - ((minX + maxX) / 2) * scale
  view.ty = viewHeight / 2 - ((minY + maxY) / 2) * scale
}

function fitView() {
  fitNodes(nodes.value, 2)
}

function fitSelection() {
  const ids = new Set(selectedNodeIds.value)
  for (const edge of edges.value) {
    if (selectedEdgeIds.value.includes(edge.id)) {
      ids.add(edge.from)
      ids.add(edge.to)
    }
  }
  fitNodes(nodes.value.filter((node) => ids.has(node.id)))
}

function errorNodes() {
  const ids = new Set<string>()
  for (const error of errors.value) {
    if (error.nodeId) ids.add(error.nodeId)
    if (error.edgeId) {
      const edge = edges.value.find((item) => item.id === error.edgeId)
      if (edge) {
        ids.add(edge.from)
        ids.add(edge.to)
      }
    }
  }
  return nodes.value.filter((node) => ids.has(node.id))
}

function fitError() {
  fitNodes(errorNodes())
}

function focusError(error: ValidationError) {
  const ids = new Set<string>()
  if (error.nodeId) ids.add(error.nodeId)
  if (error.edgeId) {
    const edge = edges.value.find((item) => item.id === error.edgeId)
    if (edge) {
      ids.add(edge.from)
      ids.add(edge.to)
    }
  }
  if (ids.size === 0) return
  fitNodes(nodes.value.filter((node) => ids.has(node.id)))
  selectedNodeIds.value = [...ids]
  selectedEdgeIds.value = error.edgeId ? [error.edgeId] : []
}

function onWheel(event: WheelEvent) {
  if (dragState.value || panState.value) return
  setScale(view.scale * (event.deltaY < 0 ? 1.1 : 1 / 1.1), clientToView(event))
}

function nextNodeId(type: string) {
  const prefix = PREFIX[type] ?? 'N'
  let index = 1
  while (nodes.value.some((node) => node.id === `${prefix}${index}`)) index++
  return `${prefix}${index}`
}

function nextEdgeId() {
  let index = 1
  while (edges.value.some((edge) => edge.id === `E${index}`)) index++
  return `E${index}`
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
  connectFrom.value = null
}

function selectAll() {
  selectedNodeIds.value = nodes.value.map((node) => node.id)
  selectedEdgeIds.value = edges.value.map((edge) => edge.id)
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

function onCanvasClick(event: MouseEvent) {
  if (suppressClick.value) {
    suppressClick.value = false
    return
  }
  if (!editable.value) return
  const type = tool.value
  if (type === 'select' || type === 'connect' || type === 'pan') {
    if (type === 'connect' && !connectFrom.value) clearSelection()
    return
  }
  const point = toWorld(event)
  if (point.x < 0 || point.y < 0 || point.x > mapWidth.value || point.y > mapHeight.value) {
    ElMessage.warning(
      `超出画布范围（当前 ${mapWidth.value}×${mapHeight.value} 米），请先调整画布尺寸`
    )
    return
  }
  nodes.value.push({
    id: nextNodeId(type),
    x: point.x,
    y: point.y,
    type: type as NetNode['type'],
    capacity: 1
  })
  selectedNodeIds.value = [nodes.value[nodes.value.length - 1].id]
  selectedEdgeIds.value = []
}

function addNodeAt(type: Tool, point: { x: number; y: number }) {
  if (!editable.value) return
  const x = clamp(Math.round(point.x / SNAP) * SNAP, 0, mapWidth.value)
  const y = clamp(Math.round(point.y / SNAP) * SNAP, 0, mapHeight.value)
  const node: NetNode = { id: nextNodeId(type), x, y, type: type as NetNode['type'], capacity: 1 }
  nodes.value.push(node)
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []
}

function onNodeClick(event: MouseEvent, node: NetNode) {
  if (tool.value !== 'connect' || !editable.value) return
  event.stopPropagation()
  if (!connectFrom.value) {
    connectFrom.value = node.id
    selectedNodeIds.value = [node.id]
    selectedEdgeIds.value = []
    return
  }
  if (connectFrom.value === node.id) {
    connectFrom.value = null
    ElMessage.info('起点和终点不能相同，已取消本次连接')
    return
  }
  const duplicate = edges.value.some(
    (edge) =>
      (edge.from === connectFrom.value && edge.to === node.id) ||
      (edge.from === node.id && edge.to === connectFrom.value)
  )
  if (duplicate) {
    ElMessage.info(`${connectFrom.value} 与 ${node.id} 之间已有路段`)
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
    capacity: 1,
    laneMode: 'SINGLE'
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
  const additive = event.ctrlKey || event.metaKey
  if (additive) {
    toggleNodeSelection(node.id)
  } else if (!isSelectedNode(node.id)) {
    selectedNodeIds.value = [node.id]
    selectedEdgeIds.value = []
  }
  if (!isSelectedNode(node.id)) return
  if (!editable.value) {
    notifyPreviewReadonly()
    return
  }
  beginDrag(event, movingNodeIds())
}

function onEdgeMouseDown(event: MouseEvent, edge: NetEdge) {
  if (event.button === 1 || spacePressed.value) return
  if (tool.value !== 'select') return
  event.stopPropagation()
  const additive = event.ctrlKey || event.metaKey
  if (additive) {
    toggleEdgeSelection(edge.id)
  } else if (!isSelectedEdge(edge.id)) {
    selectedEdgeIds.value = [edge.id]
    selectedNodeIds.value = []
  }
  if (!isSelectedEdge(edge.id)) return
  if (!editable.value) {
    notifyPreviewReadonly()
    return
  }
  beginDrag(event, movingNodeIds())
}

function beginDrag(event: MouseEvent, nodeIds: string[]) {
  if (nodeIds.length === 0) return
  const base = new Map<string, { x: number; y: number }>()
  for (const id of nodeIds) {
    const node = nodes.value.find((item) => item.id === id)
    if (node) base.set(id, { x: node.x, y: node.y })
  }
  if (base.size === 0) return
  pauseHistory()
  dragState.value = {
    start: toWorldRaw(event),
    base,
    before: lastSnapshot,
    moved: false,
    historyPushed: false
  }
}

function updateDrag(event: MouseEvent) {
  const state = dragState.value
  if (!state || !editable.value) return
  const point = toWorldRaw(event)
  let dx = Math.round((point.x - state.start.x) / SNAP) * SNAP
  let dy = Math.round((point.y - state.start.y) / SNAP) * SNAP

  if (event.shiftKey) {
    if (Math.abs(dx) >= Math.abs(dy)) dy = 0
    else dx = 0
  }

  guides.value = {}
  if (state.base.size === 1 && !event.shiftKey) {
    const [id, origin] = [...state.base.entries()][0]
    const threshold = 6 / (SCALE * view.scale)
    const targetX = origin.x + dx
    const targetY = origin.y + dy
    for (const other of nodes.value) {
      if (other.id === id) continue
      if (Math.abs(other.x - targetX) <= threshold) {
        dx = other.x - origin.x
        guides.value.x = other.x * SCALE
        break
      }
    }
    for (const other of nodes.value) {
      if (other.id === id) continue
      if (Math.abs(other.y - targetY) <= threshold) {
        dy = other.y - origin.y
        guides.value.y = other.y * SCALE
        break
      }
    }
  }

  const xs = [...state.base.values()].map((item) => item.x)
  const ys = [...state.base.values()].map((item) => item.y)
  dx = clamp(dx, -Math.min(...xs), mapWidth.value - Math.max(...xs))
  dy = clamp(dy, -Math.min(...ys), mapHeight.value - Math.max(...ys))

  if (!state.moved && (dx !== 0 || dy !== 0)) {
    state.moved = true
    if (!state.historyPushed) {
      past.value.push(state.before)
      if (past.value.length > HISTORY_LIMIT) past.value.shift()
      future.value = []
      state.historyPushed = true
    }
  }

  for (const [id, origin] of state.base) {
    const node = nodes.value.find((item) => item.id === id)
    if (node) {
      node.x = origin.x + dx
      node.y = origin.y + dy
    }
  }
  refreshEdgeLengths(new Set(state.base.keys()))
}

function onCanvasMouseDown(event: MouseEvent) {
  suppressClick.value = false
  const isPanTrigger =
    event.button === 1 || (event.button === 0 && (spacePressed.value || tool.value === 'pan'))
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
  marqueeAdditive.value = event.ctrlKey || event.metaKey
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

  const world = toWorldRaw(event)
  cursorWorld.value =
    world.x >= 0 && world.y >= 0 && world.x <= mapWidth.value && world.y <= mapHeight.value
      ? world
      : null
  if (hover.value) {
    hover.value.x = event.clientX
    hover.value.y = event.clientY
  }
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
    .filter((node) => node.x >= x1 && node.x <= x2 && node.y >= y1 && node.y <= y2)
    .map((node) => node.id)
  const nodeSet = new Set(nodeIds)
  const edgeIds = edges.value
    .filter((edge) => nodeSet.has(edge.from) && nodeSet.has(edge.to))
    .map((edge) => edge.id)

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
  if (dragState.value) {
    if (dragState.value.moved) lastSnapshot = capture()
    resumeHistory()
    dragState.value = null
  }
  guides.value = {}
}

function onCanvasLeave() {
  hovering.value = false
  hover.value = null
  onCanvasMouseUp()
}

async function deleteSelected() {
  if (!editable.value || selectedCount.value === 0) return
  if (selectedNodeIds.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        `将删除 ${selectedNodeIds.value.length} 个节点及其关联路段，确定继续吗？`,
        '删除确认',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  const nodeIds = new Set(selectedNodeIds.value)
  const edgeIds = new Set(selectedEdgeIds.value)
  nodes.value = nodes.value.filter((node) => !nodeIds.has(node.id))
  edges.value = edges.value.filter(
    (edge) => !edgeIds.has(edge.id) && !nodeIds.has(edge.from) && !nodeIds.has(edge.to)
  )
  clearSelection()
}

function copySelection() {
  if (selectedNodeIds.value.length === 0) {
    ElMessage.info('请先选择节点')
    return
  }
  const selectedNodesCopy = nodes.value
    .filter((node) => selectedNodeIds.value.includes(node.id))
    .map((node) => ({ ...node }))
  const selectedEdgeCopy = edges.value
    .filter(
      (edge) =>
        selectedNodeIds.value.includes(edge.from) && selectedNodeIds.value.includes(edge.to)
    )
    .map((edge) => ({ ...edge }))
  clipboard.value = { nodes: selectedNodesCopy, edges: selectedEdgeCopy }
  ElMessage.success(`已复制 ${selectedNodesCopy.length} 个节点`)
}

function pasteClipboard() {
  if (!editable.value || !clipboard.value || clipboard.value.nodes.length === 0) return
  const nodeIds = new Set(nodes.value.map((node) => node.id))
  const edgeIds = new Set(edges.value.map((edge) => edge.id))
  const makeNodeId = (type: string) => {
    const prefix = PREFIX[type] ?? 'N'
    let index = 1
    while (nodeIds.has(`${prefix}${index}`)) index++
    const id = `${prefix}${index}`
    nodeIds.add(id)
    return id
  }
  const makeEdgeId = () => {
    let index = 1
    while (edgeIds.has(`E${index}`)) index++
    const id = `E${index}`
    edgeIds.add(id)
    return id
  }
  const idMap = new Map<string, string>()
  const offsetX = Math.min(1, mapWidth.value)
  const offsetY = Math.min(1, mapHeight.value)
  const newNodes = clipboard.value.nodes.map((node) => {
    const id = makeNodeId(node.type)
    idMap.set(node.id, id)
    return {
      ...node,
      id,
      x: clamp(node.x + offsetX, 0, mapWidth.value),
      y: clamp(node.y + offsetY, 0, mapHeight.value)
    }
  })
  const newEdges = clipboard.value.edges
    .filter((edge) => idMap.has(edge.from) && idMap.has(edge.to))
    .map((edge) => ({
      ...edge,
      id: makeEdgeId(),
      from: idMap.get(edge.from)!,
      to: idMap.get(edge.to)!
    }))
  nodes.value.push(...newNodes)
  edges.value.push(...newEdges)
  selectedNodeIds.value = newNodes.map((node) => node.id)
  selectedEdgeIds.value = newEdges.map((edge) => edge.id)
}

function duplicateSelection() {
  copySelection()
  pasteClipboard()
}

function handleAlign(command: string) {
  if (!editable.value) return
  const list = selectedNodes.value
  if (list.length < 2) return
  const xs = list.map((node) => node.x)
  const ys = list.map((node) => node.y)
  if (command === 'left') {
    const value = Math.min(...xs)
    list.forEach((node) => (node.x = value))
  } else if (command === 'center-x') {
    const value = (Math.min(...xs) + Math.max(...xs)) / 2
    list.forEach((node) => (node.x = value))
  } else if (command === 'right') {
    const value = Math.max(...xs)
    list.forEach((node) => (node.x = value))
  } else if (command === 'top') {
    const value = Math.min(...ys)
    list.forEach((node) => (node.y = value))
  } else if (command === 'middle-y') {
    const value = (Math.min(...ys) + Math.max(...ys)) / 2
    list.forEach((node) => (node.y = value))
  } else if (command === 'bottom') {
    const value = Math.max(...ys)
    list.forEach((node) => (node.y = value))
  } else if (command === 'distribute-x' && list.length >= 3) {
    const sorted = [...list].sort((a, b) => a.x - b.x)
    const step = (sorted[sorted.length - 1].x - sorted[0].x) / (sorted.length - 1)
    sorted.forEach((node, index) => (node.x = Math.round((sorted[0].x + step * index) / SNAP) * SNAP))
  } else if (command === 'distribute-y' && list.length >= 3) {
    const sorted = [...list].sort((a, b) => a.y - b.y)
    const step = (sorted[sorted.length - 1].y - sorted[0].y) / (sorted.length - 1)
    sorted.forEach((node, index) => (node.y = Math.round((sorted[0].y + step * index) / SNAP) * SNAP))
  }
  refreshEdgeLengths(new Set(list.map((node) => node.id)))
}

function applyBatchNodeType() {
  if (!editable.value) return
  const type = batchNodeType.value as NetNode['type']
  selectedNodes.value.forEach((node) => {
    node.type = type
    node.capacity = Math.max(1, batchNodeCapacity.value)
  })
}

function applyBatchEdge() {
  if (!editable.value) return
  edges.value
    .filter((edge) => selectedEdgeIds.value.includes(edge.id))
    .forEach((edge) => {
      edge.direction = batchEdgeDirection.value
      edge.speed = batchEdgeSpeed.value
      edge.capacity = batchEdgeCapacity.value
      edge.laneMode = edge.direction === 'TWO_WAY' ? batchEdgeLaneMode.value : 'SINGLE'
    })
}

function onEdgeDirectionChange(edge: NetEdge) {
  if (edge.direction === 'ONE_WAY') {
    edge.laneMode = 'SINGLE'
  }
}

function onEdgeLaneChange(edge: NetEdge) {
  if (edge.direction === 'ONE_WAY') {
    edge.laneMode = 'SINGLE'
  }
}

function reverseEdge(edge: NetEdge) {
  if (!editable.value || edge.direction !== 'ONE_WAY') return
  const from = edge.from
  edge.from = edge.to
  edge.to = from
}

function reverseSelectedEdges() {
  if (!editable.value) return
  edges.value
    .filter((edge) => selectedEdgeIds.value.includes(edge.id) && edge.direction === 'ONE_WAY')
    .forEach((edge) => reverseEdge(edge))
}

function splitEdgeAt(edge: NetEdge, point: { x: number; y: number }) {
  if (!editable.value) return
  const from = nodeMap.value.get(edge.from)
  const to = nodeMap.value.get(edge.to)
  if (!from || !to) return
  const vx = to.x - from.x
  const vy = to.y - from.y
  const lengthSquared = vx * vx + vy * vy
  if (lengthSquared === 0) return
  const t = clamp(((point.x - from.x) * vx + (point.y - from.y) * vy) / lengthSquared, 0.15, 0.85)
  const x = clamp(Math.round((from.x + vx * t) / SNAP) * SNAP, 0, mapWidth.value)
  const y = clamp(Math.round((from.y + vy * t) / SNAP) * SNAP, 0, mapHeight.value)
  if (Math.hypot(x - from.x, y - from.y) < SNAP || Math.hypot(x - to.x, y - to.y) < SNAP) return

  const node: NetNode = { id: nextNodeId('JUNCTION'), x, y, type: 'JUNCTION', capacity: 1 }
  const index = edges.value.findIndex((item) => item.id === edge.id)
  if (index < 0) return
  edges.value.splice(index, 1)
  const first: NetEdge = {
    ...edge,
    id: nextEdgeId(),
    from: edge.from,
    to: node.id,
    length: Math.max(0.1, Math.round(Math.hypot(x - from.x, y - from.y) * 10) / 10)
  }
  edges.value.push(first)
  const second: NetEdge = {
    ...edge,
    id: nextEdgeId(),
    from: node.id,
    to: edge.to,
    length: Math.max(0.1, Math.round(Math.hypot(to.x - x, to.y - y) * 10) / 10)
  }
  edges.value.push(second)
  nodes.value.push(node)
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []
}

function onEdgeDoubleClick(event: MouseEvent, edge: NetEdge) {
  if (!editable.value) return
  event.stopPropagation()
  splitEdgeAt(edge, toWorld(event))
}

function mergeSelectedEdges() {
  if (!editable.value || selectedEdgeIds.value.length !== 2) return
  const [first, second] = edges.value.filter((edge) => selectedEdgeIds.value.includes(edge.id))
  if (!first || !second) return
  const endpoints = [first.from, first.to]
  const shared = endpoints.find((id) => id === second.from || id === second.to)
  if (!shared) {
    ElMessage.warning('两条路段没有共同端点')
    return
  }
  const otherFirst = first.from === shared ? first.to : first.from
  const otherSecond = second.from === shared ? second.to : second.from
  if (otherFirst === otherSecond) return
  const degree = edges.value.filter(
    (edge) => edge.from === shared || edge.to === shared
  ).length
  if (degree !== 2) {
    ElMessage.warning('中间节点还连接其他路段，不能合并')
    return
  }
  const firstForward = first.to === shared
  const secondForward = second.from === shared
  const direction =
    first.direction === 'ONE_WAY' && second.direction === 'ONE_WAY' && firstForward === secondForward
      ? 'ONE_WAY'
      : 'TWO_WAY'
  const from = firstForward ? otherFirst : otherSecond
  const to = firstForward ? otherSecond : otherFirst
  const fromNode = nodeMap.value.get(from)
  const toNode = nodeMap.value.get(to)
  if (!fromNode || !toNode) return
  const laneMode =
    first.laneMode === 'DUAL' && second.laneMode === 'DUAL' ? 'DUAL' : 'SINGLE'
  const merged: NetEdge = {
    id: nextEdgeId(),
    from,
    to,
    length: Math.max(0.1, Math.round(Math.hypot(toNode.x - fromNode.x, toNode.y - fromNode.y) * 10) / 10),
    direction,
    speed: Math.min(first.speed, second.speed),
    capacity: Math.min(first.capacity, second.capacity),
    laneMode: direction === 'TWO_WAY' ? laneMode : 'SINGLE'
  }
  edges.value = edges.value.filter(
    (edge) => !selectedEdgeIds.value.includes(edge.id)
  )
  nodes.value = nodes.value.filter((node) => node.id !== shared)
  edges.value.push(merged)
  selectedNodeIds.value = []
  selectedEdgeIds.value = [merged.id]
}

function nudge(dx: number, dy: number) {
  if (!editable.value || selectedNodeIds.value.length === 0) return
  const list = selectedNodes.value
  const xs = list.map((node) => node.x)
  const ys = list.map((node) => node.y)
  const deltaX = clamp(dx, -Math.min(...xs), mapWidth.value - Math.max(...xs))
  const deltaY = clamp(dy, -Math.min(...ys), mapHeight.value - Math.max(...ys))
  list.forEach((node) => {
    node.x += deltaX
    node.y += deltaY
  })
  refreshEdgeLengths(new Set(list.map((node) => node.id)))
}

function onNodeCoordChange(node: NetNode) {
  node.x = clamp(Math.round(node.x / SNAP) * SNAP, 0, mapWidth.value)
  node.y = clamp(Math.round(node.y / SNAP) * SNAP, 0, mapHeight.value)
  refreshEdgeLengths(new Set([node.id]))
}

function onNodeIdChange(node: NetNode, original: string) {
  const value = node.id.trim()
  if (!value || nodes.value.some((item) => item.id === value && item !== node)) {
    ElMessage.error('节点编号为空或重复，已还原')
    node.id = original
    return
  }
  if (value !== original) {
    edges.value.forEach((edge) => {
      if (edge.from === original) edge.from = value
      if (edge.to === original) edge.to = value
    })
    selectedNodeIds.value = selectedNodeIds.value.map((id) => (id === original ? value : id))
    if (connectFrom.value === original) connectFrom.value = value
  }
}

function onEdgeIdChange(edge: NetEdge, original: string) {
  const value = edge.id.trim()
  if (!value || edges.value.some((item) => item.id === value && item !== edge)) {
    ElMessage.error('路段编号为空或重复，已还原')
    edge.id = original
    return
  }
  if (value !== original) {
    selectedEdgeIds.value = selectedEdgeIds.value.map((id) => (id === original ? value : id))
  }
}

function onNodeIdOriginal(node: NetNode) {
  idEditOriginal.value = node.id
}

const idEditOriginal = ref('')

function applyMapSize() {
  mapWidth.value = sizeValue(mapWidth.value, 50)
  mapHeight.value = sizeValue(mapHeight.value, 34)
  nodes.value.forEach((node) => {
    node.x = clamp(node.x, 0, mapWidth.value)
    node.y = clamp(node.y, 0, mapHeight.value)
  })
  refreshAllEdgeLengths()
  guides.value = {}
  resetView()
}

function onNodeEnter(event: MouseEvent, node: NetNode) {
  hover.value = { kind: 'node', id: node.id, x: event.clientX, y: event.clientY }
}

function onEdgeEnter(event: MouseEvent, edge: NetEdge) {
  hover.value = { kind: 'edge', id: edge.id, x: event.clientX, y: event.clientY }
}

function clearHover() {
  hover.value = null
}

function edgeStroke(edge: NetEdge) {
  if (errorEdgeIds.value.has(edge.id)) return '#f56c6c'
  if (isSelectedEdge(edge.id)) return '#1f3b73'
  return '#8fa3bd'
}

function edgeMarker(edge: NetEdge) {
  if (errorEdgeIds.value.has(edge.id)) return 'url(#arrow-error)'
  if (isSelectedEdge(edge.id)) return 'url(#arrow-selected)'
  return 'url(#arrow)'
}

function edgeMid(edge: NetEdge) {
  const from = nodeMap.value.get(edge.from)
  const to = nodeMap.value.get(edge.to)
  if (!from || !to) return { x: 0, y: 0 }
  return { x: ((from.x + to.x) / 2) * SCALE, y: ((from.y + to.y) / 2) * SCALE }
}

function nextSearch() {
  const results = searchResults.value
  if (results.length === 0) {
    ElMessage.info('没有匹配的节点或路段')
    return
  }
  const item = results[searchIndex.value % results.length]
  searchIndex.value += 1
  if (item.kind === 'node') {
    selectedNodeIds.value = [item.id]
    selectedEdgeIds.value = []
    const node = nodeMap.value.get(item.id)
    if (node) fitNodes([node])
  } else {
    selectedEdgeIds.value = [item.id]
    selectedNodeIds.value = []
    const edge = edges.value.find((entry) => entry.id === item.id)
    if (edge) {
      const from = nodeMap.value.get(edge.from)
      const to = nodeMap.value.get(edge.to)
      if (from && to) fitNodes([from, to])
    }
  }
}

function onCanvasContextMenu(event: MouseEvent) {
  if (!editable.value) return
  contextMenu.value = {
    x: event.clientX,
    y: event.clientY,
    kind: 'canvas',
    world: toWorld(event)
  }
}

function onNodeContextMenu(event: MouseEvent, node: NetNode) {
  if (!editable.value) return
  event.preventDefault()
  event.stopPropagation()
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []
  contextMenu.value = { x: event.clientX, y: event.clientY, kind: 'node', id: node.id }
}

function onEdgeContextMenu(event: MouseEvent, edge: NetEdge) {
  if (!editable.value) return
  event.preventDefault()
  event.stopPropagation()
  selectedEdgeIds.value = [edge.id]
  selectedNodeIds.value = []
  contextMenu.value = { x: event.clientX, y: event.clientY, kind: 'edge', id: edge.id }
}

function closeContextMenu() {
  contextMenu.value = null
}

function contextAddNode(type: Tool) {
  const world = contextMenu.value?.world
  closeContextMenu()
  if (world) addNodeAt(type, world)
}

function contextDelete() {
  closeContextMenu()
  void deleteSelected()
}

function contextSplit() {
  const edgeId = contextMenu.value?.id
  closeContextMenu()
  const edge = edges.value.find((item) => item.id === edgeId)
  if (!edge) return
  const from = nodeMap.value.get(edge.from)
  const to = nodeMap.value.get(edge.to)
  if (!from || !to) return
  splitEdgeAt(edge, { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 })
}

function contextReverse() {
  closeContextMenu()
  reverseSelectedEdges()
}

function onMiniPointer(event: MouseEvent) {
  const svg = miniRef.value
  if (!svg) return
  const rect = svg.getBoundingClientRect()
  const contentX = (event.clientX - rect.left) / miniScale.value
  const contentY = (event.clientY - rect.top) / miniScale.value
  view.tx = (mapWidth.value * SCALE) / 2 - contentX * view.scale
  view.ty = (mapHeight.value * SCALE) / 2 - contentY * view.scale
}

function startMini(event: MouseEvent) {
  miniDragging.value = true
  onMiniPointer(event)
}

function moveMini(event: MouseEvent) {
  if (miniDragging.value) onMiniPointer(event)
}

function endMini() {
  miniDragging.value = false
}

async function toggleFullscreen() {
  if (isMaximized.value) {
    isMaximized.value = false
    isFullscreen.value = false
    return
  }
  if (!document.fullscreenElement) {
    try {
      await canvasWrap.value?.requestFullscreen()
    } catch {
      isMaximized.value = true
      isFullscreen.value = true
      ElMessage.info('已切换为页面内全屏，按 Esc 退出')
    }
  } else {
    await document.exitFullscreen()
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement || isMaximized.value
}

function download(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function buildExportSvg() {
  const svg = svgRef.value
  if (!svg) return null
  const clone = svg.cloneNode(true) as SVGSVGElement
  clone.querySelectorAll('[data-export="ignore"]').forEach((element) => element.remove())
  clone.querySelectorAll('.marquee, .guide').forEach((element) => element.remove())
  const content = clone.querySelector('g[data-content="true"]')
  content?.setAttribute('transform', 'translate(0 0) scale(1)')
  const width = mapWidth.value * SCALE
  const height = mapHeight.value * SCALE
  clone.setAttribute('width', String(width))
  clone.setAttribute('height', String(height))
  clone.setAttribute('viewBox', `0 0 ${width} ${height}`)
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  const background = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
  background.setAttribute('width', String(width))
  background.setAttribute('height', String(height))
  background.setAttribute('fill', '#ffffff')
  const contentNode = clone.querySelector('g[data-content="true"]')
  if (contentNode?.parentNode) {
    contentNode.parentNode.insertBefore(background, contentNode)
  } else {
    clone.insertBefore(background, clone.firstChild)
  }
  return { markup: new XMLSerializer().serializeToString(clone), width, height }
}

function safeName() {
  return (props.name || 'roadnet').replace(/[\\/:*?"<>|]/g, '_')
}

function exportSvg() {
  const result = buildExportSvg()
  if (!result) return
  download(`${safeName()}.svg`, new Blob([result.markup], { type: 'image/svg+xml;charset=utf-8' }))
}

async function exportPng() {
  const result = buildExportSvg()
  if (!result) return
  const scale = 2
  const image = new Image()
  const url = URL.createObjectURL(
    new Blob([result.markup], { type: 'image/svg+xml;charset=utf-8' })
  )
  try {
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve()
      image.onerror = () => reject(new Error('导出图片失败'))
      image.src = url
    })
    const canvas = document.createElement('canvas')
    canvas.width = result.width * scale
    canvas.height = result.height * scale
    const context = canvas.getContext('2d')
    if (!context) return
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
    if (blob) download(`${safeName()}.png`, blob)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出图片失败')
  } finally {
    URL.revokeObjectURL(url)
  }
}

function handleExport(command: string) {
  if (command === 'svg') exportSvg()
  else if (command === 'png') void exportPng()
}

function onToolbarMore(command: string) {
  if (command === 'fit-selection') fitSelection()
  else if (command === 'fit-error') fitError()
  else if (command === 'help') helpVisible.value = true
  else handleExport(command)
}

function onKeyDown(event: KeyboardEvent) {
  if (isEditableTarget(event.target)) return
  if (event.key === '?' && !editable.value) {
    helpVisible.value = true
    return
  }
  if (!hovering.value && !event.ctrlKey && !event.metaKey && event.key !== 'Escape') return
  if (event.key === 'Escape') {
    if (isMaximized.value) {
      isMaximized.value = false
      isFullscreen.value = false
      return
    }
    if (connectFrom.value) {
      connectFrom.value = null
      return
    }
    clearSelection()
    closeContextMenu()
    hover.value = null
    return
  }
  if (event.code === 'Space') {
    spacePressed.value = true
    event.preventDefault()
    return
  }
  const modifier = event.ctrlKey || event.metaKey
  if (modifier && event.key.toLowerCase() === 'a') {
    event.preventDefault()
    selectAll()
    return
  }
  if (modifier && event.key.toLowerCase() === 's') {
    event.preventDefault()
    emit('save')
    return
  }
  if (event.key.startsWith('Arrow') && (selectedNodeIds.value.length === 0 || !editable.value)) {
    event.preventDefault()
    const step = event.shiftKey ? 120 : 40
    if (event.key === 'ArrowLeft') panBy(-step, 0)
    else if (event.key === 'ArrowRight') panBy(step, 0)
    else if (event.key === 'ArrowUp') panBy(0, -step)
    else if (event.key === 'ArrowDown') panBy(0, step)
    return
  }
  if (!editable.value) return
  if (modifier && event.key.toLowerCase() === 'z') {
    event.preventDefault()
    if (event.shiftKey) redo()
    else undo()
    return
  }
  if (modifier && event.key.toLowerCase() === 'y') {
    event.preventDefault()
    redo()
    return
  }
  if (modifier && event.key.toLowerCase() === 'c') {
    event.preventDefault()
    copySelection()
    return
  }
  if (modifier && event.key.toLowerCase() === 'v') {
    event.preventDefault()
    pasteClipboard()
    return
  }
  if (modifier && event.key.toLowerCase() === 'd') {
    event.preventDefault()
    duplicateSelection()
    return
  }
  if (event.key === '?' ) {
    helpVisible.value = true
    return
  }
  if (event.key === 'Delete' || event.key === 'Backspace') {
    if (selectedCount.value > 0) {
      event.preventDefault()
      void deleteSelected()
    }
    return
  }
  if (event.key.startsWith('Arrow') && selectedNodeIds.value.length > 0) {
    event.preventDefault()
    const distance = event.shiftKey ? SNAP * 5 : SNAP
    if (event.key === 'ArrowLeft') nudge(-distance, 0)
    else if (event.key === 'ArrowRight') nudge(distance, 0)
    else if (event.key === 'ArrowUp') nudge(0, -distance)
    else if (event.key === 'ArrowDown') nudge(0, distance)
  }
}

function onKeyUp(event: KeyboardEvent) {
  if (event.code === 'Space') spacePressed.value = false
}

function onWindowClick() {
  closeContextMenu()
}

function loadPayload(payload: RoadNetPayload, resetHistory = true) {
  window.clearTimeout(historyTimer)
  historyLock = true
  mapWidth.value = sizeValue(payload.width, 50)
  mapHeight.value = sizeValue(payload.height, 34)
  nodes.value = cloneNodes(payload.nodes ?? [])
  edges.value = cloneEdges(payload.edges ?? [])
  nodes.value.forEach((node) => {
    node.x = clamp(node.x, 0, mapWidth.value)
    node.y = clamp(node.y, 0, mapHeight.value)
  })
  refreshAllEdgeLengths()
  clearSelection()
  resetView()
  guides.value = {}
  hover.value = null
  if (resetHistory) {
    past.value = []
    future.value = []
    lastSnapshot = capture()
    baseline = signature()
  }
  nextTick(() => {
    historyLock = false
  })
}

function setPayload(payload: RoadNetPayload) {
  loadPayload(payload, true)
}

function getPayload(): RoadNetPayload {
  return {
    nodes: cloneNodes(nodes.value),
    edges: cloneEdges(edges.value),
    width: mapWidth.value,
    height: mapHeight.value
  }
}

function isDirty() {
  return signature() !== baseline
}

function markSaved() {
  baseline = signature()
}

function markDirty() {
  baseline = '__dirty__'
}

watch(
  [nodes, edges, mapWidth, mapHeight],
  () => {
    emit('change')
    if (historyLock) return
    window.clearTimeout(historyTimer)
    historyTimer = window.setTimeout(commitHistory, 250)
  },
  { deep: true }
)

watch(searchQuery, () => {
  searchIndex.value = 0
})

watch(
  [selectedNodeIds, selectedEdgeIds],
  () => {
    if (selectedCount.value > 0) sideTab.value = 'props'
  },
  { deep: true }
)

/** 手动校验后由父组件调用，直接展示校验页签。 */
function showValidation() {
  sideTab.value = 'errors'
}

onMounted(() => {
  loadPayload(props.initial, true)
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  window.addEventListener('click', onWindowClick)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
  window.removeEventListener('click', onWindowClick)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.clearTimeout(historyTimer)
  window.clearInterval(panHoldTimer)
})

defineExpose({
  getPayload,
  setPayload,
  isDirty,
  markSaved,
  markDirty,
  focusError,
  fitSelection,
  showValidation,
  fitError,
  undo,
  redo,
  exportPng,
  exportSvg
})
</script>

<template>
  <div class="editor">
    <div class="toolbar">
      <div class="toolbar-row toolbar-main">
        <template v-if="editable">
          <el-radio-group v-model="tool" size="small">
            <el-radio-button value="select">选择</el-radio-button>
            <el-radio-button value="pan">平移</el-radio-button>
            <el-radio-button value="connect">连路段</el-radio-button>
          </el-radio-group>
          <el-radio-group v-model="tool" size="small" class="node-tools">
            <el-radio-button v-for="type in NODE_TOOLS" :key="type" :value="type">
              <span class="node-dot-mini" :style="{ background: TYPE_COLOR[type] }" />
              {{ TYPE_LABEL[type] }}
            </el-radio-button>
          </el-radio-group>
          <el-button-group class="icon-group">
            <el-button size="small" :disabled="!canUndo" title="撤销 (Ctrl+Z)" @click="undo">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.undo" />
              </svg>
            </el-button>
            <el-button size="small" :disabled="!canRedo" title="重做 (Ctrl+Shift+Z)" @click="redo">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.redo" />
              </svg>
            </el-button>
          </el-button-group>
        </template>
        <el-tag v-else type="info" size="small">预览模式 · 点「编辑」可修改</el-tag>
        <div class="spacer" />
        <span class="hint">滚轮缩放 · 空格/中键平移</span>
      </div>

      <div class="toolbar-row toolbar-secondary">
        <template v-if="editable">
          <el-button-group class="icon-group">
            <el-button size="small" :disabled="selectedCount === 0" title="复制 (Ctrl+C)" @click="copySelection">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.copy" />
              </svg>
            </el-button>
            <el-button size="small" :disabled="!clipboard" title="粘贴 (Ctrl+V)" @click="pasteClipboard">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.paste" />
              </svg>
            </el-button>
            <el-button size="small" :disabled="selectedCount === 0" title="删除 (Delete)" @click="deleteSelected">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.trash" />
              </svg>
            </el-button>
          </el-button-group>
          <el-dropdown @command="handleAlign">
            <el-button size="small" :disabled="selectedNodeIds.length < 2" title="对齐与分布">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.align" />
              </svg>
              对齐
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="left">左对齐</el-dropdown-item>
                <el-dropdown-item command="center-x">水平居中</el-dropdown-item>
                <el-dropdown-item command="right">右对齐</el-dropdown-item>
                <el-dropdown-item command="top">顶对齐</el-dropdown-item>
                <el-dropdown-item command="middle-y">垂直居中</el-dropdown-item>
                <el-dropdown-item command="bottom">底对齐</el-dropdown-item>
                <el-dropdown-item command="distribute-x" divided>水平等距</el-dropdown-item>
                <el-dropdown-item command="distribute-y">垂直等距</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" :disabled="selectedEdgeIds.length !== 2" title="合并两条共享端点的路段" @click="mergeSelectedEdges">
            合并
          </el-button>
          <el-button size="small" :disabled="!hasSelectedOneWay" title="仅单向路段可反转" @click="reverseSelectedEdges">
            反转
          </el-button>
          <el-divider direction="vertical" />
        </template>

        <el-input
          v-model="searchQuery"
          class="search-input"
          size="small"
          placeholder="搜索编号"
          clearable
          @keyup.enter="nextSearch"
        >
          <template #append>
            <el-button :disabled="!searchQuery.trim()" title="Enter 定位下一个命中" @click="nextSearch">
              {{ searchLabel }}
            </el-button>
          </template>
        </el-input>

        <el-popover placement="bottom" trigger="click" width="230">
          <template #reference>
            <el-button size="small" title="显示选项与画布尺寸">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.eye" />
              </svg>
              显示
            </el-button>
          </template>
          <div class="popover-list">
            <el-checkbox v-model="showNodeLabels">节点标签</el-checkbox>
            <el-checkbox v-model="showEdgeLabels">路段标签</el-checkbox>
            <el-checkbox v-model="showGrid">背景网格</el-checkbox>
            <el-checkbox v-model="showTasks">任务流向</el-checkbox>
            <div class="popover-divider" />
            <div class="side-title">画布尺寸</div>
            <div class="size-editor">
              <label>
                宽（米）
                <el-input-number
                  v-model="mapWidth"
                  :min="MIN_SIZE"
                  :max="MAX_SIZE"
                  :disabled="!editable"
                  size="small"
                  @change="applyMapSize"
                />
              </label>
              <label>
                高（米）
                <el-input-number
                  v-model="mapHeight"
                  :min="MIN_SIZE"
                  :max="MAX_SIZE"
                  :disabled="!editable"
                  size="small"
                  @change="applyMapSize"
                />
              </label>
            </div>
          </div>
        </el-popover>

        <el-button size="small" @click="selectAll">全选</el-button>
        <el-button size="small" :disabled="selectedCount === 0" @click="clearSelection">清空</el-button>

        <el-button-group class="zoom-group">
          <el-button size="small" title="缩小" @click="zoomOut">−</el-button>
          <el-button size="small" title="重置为 100%" @click="resetView">{{ zoomPercent }}%</el-button>
          <el-button size="small" title="放大" @click="zoomIn">+</el-button>
        </el-button-group>
        <el-button-group class="pan-buttons">
          <el-button size="small" title="向左移动" @mousedown.prevent="startPanRepeat(-80, 0)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">←</el-button>
          <el-button size="small" title="向右移动" @mousedown.prevent="startPanRepeat(80, 0)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">→</el-button>
          <el-button size="small" title="向上移动" @mousedown.prevent="startPanRepeat(0, -80)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">↑</el-button>
          <el-button size="small" title="向下移动" @mousedown.prevent="startPanRepeat(0, 80)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">↓</el-button>
        </el-button-group>
        <el-button size="small" title="适应窗口" @click="fitView">
          <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path :d="ICONS.fit" />
          </svg>
        </el-button>
        <el-dropdown trigger="click" @command="onToolbarMore">
          <el-button size="small" title="更多操作">
            <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path :d="ICONS.more" stroke-width="2.6" />
            </svg>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="fit-selection" :disabled="selectedCount === 0">适应选中</el-dropdown-item>
              <el-dropdown-item command="fit-error" :disabled="errors.length === 0">适应错误</el-dropdown-item>
              <el-dropdown-item command="png" divided>导出 PNG</el-dropdown-item>
              <el-dropdown-item command="svg">导出 SVG</el-dropdown-item>
              <el-dropdown-item command="help" divided>快捷键与操作</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="workspace">
      <div
        ref="canvasWrap"
        class="canvas-wrap"
        :class="{ 'is-maximized': isMaximized }"
      >
        <div v-if="editable && tool === 'connect' && connectFrom" class="connect-hint">
          已选起点 {{ connectFrom }}，再点一个节点完成连接，Esc 取消
        </div>
        <svg
          ref="svgRef"
          :viewBox="`0 0 ${mapWidth * SCALE} ${mapHeight * SCALE}`"
          class="canvas"
          :class="canvasClass"
          @click="onCanvasClick"
          @wheel.prevent="onWheel"
          @mousedown="onCanvasMouseDown"
          @mousemove="onCanvasMouseMove"
          @mouseup="onCanvasMouseUp"
          @mouseenter="hovering = true"
          @mouseleave="onCanvasLeave"
          @contextmenu.prevent="onCanvasContextMenu"
        >
          <defs>
            <pattern
              id="smallGrid"
              :width="SNAP * SCALE"
              :height="SNAP * SCALE"
              patternUnits="userSpaceOnUse"
            >
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
            <marker
              id="arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerUnits="userSpaceOnUse"
              markerWidth="12"
              markerHeight="12"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#8fa3bd" />
            </marker>
            <marker
              id="arrow-selected"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerUnits="userSpaceOnUse"
              markerWidth="12"
              markerHeight="12"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#1f3b73" />
            </marker>
            <marker
              id="arrow-error"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerUnits="userSpaceOnUse"
              markerWidth="12"
              markerHeight="12"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#f56c6c" />
            </marker>
            <marker
              id="task-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="5"
              markerHeight="5"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#a06a1f" />
            </marker>
          </defs>

          <g ref="contentRef" data-content="true" :transform="transform">
            <rect
              :width="mapWidth * SCALE"
              :height="mapHeight * SCALE"
              :fill="showGrid ? 'url(#grid)' : '#fbfdff'"
            />
            <rect
              class="map-border"
              x="1"
              y="1"
              :width="mapWidth * SCALE - 2"
              :height="mapHeight * SCALE - 2"
              vector-effect="non-scaling-stroke"
              pointer-events="none"
            />
            <g class="map-ticks" pointer-events="none">
              <g v-for="tick in xTicks" :key="`tx-${tick}`">
                <line :x1="tick * SCALE" y1="0" :x2="tick * SCALE" y2="8" />
                <text
                  :x="tick * SCALE"
                  y="19"
                  :text-anchor="tick === 0 ? 'start' : tick === mapWidth ? 'end' : 'middle'"
                >
                  {{ tick }}
                </text>
              </g>
              <g v-for="tick in yTicks" :key="`ty-${tick}`">
                <line x1="0" :y1="tick * SCALE" x2="8" :y2="tick * SCALE" />
                <text
                  x="11"
                  :y="Math.min(tick * SCALE + 4, mapHeight * SCALE - 3)"
                >
                  {{ tick }}
                </text>
              </g>
            </g>

            <g v-if="showTasks && taskFlows.length > 0">
              <path
                v-for="flow in taskFlows"
                :key="`task-${flow.key}`"
                :d="flow.d"
                class="task-flow"
                marker-end="url(#task-arrow)"
              />
              <g v-for="flow in taskFlows.filter((item) => item.count > 1)" :key="`task-count-${flow.key}`">
                <rect
                  :x="flow.labelX - 11"
                  :y="flow.labelY - 8"
                  width="22"
                  height="16"
                  rx="8"
                  class="task-count"
                />
                <text :x="flow.labelX" :y="flow.labelY + 3" text-anchor="middle" class="task-count-text">
                  {{ flow.count }}
                </text>
              </g>
              <g v-for="badge in taskBadges" :key="`badge-${badge.id}`">
                <rect
                  :x="badge.x - 4"
                  :y="badge.y - 10"
                  width="42"
                  height="16"
                  rx="8"
                  class="task-badge"
                />
                <text :x="badge.x + 17" :y="badge.y + 2" text-anchor="middle" class="task-badge-text">
                  {{ badge.from }}/{{ badge.to }}
                </text>
              </g>
            </g>

            <g v-for="edge in edges" :key="edge.id">
              <line
                v-for="lane in edgeLanes(edge)"
                :key="lane.key"
                :x1="lane.x1"
                :y1="lane.y1"
                :x2="lane.x2"
                :y2="lane.y2"
                :stroke="edgeStroke(edge)"
                :stroke-width="isSelectedEdge(edge.id) ? 4 : 3"
                pointer-events="none"
              />
              <line
                v-for="arrow in edgeArrows(edge)"
                :key="arrow.key"
                :x1="arrow.x1"
                :y1="arrow.y1"
                :x2="arrow.x2"
                :y2="arrow.y2"
                :stroke="edgeStroke(edge)"
                :stroke-width="isSelectedEdge(edge.id) ? 4 : 3"
                :marker-end="arrow.markerEnd"
                pointer-events="none"
              />
              <line
                :x1="px(nodeMap.get(edge.from)?.x ?? 0)"
                :y1="px(nodeMap.get(edge.from)?.y ?? 0)"
                :x2="px(nodeMap.get(edge.to)?.x ?? 0)"
                :y2="px(nodeMap.get(edge.to)?.y ?? 0)"
                class="edge-hit"
                @mousedown="onEdgeMouseDown($event, edge)"
                @dblclick="onEdgeDoubleClick($event, edge)"
                @mouseenter="onEdgeEnter($event, edge)"
                @mouseleave="clearHover"
                @contextmenu.prevent.stop="onEdgeContextMenu($event, edge)"
              />
              <line
                v-if="searchHitEdgeIds.has(edge.id)"
                :x1="px(nodeMap.get(edge.from)?.x ?? 0)"
                :y1="px(nodeMap.get(edge.from)?.y ?? 0)"
                :x2="px(nodeMap.get(edge.to)?.x ?? 0)"
                :y2="px(nodeMap.get(edge.to)?.y ?? 0)"
                class="search-hit-edge"
                pointer-events="none"
              />
              <text
                v-if="showEdgeLabels"
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
                v-if="searchHitNodeIds.has(node.id)"
                :cx="px(node.x)"
                :cy="px(node.y)"
                r="14"
                class="search-ring"
                pointer-events="none"
              />
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
                @mouseenter="onNodeEnter($event, node)"
                @mouseleave="clearHover"
                @contextmenu.prevent.stop="onNodeContextMenu($event, node)"
              />
              <text
                v-if="showNodeLabels"
                :x="px(node.x) + 12"
                :y="px(node.y) + 4"
                font-size="11"
                fill="#606266"
                pointer-events="none"
              >
                {{ node.id }}
              </text>
            </g>

            <line
              v-if="guides.x !== undefined"
              :x1="guides.x"
              y1="0"
              :x2="guides.x"
              :y2="mapHeight * SCALE"
              class="guide"
              data-export="ignore"
            />
            <line
              v-if="guides.y !== undefined"
              x1="0"
              :y1="guides.y"
              :x2="mapWidth * SCALE"
              :y2="guides.y"
              class="guide"
              data-export="ignore"
            />
          </g>

          <rect
            v-if="marquee"
            :x="marqueeBox.x"
            :y="marqueeBox.y"
            :width="marqueeBox.width"
            :height="marqueeBox.height"
            class="marquee"
            data-export="ignore"
          />
        </svg>

        <button
          v-if="legendCollapsed"
          class="overlay-toggle legend-toggle"
          type="button"
          @click="toggleLegend"
        >
          图例
        </button>
        <div v-else class="map-legend">
          <div class="legend-head">
            <span>图例</span>
            <button type="button" title="收起图例" @click="toggleLegend">−</button>
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.WORKSTATION }"></span>工位
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.CHARGER }"></span>充电位
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.BUFFER }"></span>缓存位
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.JUNCTION }"></span>路口
          </div>
          <div class="legend-row">
            <span class="legend-symbol">→</span>单车道单向
          </div>
          <div class="legend-row">
            <span class="legend-symbol">↔</span>单车道双向
          </div>
          <div class="legend-row">
            <span class="legend-symbol dual">＝</span>双车道双向
          </div>
          <div class="legend-row">
            <span class="dot error"></span>校验错误
          </div>
          <div v-if="showTasks && tasks && tasks.length > 0" class="legend-row">
            <span class="badge-sample">7/6</span>任务 发/收
          </div>
        </div>

        <div class="scale-bar">
          <div class="scale-line" :style="{ width: `${scaleBarPx}px` }"></div>
          <span>{{ scaleBarMeters }} m</span>
        </div>

        <div class="coords">
          {{ cursorWorld ? `X ${cursorWorld.x.toFixed(1)} m · Y ${cursorWorld.y.toFixed(1)} m` : '鼠标移入画布查看坐标' }}
        </div>

        <button
          v-if="minimapCollapsed"
          class="overlay-toggle minimap-toggle"
          type="button"
          @click="toggleMinimap"
        >
          概览
        </button>
        <div
          v-else
          class="minimap"
          :class="{ 'is-dragging': miniDragging }"
          @mousedown.prevent="startMini"
          @mousemove="moveMini"
          @mouseup="endMini"
          @mouseleave="endMini"
        >
          <button
            class="minimap-collapse"
            type="button"
            title="收起小地图"
            @mousedown.stop
            @click.stop="toggleMinimap"
          >
            −
          </button>
          <svg
            ref="miniRef"
            :width="miniWidth"
            :height="miniHeight"
            :viewBox="`0 0 ${miniWidth} ${miniHeight}`"
          >
            <rect :width="miniWidth" :height="miniHeight" fill="#f7fbff" />
            <line
              v-for="edge in edges"
              :key="`mini-${edge.id}`"
              :x1="(nodeMap.get(edge.from)?.x ?? 0) * SCALE * miniScale"
              :y1="(nodeMap.get(edge.from)?.y ?? 0) * SCALE * miniScale"
              :x2="(nodeMap.get(edge.to)?.x ?? 0) * SCALE * miniScale"
              :y2="(nodeMap.get(edge.to)?.y ?? 0) * SCALE * miniScale"
              stroke="#b6cbe8"
              stroke-width="1"
            />
            <circle
              v-for="node in nodes"
              :key="`mini-${node.id}`"
              :cx="node.x * SCALE * miniScale"
              :cy="node.y * SCALE * miniScale"
              r="1.8"
              :fill="TYPE_COLOR[node.type]"
            />
            <rect
              :x="miniViewport.x"
              :y="miniViewport.y"
              :width="miniViewport.width"
              :height="miniViewport.height"
              class="mini-viewport"
            />
          </svg>
        </div>

        <el-button class="fullscreen-btn" size="small" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </el-button>
      </div>

      <div class="side">
        <div class="side-inner">
          <div class="side-tabs" role="tablist">
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'props' }"
              :aria-selected="sideTab === 'props'"
              @click="sideTab = 'props'"
            >
              属性
            </button>
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'stats' }"
              :aria-selected="sideTab === 'stats'"
              @click="sideTab = 'stats'"
            >
              统计
            </button>
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'errors' }"
              :aria-selected="sideTab === 'errors'"
              @click="sideTab = 'errors'"
            >
              校验
              <span v-if="errors.length > 0" class="tab-count">{{ errors.length }}</span>
            </button>
          </div>

          <div v-show="sideTab === 'props'" class="side-pane">
          <div class="side-block">
            <template v-if="selectedNode">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="编号">
                  <el-input
                    v-model="selectedNode.id"
                    @focus="onNodeIdOriginal(selectedNode)"
                    @change="onNodeIdChange(selectedNode, idEditOriginal)"
                  />
                </el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="selectedNode.type" style="width: 100%">
                    <el-option
                      v-for="(label, key) in TYPE_LABEL"
                      :key="key"
                      :label="label"
                      :value="key"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number
                    v-model="selectedNode.capacity"
                    :min="1"
                    :max="99"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="X(m)">
                  <el-input-number
                    v-model="selectedNode.x"
                    :step="0.5"
                    :min="0"
                    :max="mapWidth"
                    style="width: 100%"
                    @change="onNodeCoordChange(selectedNode)"
                  />
                </el-form-item>
                <el-form-item label="Y(m)">
                  <el-input-number
                    v-model="selectedNode.y"
                    :step="0.5"
                    :min="0"
                    :max="mapHeight"
                    style="width: 100%"
                    @change="onNodeCoordChange(selectedNode)"
                  />
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="selectedEdge">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="编号">
                  <el-input
                    v-model="selectedEdge.id"
                    @focus="idEditOriginal = selectedEdge.id"
                    @change="onEdgeIdChange(selectedEdge, idEditOriginal)"
                  />
                </el-form-item>
                <el-form-item label="方向">
                  <el-select
                    v-model="selectedEdge.direction"
                    style="width: 100%"
                    @change="onEdgeDirectionChange(selectedEdge)"
                  >
                    <el-option label="双向" value="TWO_WAY" />
                    <el-option label="单向" value="ONE_WAY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="朝向">
                  <div class="orient-row">
                    <span class="orient-text">
                      {{
                        selectedEdge.direction === 'ONE_WAY'
                          ? `${selectedEdge.from} → ${selectedEdge.to}`
                          : '双向无固定朝向'
                      }}
                    </span>
                    <el-button
                      size="small"
                      :disabled="selectedEdge.direction !== 'ONE_WAY'"
                      @click="reverseEdge(selectedEdge)"
                    >
                      交换
                    </el-button>
                  </div>
                </el-form-item>
                <el-form-item label="车道">
                  <el-select
                    v-model="selectedEdge.laneMode"
                    style="width: 100%"
                    :disabled="selectedEdge.direction === 'ONE_WAY'"
                    @change="onEdgeLaneChange(selectedEdge)"
                  >
                    <el-option label="单车道" value="SINGLE" />
                    <el-option label="双车道" value="DUAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="长度(m)">
                  <el-input-number
                    :model-value="selectedEdge.length"
                    :step="0.1"
                    :min="0.1"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="速度">
                  <el-input-number
                    v-model="selectedEdge.speed"
                    :step="0.1"
                    :min="0.1"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number
                    v-model="selectedEdge.capacity"
                    :min="1"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="selectedNodeIds.length > 1">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="类型">
                  <el-select v-model="batchNodeType" style="width: 100%">
                    <el-option
                      v-for="(label, key) in TYPE_LABEL"
                      :key="key"
                      :label="label"
                      :value="key"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number v-model="batchNodeCapacity" :min="1" :max="99" style="width: 100%" />
                </el-form-item>
              </el-form>
              <el-button size="small" type="primary" @click="applyBatchNodeType">
                应用到 {{ selectedNodeIds.length }} 个节点
              </el-button>
            </template>
            <template v-else-if="selectedEdgeIds.length > 1">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="方向">
                  <el-select v-model="batchEdgeDirection" style="width: 100%">
                    <el-option label="双向" value="TWO_WAY" />
                    <el-option label="单向" value="ONE_WAY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="车道">
                  <el-select v-model="batchEdgeLaneMode" style="width: 100%">
                    <el-option label="单车道" value="SINGLE" />
                    <el-option label="双车道" value="DUAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="速度">
                  <el-input-number v-model="batchEdgeSpeed" :step="0.1" :min="0.1" style="width: 100%" />
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number v-model="batchEdgeCapacity" :min="1" style="width: 100%" />
                </el-form-item>
              </el-form>
              <el-button size="small" type="primary" @click="applyBatchEdge">
                应用到 {{ selectedEdgeIds.length }} 条路段
              </el-button>
            </template>
            <div v-else-if="selectedCount > 0" class="muted">
              已选 {{ selectedNodeIds.length }} 个节点、{{ selectedEdgeIds.length }} 条路段
            </div>
            <div v-else class="muted">选中节点或路段后可编辑属性</div>
          </div>
          </div>

          <div v-show="sideTab === 'stats'" class="side-pane">
          <div class="side-block">
            <div class="side-title">统计</div>
            <div class="stat-row"><span>节点</span><b>{{ stats.nodes }}</b></div>
            <div class="stat-row"><span>路段</span><b>{{ stats.edges }}</b></div>
            <div class="stat-row"><span>总长度</span><b>{{ stats.length.toFixed(1) }} m</b></div>
            <div class="stat-row"><span>充电位</span><b>{{ stats.chargers }}</b></div>
            <div class="stat-row"><span>选中</span><b>{{ selectedCount }}</b></div>
          </div>
          </div>

          <div v-show="sideTab === 'errors'" class="side-pane">
          <div class="side-block">
            <div class="side-title">
              校验
              <el-tag v-if="errors.length > 0" type="danger" size="small">{{ errors.length }}</el-tag>
            </div>
            <div v-if="errors.length === 0" class="muted">未发现问题</div>
            <div v-else class="error-list">
              <div
                v-for="(error, index) in errors"
                :key="index"
                class="error-row"
                @click="focusError(error)"
              >
                <el-tag size="small" type="danger">{{ error.code }}</el-tag>
                <span>{{ error.message }}</span>
              </div>
            </div>
          </div>
          </div>
        </div>
      </div>
    </div>

    <teleport to="body">
      <div
        v-if="hoverInfo && hover"
        class="map-tooltip"
        :style="{ left: `${hover.x + 14}px`, top: `${hover.y + 14}px` }"
      >
        <div class="tooltip-title">{{ hoverInfo.title }}</div>
        <div v-for="row in hoverInfo.rows" :key="row" class="tooltip-row">{{ row }}</div>
      </div>
    </teleport>

    <div
      v-if="contextMenu"
      class="context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
    >
      <template v-if="contextMenu.kind === 'canvas'">
        <button v-for="type in NODE_TOOLS" :key="type" @click="contextAddNode(type)">
          新建{{ TYPE_LABEL[type] }}
        </button>
      </template>
      <template v-else-if="contextMenu.kind === 'edge'">
        <button @click="contextSplit">拆分路段</button>
        <button @click="contextReverse">反转方向</button>
        <button @click="contextDelete">删除路段</button>
      </template>
      <template v-else>
        <button @click="contextDelete">删除节点</button>
        <button @click="fitSelection">适应选中</button>
      </template>
    </div>

    <el-dialog v-model="helpVisible" title="快捷键与操作" width="560px">
      <table class="help-table">
        <tbody>
          <tr><td>滚轮</td><td>以指针为中心缩放</td></tr>
          <tr><td>`空格` + 左键 / 中键</td><td>平移画布</td></tr>
          <tr><td>`Ctrl/Cmd+A`</td><td>全选</td></tr>
          <tr><td>`Ctrl/Cmd+C` / `V` / `D`</td><td>复制 / 粘贴 / 再制</td></tr>
          <tr><td>`Ctrl/Cmd+Z`</td><td>撤销</td></tr>
          <tr><td>`Ctrl/Cmd+Shift+Z` / `Ctrl+Y`</td><td>重做</td></tr>
          <tr><td>`Ctrl/Cmd+S`</td><td>保存</td></tr>
          <tr><td>`Delete` / `Backspace`</td><td>删除选中</td></tr>
          <tr><td>`Esc`</td><td>清空选择 / 退出页面内全屏 / 关闭菜单</td></tr>
          <tr><td>方向键</td><td>选中节点移动 0.5 米</td></tr>
          <tr><td>`Shift` + 方向键</td><td>选中节点移动 2.5 米</td></tr>
          <tr><td>`Shift` + 拖动</td><td>锁定水平 / 垂直</td></tr>
          <tr><td>`?`</td><td>打开本帮助</td></tr>
        </tbody>
      </table>
    </el-dialog>
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
  --map-cursor-default: default;
  --map-cursor-grab: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 24 24' fill='none' stroke-linecap='round' stroke-linejoin='round'%3E%3Cg stroke='%23ffffff' stroke-width='5'%3E%3Cpath d='M18 11V6a2 2 0 0 0-4 0v5'/%3E%3Cpath d='M14 10V4a2 2 0 0 0-4 0v6'/%3E%3Cpath d='M10 10.5V6a2 2 0 0 0-4 0v8'/%3E%3Cpath d='M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15'/%3E%3C/g%3E%3Cg stroke='%23303133' stroke-width='2.4'%3E%3Cpath d='M18 11V6a2 2 0 0 0-4 0v5'/%3E%3Cpath d='M14 10V4a2 2 0 0 0-4 0v6'/%3E%3Cpath d='M10 10.5V6a2 2 0 0 0-4 0v8'/%3E%3Cpath d='M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15'/%3E%3C/g%3E%3C/svg%3E") 16 16, grab;
  --map-cursor-grabbing: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 24 24' fill='none' stroke-linecap='round' stroke-linejoin='round'%3E%3Cg stroke='%23ffffff' stroke-width='5'%3E%3Cpath d='M8 12V9a2 2 0 0 1 4 0v3'/%3E%3Cpath d='M12 12V8a2 2 0 0 1 4 0v4'/%3E%3Cpath d='M16 12V9a2 2 0 0 1 4 0v7a6 6 0 0 1-6 6h-1.5a6 6 0 0 1-4.9-2.55l-2.2-3.1a2 2 0 0 1 3.2-2.4L11 16'/%3E%3C/g%3E%3Cg stroke='%23303133' stroke-width='2.4'%3E%3Cpath d='M8 12V9a2 2 0 0 1 4 0v3'/%3E%3Cpath d='M12 12V8a2 2 0 0 1 4 0v4'/%3E%3Cpath d='M16 12V9a2 2 0 0 1 4 0v7a6 6 0 0 1-6 6h-1.5a6 6 0 0 1-4.9-2.55l-2.2-3.1a2 2 0 0 1 3.2-2.4L11 16'/%3E%3C/g%3E%3C/svg%3E") 16 16, grabbing;
  --map-cursor-pointer: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3E%3Cpath d='M9 4 L25 18 L18 19 L21 28 L17 30 L13 21 L9 25 Z' fill='%23303133' stroke='%23ffffff' stroke-width='2.5' stroke-linejoin='round'/%3E%3C/svg%3E") 9 4, pointer;
  --map-cursor-crosshair: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3E%3Cg stroke='%23ffffff' stroke-width='5' stroke-linecap='round'%3E%3Cpath d='M16 5v22M5 16h22'/%3E%3C/g%3E%3Cg stroke='%23303133' stroke-width='2.2' stroke-linecap='round'%3E%3Cpath d='M16 5v22M5 16h22'/%3E%3C/g%3E%3Ccircle cx='16' cy='16' r='1.6' fill='%23303133'/%3E%3C/svg%3E") 16 16, crosshair;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.toolbar-main {
  flex-wrap: wrap;
}

.toolbar-secondary {
  flex-wrap: wrap;
  padding-top: 6px;
  border-top: 1px solid #eef2f7;
}

.spacer {
  flex: 1;
}

.hint {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.tool-icon {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
  vertical-align: -2px;
}

.icon-group :deep(.el-button) {
  padding-left: 7px;
  padding-right: 7px;
}

.node-tools :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.node-dot-mini {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.connect-hint {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 6;
  font-size: 12px;
  color: #a06a1f;
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 999px;
  padding: 4px 10px;
  white-space: nowrap;
  pointer-events: none;
}

.search-input {
  width: 140px;
}

.popover-divider {
  height: 1px;
  background: #eef2f7;
  margin: 2px 0;
}

.popover-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.size-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.size-editor label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #606266;
  gap: 8px;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  gap: 10px;
  flex: 1;
  min-height: 0;
}

.canvas-wrap {
  position: relative;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdff;
  min-height: 0;
}

.canvas-wrap.is-maximized {
  position: fixed;
  inset: 0;
  z-index: 2600;
  border: none;
  border-radius: 0;
  background: #fbfdff;
}

.canvas-wrap:fullscreen {
  background: #fbfdff;
}

.canvas {
  width: 100%;
  height: 100%;
  display: block;
  cursor: var(--map-cursor-default);
  user-select: none;
}

.canvas.is-drawing {
  cursor: var(--map-cursor-crosshair);
}

.canvas.is-drawing .node-dot,
.canvas.is-drawing .edge-hit {
  cursor: var(--map-cursor-crosshair);
}

.canvas.can-pan {
  cursor: var(--map-cursor-grab);
}

.canvas.is-pan-tool {
  cursor: var(--map-cursor-grab);
}

.canvas.is-pan-tool .node-dot,
.canvas.is-pan-tool .edge-hit {
  cursor: var(--map-cursor-grab);
}

.canvas.is-readonly .node-dot,
.canvas.is-readonly .edge-hit {
  cursor: var(--map-cursor-pointer);
}

.canvas.is-readonly.is-pan-tool .node-dot,
.canvas.is-readonly.is-pan-tool .edge-hit {
  cursor: var(--map-cursor-grab);
}

.canvas.is-panning,
.canvas.is-dragging {
  cursor: var(--map-cursor-grabbing);
}

.node-dot {
  cursor: var(--map-cursor-grab);
}

.canvas.is-dragging .node-dot {
  cursor: var(--map-cursor-grabbing);
}

.edge-hit {
  stroke: rgba(0, 0, 0, 0.001);
  stroke-width: 14;
  cursor: var(--map-cursor-pointer);
}

.marquee {
  fill: rgba(31, 59, 115, 0.08);
  stroke: #1f3b73;
  stroke-width: 1;
  stroke-dasharray: 4 3;
  pointer-events: none;
}

.guide {
  stroke: #e6a23c;
  stroke-width: 1;
  stroke-dasharray: 4 3;
  pointer-events: none;
}

.map-border {
  fill: none;
  stroke: #7f9cc0;
  stroke-width: 1.5;
}

.map-ticks line {
  stroke: #c5d3e4;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.map-ticks text {
  font-size: 9px;
  fill: #8fa3bd;
}

.task-flow {
  fill: none;
  stroke: #e6a23c;
  stroke-width: 1.4;
  stroke-dasharray: 5 4;
  opacity: 0.55;
  pointer-events: none;
}

.task-badge {
  fill: #fdf6ec;
  stroke: #e6a23c;
  stroke-width: 1;
  pointer-events: none;
}

.task-badge-text {
  font-size: 10px;
  fill: #a06a1f;
  pointer-events: none;
}

.task-count {
  fill: #ffffff;
  stroke: #e6a23c;
  stroke-width: 1;
  pointer-events: none;
}

.task-count-text {
  font-size: 10px;
  font-weight: 600;
  fill: #a06a1f;
  pointer-events: none;
}

.search-ring {
  fill: rgba(230, 162, 60, 0.15);
  stroke: #e6a23c;
  stroke-width: 2;
  stroke-dasharray: 3 2;
}

.search-hit-edge {
  stroke: rgba(230, 162, 60, 0.5);
  stroke-width: 8;
  stroke-linecap: round;
}

.map-legend {
  position: absolute;
  left: 12px;
  bottom: 38px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  color: #606266;
  pointer-events: auto;
}

.legend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 2px;
  font-weight: 600;
  color: #1f3b73;
}

.legend-head button {
  border: none;
  background: transparent;
  color: #8fa3bd;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
}

.overlay-toggle {
  position: absolute;
  left: 12px;
  bottom: 38px;
  border: 1px solid #e4e9f0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: #5c7392;
  font-size: 11px;
  line-height: 1;
  padding: 5px 10px;
  cursor: pointer;
}

.overlay-toggle:hover {
  color: #1f3b73;
  border-color: #b6cbe8;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot.error {
  background: #f56c6c;
}

.legend-line {
  width: 18px;
  height: 0;
  border-top: 2px solid #8fa3bd;
  display: inline-block;
}

.legend-line.one-way {
  border-top: 2px dashed #8fa3bd;
}

.legend-symbol {
  width: 18px;
  text-align: center;
  color: #5c7392;
  font-weight: 700;
  line-height: 12px;
}

.legend-symbol.dual {
  letter-spacing: -1px;
}

.badge-sample {
  font-size: 9px;
  color: #a06a1f;
  background: #fdf6ec;
  border: 1px solid #e6a23c;
  border-radius: 6px;
  padding: 0 3px;
  line-height: 12px;
}

.scale-bar {
  position: absolute;
  left: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #606266;
  background: rgba(255, 255, 255, 0.9);
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}

.scale-line {
  height: 6px;
  border-left: 1px solid #606266;
  border-right: 1px solid #606266;
  border-bottom: 1px solid #606266;
}

.coords {
  position: absolute;
  right: 12px;
  bottom: 12px;
  font-size: 11px;
  color: #606266;
  background: rgba(255, 255, 255, 0.9);
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}

.minimap {
  position: absolute;
  right: 12px;
  top: 44px;
  border: 1px solid #dbe4ef;
  border-radius: 6px;
  overflow: hidden;
  background: #f7fbff;
  cursor: var(--map-cursor-grab);
}

.minimap-collapse {
  position: absolute;
  right: 4px;
  top: 4px;
  z-index: 2;
  width: 18px;
  height: 18px;
  line-height: 1;
  border: 1px solid #dbe4ef;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.92);
  color: #5c7392;
  cursor: pointer;
  padding: 0;
}

.minimap-toggle {
  top: 44px;
  right: 12px;
  left: auto;
  bottom: auto;
}

.minimap.is-dragging {
  cursor: var(--map-cursor-grabbing);
}

.mini-viewport {
  fill: rgba(31, 59, 115, 0.08);
  stroke: #1f3b73;
  stroke-width: 1;
  pointer-events: none;
}

.fullscreen-btn {
  position: absolute;
  right: 12px;
  top: 12px;
}

.side {
  position: relative;
  min-height: 0;
}

.side-inner {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.side-tabs {
  display: flex;
  gap: 3px;
  padding: 3px;
  background: #eef2f7;
  border-radius: 8px;
  flex-shrink: 0;
}

.side-tabs button {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #5c7392;
  font-size: 12px;
  padding: 5px 6px;
  border-radius: 6px;
  cursor: pointer;
}

.side-tabs button:hover {
  color: #1f3b73;
}

.side-tabs button.active {
  background: #fff;
  color: #1f3b73;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(31, 59, 115, 0.12);
}

.tab-count {
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 500;
}

.side-pane {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
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
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
  color: #606266;
}

.orient-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.orient-text {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
}

.error-list {
  max-height: 240px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.error-row {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  color: #606266;
  padding: 4px 0;
  line-height: 1.4;
  cursor: pointer;
}

.error-row:hover {
  color: #1f3b73;
}

.muted {
  font-size: 12px;
  color: #909399;
}

@media (max-width: 1600px) {
  .pan-buttons {
    display: none;
  }
}

@media (max-width: 1400px) {
  .hint {
    display: none;
  }

  .search-input {
    width: 110px;
  }

  .toolbar-secondary :deep(.el-button) {
    padding-left: 8px;
    padding-right: 8px;
  }
}

.map-tooltip {
  position: fixed;
  z-index: 3000;
  background: rgba(32, 41, 56, 0.94);
  color: #fff;
  border-radius: 6px;
  padding: 6px 9px;
  font-size: 12px;
  pointer-events: none;
  max-width: 260px;
}

.tooltip-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.tooltip-row {
  color: #dbe4ef;
}

.context-menu {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  box-shadow: 0 6px 20px rgba(31, 59, 115, 0.16);
  padding: 4px;
  display: flex;
  flex-direction: column;
  min-width: 128px;
}

.context-menu button {
  border: none;
  background: transparent;
  text-align: left;
  padding: 6px 10px;
  font-size: 12px;
  color: #303133;
  cursor: pointer;
  border-radius: 4px;
}

.context-menu button:hover {
  background: #f2f6fc;
  color: #1f3b73;
}

.help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.help-table td {
  padding: 6px 8px;
  border-bottom: 1px solid #f0f2f5;
  color: #606266;
}

.help-table td:first-child {
  width: 42%;
  color: #303133;
  font-weight: 600;
}
</style>
