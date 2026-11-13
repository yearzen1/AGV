/** 三维场景里一台车的可渲染姿态，字段与 `AgvView` 对应。 */
export interface AgvPose {
  id: string
  x: number
  y: number
  heading: number
  status: string
  arcId?: string | null
}

/** 一帧仿真快照，`at` 是前端收到的时刻（毫秒）。 */
export interface PoseFrame {
  tick: number
  at: number
  agvs: AgvPose[]
}

/** 渲染延迟：留出这段缓冲，低推送频率也能连续插值。 */
export const DEFAULT_FRAME_DELAY_MS = 120

export function shortestAngleDelta(_from: number, _to: number): number {
  throw new Error('not implemented')
}

export function expSmooth(_current: number, _target: number, _lambda: number, _dt: number): number {
  throw new Error('not implemented')
}

export class PoseBuffer {
  constructor(_delayMs = DEFAULT_FRAME_DELAY_MS, _maxFrames = 60) {}

  push(_tick: number, _agvs: AgvPose[], _at: number): void {
    throw new Error('not implemented')
  }

  sample(_at: number): AgvPose[] {
    throw new Error('not implemented')
  }

  clear(): void {
    throw new Error('not implemented')
  }

  get size(): number {
    return 0
  }
}
