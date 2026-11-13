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

/** 两个角度之间的最短差值，范围 (-180, 180]，避免转向绕远路。 */
export function shortestAngleDelta(from: number, to: number): number {
  const delta = (((to - from) % 360) + 540) % 360 - 180
  return delta === -180 ? 180 : delta
}

/** 与帧率无关的指数平滑：同样时长内收敛到同样比例。 */
export function expSmooth(current: number, target: number, lambda: number, dt: number): number {
  if (dt <= 0 || lambda <= 0) return current
  const k = 1 - Math.exp(-lambda * dt)
  return current + (target - current) * k
}

/**
 * 仿真帧缓冲：按到达时间排队，渲染时取「当前时间 − 延迟」做插值。
 *
 * 直接按帧追赶会让 60Hz 和 144Hz 屏幕速度不同；延迟一小段再插值，
 * 即使后端以 5Hz 推帧也能得到连续运动，且不会向外推位置。
 */
export class PoseBuffer {
  private frames: PoseFrame[] = []
  private readonly delayMs: number
  private readonly maxFrames: number

  constructor(delayMs = DEFAULT_FRAME_DELAY_MS, maxFrames = 60) {
    this.delayMs = Math.max(0, delayMs)
    this.maxFrames = Math.max(1, maxFrames)
  }

  push(tick: number, agvs: AgvPose[], at: number): void {
    if (!Number.isFinite(tick) || !Number.isFinite(at) || !Array.isArray(agvs)) return
    const last = this.frames[this.frames.length - 1]
    if (last && tick <= last.tick) return
    this.frames.push({ tick, at, agvs: agvs.map((agv) => ({ ...agv })) })
    if (this.frames.length > this.maxFrames) {
      this.frames.splice(0, this.frames.length - this.maxFrames)
    }
  }

  sample(at: number): AgvPose[] {
    if (this.frames.length === 0) return []
    const time = at - this.delayMs
    const first = this.frames[0]
    if (time <= first.at) return first.agvs.map((agv) => ({ ...agv }))

    const last = this.frames[this.frames.length - 1]
    if (time >= last.at) return last.agvs.map((agv) => ({ ...agv }))

    let index = 0
    while (index + 1 < this.frames.length && this.frames[index + 1].at <= time) {
      index += 1
    }
    const earlier = this.frames[index]
    const later = this.frames[index + 1]
    const span = later.at - earlier.at
    const alpha = span > 0 ? (time - earlier.at) / span : 1
    const laterById = new Map(later.agvs.map((agv) => [agv.id, agv]))

    const sampled = earlier.agvs.map((agv) => {
      const next = laterById.get(agv.id)
      if (!next) return { ...agv }
      return {
        ...agv,
        x: agv.x + (next.x - agv.x) * alpha,
        y: agv.y + (next.y - agv.y) * alpha,
        heading: agv.heading + shortestAngleDelta(agv.heading, next.heading) * alpha
      }
    })

    // 新出现的车辆没有上一帧可插值，直接按下一帧位置显示，避免从原点飞入。
    const earlierIds = new Set(earlier.agvs.map((agv) => agv.id))
    for (const agv of later.agvs) {
      if (!earlierIds.has(agv.id)) sampled.push({ ...agv })
    }
    return sampled
  }

  clear(): void {
    this.frames = []
  }

  get size(): number {
    return this.frames.length
  }
}
