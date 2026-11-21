import type { NetZone } from '@/api/roadnet'
import { ROAD_Y } from './constants.ts'
import { zoneTypeColor, zoneTypeColorValue } from '../utils/zoneStyles.ts'

/** 区域色块略高于路面、低于方向箭头（+8 毫米）和分隔线（+12 毫米）。 */
export const ZONE_FILL_Y = ROAD_Y + 0.006
export const ZONE_BORDER_Y = ROAD_Y + 0.007

export interface ZoneFillArrays {
  positions: number[]
  /** 每个顶点一个 `0xrrggbb` 数值，渲染层再转成 three 的颜色空间。 */
  colors: number[]
  normals: number[]
  indices: number[]
}

export interface ZoneBorderArrays {
  positions: number[]
  /** 每个线段端点一个 `0xrrggbb` 数值。 */
  colors: number[]
}

export interface ZoneLabelAnchor {
  id: string
  name: string
  color: string
  x: number
  y: number
}

export interface ZoneExtents {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export interface ZoneGeometry {
  fills: ZoneFillArrays
  borders: ZoneBorderArrays
  labels: ZoneLabelAnchor[]
  stats: { zones: number }
}

function isUsableZone(zone: NetZone) {
  return (
    Number.isFinite(zone.x) &&
    Number.isFinite(zone.y) &&
    Number.isFinite(zone.width) &&
    Number.isFinite(zone.height) &&
    zone.width > 0 &&
    zone.height > 0
  )
}

/**
 * 把区域矩形转成色块三角形、边框线段和名称锚点，不创建 three 对象，也不读取 DOM。
 *
 * 色块与边框始终按矩形原样生成，不做重叠布尔合并：区域允许重叠，合并既会改变用户
 * 画出的形状，也会让重叠区域难以单独辨认。
 */
export function buildZoneGeometry(zones: NetZone[] = []): ZoneGeometry {
  const fills: ZoneFillArrays = { positions: [], colors: [], normals: [], indices: [] }
  const borders: ZoneBorderArrays = { positions: [], colors: [] }
  const labels: ZoneLabelAnchor[] = []
  let count = 0

  for (const zone of zones) {
    if (!isUsableZone(zone)) continue

    const x1 = zone.x
    const x2 = zone.x + zone.width
    const y1 = zone.y
    const y2 = zone.y + zone.height
    const color = zoneTypeColorValue(zone.type)
    const base = fills.positions.length / 3

    // 顺序 A(x1,y1)、B(x2,y1)、C(x2,y2)、D(x1,y2)；索引 A-C-B、A-D-C 让法线朝上。
    fills.positions.push(
      x1, ZONE_FILL_Y, y1,
      x2, ZONE_FILL_Y, y1,
      x2, ZONE_FILL_Y, y2,
      x1, ZONE_FILL_Y, y2
    )
    fills.colors.push(color, color, color, color)
    fills.normals.push(0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0)
    fills.indices.push(base, base + 2, base + 1, base, base + 3, base + 2)

    const corners = [
      [x1, y1],
      [x2, y1],
      [x2, y2],
      [x1, y2]
    ]
    for (let index = 0; index < corners.length; index++) {
      const start = corners[index]
      const end = corners[(index + 1) % corners.length]
      borders.positions.push(
        start[0], ZONE_BORDER_Y, start[1],
        end[0], ZONE_BORDER_Y, end[1]
      )
      borders.colors.push(color, color)
    }

    labels.push({
      id: zone.id,
      name: zone.name,
      color: zoneTypeColor(zone.type),
      x: (x1 + x2) / 2,
      y: (y1 + y2) / 2
    })
    count += 1
  }

  return { fills, borders, labels, stats: { zones: count } }
}

/** 区域的外接范围；没有可用区域时返回 null，交给节点包围盒兜底。 */
export function zoneExtents(zones: NetZone[] = []): ZoneExtents | null {
  let result: ZoneExtents | null = null

  for (const zone of zones) {
    if (!isUsableZone(zone)) continue
    const maxX = zone.x + zone.width
    const maxY = zone.y + zone.height
    if (!result) {
      result = { minX: zone.x, minY: zone.y, maxX, maxY }
      continue
    }
    result.minX = Math.min(result.minX, zone.x)
    result.minY = Math.min(result.minY, zone.y)
    result.maxX = Math.max(result.maxX, maxX)
    result.maxY = Math.max(result.maxY, maxY)
  }

  return result
}
