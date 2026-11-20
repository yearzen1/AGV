import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type {
  NetEdge,
  NetNode,
  NetZone,
  RoadNetPayload,
  ValidationError,
  ZoneType
} from '@/api/roadnet'
import {
  ZONE_DEFAULT_HEIGHT,
  ZONE_DEFAULT_WIDTH,
  ZONE_TYPES,
  nextZoneId,
  normalizeZones,
  snapZoneValue,
  zoneContains,
  zoneRectFromPoints,
  zoneTypeColor,
  zoneTypeLabel
} from './editorZones'

export interface EditorProps {
  initial: RoadNetPayload
  errors?: ValidationError[]
  readonly?: boolean
  tasks?: { id: number; from: string; to: string }[]
  name?: string
}

export interface EditorEmit {
  (e: 'change'): void
  (e: 'save'): void
}

export function useRoadNetEditor(props: EditorProps, emit: EditorEmit) {
const SCALE = 20 // 1 米 = 20 像素
const SNAP = 0.5 // 米
const MIN_SCALE = 0.4
const MAX_SCALE = 4
const MIN_SIZE = 10
const MAX_SIZE = 200
const HISTORY_LIMIT = 60

type Tool = 'select' | 'pan' | 'WORKSTATION' | 'CHARGER' | 'BUFFER' | 'JUNCTION' | 'connect' | 'zone'

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
const zones = ref<NetZone[]>([])

const svgRef = ref<SVGSVGElement | null>(null)
const contentRef = ref<SVGGElement | null>(null)
const canvasWrap = ref<HTMLDivElement | null>(null)
const miniRef = ref<SVGSVGElement | null>(null)

const tool = ref<Tool>('select')
const selectedNodeIds = ref<string[]>([])
const selectedEdgeIds = ref<string[]>([])
const selectedZoneId = ref<string | null>(null)
const connectFrom = ref<string | null>(null)
const zoneType = ref<ZoneType>('OTHER')
const showZones = ref(true)
const zoneDraft = ref<{ start: { x: number; y: number }; end: { x: number; y: number } } | null>(
  null
)
const zoneDrag = ref<{
  mode: 'move' | 'resize'
  handle: string
  start: { x: number; y: number }
  original: NetZone
  before: Snapshot
  moved: boolean
  historyPushed: boolean
} | null>(null)

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
const sideTab = ref<'props' | 'zones' | 'stats' | 'errors'>('props')
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
const tasks = computed(() => props.tasks ?? [])
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
const selectedZone = computed(() => {
  if (!selectedZoneId.value) return null
  return zones.value.find((zone) => zone.id === selectedZoneId.value) ?? null
})
const selectedNodes = computed(() =>
  nodes.value.filter((n) => selectedNodeIds.value.includes(n.id))
)
const hasNodeEdgeSelection = computed(
  () => selectedNodeIds.value.length + selectedEdgeIds.value.length > 0
)
const selectedCount = computed(
  () => selectedNodeIds.value.length + selectedEdgeIds.value.length + (selectedZone.value ? 1 : 0)
)
const hasSelectedOneWay = computed(() =>
  edges.value.some((e) => selectedEdgeIds.value.includes(e.id) && e.direction === 'ONE_WAY')
)

const nodeMap = computed(() => new Map(nodes.value.map((n) => [n.id, n])))

const stats = computed(() => ({
  nodes: nodes.value.length,
  edges: edges.value.length,
  zones: zones.value.length,
  length: edges.value.reduce((sum, e) => sum + (Number(e.length) || 0), 0),
  chargers: nodes.value.filter((n) => n.type === 'CHARGER').length
}))

const zoneLegend = computed(() => {
  const present = new Set(zones.value.map((zone) => zone.type))
  const known = ZONE_TYPES.filter((type) => present.has(type))
  const unknown = [...present].filter((type) => !ZONE_TYPES.includes(type as ZoneType))
  return [...known, ...unknown].map((type) => ({
    type,
    label: zoneTypeLabel(type),
    color: zoneTypeColor(type)
  }))
})

const zoneDraftRect = computed(() => {
  const draft = zoneDraft.value
  if (!draft) return null
  const dx = Math.abs(draft.end.x - draft.start.x)
  const dy = Math.abs(draft.end.y - draft.start.y)
  if (dx < 0.5 && dy < 0.5) {
    const width = Math.min(ZONE_DEFAULT_WIDTH, mapWidth.value)
    const height = Math.min(ZONE_DEFAULT_HEIGHT, mapHeight.value)
    const x = Math.max(
      0,
      Math.min(mapWidth.value - width, snapZoneValue(draft.start.x - width / 2))
    )
    const y = Math.max(
      0,
      Math.min(mapHeight.value - height, snapZoneValue(draft.start.y - height / 2))
    )
    return { x, y, width, height }
  }
  return zoneRectFromPoints(draft.start, draft.end, mapWidth.value, mapHeight.value)
})

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
  zones: NetZone[]
  width: number
  height: number
  nodeIds: string[]
  edgeIds: string[]
  zoneId: string | null
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
  return {
    nodes: [],
    edges: [],
    zones: [],
    width: 50,
    height: 34,
    nodeIds: [],
    edgeIds: [],
    zoneId: null
  }
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

function cloneZones(list: NetZone[]) {
  return list.map((zone) => ({ ...zone }))
}

function capture(): Snapshot {
  return {
    nodes: cloneNodes(nodes.value),
    edges: cloneEdges(edges.value),
    zones: cloneZones(zones.value),
    width: mapWidth.value,
    height: mapHeight.value,
    nodeIds: [...selectedNodeIds.value],
    edgeIds: [...selectedEdgeIds.value],
    zoneId: selectedZoneId.value
  }
}

function signature(snapshot = capture()) {
  return JSON.stringify({
    nodes: snapshot.nodes,
    edges: snapshot.edges,
    zones: snapshot.zones,
    width: snapshot.width,
    height: snapshot.height
  })
}

function applySnapshot(snapshot: Snapshot) {
  window.clearTimeout(historyTimer)
  historyLock = true
  nodes.value = cloneNodes(snapshot.nodes)
  edges.value = cloneEdges(snapshot.edges)
  zones.value = cloneZones(snapshot.zones)
  mapWidth.value = snapshot.width
  mapHeight.value = snapshot.height
  selectedNodeIds.value = [...snapshot.nodeIds]
  selectedEdgeIds.value = [...snapshot.edgeIds]
  selectedZoneId.value = snapshot.zoneId
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
  if (tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable) return true
  if (tag !== 'INPUT') return false
  const type = (el as HTMLInputElement).type
  return !['button', 'checkbox', 'radio', 'range', 'color', 'file', 'submit', 'reset'].includes(type)
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
  if (selectedZone.value) {
    focusZone(selectedZone.value)
    return
  }
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
  selectedZoneId.value = null
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
  selectedZoneId.value = null
  connectFrom.value = null
}

function selectAll() {
  selectedNodeIds.value = nodes.value.map((node) => node.id)
  selectedEdgeIds.value = edges.value.map((edge) => edge.id)
  selectedZoneId.value = null
}

function toggleNodeSelection(id: string) {
  selectedZoneId.value = null
  selectedNodeIds.value = isSelectedNode(id)
    ? selectedNodeIds.value.filter((item) => item !== id)
    : [...selectedNodeIds.value, id]
}

function toggleEdgeSelection(id: string) {
  selectedZoneId.value = null
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
  if (type === 'select' || type === 'connect' || type === 'pan' || type === 'zone') {
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
  selectedZoneId.value = null
}

function addNodeAt(type: Tool, point: { x: number; y: number }) {
  if (!editable.value) return
  const x = clamp(Math.round(point.x / SNAP) * SNAP, 0, mapWidth.value)
  const y = clamp(Math.round(point.y / SNAP) * SNAP, 0, mapHeight.value)
  const node: NetNode = { id: nextNodeId(type), x, y, type: type as NetNode['type'], capacity: 1 }
  nodes.value.push(node)
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []
  selectedZoneId.value = null
}

function onNodeClick(event: MouseEvent, node: NetNode) {
  if (tool.value !== 'connect' || !editable.value) return
  event.stopPropagation()
  if (!connectFrom.value) {
    connectFrom.value = node.id
    selectedNodeIds.value = [node.id]
    selectedEdgeIds.value = []
    selectedZoneId.value = null
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
  selectedZoneId.value = null
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
    selectedZoneId.value = null
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
    selectedZoneId.value = null
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
  if (tool.value === 'zone') {
    if (event.button !== 0 || !editable.value) return
    event.preventDefault()
    const point = toWorldRaw(event)
    zoneDraft.value = {
      start: { x: point.x, y: point.y },
      end: { x: point.x, y: point.y }
    }
    return
  }
  if (tool.value !== 'select' || event.button !== 0) return
  const point = clientToView(event)
  marquee.value = { x1: point.x, y1: point.y, x2: point.x, y2: point.y }
  marqueeAdditive.value = event.ctrlKey || event.metaKey
}

function onCanvasMouseMove(event: MouseEvent) {
  if (zoneDraft.value) {
    const point = toWorldRaw(event)
    zoneDraft.value.end = { x: point.x, y: point.y }
    return
  }
  if (zoneDrag.value) {
    updateZoneDrag(event)
    return
  }
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
    if (!marqueeAdditive.value) {
      const worldX = (box.x + box.width / 2 - view.tx) / view.scale / SCALE
      const worldY = (box.y + box.height / 2 - view.ty) / view.scale / SCALE
      const hit = showZones.value
        ? [...zones.value].reverse().find((zone) => zoneContains(zone, worldX, worldY))
        : null
      if (hit) {
        selectedZoneId.value = hit.id
        selectedNodeIds.value = []
        selectedEdgeIds.value = []
        sideTab.value = 'zones'
      } else {
        clearSelection()
      }
    }
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

  selectedZoneId.value = null
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
  if (zoneDraft.value) {
    commitZoneDraft()
    return
  }
  if (zoneDrag.value) {
    finishZoneDrag()
    return
  }
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
  if (zoneDraft.value) {
    zoneDraft.value = null
    return
  }
  onCanvasMouseUp()
}

const ZONE_HANDLES = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const

function zoneHandleRect(zone: NetZone, handle: string) {
  const x = zone.x * SCALE
  const y = zone.y * SCALE
  const width = zone.width * SCALE
  const height = zone.height * SCALE
  const cx = handle.includes('w') ? x : handle.includes('e') ? x + width : x + width / 2
  const cy = handle.includes('n') ? y : handle.includes('s') ? y + height : y + height / 2
  const size = 9
  return { x: cx - size / 2, y: cy - size / 2, width: size, height: size }
}

function zoneHandleCursor(handle: string) {
  if (handle === 'nw' || handle === 'se') return 'nwse-resize'
  if (handle === 'ne' || handle === 'sw') return 'nesw-resize'
  if (handle === 'n' || handle === 's') return 'ns-resize'
  return 'ew-resize'
}

const selectedZoneHandleRects = computed(() => {
  const zone = selectedZone.value
  if (!zone || !editable.value) return []
  return ZONE_HANDLES.map((handle) => ({
    handle,
    ...zoneHandleRect(zone, handle),
    cursor: zoneHandleCursor(handle)
  }))
})

function commitZoneDraft() {
  const rect = zoneDraftRect.value
  if (!rect) return
  const id = nextZoneId(zones.value)
  const index = zones.value.filter((zone) => zone.type === zoneType.value).length + 1
  const zone: NetZone = {
    id,
    name: `${zoneTypeLabel(zoneType.value)} ${index}`,
    type: zoneType.value,
    ...rect
  }
  zones.value.push(zone)
  selectedZoneId.value = zone.id
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
  sideTab.value = 'zones'
  zoneDraft.value = null
  suppressClick.value = true
}

function onZoneMouseDown(event: MouseEvent, zone: NetZone) {
  if (tool.value !== 'select' || event.button !== 0) return
  event.preventDefault()
  event.stopPropagation()
  selectedZoneId.value = zone.id
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
  sideTab.value = 'zones'
  if (!editable.value) {
    notifyPreviewReadonly()
    return
  }
  beginZoneDrag(event, zone, 'move', '')
}

function beginZoneDrag(
  event: MouseEvent,
  zone: NetZone,
  mode: 'move' | 'resize',
  handle: string
) {
  if (!editable.value) return
  event.preventDefault()
  event.stopPropagation()
  pauseHistory()
  zoneDrag.value = {
    mode,
    handle,
    start: toWorldRaw(event),
    original: { ...zone },
    before: lastSnapshot,
    moved: false,
    historyPushed: false
  }
}

function beginZoneResize(event: MouseEvent, zone: NetZone, handle: string) {
  if (tool.value !== 'select') return
  selectedZoneId.value = zone.id
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
  beginZoneDrag(event, zone, 'resize', handle)
}

function beginZoneResizeSelected(event: MouseEvent, handle: string) {
  if (selectedZone.value) beginZoneResize(event, selectedZone.value, handle)
}

function updateZoneDrag(event: MouseEvent) {
  const state = zoneDrag.value
  if (!state || !editable.value) return
  const zone = zones.value.find((item) => item.id === state.original.id)
  if (!zone) return
  const point = toWorldRaw(event)
  const original = state.original
  let x = original.x
  let y = original.y
  let width = original.width
  let height = original.height

  if (state.mode === 'move') {
    const dx = clamp(
      snapZoneValue(point.x - state.start.x),
      -original.x,
      mapWidth.value - original.x - original.width
    )
    const dy = clamp(
      snapZoneValue(point.y - state.start.y),
      -original.y,
      mapHeight.value - original.y - original.height
    )
    x = original.x + dx
    y = original.y + dy
  } else {
    let x1 = original.x
    let y1 = original.y
    let x2 = original.x + original.width
    let y2 = original.y + original.height
    const px = snapZoneValue(point.x)
    const py = snapZoneValue(point.y)
    if (state.handle.includes('w')) x1 = clamp(px, 0, x2 - 1)
    if (state.handle.includes('e')) x2 = clamp(px, x1 + 1, mapWidth.value)
    if (state.handle.includes('n')) y1 = clamp(py, 0, y2 - 1)
    if (state.handle.includes('s')) y2 = clamp(py, y1 + 1, mapHeight.value)
    x = x1
    y = y1
    width = x2 - x1
    height = y2 - y1
  }

  const changed =
    Math.abs(x - original.x) > 1e-9 ||
    Math.abs(y - original.y) > 1e-9 ||
    Math.abs(width - original.width) > 1e-9 ||
    Math.abs(height - original.height) > 1e-9
  if (!changed) return
  zone.x = x
  zone.y = y
  zone.width = width
  zone.height = height
  state.moved = true
  if (!state.historyPushed) {
    past.value.push(state.before)
    if (past.value.length > HISTORY_LIMIT) past.value.shift()
    future.value = []
    state.historyPushed = true
  }
}

function finishZoneDrag() {
  const state = zoneDrag.value
  if (!state) return
  if (state.moved) lastSnapshot = capture()
  resumeHistory()
  zoneDrag.value = null
}

function deleteZone(zone: NetZone) {
  if (!editable.value) return
  zones.value = zones.value.filter((item) => item.id !== zone.id)
  if (selectedZoneId.value === zone.id) selectedZoneId.value = null
}

function clampZone(zone: NetZone) {
  zone.width = clamp(snapZoneValue(Number(zone.width) || 1), 1, mapWidth.value)
  zone.height = clamp(snapZoneValue(Number(zone.height) || 1), 1, mapHeight.value)
  zone.x = clamp(snapZoneValue(Number(zone.x) || 0), 0, mapWidth.value - zone.width)
  zone.y = clamp(snapZoneValue(Number(zone.y) || 0), 0, mapHeight.value - zone.height)
}

function focusZone(zone: NetZone) {
  selectedZoneId.value = zone.id
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
  sideTab.value = 'zones'
  const viewWidth = mapWidth.value * SCALE
  const viewHeight = mapHeight.value * SCALE
  const margin = 50
  const minX = zone.x * SCALE - margin
  const maxX = (zone.x + zone.width) * SCALE + margin
  const minY = zone.y * SCALE - margin
  const maxY = (zone.y + zone.height) * SCALE + margin
  const next = clamp(
    Math.min(viewWidth / Math.max(maxX - minX, 1), viewHeight / Math.max(maxY - minY, 1)),
    MIN_SCALE,
    2
  )
  view.scale = next
  view.tx = viewWidth / 2 - ((minX + maxX) / 2) * next
  view.ty = viewHeight / 2 - ((minY + maxY) / 2) * next
}

function startZoneTool() {
  if (!editable.value) return
  tool.value = 'zone'
  sideTab.value = 'zones'
}

async function deleteSelected() {
  if (!editable.value || selectedCount.value === 0) return
  if (selectedZone.value) {
    deleteZone(selectedZone.value)
    return
  }
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
  if (selectedZone.value) {
    ElMessage.info('区域注释不能复制，请在「区域」页签里新建或调整')
    return
  }
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
  selectedZoneId.value = null
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
  selectedZoneId.value = null
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
  selectedZoneId.value = null
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
  zones.value.forEach((zone) => clampZone(zone))
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
  selectedZoneId.value = null
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
  selectedZoneId.value = null
  contextMenu.value = { x: event.clientX, y: event.clientY, kind: 'node', id: node.id }
}

function onEdgeContextMenu(event: MouseEvent, edge: NetEdge) {
  if (!editable.value) return
  event.preventDefault()
  event.stopPropagation()
  selectedEdgeIds.value = [edge.id]
  selectedNodeIds.value = []
  selectedZoneId.value = null
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
    if (zoneDraft.value) {
      zoneDraft.value = null
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
  zones.value = normalizeZones(payload.zones, mapWidth.value, mapHeight.value)
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
    zones: cloneZones(zones.value),
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
  [nodes, edges, zones, mapWidth, mapHeight],
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
  [selectedNodeIds, selectedEdgeIds, selectedZoneId],
  () => {
    if (selectedZoneId.value) sideTab.value = 'zones'
    else if (selectedCount.value > 0) sideTab.value = 'props'
  },
  { deep: true }
)

watch(tool, (value) => {
  if (value === 'zone') sideTab.value = 'zones'
})

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

const expose = {
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
}

return {
  SCALE,
  SNAP,
  MIN_SCALE,
  MAX_SCALE,
  MIN_SIZE,
  MAX_SIZE,
  HISTORY_LIMIT,
  NODE_TOOLS,
  TYPE_LABEL,
  TYPE_COLOR,
  ICONS,
  PREFIX,
  mapWidth,
  mapHeight,
  nodes,
  edges,
  zones,
  svgRef,
  contentRef,
  canvasWrap,
  miniRef,
  tool,
  selectedNodeIds,
  selectedEdgeIds,
  selectedZoneId,
  connectFrom,
  zoneType,
  showZones,
  zoneDraft,
  zoneDrag,
  view,
  spacePressed,
  panning,
  hovering,
  suppressClick,
  marquee,
  marqueeAdditive,
  miniDragging,
  isFullscreen,
  showNodeLabels,
  showEdgeLabels,
  showGrid,
  showTasks,
  readUiPref,
  writeUiPref,
  narrowWindow,
  minimapCollapsed,
  legendCollapsed,
  sideTab,
  isMaximized,
  toggleMinimap,
  toggleLegend,
  cursorWorld,
  hover,
  guides,
  searchQuery,
  searchIndex,
  helpVisible,
  contextMenu,
  batchNodeType,
  batchNodeCapacity,
  batchEdgeDirection,
  batchEdgeSpeed,
  batchEdgeCapacity,
  batchEdgeLaneMode,
  editable,
  tasks,
  errors,
  errorNodeIds,
  errorEdgeIds,
  selectedNode,
  selectedEdge,
  selectedZone,
  selectedNodes,
  hasNodeEdgeSelection,
  selectedCount,
  hasSelectedOneWay,
  nodeMap,
  stats,
  zoneLegend,
  zoneDraftRect,
  transform,
  zoomPercent,
  canvasClass,
  tickStep,
  xTicks,
  yTicks,
  scaleBarMeters,
  scaleBarPx,
  marqueeBox,
  hoverInfo,
  searchResults,
  searchHitNodeIds,
  searchHitEdgeIds,
  searchLabel,
  miniWidth,
  miniHeight,
  miniScale,
  miniViewport,
  taskFlows,
  taskBadges,
  past,
  future,
  lastSnapshot,
  historyLock,
  historyTimer,
  baseline,
  canUndo,
  canRedo,
  dragState,
  panState,
  clipboard,
  emptySnapshot,
  clamp,
  sizeValue,
  cloneNodes,
  cloneEdges,
  cloneZones,
  capture,
  signature,
  applySnapshot,
  commitHistory,
  flushHistory,
  pauseHistory,
  resumeHistory,
  undo,
  redo,
  refreshEdgeLengths,
  refreshAllEdgeLengths,
  px,
  LANE_OFFSET_PX,
  edgeLanes,
  arrowAt,
  edgeArrows,
  isEditableTarget,
  clientToView,
  toWorldRaw,
  toWorld,
  setScale,
  zoomIn,
  zoomOut,
  resetView,
  panBy,
  panHoldTimer,
  startPanRepeat,
  stopPanRepeat,
  previewNoticeAt,
  notifyPreviewReadonly,
  fitNodes,
  fitView,
  fitSelection,
  errorNodes,
  fitError,
  focusError,
  onWheel,
  nextNodeId,
  nextEdgeId,
  isSelectedNode,
  isSelectedEdge,
  clearSelection,
  selectAll,
  toggleNodeSelection,
  toggleEdgeSelection,
  movingNodeIds,
  onCanvasClick,
  addNodeAt,
  onNodeClick,
  onNodeMouseDown,
  onEdgeMouseDown,
  beginDrag,
  updateDrag,
  onCanvasMouseDown,
  onCanvasMouseMove,
  finalizeMarquee,
  onCanvasMouseUp,
  onCanvasLeave,
  ZONE_HANDLES,
  zoneHandleRect,
  zoneHandleCursor,
  selectedZoneHandleRects,
  commitZoneDraft,
  onZoneMouseDown,
  beginZoneDrag,
  beginZoneResize,
  beginZoneResizeSelected,
  updateZoneDrag,
  finishZoneDrag,
  deleteZone,
  clampZone,
  focusZone,
  startZoneTool,
  deleteSelected,
  copySelection,
  pasteClipboard,
  duplicateSelection,
  handleAlign,
  applyBatchNodeType,
  applyBatchEdge,
  onEdgeDirectionChange,
  onEdgeLaneChange,
  reverseEdge,
  reverseSelectedEdges,
  splitEdgeAt,
  onEdgeDoubleClick,
  mergeSelectedEdges,
  nudge,
  onNodeCoordChange,
  onNodeIdChange,
  onEdgeIdChange,
  onNodeIdOriginal,
  idEditOriginal,
  applyMapSize,
  onNodeEnter,
  onEdgeEnter,
  clearHover,
  edgeStroke,
  edgeMarker,
  edgeMid,
  nextSearch,
  onCanvasContextMenu,
  onNodeContextMenu,
  onEdgeContextMenu,
  closeContextMenu,
  contextAddNode,
  contextDelete,
  contextSplit,
  contextReverse,
  onMiniPointer,
  startMini,
  moveMini,
  endMini,
  toggleFullscreen,
  onFullscreenChange,
  download,
  buildExportSvg,
  safeName,
  exportSvg,
  exportPng,
  handleExport,
  onToolbarMore,
  onKeyDown,
  onKeyUp,
  onWindowClick,
  loadPayload,
  setPayload,
  getPayload,
  isDirty,
  markSaved,
  markDirty,
  showValidation,
  expose
}
}
