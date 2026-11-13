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
