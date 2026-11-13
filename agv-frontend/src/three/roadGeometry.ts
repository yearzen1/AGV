import type { NetEdge, NetNode, RoadNetPayload } from '@/api/roadnet'
import { LANE, ROAD_Y } from './constants.ts'
import { lanesForEdge, type LanePoint } from './lane.ts'

export interface MeshArrays {
  positions: number[]
  uvs: number[]
  normals: number[]
  indices: number[]
}

export interface RoadGeometryStats {
  ribbons: number
  dividers: number
  arrows: number
  junctions: number
}

export interface RoadGeometry {
  ribbons: MeshArrays
  dividers: MeshArrays
  arrows: MeshArrays
  junctions: MeshArrays
  stats: RoadGeometryStats
}

/** 车道断面在节点处的记录：中心点、以及从节点指向边内部的单位向量。 */
interface LaneEnd {
  x: number
  y: number
  ux: number
  uy: number
}

const APRON_HEIGHT = ROAD_Y + 0.004
const MIN_APRON_AREA = 0.2

function emptyArrays(): MeshArrays {
  return { positions: [], uvs: [], normals: [], indices: [] }
}

/**
 * 生成路面、分隔线、方向箭头和路口铺装的顶点数组，不创建 three 对象，也不读取 DOM。
 *
 * 这样几何可以在 Node 里单测：绕序、索引、车道中心、箭头方向和路口覆盖范围都直接断言，
 * 不用等到浏览器截图才发现路面消失、两条车道叠在一起或者路口画成 X。
 */
