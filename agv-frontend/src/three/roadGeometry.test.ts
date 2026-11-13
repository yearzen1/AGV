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

function distanceTo(buffer: MeshArrays, x: number, z: number): number {
  let closest = Infinity
  for (let i = 0; i < buffer.positions.length; i += 3) {
    const dx = buffer.positions[i] - x
    const dz = buffer.positions[i + 2] - z
    closest = Math.min(closest, Math.hypot(dx, dz))
  }
  return closest
}

/** 判断平面三角形网格是否覆盖某个点（重心坐标）。 */
function coversPoint(buffer: MeshArrays, x: number, z: number): boolean {
  for (let i = 0; i < buffer.indices.length; i += 3) {
    const a = buffer.indices[i] * 3
    const b = buffer.indices[i + 1] * 3
    const c = buffer.indices[i + 2] * 3
    const ax = buffer.positions[a]
    const az = buffer.positions[a + 2]
    const bx = buffer.positions[b]
    const bz = buffer.positions[b + 2]
    const cx = buffer.positions[c]
    const cz = buffer.positions[c + 2]

    const denominator = (bz - cz) * (ax - cx) + (cx - bx) * (az - cz)
    if (Math.abs(denominator) < 1e-9) continue
    const first = ((bz - cz) * (x - cx) + (cx - bx) * (z - cz)) / denominator
    const second = ((cz - az) * (x - cx) + (ax - cx) * (z - cz)) / denominator
    const third = 1 - first - second
    if (first >= -1e-9 && second >= -1e-9 && third >= -1e-9) return true
  }
  return false
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
  assert.deepEqual(missing.stats, { ribbons: 0, dividers: 0, arrows: 0, junctions: 0 })

  const zero = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 2, y: 2, type: 'JUNCTION' },
        { id: 'B', x: 2, y: 2, type: 'JUNCTION' }
      ],
      [edge({ length: 0 })]
    )
  )
  assert.deepEqual(zero.stats, { ribbons: 0, dividers: 0, arrows: 0, junctions: 0 })
})

test('a right-angle corner gets one apron that covers the crossing strips', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 10, y: 0, type: 'JUNCTION' },
        { id: 'C', x: 10, y: 10, type: 'JUNCTION' }
      ],
      [edge({ id: 'E1', from: 'A', to: 'B' }), edge({ id: 'E2', from: 'B', to: 'C' })]
    )
  )

  assert.equal(result.stats.junctions, 1)
  // 两条 1.3 米宽的车道带在 B 处叠出的交叉区，铺装必须盖住
  assert.ok(coversPoint(result.junctions, 9.5, 0.5), '铺装没有盖住交叉区中心')
  assert.ok(coversPoint(result.junctions, 9.6, 1.2), '铺装没有盖住交叉区外角')
  assert.ok(!coversPoint(result.junctions, 7, -3), '铺装不应延伸到路口之外')

  // 标线在铺装边缘断开，不会穿进路口
  assert.ok(distanceTo(result.dividers, 10, 0) > 1.3, '分隔线穿进了路口')
  assert.ok(distanceTo(result.arrows, 10, 0) > 1.3, '箭头穿进了路口')

  assertUpward(result.junctions)
  assertIndexBounds(result.junctions)
  assert.equal(result.junctions.uvs.length, (result.junctions.positions.length / 3) * 2)
})

test('a straight pass-through node keeps the lanes continuous without an apron', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 5, y: 0, type: 'JUNCTION' },
        { id: 'C', x: 10, y: 0, type: 'JUNCTION' }
      ],
      [edge({ id: 'E1', from: 'A', to: 'B' }), edge({ id: 'E2', from: 'B', to: 'C' })]
    )
  )

  assert.equal(result.stats.junctions, 0)
  assert.equal(result.stats.dividers, 2)
  // 直行节点只在节点处留一个缺口，不需要铺装
  assert.ok(distanceTo(result.dividers, 5, 0) > 0.3)
})

test('a T junction between dual lanes and a single branch gets an apron too', () => {
  const result = buildRoadGeometry(
    makeNet(
      [
        { id: 'A', x: 0, y: 0, type: 'JUNCTION' },
        { id: 'B', x: 10, y: 0, type: 'JUNCTION' },
        { id: 'C', x: 20, y: 0, type: 'JUNCTION' },
        { id: 'D', x: 10, y: 10, type: 'WORKSTATION' }
      ],
      [
        edge({ id: 'E1', from: 'A', to: 'B' }),
        edge({ id: 'E2', from: 'B', to: 'C' }),
        edge({ id: 'E3', from: 'B', to: 'D', laneMode: 'SINGLE' })
      ]
    )
  )

  assert.equal(result.stats.junctions, 1)
  assert.ok(coversPoint(result.junctions, 9.6, 0.5), '铺装没有盖住支路与主路的交叉区')
  assert.ok(coversPoint(result.junctions, 10.4, 0.5), '铺装没有盖住支路与主路的交叉区')
  assertUpward(result.junctions)
})
