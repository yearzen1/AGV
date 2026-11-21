import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mapZones, zoneExtents } from './mapZones.ts'
import { ZONE_TYPE_COLOR, ZONE_TYPE_LABEL } from './zoneStyles.ts'
import type { NetZone } from '@/api/roadnet'

function zone(overrides: Partial<NetZone> = {}): NetZone {
  return {
    id: 'Z1',
    name: '充电区',
    type: 'CHARGING',
    x: 0,
    y: 0,
    width: 10,
    height: 5,
    ...overrides
  }
}

test('mapZones returns nothing for missing zones', () => {
  assert.deepEqual(mapZones(undefined, 20), [])
  assert.deepEqual(mapZones([], 20), [])
})

test('mapZones keeps large zones and colors them by type', () => {
  const zones = mapZones([zone()], 20)

  assert.equal(zones.length, 1)
  assert.equal(zones[0].name, '充电区')
  assert.equal(zones[0].color, ZONE_TYPE_COLOR.CHARGING)
  assert.deepEqual(
    { x: zones[0].x, y: zones[0].y, width: zones[0].width, height: zones[0].height },
    { x: 0, y: 0, width: 10, height: 5 }
  )
  assert.equal(zones[0].showLabel, true)
})

test('mapZones hides labels for zones that are too small on screen', () => {
  const zones = mapZones([zone({ width: 1, height: 1 })], 20)

  assert.equal(zones.length, 1)
  assert.equal(zones[0].showLabel, false)
})

test('mapZones falls back to other for unknown types and empty names', () => {
  const zones = mapZones([zone({ type: 'CUSTOM', name: '   ' })], 20)

  assert.equal(zones[0].color, ZONE_TYPE_COLOR.OTHER)
  assert.equal(zones[0].name, ZONE_TYPE_LABEL.OTHER)
})

test('mapZones skips invalid sizes without breaking the rest', () => {
  const zones = mapZones(
    [zone({ id: 'BAD', width: 0 }), zone({ id: 'OK', x: -2, y: 3, width: 4, height: 4 })],
    20
  )

  assert.equal(zones.length, 1)
  assert.equal(zones[0].id, 'OK')
})

test('zoneExtents covers every zone and returns null without zones', () => {
  assert.equal(zoneExtents(undefined), null)
  assert.equal(zoneExtents([]), null)

  const extents = zoneExtents([
    zone({ x: 2, y: 3, width: 6, height: 4 }),
    zone({ id: 'Z2', x: -1, y: 8, width: 2, height: 2 })
  ])

  assert.deepEqual(extents, { minX: -1, minY: 3, maxX: 8, maxY: 10 })
})
