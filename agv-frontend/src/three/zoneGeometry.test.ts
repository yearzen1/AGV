import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ROAD_Y } from './constants.ts'
import {
  buildZoneGeometry,
  zoneExtents,
  ZONE_BORDER_Y,
  ZONE_FILL_Y
} from './zoneGeometry.ts'
import { ZONE_TYPE_COLOR } from '../utils/zoneStyles.ts'

interface TestZone {
  id: string
  name: string
  type: string
  x: number
  y: number
  width: number
  height: number
}

function zone(overrides: Partial<TestZone> = {}): TestZone {
  return { id: 'Z1', name: '充电区', type: 'CHARGING', x: 2, y: 3, width: 6, height: 4, ...overrides }
}

function colorNumber(hex: string) {
  return Number.parseInt(hex.slice(1), 16)
}

test('buildZoneGeometry returns empty geometry for missing zones', () => {
  const geometry = buildZoneGeometry([])

  assert.equal(geometry.stats.zones, 0)
  assert.deepEqual(geometry.fills.positions, [])
  assert.deepEqual(geometry.borders.positions, [])
  assert.deepEqual(geometry.labels, [])
})

test('one zone produces a fill quad below lane markings', () => {
  const geometry = buildZoneGeometry([zone()])

  assert.equal(geometry.stats.zones, 1)
  assert.equal(geometry.fills.positions.length, 4 * 3)
  assert.deepEqual(geometry.fills.indices, [0, 2, 1, 0, 3, 2])

  for (let index = 1; index < geometry.fills.positions.length; index += 3) {
    assert.equal(geometry.fills.positions[index], ZONE_FILL_Y)
  }
  assert.ok(ZONE_FILL_Y > ROAD_Y)
  assert.ok(ZONE_FILL_Y < ZONE_BORDER_Y)

  const xs = [geometry.fills.positions[0], geometry.fills.positions[3]]
  const zs = [geometry.fills.positions[2], geometry.fills.positions[8]]
  assert.deepEqual(new Set(xs), new Set([2, 8]))
  assert.deepEqual(new Set(zs), new Set([3, 7]))
})

test('one zone produces four border edges with the type color', () => {
  const geometry = buildZoneGeometry([zone()])
  const color = colorNumber(ZONE_TYPE_COLOR.CHARGING)

  assert.equal(geometry.borders.positions.length, 4 * 2 * 3)
  for (let index = 1; index < geometry.borders.positions.length; index += 3) {
    assert.equal(geometry.borders.positions[index], ZONE_BORDER_Y)
  }
  assert.equal(geometry.borders.colors.length, 4 * 2)
  for (const borderColor of geometry.borders.colors) {
    assert.equal(borderColor, color)
  }
})

test('zone labels sit at the rectangle center and keep their type color', () => {
  const geometry = buildZoneGeometry([zone({ name: '卸货区', type: 'UNLOADING' })])

  assert.equal(geometry.labels.length, 1)
  assert.equal(geometry.labels[0].id, 'Z1')
  assert.equal(geometry.labels[0].name, '卸货区')
  assert.equal(geometry.labels[0].x, 5)
  assert.equal(geometry.labels[0].y, 5)
  assert.equal(geometry.labels[0].color, ZONE_TYPE_COLOR.UNLOADING)
})

test('unknown zone types fall back to the other color', () => {
  const geometry = buildZoneGeometry([zone({ type: 'CUSTOM' })])
  const color = colorNumber(ZONE_TYPE_COLOR.OTHER)

  assert.equal(geometry.fills.colors[0], color)
  assert.equal(geometry.labels[0].color, ZONE_TYPE_COLOR.OTHER)
})

test('invalid zones are skipped without breaking the rest', () => {
  const geometry = buildZoneGeometry([
    zone({ id: 'BAD', width: 0 }),
    zone({ id: 'OK', x: 0, y: 0, width: 1, height: 1 })
  ])

  assert.equal(geometry.stats.zones, 1)
  assert.equal(geometry.labels.length, 1)
  assert.equal(geometry.labels[0].id, 'OK')
})

test('zoneExtents covers every zone and returns null without zones', () => {
  assert.equal(zoneExtents([]), null)

  const extents = zoneExtents([
    zone({ x: 2, y: 3, width: 6, height: 4 }),
    zone({ id: 'Z2', x: -1, y: 8, width: 2, height: 2 })
  ])

  assert.deepEqual(extents, { minX: -1, minY: 3, maxX: 8, maxY: 10 })
})
