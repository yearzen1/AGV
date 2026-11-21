import * as THREE from 'three'
import type { NetZone } from '@/api/roadnet'
import { ROAD_Y } from './constants.ts'
import type { LabelFactory } from './labels'
import { buildZoneGeometry } from './zoneGeometry.ts'

export interface ZoneLayer {
  group: THREE.Group
  stats: { zones: number }
}

const LABEL_HEIGHT = 0.55

/**
 * 区域注释层：所有色块合并成一个半透明 Mesh，边框合并成一个 LineSegments，
 * 名称复用 LabelFactory 的纹理缓存。区域再多也只增加 2 个 draw call。
 */
export function buildZoneLayer(zones: NetZone[], labels: LabelFactory): ZoneLayer {
  const group = new THREE.Group()
  group.name = 'zones'

  const geometry = buildZoneGeometry(zones)
  if (geometry.stats.zones === 0) return { group, stats: geometry.stats }

  const fillGeometry = new THREE.BufferGeometry()
  fillGeometry.setAttribute('position', new THREE.Float32BufferAttribute(geometry.fills.positions, 3))
  fillGeometry.setAttribute('normal', new THREE.Float32BufferAttribute(geometry.fills.normals, 3))
  fillGeometry.setAttribute('color', new THREE.Float32BufferAttribute(toRgb(geometry.fills.colors), 3))
  fillGeometry.setIndex(geometry.fills.indices)
  fillGeometry.computeBoundingSphere()

  const fill = new THREE.Mesh(
    fillGeometry,
    new THREE.MeshBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 0.22,
      depthWrite: false,
      // 和同一块路面叠在一起时用深度偏移兜底，避免远处闪烁。
      polygonOffset: true,
      polygonOffsetFactor: -2,
      polygonOffsetUnits: -2,
      toneMapped: false
    })
  )
  fill.name = 'zone-fills'
  fill.renderOrder = 0
  group.add(fill)

  const borderGeometry = new THREE.BufferGeometry()
  borderGeometry.setAttribute(
    'position',
    new THREE.Float32BufferAttribute(geometry.borders.positions, 3)
  )
  borderGeometry.setAttribute('color', new THREE.Float32BufferAttribute(toRgb(geometry.borders.colors), 3))
  borderGeometry.computeBoundingSphere()

  const borders = new THREE.LineSegments(
    borderGeometry,
    new THREE.LineBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 0.85,
      depthWrite: false,
      toneMapped: false
    })
  )
  borders.name = 'zone-borders'
  borders.renderOrder = 1
  group.add(borders)

  for (const anchor of geometry.labels) {
    const sprite = labels.create(anchor.name, { color: anchor.color, height: LABEL_HEIGHT })
    sprite.position.set(anchor.x, ROAD_Y + 0.45, anchor.y)
    sprite.name = `zone-label-${anchor.id}`
    group.add(sprite)
  }

  return { group, stats: geometry.stats }
}

/** 顶点色按 three 的工作颜色空间写入，和材质颜色的 sRGB 转换保持一致。 */
function toRgb(colors: number[]) {
  const result: number[] = []
  const color = new THREE.Color()
  for (const value of colors) {
    color.setHex(value)
    result.push(color.r, color.g, color.b)
  }
  return result
}
