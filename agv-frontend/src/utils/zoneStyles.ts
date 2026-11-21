/**
 * 区域类型的显示元数据：2D 编辑器、导出图和 3D 场景共用同一份名称与颜色。
 * 这里只描述「怎么显示」，不承担校验或算法语义。
 */
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

export function zoneTypeLabel(type: string | undefined | null) {
  return ZONE_TYPE_LABEL[type ?? ''] ?? ZONE_TYPE_LABEL.OTHER
}

export function zoneTypeColor(type: string | undefined | null) {
  return ZONE_TYPE_COLOR[type ?? ''] ?? ZONE_TYPE_COLOR.OTHER
}

/** 把 `#rrggbb` 转成 three 的数值颜色。 */
export function zoneTypeColorValue(type: string | undefined | null) {
  return Number.parseInt(zoneTypeColor(type).slice(1), 16)
}
