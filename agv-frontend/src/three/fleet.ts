import * as THREE from 'three'
import type { NetEdge } from '@/api/roadnet'
import { AGV_SIZE, AGV_Y, STATUS_COLORS } from './constants'
import { laneOffsetForArc, type LaneEdge } from './lane'
import { expSmooth, PoseBuffer, type AgvPose } from './motion'
import type { LabelFactory } from './labels'

interface AgvEntry {
  group: THREE.Group
  body: THREE.Mesh
  cargo: THREE.Mesh
  beaconMaterial: THREE.MeshStandardMaterial
  ring: THREE.Mesh
  label: THREE.Sprite
  laneOffset: number
  status: string
}

const DEFAULT_STATUS_COLOR = 0x409eff

/**
 * AGV 车队：负责车辆模型、状态表现和「视觉车道」偏移。
 *
 * 位置来自 PoseBuffer 的时间采样，车队本身不推进仿真。
 */
export class AgvFleet {
  readonly group = new THREE.Group()
  private readonly buffer = new PoseBuffer()
  private readonly entries = new Map<string, AgvEntry>()
  private readonly edges: Map<string, LaneEdge> = new Map()
  private readonly bodyMaterials = new Map<string, THREE.MeshStandardMaterial>()
  private readonly sharedGeometries: THREE.BufferGeometry[] = []
  private readonly sharedMaterials: THREE.Material[] = []
  private readonly chassisGeometry: THREE.BufferGeometry
  private readonly bumperGeometry: THREE.BufferGeometry
  private readonly wheelGeometry: THREE.BufferGeometry
  private readonly cargoGeometry: THREE.BufferGeometry
  private readonly beaconGeometry: THREE.BufferGeometry
  private readonly lightGeometry: THREE.BufferGeometry
  private readonly frontBarGeometry: THREE.BufferGeometry
  private readonly ringGeometry: THREE.BufferGeometry
  private readonly blobGeometry: THREE.BufferGeometry
  private readonly blobMaterial: THREE.MeshBasicMaterial
  private readonly wheelMaterial: THREE.MeshStandardMaterial
  private readonly detailMaterial: THREE.MeshStandardMaterial
  private readonly cargoMaterial: THREE.MeshStandardMaterial
  private readonly lightMaterial: THREE.MeshStandardMaterial
  private readonly ringMaterial: THREE.MeshBasicMaterial
  private selectedId: string | null = null
  private labelsVisible = true

  constructor(private readonly labels: LabelFactory) {
    this.group.name = 'fleet'

    this.chassisGeometry = new THREE.BoxGeometry(
      AGV_SIZE.length,
      AGV_SIZE.chassisHeight,
      AGV_SIZE.width
    )
    this.bumperGeometry = new THREE.BoxGeometry(1.3, 0.14, 0.86)
    this.wheelGeometry = new THREE.CylinderGeometry(
      AGV_SIZE.wheelRadius,
      AGV_SIZE.wheelRadius,
      AGV_SIZE.wheelWidth,
      16
    )
    this.cargoGeometry = new THREE.BoxGeometry(0.9, 0.34, 0.68)
    this.beaconGeometry = new THREE.SphereGeometry(0.1, 16, 10)
    this.lightGeometry = new THREE.BoxGeometry(0.06, 0.1, 0.5)
    this.frontBarGeometry = new THREE.BoxGeometry(0.14, 0.03, 0.72)
    this.ringGeometry = new THREE.RingGeometry(0.95, 1.08, 40)
    this.blobGeometry = new THREE.CircleGeometry(0.85, 32)

    this.wheelMaterial = new THREE.MeshStandardMaterial({
      color: 0x1b2430,
      roughness: 0.85,
      metalness: 0.1
    })
    this.detailMaterial = new THREE.MeshStandardMaterial({
      color: 0x2a3a4f,
      roughness: 0.6,
      metalness: 0.3
    })
    this.cargoMaterial = new THREE.MeshStandardMaterial({
      color: 0xd99b54,
      emissive: 0xe6a23c,
      emissiveIntensity: 0.35,
      roughness: 0.6,
      metalness: 0.05
    })
    this.lightMaterial = new THREE.MeshStandardMaterial({
      color: 0xffffff,
      emissive: 0xffffff,
      emissiveIntensity: 0.8,
      roughness: 0.3
    })
    this.ringMaterial = new THREE.MeshBasicMaterial({
      color: 0x7fd4ff,
      transparent: true,
      opacity: 0.9,
      side: THREE.DoubleSide,
      depthWrite: false
    })
    this.blobMaterial = new THREE.MeshBasicMaterial({
      map: makeBlobTexture(),
      transparent: true,
      opacity: 0.6,
      depthWrite: false,
      toneMapped: false
    })

    for (const [status, color] of Object.entries(STATUS_COLORS)) {
      this.bodyMaterials.set(
        status,
        new THREE.MeshStandardMaterial({
          color,
          emissive: color,
          emissiveIntensity: status === 'WAITING' ? 0.5 : 0.3,
          roughness: 0.42,
          metalness: 0.35
        })
      )
    }

    this.sharedGeometries.push(
      this.chassisGeometry,
      this.bumperGeometry,
      this.wheelGeometry,
      this.cargoGeometry,
      this.beaconGeometry,
      this.lightGeometry,
      this.frontBarGeometry,
      this.ringGeometry,
      this.blobGeometry
    )
    this.sharedMaterials.push(
      this.wheelMaterial,
      this.detailMaterial,
      this.cargoMaterial,
      this.lightMaterial,
      this.ringMaterial,
      this.blobMaterial,
      ...this.bodyMaterials.values()
    )
  }

