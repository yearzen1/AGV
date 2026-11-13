import * as THREE from 'three'
import type { NetEdge, NetNode, RoadNetPayload } from '@/api/roadnet'
import { NODE_COLORS, ROAD_Y } from './constants'
import { buildRoadGeometry, type MeshArrays, type RoadGeometryStats } from './roadGeometry'
import type { LabelFactory } from './labels'

export interface RoadNetRender {
  group: THREE.Group
  edges: Map<string, NetEdge>
  bounds: THREE.Box3
  stats: RoadGeometryStats
  dispose(): void
}

interface PadDefinition {
  create: () => THREE.BufferGeometry
  color: number
  emissiveIntensity: number
  height: number
}

/**
 * 把路网载荷转换成三维场景：实体路面、方向箭头、车道分隔线、按类型实例化的节点地台和编号。
 *
 * 顶点数据由 `buildRoadGeometry` 生成，本文件只负责贴图、材质和 three 对象；
 * 几何因此可以在 Node 单测里直接断言，不需要浏览器。
 */
export function buildRoadNet(net: RoadNetPayload, labels: LabelFactory): RoadNetRender {
  const group = new THREE.Group()
  group.name = 'roadnet'
  const edges = new Map(net.edges.map((edge) => [edge.id, edge]))
  const bounds = computeBounds(net.nodes)
  const geometry = buildRoadGeometry(net)

  if (geometry.stats.ribbons > 0) {
    const road = new THREE.Mesh(
      buildMesh(geometry.ribbons),
      new THREE.MeshStandardMaterial({
        map: makeRoadTexture(),
        color: 0xffffff,
        roughness: 0.92,
        metalness: 0.02
      })
    )
    road.name = 'road'
    road.receiveShadow = true
    group.add(road)
  }

  if (geometry.stats.arrows > 0) {
    const arrows = new THREE.Mesh(
      buildMesh(geometry.arrows),
      new THREE.MeshBasicMaterial({
        map: makeArrowTexture(),
        transparent: true,
        opacity: 0.9,
        depthWrite: false,
        toneMapped: false
      })
    )
    arrows.name = 'lane-arrows'
    arrows.renderOrder = 1
    group.add(arrows)
  }

  if (geometry.stats.dividers > 0) {
    const divider = new THREE.Mesh(
      buildMesh(geometry.dividers),
      new THREE.MeshBasicMaterial({
        map: makeDashTexture(),
        transparent: true,
        opacity: 0.8,
        depthWrite: false,
        side: THREE.DoubleSide
      })
    )
    divider.name = 'lane-divider'
    divider.renderOrder = 1
    group.add(divider)
  }

  addNodePads(group, net.nodes)
  addNodeLabels(group, net.nodes, labels)

  return {
    group,
    edges,
    bounds,
    stats: geometry.stats,
    dispose: () => disposeGroup(group)
  }
}

function computeBounds(nodes: NetNode[]): THREE.Box3 {
  const box = new THREE.Box3()
  if (nodes.length === 0) {
    box.set(new THREE.Vector3(-5, 0, -5), new THREE.Vector3(5, 0, 5))
    return box
  }
  box.set(
    new THREE.Vector3(
      Math.min(...nodes.map((node) => node.x)),
      0,
      Math.min(...nodes.map((node) => node.y))
    ),
    new THREE.Vector3(
      Math.max(...nodes.map((node) => node.x)),
      0,
      Math.max(...nodes.map((node) => node.y))
    )
  )
  return box
}

function buildMesh(arrays: MeshArrays): THREE.BufferGeometry {
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(arrays.positions, 3))
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(arrays.uvs, 2))
  geometry.setAttribute('normal', new THREE.Float32BufferAttribute(arrays.normals, 3))
  geometry.setIndex(arrays.indices)
  geometry.computeBoundingSphere()
  return geometry
}

function makeRoadTexture(): THREE.CanvasTexture {
  const width = 128
  const height = 256
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')!

  ctx.fillStyle = '#26364d'
  ctx.fillRect(0, 0, width, height)

  // 细颗粒让大面积路面不显得像纯色块。
  for (let i = 0; i < 260; i++) {
    const x = Math.random() * width
    const y = Math.random() * height
    ctx.fillStyle = `rgba(255, 255, 255, ${0.015 + Math.random() * 0.02})`
    ctx.fillRect(x, y, 1 + Math.random() * 2, 1 + Math.random() * 2)
  }

  ctx.fillStyle = 'rgba(84, 117, 156, 0.55)'
  ctx.fillRect(4, 0, 3, height)
  ctx.fillRect(width - 7, 0, 3, height)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.ClampToEdgeWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.anisotropy = 4
  return texture
}

function makeArrowTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 128
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, 64, 128)
  ctx.fillStyle = 'rgba(186, 210, 238, 0.9)'
  ctx.beginPath()
  ctx.moveTo(32, 8)
  ctx.lineTo(56, 52)
  ctx.lineTo(40, 52)
  ctx.lineTo(40, 118)
  ctx.lineTo(24, 118)
  ctx.lineTo(24, 52)
  ctx.lineTo(8, 52)
  ctx.closePath()
  ctx.fill()

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.minFilter = THREE.LinearFilter
  return texture
}

function makeDashTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = 8
  canvas.height = 64
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, 8, 64)
  ctx.fillStyle = 'rgba(127, 157, 192, 0.85)'
  ctx.fillRect(0, 8, 8, 26)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.ClampToEdgeWrapping
  texture.wrapT = THREE.RepeatWrapping
  return texture
}

const PAD_DEFINITIONS: Record<string, PadDefinition> = {
  WORKSTATION: {
    create: () => new THREE.BoxGeometry(2.4, 0.04, 2.4),
    color: 0x3d6f9e,
    emissiveIntensity: 0.22,
    height: 0.04
  },
  CHARGER: {
    create: () => new THREE.CylinderGeometry(1.2, 1.2, 0.04, 28),
    color: 0x4f8a54,
    emissiveIntensity: 0.28,
    height: 0.04
  },
  BUFFER: {
    create: () => new THREE.BoxGeometry(1.8, 0.04, 1.8),
    color: 0xa87a3c,
    emissiveIntensity: 0.2,
    height: 0.04
  },
  JUNCTION: {
    create: () => new THREE.CylinderGeometry(0.9, 0.9, 0.03, 24),
    color: 0x3a4a60,
    emissiveIntensity: 0.05,
    height: 0.03
  }
}

function addNodePads(group: THREE.Group, nodes: NetNode[]) {
  const byType = new Map<string, NetNode[]>()
  for (const node of nodes) {
    const list = byType.get(node.type) ?? []
    list.push(node)
    byType.set(node.type, list)
  }

  const matrix = new THREE.Matrix4()
  for (const [type, list] of byType) {
    const definition = PAD_DEFINITIONS[type] ?? PAD_DEFINITIONS.JUNCTION
    const material = new THREE.MeshStandardMaterial({
      color: definition.color,
      emissive: definition.color,
      emissiveIntensity: definition.emissiveIntensity,
      roughness: 0.55,
      metalness: 0.15
    })
    const mesh = new THREE.InstancedMesh(definition.create(), material, list.length)
    list.forEach((node, index) => {
      matrix.makeTranslation(node.x, ROAD_Y + definition.height / 2, node.y)
      mesh.setMatrixAt(index, matrix)
    })
    mesh.instanceMatrix.needsUpdate = true
    mesh.receiveShadow = true
    mesh.name = `node-${type.toLowerCase()}`
    group.add(mesh)
  }

  const chargers = byType.get('CHARGER') ?? []
  if (chargers.length > 0) {
    const ringGeometry = new THREE.TorusGeometry(0.88, 0.045, 8, 36)
    const ringMaterial = new THREE.MeshStandardMaterial({
      color: 0x8fd47a,
      emissive: 0x8fd47a,
      emissiveIntensity: 2,
      roughness: 0.4
    })
    const rings = new THREE.InstancedMesh(ringGeometry, ringMaterial, chargers.length)
    const rotation = new THREE.Matrix4().makeRotationX(-Math.PI / 2)
    chargers.forEach((node, index) => {
      matrix.makeTranslation(node.x, ROAD_Y + 0.06, node.y).multiply(rotation)
      rings.setMatrixAt(index, matrix)
    })
    rings.instanceMatrix.needsUpdate = true
    rings.name = 'charger-rings'
    group.add(rings)
  }
}

function addNodeLabels(group: THREE.Group, nodes: NetNode[], labels: LabelFactory) {
  for (const node of nodes) {
    const color = `#${(NODE_COLORS[node.type] ?? 0x8fa3bd).toString(16).padStart(6, '0')}`
    const height = node.type === 'JUNCTION' ? 0.52 : 0.66
    const sprite = labels.create(node.id, { color, height })
    sprite.position.set(node.x, ROAD_Y + 0.5, node.y)
    sprite.name = `label-${node.id}`
    group.add(sprite)
  }
}

/** 释放一组对象；精灵材质由 LabelFactory 统一释放，这里跳过。 */
export function disposeGroup(group: THREE.Group) {
  const geometries = new Set<THREE.BufferGeometry>()
  const materials = new Set<THREE.Material>()

  group.traverse((obj) => {
    if (obj instanceof THREE.Sprite) return
    if (obj instanceof THREE.Mesh || obj instanceof THREE.Line) {
      geometries.add(obj.geometry)
      const list = Array.isArray(obj.material) ? obj.material : [obj.material]
      for (const material of list) materials.add(material)
    }
  })

  for (const material of materials) {
    const map = (material as THREE.MeshStandardMaterial).map
    map?.dispose()
    material.dispose()
  }
  for (const geometry of geometries) geometry.dispose()
  group.clear()
}