export function buildRoadGeometry(net: RoadNetPayload): RoadGeometry {
  const ribbons = emptyArrays()
  const dividers = emptyArrays()
  const arrows = emptyArrays()
  const junctions = emptyArrays()
  const stats: RoadGeometryStats = { ribbons: 0, dividers: 0, arrows: 0, junctions: 0 }
  const nodeMap = new Map(net.nodes.map((node) => [node.id, node]))

  const ends = new Map<string, LaneEnd[]>()
  const addEnd = (nodeId: string, x: number, y: number, ux: number, uy: number) => {
    const list = ends.get(nodeId) ?? []
    list.push({ x, y, ux, uy })
    ends.set(nodeId, list)
  }

  for (const edge of net.edges) {
    const from = nodeMap.get(edge.from)
    const to = nodeMap.get(edge.to)
    if (!from || !to) continue
    const length = Math.hypot(to.x - from.x, to.y - from.y)
    if (length < 0.01) continue

    const start: LanePoint = { x: from.x, y: from.y }
    const end: LanePoint = { x: to.x, y: to.y }
    for (const lane of lanesForEdge(edge, start, end)) {
      const laneLength = Math.hypot(lane.end.x - lane.start.x, lane.end.y - lane.start.y)
      if (laneLength < 0.01) continue
      const tx = (lane.end.x - lane.start.x) / laneLength
      const ty = (lane.end.y - lane.start.y) / laneLength
      const nx = -ty
      const ny = tx
      // 双车道返回两条反向车道，起点分别对应 from 和 to
      const startsAtFrom = lane.start.x === from.x && lane.start.y === from.y
      addEnd(
        startsAtFrom ? edge.from : edge.to,
        lane.start.x + nx * lane.offset,
        lane.start.y + ny * lane.offset,
        tx,
        ty
      )
      addEnd(
        startsAtFrom ? edge.to : edge.from,
        lane.end.x + nx * lane.offset,
        lane.end.y + ny * lane.offset,
        -tx,
        -ty
      )
    }
  }

  // 每个节点的车道断面 → 路口铺装的凸包。回缩 SETBACK 保证凸包在两条边方向上都有厚度，
  // 否则直角转角的凸包会退化成一条线，盖不住交叉区。
  const hulls = new Map<string, LanePoint[]>()
  for (const [nodeId, list] of ends) {
    const points: LanePoint[] = []
    const half = LANE.width / 2
    for (const item of list) {
      const nx = -item.uy
      const ny = item.ux
      const backX = item.x + item.ux * LANE.junctionSetback
      const backY = item.y + item.uy * LANE.junctionSetback
      points.push(
        { x: item.x + nx * half, y: item.y + ny * half },
        { x: item.x - nx * half, y: item.y - ny * half },
        { x: backX + nx * half, y: backY + ny * half },
        { x: backX - nx * half, y: backY - ny * half }
      )
    }
    const hull = hullForTriangles(points)
    if (hull.length >= 3) hulls.set(nodeId, hull)
  }

  /** 铺装在某个边方向上离节点的最远距离，用来裁断分隔线和箭头。 */
  const reachAt = (nodeId: string, ux: number, uy: number): number => {
    const hull = hulls.get(nodeId)
    const node = nodeMap.get(nodeId)
    if (!hull || !node) return 0
    let reach = 0
    for (const point of hull) {
      reach = Math.max(reach, (point.x - node.x) * ux + (point.y - node.y) * uy)
    }
    return reach
  }

  for (const [nodeId, hull] of hulls) {
    // 直行节点（各相邻边方向上的延伸都很小）不需要铺装，保持一条连续路面
    let reach = 0
    for (const item of ends.get(nodeId) ?? []) {
      reach = Math.max(reach, reachAt(nodeId, item.ux, item.uy))
    }
    if (reach < LANE.junctionMinReach) continue
    if (pushPolygon(junctions, hull, APRON_HEIGHT)) stats.junctions += 1
  }

  for (const edge of net.edges) {
    const from = nodeMap.get(edge.from)
    const to = nodeMap.get(edge.to)
    if (!from || !to) continue
    const start: LanePoint = { x: from.x, y: from.y }
    const end: LanePoint = { x: to.x, y: to.y }
    const length = Math.hypot(end.x - start.x, end.y - start.y)
    if (length < 0.01) continue

    const ux = (end.x - start.x) / length
    const uy = (end.y - start.y) / length
    const reachFrom = reachAt(edge.from, ux, uy)
    const reachTo = reachAt(edge.to, -ux, -uy)
    // 标线止于铺装边缘，车道带仍然铺满整条边，由铺装盖住交叉
    const trimStart: LanePoint = { x: start.x + ux * reachFrom, y: start.y + uy * reachFrom }
    const trimEnd: LanePoint = { x: end.x - ux * reachTo, y: end.y - uy * reachTo }
    const keepMarkings = length - reachFrom - reachTo > 0.3

    const lanes = lanesForEdge(edge, start, end)
    for (const lane of lanes) {
      if (pushQuad(ribbons, lane.start, lane.end, LANE.width, lane.offset, ROAD_Y)) {
        stats.ribbons += 1
      }
    }

    if (lanes.length === 2) {
      if (keepMarkings && pushQuad(dividers, trimStart, trimEnd, 0.12, 0, ROAD_Y + 0.012)) {
        stats.dividers += 1
      }
      if (keepMarkings) {
        // lanes[0] 是 from→to，lanes[1] 是 to→from，箭头方向跟着各自的车道
        lanes.forEach((lane, index) => {
          const arrowStart = index === 0 ? trimStart : trimEnd
          const arrowEnd = index === 0 ? trimEnd : trimStart
          stats.arrows += addArrowsAlong(arrows, arrowStart, arrowEnd, lane.offset)
        })
      }
      continue
    }

    if (edge.direction === 'ONE_WAY') {
      if (keepMarkings) stats.arrows += addArrowsAlong(arrows, trimStart, trimEnd)
    } else {
      // 单车道双向：一条路面无法同时画两个方向，用前后错开的反向箭头表示。
      if (keepMarkings) {
        if (pushQuad(dividers, trimStart, trimEnd, 0.12, 0, ROAD_Y + 0.012)) stats.dividers += 1
        if (addArrow(arrows, trimStart, trimEnd, 1 / 3)) stats.arrows += 1
        if (addArrow(arrows, trimEnd, trimStart, 1 / 3)) stats.arrows += 1
      }
    }
  }

  return { ribbons, dividers, arrows, junctions, stats }
}

/** 二维凸包（单调链），返回绕序能让扇形三角化后面朝上的顶点序列。 */
function hullForTriangles(points: LanePoint[]): LanePoint[] {
  if (points.length < 3) return []
  const sorted = [...points].sort((a, b) => a.x - b.x || a.y - b.y)
  const cross = (o: LanePoint, a: LanePoint, b: LanePoint) =>
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

  const lower: LanePoint[] = []
  for (const point of sorted) {
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], point) <= 0) {
      lower.pop()
    }
    lower.push(point)
  }

  const upper: LanePoint[] = []
  for (let index = sorted.length - 1; index >= 0; index -= 1) {
    const point = sorted[index]
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], point) <= 0) {
      upper.pop()
    }
    upper.push(point)
  }

  lower.pop()
  upper.pop()
  const hull = lower.concat(upper)
  if (hull.length < 3) return []
  // 二维正面积在三维里朝下，翻转后扇形三角化才能得到向上的法线
  if (signedArea(hull) > 0) hull.reverse()
  return hull
}

function signedArea(points: LanePoint[]): number {
  let area = 0
  for (let index = 0; index < points.length; index += 1) {
    const current = points[index]
    const next = points[(index + 1) % points.length]
    area += current.x * next.y - next.x * current.y
  }
  return area / 2
}

