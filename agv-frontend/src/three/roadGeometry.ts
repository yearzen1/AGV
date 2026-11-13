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
}

export interface RoadGeometry {
  ribbons: MeshArrays
  dividers: MeshArrays
  arrows: MeshArrays
  stats: RoadGeometryStats
}

function emptyArrays(): MeshArrays {
  return { positions: [], uvs: [], normals: [], indices: [] }
}

/**
 * 生成路面、分隔线和方向箭头的顶点数组，不创建 three 对象，也不读取 DOM。
 *
 * 这样几何可以在 Node 里单测：绕序、索引、车道中心和箭头方向都直接断言，
 * 不用等到浏览器截图才发现路面消失或两条车道叠在一起。
 */
export function buildRoadGeometry(net: RoadNetPayload): RoadGeometry {
  const ribbons = emptyArrays()
  const dividers = emptyArrays()
  const arrows = emptyArrays()
  const stats: RoadGeometryStats = { ribbons: 0, dividers: 0, arrows: 0 }
  const nodeMap = new Map(net.nodes.map((node) => [node.id, node]))

  for (const edge of net.edges) {
    const from = nodeMap.get(edge.from)
    const to = nodeMap.get(edge.to)
    if (!from || !to) continue
    const start: LanePoint = { x: from.x, y: from.y }
    const end: LanePoint = { x: to.x, y: to.y }
    const length = Math.hypot(end.x - start.x, end.y - start.y)
    if (length < 0.01) continue

    const lanes = lanesForEdge(edge, start, end)
    if (lanes.length === 2) {
      for (const lane of lanes) {
        if (pushQuad(ribbons, lane.start, lane.end, LANE.width, lane.offset, ROAD_Y)) {
          stats.ribbons += 1
        }
      }
      if (pushQuad(dividers, start, end, 0.12, 0, ROAD_Y + 0.012)) stats.dividers += 1
      for (const lane of lanes) {
        stats.arrows += addArrowsAlong(arrows, lane.start, lane.end, lane.offset)
      }
      continue
    }

    if (pushQuad(ribbons, start, end, LANE.width, 0, ROAD_Y)) stats.ribbons += 1
    if (edge.direction === 'ONE_WAY') {
      stats.arrows += addArrowsAlong(arrows, start, end)
    } else {
      // 单车道双向：一条路面无法同时画两个方向，用前后错开的反向箭头表示。
      if (pushQuad(dividers, start, end, 0.12, 0, ROAD_Y + 0.012)) stats.dividers += 1
      if (addArrow(arrows, start, end, 1 / 3)) stats.arrows += 1
      if (addArrow(arrows, end, start, 1 / 3)) stats.arrows += 1
    }
  }

  return { ribbons, dividers, arrows, stats }
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
