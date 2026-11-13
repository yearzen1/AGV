import { test } from 'node:test'
import assert from 'node:assert/strict'
import { lanesForEdge, laneOffsetForArc, type LaneEdge } from './lane.ts'
import { LANE } from './constants.ts'

const nodes = {
  A: { id: 'A', x: 0, y: 0 },
  B: { id: 'B', x: 10, y: 0 }
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
  assert.ok(lanes[1].offset < 0)
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

test('laneOffsetForArc places dual lanes on the right of travel direction', () => {
  const edges = new Map([['E1', edge()]])
  assert.equal(laneOffsetForArc(edges, 'E1+'), LANE.offset)
  assert.equal(laneOffsetForArc(edges, 'E1-'), -LANE.offset)
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
