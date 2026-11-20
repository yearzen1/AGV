import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  normalizeZones,
  nextZoneId,
  zoneContains,
  zoneRectFromPoints,
  zoneTypeColor,
  zoneTypeLabel
} from './editorZones.ts'

test('normalizeZones returns empty for old payloads without zones', () => {
  assert.deepEqual(normalizeZones(undefined, 50, 34), [])
  assert.deepEqual(normalizeZones(null, 50, 34), [])
})

test('normalizeZones fills missing fields, clamps geometry and keeps the map usable', () => {
  const zones = normalizeZones(
    [
      { id: 'Z1', type: 'CHARGING' },
      { id: 'Z1', name: ' 卸货区 ', type: 'UNLOADING', x: 48, y: 32, width: 20, height: 10 },
      { name: 'X', type: 'CUSTOM', x: -5, y: -5, width: 0, height: 0 }
    ],
    50,
    34
  )

  assert.equal(zones.length, 3)
  assert.equal(zones[0].id, 'Z1')
  assert.equal(zones[0].name, '充电区 1')
  assert.equal(zones[0].width, 6)
  assert.equal(zones[0].height, 4)
  assert.equal(zones[1].id, 'Z2')
  assert.equal(zones[1].name, '卸货区')
  assert.equal(zones[1].x, 48)
  assert.equal(zones[1].width, 2)
  assert.equal(zones[1].y, 32)
  assert.equal(zones[1].height, 2)
  assert.equal(zones[2].type, 'CUSTOM')
  assert.equal(zones[2].x, 0)
  assert.equal(zones[2].y, 0)
  assert.equal(zones[2].width, 1)
  assert.equal(zones[2].height, 1)
})

test('nextZoneId uses the largest numeric suffix', () => {
  assert.equal(nextZoneId([]), 'Z1')
  assert.equal(nextZoneId([{ id: 'Z2' }, { id: 'Z10' }, { id: 'A' }]), 'Z11')
})

test('zoneRectFromPoints snaps to 0.5m and clamps to the canvas', () => {
  assert.deepEqual(zoneRectFromPoints({ x: 1.2, y: 1.3 }, { x: 7.7, y: 5.2 }, 50, 34), {
    x: 1,
    y: 1.5,
    width: 6.5,
    height: 3.5
  })
  assert.deepEqual(zoneRectFromPoints({ x: 49.8, y: 33.8 }, { x: 60, y: 40 }, 50, 34), {
    x: 49,
    y: 33,
    width: 1,
    height: 1
  })
})

test('zoneContains checks the rectangle bounds and labels fall back to other', () => {
  const zone = { id: 'Z1', name: 'a', type: 'RACK', x: 1, y: 2, width: 4, height: 3 }
  assert.equal(zoneContains(zone, 1, 2), true)
  assert.equal(zoneContains(zone, 5, 5), true)
  assert.equal(zoneContains(zone, 5.1, 5), false)
  assert.equal(zoneTypeLabel('RACK'), '货架区')
  assert.equal(zoneTypeLabel('UNKNOWN'), '其他')
  assert.equal(zoneTypeColor('UNKNOWN'), zoneTypeColor('OTHER'))
})
