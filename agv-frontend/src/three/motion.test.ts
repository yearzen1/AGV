import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  DEFAULT_FRAME_DELAY_MS,
  PoseBuffer,
  expSmooth,
  shortestAngleDelta,
  type AgvPose
} from './motion.ts'

function pose(overrides: Partial<AgvPose> = {}): AgvPose {
  return {
    id: 'AGV-1',
    x: 0,
    y: 0,
    heading: 0,
    status: 'MOVING',
    arcId: 'E1+',
    ...overrides
  }
}

test('shortestAngleDelta wraps across the 180 degree boundary', () => {
  assert.equal(shortestAngleDelta(179, -179), 2)
  assert.equal(shortestAngleDelta(-170, 170), -20)
  assert.equal(shortestAngleDelta(10, 350), -20)
  assert.equal(shortestAngleDelta(0, 0), 0)
})

test('shortestAngleDelta treats a half turn as one direction only', () => {
  assert.equal(Math.abs(shortestAngleDelta(0, 180)), 180)
  assert.equal(Math.abs(shortestAngleDelta(90, 270)), 180)
})

test('expSmooth converges at the same rate regardless of frame rate', () => {
  const lambda = 10
  const seconds = 1
  let at60 = 0
  let at144 = 0
  for (let i = 0; i < 60; i++) at60 = expSmooth(at60, 1, lambda, seconds / 60)
  for (let i = 0; i < 144; i++) at144 = expSmooth(at144, 1, lambda, seconds / 144)
  assert.ok(Math.abs(at60 - at144) < 1e-9, `${at60} vs ${at144}`)
  assert.ok(at60 > 0.99 && at60 < 1, `${at60}`)
})

test('expSmooth keeps the value when no time passed', () => {
  assert.equal(expSmooth(3, 7, 10, 0), 3)
})

test('PoseBuffer interpolates position and heading between two frames', () => {
  const buffer = new PoseBuffer(0)
  buffer.push(1, [pose({ x: 0, y: 0, heading: 170 })], 0)
  buffer.push(2, [pose({ x: 10, y: 4, heading: -170 })], 100)

  const [sampled] = buffer.sample(50)
  assert.equal(sampled.x, 5)
  assert.equal(sampled.y, 2)
  assert.equal(sampled.heading, 180)
})

test('PoseBuffer delays the sampled time by the configured delay', () => {
  const buffer = new PoseBuffer(DEFAULT_FRAME_DELAY_MS)
  buffer.push(1, [pose({ x: 0 })], 0)
  buffer.push(2, [pose({ x: 10 })], 100)

  const [sampled] = buffer.sample(200)
  assert.equal(sampled.x, 8)
})

test('PoseBuffer holds the newest frame after the buffer runs dry', () => {
  const buffer = new PoseBuffer(0)
  buffer.push(1, [pose({ x: 0 })], 0)
  buffer.push(2, [pose({ x: 10 })], 100)

  const [sampled] = buffer.sample(500)
  assert.equal(sampled.x, 10)
})

test('PoseBuffer uses the earlier frame status so colors never lead the motion', () => {
  const buffer = new PoseBuffer(0)
  buffer.push(1, [pose({ status: 'MOVING' })], 0)
  buffer.push(2, [pose({ status: 'LOADING' })], 100)

  assert.equal(buffer.sample(50)[0].status, 'MOVING')
  assert.equal(buffer.sample(100)[0].status, 'LOADING')
})

test('PoseBuffer ignores out-of-order frames', () => {
  const buffer = new PoseBuffer(0)
  buffer.push(2, [pose({ x: 10 })], 0)
  buffer.push(1, [pose({ x: 0 })], 50)

  assert.equal(buffer.sample(50)[0].x, 10)
})

test('PoseBuffer shows an AGV that appears in the next frame', () => {
  const buffer = new PoseBuffer(0)
  buffer.push(1, [pose({ id: 'AGV-1' })], 0)
  buffer.push(2, [pose({ id: 'AGV-1' }), pose({ id: 'AGV-2', x: 4 })], 100)

  const sampled = buffer.sample(50)
  assert.equal(sampled.length, 2)
  assert.equal(sampled.find((item) => item.id === 'AGV-2')?.x, 4)
})

test('PoseBuffer carries the lane direction from the earlier frame', () => {
  const buffer = new PoseBuffer(0)
  buffer.push(1, [pose({ arcId: 'E1+' })], 0)
  buffer.push(2, [pose({ arcId: 'E1-' })], 100)

  assert.equal(buffer.sample(50)[0].arcId, 'E1+')
})

test('PoseBuffer drops old frames beyond its capacity and clears on demand', () => {
  const buffer = new PoseBuffer(0, 2)
  buffer.push(1, [pose({ x: 1 })], 0)
  buffer.push(2, [pose({ x: 2 })], 100)
  buffer.push(3, [pose({ x: 3 })], 200)
  assert.equal(buffer.size, 2)

  buffer.clear()
  assert.equal(buffer.size, 0)
  assert.deepEqual(buffer.sample(300), [])
})
