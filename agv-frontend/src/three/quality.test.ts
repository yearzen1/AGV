import { test } from 'node:test'
import assert from 'node:assert/strict'
import { loadQuality, saveQuality, isSceneQuality } from './quality.ts'

function fakeStorage(initial: Record<string, string> = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem(key: string) {
      return map.get(key) ?? null
    },
    setItem(key: string, value: string) {
      map.set(key, value)
    }
  }
}

test('loadQuality falls back to medium when nothing is stored', () => {
  assert.equal(loadQuality(fakeStorage()), 'medium')
})

test('loadQuality ignores unknown values', () => {
  assert.equal(loadQuality(fakeStorage({ 'agv.scene.quality': 'ultra' })), 'medium')
  assert.equal(loadQuality(null), 'medium')
})

test('loadQuality reads a valid stored tier', () => {
  assert.equal(loadQuality(fakeStorage({ 'agv.scene.quality': 'low' })), 'low')
  assert.equal(loadQuality(fakeStorage({ 'agv.scene.quality': 'high' })), 'high')
})

test('saveQuality persists only valid tiers', () => {
  const storage = fakeStorage()
  saveQuality('high', storage)
  assert.equal(storage.getItem('agv.scene.quality'), 'high')
})

test('isSceneQuality narrows the three supported tiers', () => {
  assert.equal(isSceneQuality('low'), true)
  assert.equal(isSceneQuality('medium'), true)
  assert.equal(isSceneQuality('high'), true)
  assert.equal(isSceneQuality('HIGH'), false)
  assert.equal(isSceneQuality(null), false)
})
