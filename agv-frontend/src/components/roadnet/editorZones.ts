import type { NetZone, ZoneType } from '@/api/roadnet'

export const ZONE_MIN_SIZE = 1
export const ZONE_DEFAULT_WIDTH = 6
export const ZONE_DEFAULT_HEIGHT = 4

export const ZONE_TYPE_LABEL: Record<string, string> = {
  CHARGING: '充电区',
  RECEIVING: '进货区',
  UNLOADING: '卸货区',
  RACK: '货架区',
  PARKING: '停车区',
  OTHER: '其他'
}

export const ZONE_TYPE_COLOR: Record<string, string> = {
  CHARGING: '#67c23a',
  RECEIVING: '#409eff',
  UNLOADING: '#e6a23c',
  RACK: '#b37feb',
  PARKING: '#909399',
  OTHER: '#8fa3bd'
}

export const ZONE_TYPES: ZoneType[] = [
  'CHARGING',
  'RECEIVING',
  'UNLOADING',
  'RACK',
  'PARKING',
  'OTHER'
]

export function zoneTypeLabel(type: string | undefined | null) {
  return ZONE_TYPE_LABEL[type ?? ''] ?? ZONE_TYPE_LABEL.OTHER
}

export function zoneTypeColor(type: string | undefined | null) {
  return ZONE_TYPE_COLOR[type ?? ''] ?? ZONE_TYPE_COLOR.OTHER
}

export function nextZoneId(zones: Array<{ id: string }>) {
  let max = 0
  for (const zone of zones) {
    const match = /^Z(\d+)$/i.exec(zone.id)
    if (match) max = Math.max(max, Number(match[1]))
  }
  return `Z${max + 1}`
}

export function zoneContains(zone: NetZone, x: number, y: number) {
  return x >= zone.x && x <= zone.x + zone.width && y >= zone.y && y <= zone.y + zone.height
}

export function snapZoneValue(value: number) {
  return Math.round(value * 2) / 2
}

/** 把拖拽的两个角点变成合法矩形，结果已吸附并夹在画布内。 */
export function zoneRectFromPoints(
  start: { x: number; y: number },
  end: { x: number; y: number },
  mapWidth: number,
  mapHeight: number
) {
  const x1 = Math.min(start.x, end.x)
  const y1 = Math.min(start.y, end.y)
  const x2 = Math.max(start.x, end.x)
  const y2 = Math.max(start.y, end.y)
  const x = Math.max(0, Math.min(mapWidth - ZONE_MIN_SIZE, snapZoneValue(x1)))
  const y = Math.max(0, Math.min(mapHeight - ZONE_MIN_SIZE, snapZoneValue(y1)))
  const width = Math.max(
    ZONE_MIN_SIZE,
    Math.min(mapWidth - x, snapZoneValue(x2) - x)
  )
  const height = Math.max(
    ZONE_MIN_SIZE,
    Math.min(mapHeight - y, snapZoneValue(y2) - y)
  )
  return { x, y, width, height }
}

function finite(value: unknown, fallback: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/**
 * 归一化区域数组：旧数据缺 zones、缺字段、编号重复、坐标越界都不能让整张图打不开。
 * 只保证能显示和编辑，不承担路网校验。
 */
export function normalizeZones(
  raw: unknown,
  mapWidth: number,
  mapHeight: number
): NetZone[] {
  if (!Array.isArray(raw)) return []
  const used = new Set<string>()
  const result: NetZone[] = []

  raw.forEach((item, index) => {
    if (!item || typeof item !== 'object') return
    const source = item as Record<string, unknown>
    const type = typeof source.type === 'string' && source.type ? source.type : 'OTHER'
    let id = typeof source.id === 'string' ? source.id.trim() : ''
    if (!id || used.has(id)) id = nextZoneId([...used].map((value) => ({ id: value })))
    while (used.has(id)) id = nextZoneId([...used].map((value) => ({ id: value })))
    used.add(id)

    const name =
      typeof source.name === 'string' && source.name.trim()
        ? source.name.trim()
        : `${zoneTypeLabel(type)} ${index + 1}`
    const x = Math.max(0, Math.min(mapWidth - ZONE_MIN_SIZE, snapZoneValue(finite(source.x, 0))))
    const y = Math.max(0, Math.min(mapHeight - ZONE_MIN_SIZE, snapZoneValue(finite(source.y, 0))))
    const width = Math.max(
      ZONE_MIN_SIZE,
      Math.min(mapWidth - x, snapZoneValue(finite(source.width, ZONE_DEFAULT_WIDTH)))
    )
    const height = Math.max(
      ZONE_MIN_SIZE,
      Math.min(mapHeight - y, snapZoneValue(finite(source.height, ZONE_DEFAULT_HEIGHT)))
    )

    result.push({ id, name, type, x, y, width, height })
  })

  return result
}
