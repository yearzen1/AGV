import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  lanesForEdge,
  laneOffsetForArc,
  offsetAlongRight,
  type LaneEdge,
  type LanePoint
} from './lane.ts'
import { LANE } from './constants.ts'

const nodes = {
  A: { id: 'A', x: 0, y: 0 },
  B: { id: 'B', x: 10, y: 0 }
}

function rightOf(from: LanePoint, to: LanePoint) {
  const length = Math.hypot(to.x - from.x, to.y - from.y)
  return { x: -(to.y - from.y) / length, y: (to.x - from.x) / length }
}

function assertClose(actual: number, expected: number, message?: string) {
  assert.ok(Math.abs(actual - expected) < 1e-9, message ?? `${actual} != ${expected}`)
}

function edge(overrides: Partial<LaneEdge> = {}): LaneEdge {
  return {
    id: 'E1',
    from: 'A',
    to: 'B',
    direction: 'TWO_WAY',
    laneMode: 'DUAL',
    ...overrides
  }
}

test('lanesForEdge splits a dual two-way edge into two opposite lanes', () => {
  const lanes = lanesForEdge(edge(), nodes.A, nodes.B)
  assert.equal(lanes.length, 2)
  assert.deepEqual(lanes[0].start, nodes.A)
  assert.deepEqual(lanes[0].end, nodes.B)
  assert.ok(lanes[0].offset > 0)
  assert.deepEqual(lanes[1].start, nodes.B)
  assert.deepEqual(lanes[1].end, nodes.A)
  assert.ok(lanes[1].offset > 0)
})

test('dual lanes end up on opposite sides of the physical center line', () => {
  const [forward, reverse] = lanesForEdge(edge(), nodes.A, nodes.B)
  const forwardRight = rightOf(forward.start, forward.end)
  const reverseRight = rightOf(reverse.start, reverse.end)
  const forwardCenter = {
    x: nodes.A.x + forwardRight.x * forward.offset,
    y: nodes.A.y + forwardRight.y * forward.offset
  }
  const reverseCenter = {
    x: nodes.B.x + reverseRight.x * reverse.offset,
    y: nodes.B.y + reverseRight.y * reverse.offset
  }
  assertClose(forwardCenter.y, LANE.offset)
  assertClose(reverseCenter.y, -LANE.offset)
})

test('lanesForEdge keeps one centered lane for single or one-way edges', () => {
  for (const candidate of [
    edge({ laneMode: 'SINGLE' }),
    edge({ direction: 'ONE_WAY' }),
    edge({ direction: 'ONE_WAY', laneMode: 'DUAL' })
  ]) {
    const lanes = lanesForEdge(candidate, nodes.A, nodes.B)
    assert.equal(lanes.length, 1)
    assert.equal(lanes[0].offset, 0)
    assert.deepEqual(lanes[0].start, nodes.A)
  }
})

test('laneOffsetForArc places both directions on their own right lane', () => {
  const edges = new Map([['E1', edge()]])
  assert.equal(laneOffsetForArc(edges, 'E1+'), LANE.offset)
  assert.equal(laneOffsetForArc(edges, 'E1-'), LANE.offset)
})

test('laneOffsetForArc ignores single lanes, one-way edges and node stops', () => {
  assert.equal(laneOffsetForArc(new Map([['E1', edge({ laneMode: 'SINGLE' })]]), 'E1+'), 0)
  assert.equal(
    laneOffsetForArc(new Map([['E1', edge({ direction: 'ONE_WAY' })]]), 'E1+'),
    0
  )
  assert.equal(laneOffsetForArc(new Map([['E1', edge()]]), null), 0)
  assert.equal(laneOffsetForArc(new Map([['E1', edge()]]), 'E9+'), 0)
})

test('offsetAlongRight lands on the lane centers used by the road builder', () => {
  const [forward, reverse] = lanesForEdge(edge(), nodes.A, nodes.B)

  const forwardCenter = offsetAlongRight({ x: nodes.A.x, y: nodes.A.y, heading: 0 }, forward.offset)
  const reverseCenter = offsetAlongRight(
    { x: nodes.B.x, y: nodes.B.y, heading: 180 },
    reverse.offset
  )

  assertClose(forwardCenter.y, LANE.offset)
  assertClose(reverseCenter.y, -LANE.offset)
})

test('offsetAlongRight keeps the two travel directions on opposite sides', () => {
  const forward = offsetAlongRight({ x: 0, y: 0, heading: 0 }, LANE.offset)
  const reverse = offsetAlongRight({ x: 0, y: 0, heading: 180 }, LANE.offset)
  assertClose(forward.y, LANE.offset)
  assertClose(reverse.y, -LANE.offset)

  const east = offsetAlongRight({ x: 0, y: 0, heading: 90 }, LANE.offset)
  assertClose(east.x, -LANE.offset)
  assertClose(east.y, 0)
})