  /** 路网切换时同步边表，用于把 `E1+` / `E1-` 解析成车道偏移。 */
  setRoadNet(edges: Map<string, NetEdge>) {
    this.edges.clear()
    for (const [id, edge] of edges) this.edges.set(id, edge)
  }

  pushFrame(tick: number, agvs: AgvPose[], at: number) {
    this.buffer.push(tick, agvs, at)
  }

  update(dt: number, now: number) {
    const poses = this.buffer.sample(now)
    if (poses.length === 0) return

    const seen = new Set<string>()
    for (const pose of poses) {
      seen.add(pose.id)
      const entry = this.entries.get(pose.id) ?? this.createEntry(pose.id)
      const targetOffset = laneOffsetForArc(this.edges, pose.arcId)
      entry.laneOffset = expSmooth(entry.laneOffset, targetOffset, 9, dt)

      const radians = THREE.MathUtils.degToRad(pose.heading || 0)
      entry.group.position.set(
        pose.x - Math.sin(radians) * entry.laneOffset,
        AGV_Y,
        pose.y + Math.cos(radians) * entry.laneOffset
      )
      entry.group.rotation.y = -radians

      if (entry.status !== pose.status) {
        entry.status = pose.status
        entry.body.material = this.bodyMaterials.get(pose.status) ?? this.defaultBodyMaterial()
        entry.cargo.visible = pose.status === 'LOADING'
      }

      const color = STATUS_COLORS[pose.status] ?? DEFAULT_STATUS_COLOR
      entry.beaconMaterial.color.setHex(color)
      entry.beaconMaterial.emissive.setHex(color)
      entry.beaconMaterial.emissiveIntensity = beaconIntensity(pose.status, now)
      entry.ring.visible = this.selectedId === pose.id
    }

    for (const [id, entry] of this.entries) {
      if (seen.has(id)) continue
      this.removeEntry(id, entry)
    }
  }

  private defaultBodyMaterial() {
    return this.bodyMaterials.get('MOVING')!
  }