/** 把凸包三角化成扇形铺装；面积太小说明这条边只是直行穿过节点。 */
function pushPolygon(target: MeshArrays, hull: LanePoint[], height: number): boolean {
  if (hull.length < 3) return false
  if (Math.abs(signedArea(hull)) < MIN_APRON_AREA) return false

  const offsetIndex = target.positions.length / 3
  for (const point of hull) {
    target.positions.push(point.x, height, point.y)
    target.normals.push(0, 1, 0)
    // 取路面纹理的中间列，避开纹理两侧的路缘线
    target.uvs.push(0.5, (point.x + point.y) / LANE.textureTileMeters)
  }
  for (let index = 1; index < hull.length - 1; index += 1) {
    target.indices.push(offsetIndex, offsetIndex + index, offsetIndex + index + 1)
  }
  return true
}

/** 沿 from→end 铺一条带；offset 是前进方向右侧的偏移，单位米。 */
function pushQuad(
  target: MeshArrays,
  start: LanePoint,
  end: LanePoint,
  width: number,
  offset: number,
  height: number
): boolean {
  const dx = end.x - start.x
  const dy = end.y - start.y
  const length = Math.hypot(dx, dy)
  if (length < 0.01) return false

  const dirX = dx / length
  const dirY = dy / length
  // 二维坐标的 y 向下，前进方向的右手边是 (-dirY, dirX)。
  const rightX = -dirY
  const rightY = dirX
  const half = width / 2
  const startX = start.x + rightX * offset
  const startY = start.y + rightY * offset
  const endX = end.x + rightX * offset
  const endY = end.y + rightY * offset

  const offsetIndex = target.positions.length / 3
  target.positions.push(
    startX - rightX * half, height, startY - rightY * half,
    startX + rightX * half, height, startY + rightY * half,
    endX - rightX * half, height, endY - rightY * half,
    endX + rightX * half, height, endY + rightY * half
  )
  const v = length / LANE.textureTileMeters
  target.uvs.push(0, 0, 1, 0, 0, v, 1, v)
  target.normals.push(0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0)
  target.indices.push(
    offsetIndex,
    offsetIndex + 1,
    offsetIndex + 2,
    offsetIndex + 2,
    offsetIndex + 1,
    offsetIndex + 3
  )
  return true
}

/** 沿车道每隔 4 米放一个箭头；短于一个车位时至少放一个。 */
function addArrowsAlong(
  target: MeshArrays,
  from: LanePoint,
  to: LanePoint,
  offset = 0,
  spacing = 4
): number {
  const length = Math.hypot(to.x - from.x, to.y - from.y)
  if (length < 0.5) return 0
  const fractions: number[] = []
  for (let distance = spacing / 2; distance <= length - 0.6; distance += spacing) {
    fractions.push(distance / length)
  }
  if (fractions.length === 0 && length >= 1.4) fractions.push(0.5)

  let count = 0
  for (const fraction of fractions) {
    if (addArrow(target, from, to, fraction, offset)) count += 1
  }
  return count
}

/** 在 from→to 车道（可带右偏移）的 fraction 处放一个指向 to 的箭头。 */
function addArrow(
  target: MeshArrays,
  from: LanePoint,
  to: LanePoint,
  fraction: number,
  offset = 0,
  width = 0.42,
  length = 1.5
): boolean {
  const dx = to.x - from.x
  const dy = to.y - from.y
  const laneLength = Math.hypot(dx, dy)
  if (laneLength < 0.01) return false

  const dirX = dx / laneLength
  const dirY = dy / laneLength
  const rightX = -dirY
  const rightY = dirX
  const centerX = from.x + dx * fraction + rightX * offset
  const centerY = from.y + dy * fraction + rightY * offset
  const halfLength = length / 2
  const halfWidth = width / 2
  const y = ROAD_Y + 0.008

  const offsetIndex = target.positions.length / 3
  target.positions.push(
    centerX - dirX * halfLength - rightX * halfWidth,
    y,
    centerY - dirY * halfLength - rightY * halfWidth,
    centerX - dirX * halfLength + rightX * halfWidth,
    y,
    centerY - dirY * halfLength + rightY * halfWidth,
    centerX + dirX * halfLength - rightX * halfWidth,
    y,
    centerY + dirY * halfLength - rightY * halfWidth,
    centerX + dirX * halfLength + rightX * halfWidth,
    y,
    centerY + dirY * halfLength + rightY * halfWidth
  )
  target.uvs.push(0, 0, 1, 0, 0, 1, 1, 1)
  target.normals.push(0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0)
  target.indices.push(
    offsetIndex,
    offsetIndex + 1,
    offsetIndex + 2,
    offsetIndex + 2,
    offsetIndex + 1,
    offsetIndex + 3
  )
  return true
}
