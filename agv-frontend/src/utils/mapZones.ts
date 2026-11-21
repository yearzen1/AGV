import type { NetZone } from '@/api/roadnet'
import { zoneTypeColor, zoneTypeLabel } from './zoneStyles.ts'

export interface MapZone {
  id: string
  name: string
  type: string
  color: string
  x: number
  y: number
  width: number
  height: number
  /** 区域太小时不画名称，避免文字溢出到相邻区域。 */
  showLabel: boolean
}

export interface ZoneExtents {
  minX: number
  minY: number
  maxX: number
  maxY: number
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
 * 把路网载荷里的区域注释转成任务集选点地图可直接绘制的矩形。
 * `unit` 用来判断标签是否放得下，通常传地图 viewBox 的长边。
 */
export function mapZones(zones: NetZone[] | undefined, unit: number): MapZone[] {
  const safeUnit = Number.isFinite(unit) && unit > 0 ? unit : 1
  const result: MapZone[] = []

  for (const zone of zones ?? []) {
    if (!isUsableZone(zone)) continue
    const name = zone.name?.trim() || zoneTypeLabel(zone.type)
    result.push({
      id: zone.id,
      name,
      type: zone.type,
      color: zoneTypeColor(zone.type),
      x: zone.x,
      y: zone.y,
      width: zone.width,
      height: zone.height,
      showLabel: zone.width >= safeUnit * 0.12 && zone.height >= safeUnit * 0.07
    })
  }

  return result
}

/** 所有区域的外接范围；没有可用区域时返回 null。 */
export function zoneExtents(zones: NetZone[] | undefined): ZoneExtents | null {
  let result: ZoneExtents | null = null

  for (const zone of zones ?? []) {
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