  private createEntry(id: string): AgvEntry {
    const group = new THREE.Group()
    group.name = `agv-${id}`
    group.userData.agvId = id
    group.position.set(0, AGV_Y, 0)

    const body = new THREE.Mesh(this.chassisGeometry, this.defaultBodyMaterial())
    body.position.y = AGV_SIZE.chassisY
    body.castShadow = true
    group.add(body)

    const bumper = new THREE.Mesh(this.bumperGeometry, this.detailMaterial)
    bumper.position.y = AGV_SIZE.wheelRadius + 0.02
    bumper.castShadow = true
    group.add(bumper)

    const frontBar = new THREE.Mesh(this.frontBarGeometry, this.lightMaterial)
    frontBar.position.set(0.55, AGV_SIZE.chassisY + AGV_SIZE.chassisHeight / 2 + 0.02, 0)
    group.add(frontBar)

    const cargo = new THREE.Mesh(this.cargoGeometry, this.cargoMaterial)
    cargo.position.y = AGV_SIZE.cargoY
    cargo.castShadow = true
    cargo.visible = false
    group.add(cargo)

    const wheelZ = AGV_SIZE.width / 2 - 0.05
    for (const x of [-0.45, 0.45]) {
      for (const z of [-wheelZ, wheelZ]) {
        const wheel = new THREE.Mesh(this.wheelGeometry, this.wheelMaterial)
        wheel.rotation.x = Math.PI / 2
        wheel.position.set(x, AGV_SIZE.wheelRadius, z)
        wheel.castShadow = true
        group.add(wheel)
      }
    }

    const light = new THREE.Mesh(this.lightGeometry, this.lightMaterial)
    light.position.set(AGV_SIZE.length / 2 + 0.02, AGV_SIZE.chassisY + 0.08, 0)
    group.add(light)

    const beaconMaterial = new THREE.MeshStandardMaterial({
      color: DEFAULT_STATUS_COLOR,
      emissive: DEFAULT_STATUS_COLOR,
      emissiveIntensity: 1
    })
    const beacon = new THREE.Mesh(this.beaconGeometry, beaconMaterial)
    beacon.position.set(-AGV_SIZE.length / 2 + 0.22, AGV_SIZE.beaconY, 0)
    group.add(beacon)

    const ring = new THREE.Mesh(this.ringGeometry, this.ringMaterial)
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.04
    ring.visible = false
    group.add(ring)

    const blob = new THREE.Mesh(this.blobGeometry, this.blobMaterial)
    blob.rotation.x = -Math.PI / 2
    blob.scale.set(1.05, 0.72, 1)
    blob.position.y = 0.006
    blob.renderOrder = 2
    group.add(blob)

    const label = this.labels.create(id, { height: 0.55 })
    label.position.set(0, 1.1, 0)
    label.visible = this.labelsVisible
    group.add(label)

    this.group.add(group)
    const entry: AgvEntry = {
      group,
      body,
      cargo,
      beaconMaterial,
      ring,
      label,
      laneOffset: 0,
      status: ''
    }
    this.entries.set(id, entry)
    return entry
  }

  private removeEntry(id: string, entry: AgvEntry) {
    this.group.remove(entry.group)
    entry.group.clear()
    entry.beaconMaterial.dispose()
    this.entries.delete(id)
  }

  setSelected(id: string | null) {
    this.selectedId = id
    for (const [entryId, entry] of this.entries) {
      entry.ring.visible = entryId === id
    }
  }

  /** 相机跟随需要的实时位置；返回内部向量，调用方只读。 */
  positionOf(id: string): THREE.Vector3 | null {
    return this.entries.get(id)?.group.position ?? null
  }

  pick(raycaster: THREE.Raycaster): string | null {
    const hits = raycaster.intersectObjects(this.group.children, true)
    for (const hit of hits) {
      let object: THREE.Object3D | null = hit.object
      while (object) {
        const id = object.userData.agvId
        if (typeof id === 'string') return id
        object = object.parent
      }
    }
    return null
  }

  setLabelsVisible(visible: boolean) {
    this.labelsVisible = visible
    for (const entry of this.entries.values()) entry.label.visible = visible
  }

  clear() {
    for (const [id, entry] of this.entries) this.removeEntry(id, entry)
    this.buffer.clear()
    this.selectedId = null
  }

  dispose() {
    this.clear()
    for (const geometry of this.sharedGeometries) geometry.dispose()
    for (const material of this.sharedMaterials) material.dispose()
    this.sharedGeometries.length = 0
    this.sharedMaterials.length = 0
    this.bodyMaterials.clear()
    this.edges.clear()
    this.group.clear()
  }
}

function beaconIntensity(status: string, now: number): number {
  if (status === 'WAITING') return 1.1 + Math.sin(now / 110) * 0.75
  if (status === 'CHARGING') return 0.9 + Math.sin(now / 320) * 0.35
  if (status === 'DONE') return 0.25
  return 0.9
}

/** 车底接触阴影：不依赖阴影贴图，低画质也能把车「压」在地面上。 */
function makeBlobTexture(): THREE.CanvasTexture {
  const size = 128
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  const gradient = ctx.createRadialGradient(
    size / 2,
    size / 2,
    size * 0.08,
    size / 2,
    size / 2,
    size * 0.5
  )
  gradient.addColorStop(0, 'rgba(0, 0, 0, 0.9)')
  gradient.addColorStop(0.55, 'rgba(0, 0, 0, 0.45)')
  gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  const texture = new THREE.CanvasTexture(canvas)
  texture.minFilter = THREE.LinearFilter
  return texture
}
