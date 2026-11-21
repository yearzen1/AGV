import type { SimFrame } from '@/api/sim'

/** 单次仿真最多缓存多少帧；按 100 ms/tick 约 33 分钟。 */
export const MAX_REPLAY_FRAMES = 20000

/** 仿真引擎固定 100 ms 推进一个 tick。 */
export const TICKS_PER_SECOND = 10

/** 回放倍速档位，后端本身允许 0.25–8 倍。 */
export const REPLAY_SPEEDS = [0.25, 0.5, 1, 2, 4, 8] as const

/**
 * 本次仿真的逐帧录像：只存在页面内存里，刷新即失效，不写数据库。
 * 超过上限后停止记录并标记截断，避免长时间仿真把内存撑爆。
 */
export class ReplayRecorder {
  private list: SimFrame[] = []
  private cut = false
  private readonly limit: number

  constructor(limit = MAX_REPLAY_FRAMES) {
    this.limit = Math.max(1, limit)
  }

  record(frame: SimFrame): void {
    if (this.list.length >= this.limit) {
      this.cut = true
      return
    }
    this.list.push(frame)
  }

  clear(): void {
    this.list = []
    this.cut = false
  }

  get frames(): readonly SimFrame[] {
    return this.list
  }

  get available(): boolean {
    return this.list.length > 1
  }

  get truncated(): boolean {
    return this.cut
  }

  get from(): number {
    return this.list[0]?.tick ?? 0
  }

  get to(): number {
    return this.list[this.list.length - 1]?.tick ?? 0
  }
}

/**
 * 二分查找不晚于目标 tick 的最后一帧。
 * 目标早于录像起点时停在第一帧，晚于终点时停在最后一帧；空录像返回 -1。
 */
export function findReplayIndex(frames: readonly SimFrame[], tick: number): number {
  if (frames.length === 0) return -1
  if (tick < frames[0].tick) return 0

  let low = 0
  let high = frames.length - 1
  let result = 0
  while (low <= high) {
    const mid = (low + high) >> 1
    if (frames[mid].tick <= tick) {
      result = mid
      low = mid + 1
    } else {
      high = mid - 1
    }
  }
  return result
}

/** 按真实经过时间和倍速推进回放 tick；非法输入按 0 处理。 */
export function replayTickAfter(
  elapsedMs: number,
  speed: number,
  currentTick: number
): number {
  const safeSpeed = Number.isFinite(speed) && speed > 0 ? speed : 1
  const safeElapsed = Number.isFinite(elapsedMs) && elapsedMs > 0 ? elapsedMs : 0
  return currentTick + (safeElapsed / 1000) * TICKS_PER_SECOND * safeSpeed
}
