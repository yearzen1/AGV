import { LANE } from './constants.ts'

export interface LanePoint {
  x: number
  y: number
}

export interface LaneEdge {
  id: string
  from: string
  to: string
  direction: 'ONE_WAY' | 'TWO_WAY'
  laneMode?: 'SINGLE' | 'DUAL'
}

export interface LaneSpec {
  start: LanePoint
  end: LanePoint
  /** 沿前进方向右侧的偏移量，单位米；0 表示走中心线。 */
  offset: number
}

export function isDualLane(edge: LaneEdge): boolean {
  return edge.direction === 'TWO_WAY' && edge.laneMode === 'DUAL'
}

export function lanesForEdge(edge: LaneEdge, from: LanePoint, to: LanePoint): LaneSpec[] {
  if (!isDualLane(edge)) {
    return [{ start: from, end: to, offset: 0 }]
  }
  // 偏移量相对各自的前进方向取右，因此两条车道会分居物理中心线两侧。
  return [
    { start: from, end: to, offset: LANE.offset },
    { start: to, end: from, offset: LANE.offset }
  ]
}

export function laneOffsetForArc(
  edges: Map<string, LaneEdge>,
  arcId: string | null | undefined
): number {
  if (!arcId) return 0
  const edge = edges.get(arcId.replace(/[+-]$/, ''))
  if (!edge || !isDualLane(edge)) return 0
  return LANE.offset
}

export interface Pose2D {
  x: number
  y: number
  /** 朝向，单位度，0 指向 +x。 */
  heading: number
}

/**
 * 把车辆从弧中心线移到「前进方向右侧」。二维 y 向下，
 * 所以右手边是 (-sinθ, cosθ)，和路网铺装使用同一套规则。
 */
export function offsetAlongRight(pose: Pose2D, offset: number): { x: number; y: number } {
  if (!offset) return { x: pose.x, y: pose.y }
  const radians = (pose.heading * Math.PI) / 180
  return {
    x: pose.x - Math.sin(radians) * offset,
    y: pose.y + Math.cos(radians) * offset
  }
}
