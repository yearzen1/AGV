import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildRoadGeometry, type MeshArrays } from './roadGeometry.ts'
import { LANE } from './constants.ts'

interface TestNode {
  id: string
  x: number
  y: number
  type: 'WORKSTATION' | 'CHARGER' | 'BUFFER' | 'JUNCTION'
  capacity?: number
}

interface TestEdge {
  id: string
  from: string
  to: string
  length: number
  direction: 'ONE_WAY' | 'TWO_WAY'
  speed: number
  capacity: number
  laneMode?: 'SINGLE' | 'DUAL'
}

function makeNet(nodes: TestNode[], edges: TestEdge[]) {
  return { nodes, edges }
}

function edge(overrides: Partial<TestEdge> = {}): TestEdge {
  return {
    id: 'E1',
    from: 'A',
    to: 'B',
    length: 10,
    direction: 'TWO_WAY',
    speed: 1,
    capacity: 2,
    laneMode: 'DUAL',
    ...overrides
  }
}

function close(actual: number, expected: number, message?: string) {
  assert.ok(Math.abs(actual - expected) < 1e-6, message ?? `${actual} != ${expected}`)
}

function zExtent(buffer: MeshArrays) {
  let min = Infinity
  let max = -Infinity
  for (let i = 0; i < buffer.positions.length; i += 3) {
    const z = buffer.positions[i + 2]
    min = Math.min(min, z)
    max = Math.max(max, z)
  }
  return { min, max }
}

/** 每 4 个顶点组成一个箭头，返回 a→b 的水平方向向量。 */
function arrowDirections(buffer: MeshArrays) {
  const directions: Array<{ x: number; y: number }> = []
  const quads = buffer.positions.length / 12
  for (let index = 0; index < quads; index++) {
    const base = index * 12
    const aX = (buffer.positions[base] + buffer.positions[base + 3]) / 2
    const aY = (buffer.positions[base + 2] + buffer.positions[base + 5]) / 2
    const bX = (buffer.positions[base + 6] + buffer.positions[base + 9]) / 2
    const bY = (buffer.positions[base + 8] + buffer.positions[base + 11]) / 2
    directions.push({ x: bX - aX, y: bY - aY })
  }
  return directions
}

function assertUpward(buffer: MeshArrays) {
  assert.ok(buffer.positions.length > 0)
  for (let i = 0; i < buffer.indices.length; i += 3) {
    const a = buffer.indices[i] * 3
    const b = buffer.indices[i + 1] * 3
    const c = buffer.indices[i + 2] * 3
    const abX = buffer.positions[b] - buffer.positions[a]
    const abZ = buffer.positions[b + 2] - buffer.positions[a + 2]
    const acX = buffer.positions[c] - buffer.positions[a]
    const acZ = buffer.positions[c + 2] - buffer.positions[a + 2]
    const normalY = abZ * acX - abX * acZ
    assert.ok(normalY > 0, `triangle ${i / 3} faces down`)
  }
}

function assertIndexBounds(buffer: MeshArrays) {
  const vertices = buffer.positions.length / 3
  for (const index of buffer.indices) {
    assert.ok(index >= 0 && index < vertices, `index ${index} out of ${vertices}`)
  }
}

test('buildRoadGeometry splits a dual edge into two lanes and a divider', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 10, y: 0, type: 'JUNCTION' }
      ],
      [edge()]
    )
  )

  assert.equal(result.stats.ribbons, 2)
  assert.equal(result.stats.dividers, 1)
  assert.equal(result.stats.arrows, 4)
  close(zExtent(result.ribbons).min, -(LANE.offset + LANE.width / 2))
  close(zExtent(result.ribbons).max, LANE.offset + LANE.width / 2)
  close(Math.abs(zExtent(result.arrows).max), LANE.offset + 0.21)

  const directions = arrowDirections(result.arrows)
  assert.equal(directions.filter((direction) => direction.x > 0).length, 2)
  assert.equal(directions.filter((direction) => direction.x < 0).length, 2)

  for (const buffer of [result.ribbons, result.dividers, result.arrows]) {
    assertUpward(buffer)
    assertIndexBounds(buffer)
  }
})

test('dual lane centers sit on opposite sides of a diagonal edge', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 10, y: 10, type: 'JUNCTION' }
      ],
      [edge()]
    )
  )
  assert.equal(result.stats.ribbons, 2)

  const length = Math.hypot(10, 10)
  const right = { x: -10 / length, y: 10 / length }
  const mid = { x: 5, y: 5 }
  const offsets: number[] = []

  for (let quad = 0; quad < 2; quad++) {
    const base = quad * 12
    let sumX = 0
    let sumZ = 0
    for (let vertex = 0; vertex < 4; vertex++) {
      sumX += result.ribbons.positions[base + vertex * 3]
      sumZ += result.ribbons.positions[base + vertex * 3 + 2]
    }
    const center = { x: sumX / 4, y: sumZ / 4 }
    offsets.push((center.x - mid.x) * right.x + (center.y - mid.y) * right.y)
  }

  close(Math.min(...offsets), -LANE.offset)
  close(Math.max(...offsets), LANE.offset)
})

test('buildRoadGeometry keeps a single lane for a two-way single-lane edge', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 10, y: 0, type: 'JUNCTION' }
      ],
      [edge({ laneMode: 'SINGLE' })]
    )
  )

  assert.equal(result.stats.ribbons, 1)
  assert.equal(result.stats.dividers, 1)
  assert.equal(result.stats.arrows, 2)
  close(zExtent(result.ribbons).min, -LANE.width / 2)
  close(zExtent(result.ribbons).max, LANE.width / 2)

  const directions = arrowDirections(result.arrows)
  assert.equal(directions.filter((direction) => direction.x > 0).length, 1)
  assert.equal(directions.filter((direction) => direction.x < 0).length, 1)
  assertUpward(result.arrows)
  assertIndexBounds(result.arrows)
})

test('buildRoadGeometry treats one-way DUAL as a single lane without a divider', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 10, y: 0, type: 'JUNCTION' }
      ],
      [edge({ direction: 'ONE_WAY' })]
    )
  )

  assert.equal(result.stats.ribbons, 1)
  assert.equal(result.stats.dividers, 0)
  assert.equal(result.stats.arrows, 2)
  assert.ok(arrowDirections(result.arrows).every((direction) => direction.x > 0))
  assertUpward(result.ribbons)
})

test('buildRoadGeometry skips missing endpoints and zero-length edges', () => {
  const missing = buildRoadGeometry(
    makeNet(
      [{ id: 'A', x: 0, y: 0, type: 'JUNCTION' }],
      [edge({ to: 'Z' })]
    )
  )
  assert.deepEqual(missing.stats, { ribbons: 0, dividers: 0, arrows: 0 })

  const zero = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 2, y: 2, type: 'JUNCTION' },
        { id: 'B', x: 2, y: 2, type: 'JUNCTION' }
      ],
      [edge({ length: 0 })]
    )
  )
  assert.deepEqual(zero.stats, { ribbons: 0, dividers: 0, arrows: 0 })
})
