import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  MAX_REPLAY_FRAMES,
  REPLAY_SPEEDS,
  ReplayRecorder,
  findReplayIndex,
  replayTickAfter
} from './simReplay.ts'
import type { SimFrame } from '@/api/sim'

function frame(tick: number): SimFrame {
  return {
    tick,
    agvs: [],
    events: [],
    finished: false,
    metrics: {
      makespan: tick,
      totalPathLen: 0,
      totalWaitTick: 0,
      conflictCount: 0,
      completedTasks: 0,
      totalTasks: 0
    }
  }
}

test('ReplayRecorder needs at least two frames before replay is available', () => {
  const recorder = new ReplayRecorder()

  assert.equal(recorder.available, false)
  recorder.record(frame(0))
  assert.equal(recorder.available, false)
  recorder.record(frame(1))
  assert.equal(recorder.available, true)
  assert.equal(recorder.from, 0)
  assert.equal(recorder.to, 1)
  assert.equal(recorder.frames.length, 2)
})

test('ReplayRecorder stops at its limit and reports truncation', () => {
  const recorder = new ReplayRecorder(2)

  recorder.record(frame(0))
  recorder.record(frame(1))
  recorder.record(frame(2))

  assert.equal(recorder.frames.length, 2)
  assert.equal(recorder.truncated, true)
  assert.equal(recorder.to, 1)
  assert.equal(MAX_REPLAY_FRAMES, 20000)
})

test('ReplayRecorder clear removes frames and truncation', () => {
  const recorder = new ReplayRecorder(1)
  recorder.record(frame(0))
  recorder.record(frame(1))
  assert.equal(recorder.truncated, true)

  recorder.clear()

  assert.equal(recorder.frames.length, 0)
  assert.equal(recorder.truncated, false)
  assert.equal(recorder.available, false)
})

test('findReplayIndex clamps to the ends and picks the earlier frame', () => {
  const frames = [frame(10), frame(11), frame(13)]

  assert.equal(findReplayIndex(frames, 0), 0)
  assert.equal(findReplayIndex(frames, 10), 0)
  assert.equal(findReplayIndex(frames, 11), 1)
  assert.equal(findReplayIndex(frames, 12), 1)
  assert.equal(findReplayIndex(frames, 13), 2)
  assert.equal(findReplayIndex(frames, 99), 2)
  assert.equal(findReplayIndex([], 5), -1)
})

test('replayTickAfter advances ten ticks per second at 1x', () => {
  assert.equal(replayTickAfter(100, 1, 0), 1)
  assert.equal(replayTickAfter(100, 2, 10), 12)
  assert.equal(replayTickAfter(100, 8, 0), 8)
  assert.equal(replayTickAfter(100, 0.25, 0), 0.25)
  assert.equal(replayTickAfter(-100, 1, 5), 5)
})

test('REPLAY_SPEEDS exposes the extended presets', () => {
  assert.deepEqual([...REPLAY_SPEEDS], [0.25, 0.5, 1, 2, 4, 8])
})
