import type { SceneQuality } from './quality.ts'

/** 场景配色，统一在深色车间底上调饱和度，避免和浅色面板互相干扰。 */
export const COLORS = {
  background: 0x0b1526,
  ground: 0x0f1a2b,
  gridLine: 0x1b2c42,
  road: 0x26364d,
  roadEdge: 0x54759c,
  laneDash: 0x7f9dc0,
  path: 0xffd666
} as const

export const NODE_COLORS: Record<string, number> = {
  CHARGER: 0x67c23a,
  WORKSTATION: 0x409eff,
  BUFFER: 0xe6a23c,
  JUNCTION: 0x8fa3bd
}

export const STATUS_COLORS: Record<string, number> = {
  MOVING: 0x409eff,
  WAITING: 0xf56c6c,
  LOADING: 0xe6a23c,
  IDLE: 0x8fa3bd,
  CHARGING: 0x67c23a,
  DONE: 0x5c6b80
}

/** 车辆与车道尺寸（米），保持和 4–10 米的节点间距同一量级。 */
export const AGV_SIZE = {
  length: 1.5,
  width: 1.0,
  chassisHeight: 0.3,
  chassisY: 0.48,
  wheelRadius: 0.22,
  wheelWidth: 0.16,
  cargoY: 0.88,
  beaconY: 0.75
} as const

export const LANE = {
  width: 1.3,
  offset: 0.7,
  textureTileMeters: 2,
  /** 路口凸包沿每条边回缩的距离，避免直角转角的凸包退化成一条线。 */
  junctionSetback: 0.35,
  /** 相邻边方向延伸小于该值的节点按直行处理，不铺路口。 */
  junctionMinReach: 0.6
} as const

/** 场景基座高度：路面略高于地面，车辆轮胎贴路面。 */
export const ROAD_Y = 0.02
export const AGV_Y = 0.02

export interface QualitySettings {
  pixelRatio: number
  shadows: boolean
  shadowMapSize: number
  environment: boolean
  bloom: boolean
}

export const QUALITY_SETTINGS: Record<SceneQuality, QualitySettings> = {
  low: { pixelRatio: 1, shadows: false, shadowMapSize: 512, environment: false, bloom: false },
  medium: { pixelRatio: 1.5, shadows: true, shadowMapSize: 1024, environment: true, bloom: false },
  high: { pixelRatio: 2, shadows: true, shadowMapSize: 2048, environment: true, bloom: true }
}
