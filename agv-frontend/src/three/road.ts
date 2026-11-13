import * as THREE from 'three'
import type { NetEdge, NetNode, RoadNetPayload } from '@/api/roadnet'
import { LANE, NODE_COLORS, ROAD_Y } from './constants'
import { lanesForEdge, type LanePoint } from './lane'
import type { LabelFactory } from './labels'

export interface RoadNetRender {
  group: THREE.Group
  edges: Map<string, NetEdge>
  bounds: THREE.Box3
  dispose(): void
}

interface PadDefinition {
  geometry: THREE.BufferGeometry
  color: number
  emissiveIntensity: number
  height: number
}

/**
 * 把路网载荷转换成三维场景：实体路面、车道分隔线、按类型实例化的节点地台和编号。
 *
 * 路面按「行驶方向」铺装，双车道各铺一条，UV 的 v 方向就是前进方向，
 * 因此纹理里的箭头天然指向通行方向，不再需要每条边放锥体。
 */
export function buildRoadNet(net: RoadNetPayload, labels: LabelFactory): RoadNetRender {
  const group = new THREE.Group()
  group.name = 'roadnet'
  const edges = new Map(net.edges.map((edge) => [edge.id, edge]))
  const bounds = computeBounds(net.nodes)
  const nodeMap = new Map(net.nodes.map((node) => [node.id, node]))

  const ribbons: THREE.BufferGeometry[] = []
  const dividers: THREE.BufferGeometry[] = []

  for (const edge of net.edges) {
    const from = nodeMap.get(edge.from)
    const to = nodeMap.get(edge.to)
    if (!from || !to) continue
    const start: LanePoint = { x: from.x, y: from.y }
    const end: LanePoint = { x: to.x, y: to.y }
    if (Math.hypot(end.x - start.x, end.y - start.y) < 0.01) continue

    const lanes = lanesForEdge(edge, start, end)
    for (const lane of lanes) {
      const geometry = buildRibbon(lane.start, lane.end, LANE.width, lane.offset, ROAD_Y)
      if (geometry) ribbons.push(geometry)
    }
    if (lanes.length === 2) {
      const divider = buildRibbon(start, end, 0.12, 0, ROAD_Y + 0.012)
      if (divider) dividers.push(divider)
    }
  }

  if (ribbons.length > 0) {
    const road = new THREE.Mesh(
      mergeGeometries(ribbons),
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
    for (const geometry of ribbons) geometry.dispose()
  }

  if (dividers.length > 0) {
    const divider = new THREE.Mesh(
      mergeGeometries(dividers),
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
    for (const geometry of dividers) geometry.dispose()
  }

  addNodePads(group, net.nodes)
  addNodeLabels(group, net.nodes, labels)

  return {
    group,
    edges,
    bounds,
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

/** 生成一条沿 start→end 的路面带；offset 是前进方向右侧的偏移。 */
function buildRibbon(
  start: LanePoint,
  end: LanePoint,
  width: number,
  offset: number,
  height: number
): THREE.BufferGeometry | null {
  const dx = end.x - start.x
  const dy = end.y - start.y
  const length = Math.hypot(dx, dy)
  if (length < 0.01) return null

  const dirX = dx / length
  const dirY = dy / length
  // 二维里「前进方向的右侧」是 (dy, -dx)，映射到三维就是 (x + rightX, z + rightY)。
  const rightX = dirY
  const rightY = -dirX
  const half = width / 2

  const a = new THREE.Vector3(start.x + rightX * offset, height, start.y + rightY * offset)
  const b = new THREE.Vector3(end.x + rightX * offset, height, end.y + rightY * offset)

  const aLeft = a.clone().addScaledVector(new THREE.Vector3(rightX, 0, rightY), -half)
  const aRight = a.clone().addScaledVector(new THREE.Vector3(rightX, 0, rightY), half)
  const bLeft = b.clone().addScaledVector(new THREE.Vector3(rightX, 0, rightY), -half)
  const bRight = b.clone().addScaledVector(new THREE.Vector3(rightX, 0, rightY), half)

  const positions = new Float32Array([
    aLeft.x, aLeft.y, aLeft.z,
    aRight.x, aRight.y, aRight.z,
    bLeft.x, bLeft.y, bLeft.z,
    bRight.x, bRight.y, bRight.z
  ])
  const uvs = new Float32Array([
    0, 0,
    1, 0,
    0, length / LANE.arrowTileMeters,
    1, length / LANE.arrowTileMeters
  ])
  const normals = new Float32Array([
    0, 1, 0,
    0, 1, 0,
    0, 1, 0,
    0, 1, 0
  ])
  const indices = [0, 2, 1, 1, 2, 3]

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('uv', new THREE.BufferAttribute(uvs, 2))
  geometry.setAttribute('normal', new THREE.BufferAttribute(normals, 3))
  geometry.setIndex(indices)
  geometry.computeBoundingSphere()
  return geometry
}

/** 把多个路段合并成一个几何体，把 draw call 从「每条边一次」压到常数级。 */
function mergeGeometries(geometries: THREE.BufferGeometry[]): THREE.BufferGeometry {
  const merged = new THREE.BufferGeometry()
  const vertexCount = geometries.reduce(
    (sum, geometry) => sum + geometry.getAttribute('position').count,
    0
  )
  const positions = new Float32Array(vertexCount * 3)
  const uvs = new Float32Array(vertexCount * 2)
  const normals = new Float32Array(vertexCount * 3)
  const indices: number[] = []

  let vertexOffset = 0
  for (const geometry of geometries) {
    const position = geometry.getAttribute('position') as THREE.BufferAttribute
    const uv = geometry.getAttribute('uv') as THREE.BufferAttribute
    const normal = geometry.getAttribute('normal') as THREE.BufferAttribute
    positions.set(position.array as Float32Array, vertexOffset * 3)
    uvs.set(uv.array as Float32Array, vertexOffset * 2)
    normals.set(normal.array as Float32Array, vertexOffset * 3)

    const index = geometry.getIndex()
    if (index) {
      for (let i = 0; i < index.count; i++) {
        indices.push(index.getX(i) + vertexOffset)
      }
    }
    vertexOffset += position.count
  }

  merged.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  merged.setAttribute('uv', new THREE.BufferAttribute(uvs, 2))
  merged.setAttribute('normal', new THREE.BufferAttribute(normals, 3))
  merged.setIndex(indices)
  merged.computeBoundingSphere()
  return merged
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

  ctx.fillStyle = 'rgba(127, 157, 192, 0.5)'
  ctx.beginPath()
  ctx.moveTo(width / 2, 58)
  ctx.lineTo(width / 2 + 17, 96)
  ctx.lineTo(width / 2 + 6, 96)
  ctx.lineTo(width / 2 + 6, 188)
  ctx.lineTo(width / 2 - 6, 188)
  ctx.lineTo(width / 2 - 6, 96)
  ctx.lineTo(width / 2 - 17, 96)
  ctx.closePath()
  ctx.fill()

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.ClampToEdgeWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.anisotropy = 4
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

const PAD_DEFINITIONS: Record<string, Omit<PadDefinition, 'geometry'> & { create: () => THREE.BufferGeometry }> = {
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
