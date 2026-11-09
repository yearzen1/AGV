<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { RoadNetPayload } from '@/api/roadnet'
import type { AgvView } from '@/types'

const props = defineProps<{
  roadnet: RoadNetPayload | null
  agvs?: AgvView[]
  showGrid?: boolean
}>()

const container = ref<HTMLDivElement | null>(null)

let scene: THREE.Scene
let camera: THREE.PerspectiveCamera
let renderer: THREE.WebGLRenderer
let controls: OrbitControls
let raf = 0
let resizeObserver: ResizeObserver | null = null

const netGroup = new THREE.Group()
const agvGroup = new THREE.Group()
const pathGroup = new THREE.Group()

interface AgvMesh {
  group: THREE.Group
  target: THREE.Vector3
  targetHeading: number
  status: string
}

const agvMeshes = new Map<string, AgvMesh>()

const NODE_COLORS: Record<string, number> = {
  CHARGER: 0x67c23a,
  WORKSTATION: 0x409eff,
  BUFFER: 0xe6a23c,
  JUNCTION: 0x909399
}

const STATUS_COLORS: Record<string, number> = {
  MOVING: 0x409eff,
  WAITING: 0xf56c6c,
  LOADING: 0xe6a23c,
  IDLE: 0x909399,
  CHARGING: 0x67c23a,
  DONE: 0x303133
}

function init() {
  const el = container.value
  if (!el) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0b1526)
  scene.fog = new THREE.Fog(0x0b1526, 90, 220)

  camera = new THREE.PerspectiveCamera(50, el.clientWidth / el.clientHeight, 0.1, 1000)
  camera.position.set(25, 32, 42)

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(el.clientWidth, el.clientHeight)
  el.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.maxPolarAngle = Math.PI / 2.05
  controls.target.set(15, 0, 12)

  scene.add(new THREE.AmbientLight(0xffffff, 0.85))
  const dir = new THREE.DirectionalLight(0xffffff, 1.1)
  dir.position.set(30, 60, 20)
  scene.add(dir)

  const grid = new THREE.GridHelper(200, 100, 0x1e3350, 0x152740)
  grid.position.y = -0.02
  scene.add(grid)

  scene.add(netGroup)
  scene.add(agvGroup)
  scene.add(pathGroup)

  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(el)

  animate()
}

function resize() {
  const el = container.value
  if (!el || !renderer) return
  const width = el.clientWidth
  const height = el.clientHeight
  if (width === 0 || height === 0) return
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

function animate() {
  raf = requestAnimationFrame(animate)
  const k = 0.35
  agvMeshes.forEach((mesh) => {
    mesh.group.position.lerp(mesh.target, k)
    const current = mesh.group.rotation.y
    mesh.group.rotation.y = current + (mesh.targetHeading - current) * k
  })
  controls?.update()
  renderer?.render(scene, camera)
}

function disposeMaterial(material: THREE.Material | THREE.Material[]) {
  const list = Array.isArray(material) ? material : [material]
  for (const item of list) {
    const map = (item as THREE.MeshStandardMaterial).map
    map?.dispose()
    item.dispose()
  }
}

function disposeGroup(group: THREE.Group) {
  group.traverse((obj) => {
    if (obj instanceof THREE.Mesh || obj instanceof THREE.Line) {
      obj.geometry.dispose()
      disposeMaterial(obj.material)
    } else if (obj instanceof THREE.Sprite) {
      // 标签精灵没有 geometry，但持有 CanvasTexture，必须一起释放
      disposeMaterial(obj.material)
    }
  })
  group.clear()
}

function rebuildRoadNet(net: RoadNetPayload | null) {
  if (!scene) return
  disposeGroup(netGroup)
  if (!net || net.nodes.length === 0) return

  const nodeMap = new Map(net.nodes.map((n) => [n.id, n]))

  for (const edge of net.edges) {
    const from = nodeMap.get(edge.from)
    const to = nodeMap.get(edge.to)
    if (!from || !to) continue

    const start = new THREE.Vector3(from.x, 0.06, from.y)
    const end = new THREE.Vector3(to.x, 0.06, to.y)
    const dirVec = end.clone().sub(start)
    const len = dirVec.length()
    if (len < 0.01) continue

    const addLine = (a: THREE.Vector3, b: THREE.Vector3) => {
      const geometry = new THREE.BufferGeometry().setFromPoints([a, b])
      netGroup.add(new THREE.Line(geometry, new THREE.LineBasicMaterial({ color: 0x2f4a72 })))
    }
    const addArrow = (position: THREE.Vector3, direction: THREE.Vector3) => {
      const arrow = new THREE.Mesh(
        new THREE.ConeGeometry(0.32, 0.9, 10),
        new THREE.MeshStandardMaterial({ color: 0x3c5f92, metalness: 0.1, roughness: 0.8 })
      )
      arrow.position.copy(position)
      arrow.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction.clone().normalize())
      netGroup.add(arrow)
    }

    const dual = edge.direction === 'TWO_WAY' && edge.laneMode === 'DUAL'
    if (dual) {
      const nx = -dirVec.z / len
      const nz = dirVec.x / len
      const offset = new THREE.Vector3(nx, 0, nz).multiplyScalar(0.22)
      addLine(start.clone().add(offset), end.clone().add(offset))
      addLine(end.clone().sub(offset), start.clone().sub(offset))
      const mid = start.clone().add(end).multiplyScalar(0.5)
      addArrow(mid.clone().add(offset), dirVec)
      addArrow(mid.clone().sub(offset), dirVec.clone().negate())
    } else {
      addLine(start, end)
      const mid = start.clone().add(end).multiplyScalar(0.5)
      if (edge.direction === 'ONE_WAY') {
        addArrow(mid, dirVec)
      } else {
        // 单车道双向：一条线，两个方向各放一个锥，位置错开避免重叠
        addArrow(start.clone().lerp(end, 1 / 3), dirVec)
        addArrow(start.clone().lerp(end, 2 / 3), dirVec.clone().negate())
      }
    }
  }

  for (const node of net.nodes) {
    const color = NODE_COLORS[node.type] ?? 0x909399
    const mesh = new THREE.Mesh(
      new THREE.CylinderGeometry(0.55, 0.55, 0.5, 18),
      new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.25, roughness: 0.5 })
    )
    mesh.position.set(node.x, 0.25, node.y)
    netGroup.add(mesh)

    const sprite = makeLabel(node.id)
    sprite.position.set(node.x, 1.9, node.y)
    netGroup.add(sprite)
  }
}

function makeLabel(text: string): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 128
  canvas.height = 64
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(0,0,0,0)'
  ctx.fillRect(0, 0, 128, 64)
  ctx.font = 'bold 34px "Microsoft YaHei", sans-serif'
  ctx.fillStyle = '#dbe6f5'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 64, 32)
  const texture = new THREE.CanvasTexture(canvas)
  texture.minFilter = THREE.LinearFilter
  const material = new THREE.SpriteMaterial({ map: texture, transparent: true, depthTest: false })
  const sprite = new THREE.Sprite(material)
  sprite.scale.set(3.2, 1.6, 1)
  return sprite
}

function createAgvMesh(id: string): AgvMesh {
  const group = new THREE.Group()

  const body = new THREE.Mesh(
    new THREE.BoxGeometry(1.5, 0.55, 1.0),
    new THREE.MeshStandardMaterial({ color: 0x409eff, metalness: 0.3, roughness: 0.4 })
  )
  body.position.y = 0.45
  group.add(body)

  const nose = new THREE.Mesh(
    new THREE.ConeGeometry(0.3, 0.7, 12),
    new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xffffff, emissiveIntensity: 0.4 })
  )
  nose.rotation.z = -Math.PI / 2
  nose.position.set(1.0, 0.45, 0)
  group.add(nose)

  const sprite = makeLabel(id)
  sprite.position.set(0, 1.7, 0)
  sprite.scale.set(2.8, 1.4, 1)
  group.add(sprite)

  agvGroup.add(group)
  return { group, target: new THREE.Vector3(0, 0.45, 0), targetHeading: 0, status: 'IDLE' }
}

function updateAgvs(list: AgvView[] | undefined) {
  if (!scene) return
  const seen = new Set<string>()

  for (const agv of list ?? []) {
    seen.add(agv.id)
    let mesh = agvMeshes.get(agv.id)
    if (!mesh) {
      mesh = createAgvMesh(agv.id)
      mesh.group.position.set(agv.x, 0.45, agv.y)
      agvMeshes.set(agv.id, mesh)
    }
    mesh.target.set(agv.x, 0.45, agv.y)
    mesh.targetHeading = -THREE.MathUtils.degToRad(agv.heading || 0)

    if (mesh.status !== agv.status) {
      mesh.status = agv.status
      const color = STATUS_COLORS[agv.status] ?? 0x409eff
      const body = mesh.group.children[0] as THREE.Mesh
      const material = body.material as THREE.MeshStandardMaterial
      material.color.setHex(color)
      material.emissive = new THREE.Color(color)
      material.emissiveIntensity = agv.status === 'WAITING' ? 0.9 : 0.25
    }
  }

  for (const [id, mesh] of agvMeshes) {
    if (!seen.has(id)) {
      agvGroup.remove(mesh.group)
      disposeGroup(mesh.group)
      agvMeshes.delete(id)
    }
  }
}

/** 画一条规划路径（用于单车路径演示）。 */
function drawPath(points: Array<{ x: number; y: number }>) {
  if (!scene) return
  disposeGroup(pathGroup)
  if (!points || points.length < 2) return
  const geometry = new THREE.BufferGeometry().setFromPoints(
    points.map((p) => new THREE.Vector3(p.x, 0.35, p.y))
  )
  const material = new THREE.LineBasicMaterial({ color: 0xffd666 })
  pathGroup.add(new THREE.Line(geometry, material))
}

function clearPath() {
  if (!scene) return
  disposeGroup(pathGroup)
}

function fitView() {
  if (!props.roadnet || props.roadnet.nodes.length === 0 || !camera) return
  const xs = props.roadnet.nodes.map((n) => n.x)
  const ys = props.roadnet.nodes.map((n) => n.y)
  const cx = (Math.min(...xs) + Math.max(...xs)) / 2
  const cy = (Math.min(...ys) + Math.max(...ys)) / 2
  const span = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys), 10)
  controls.target.set(cx, 0, cy)
  camera.position.set(cx + span * 0.6, span * 1.1, cy + span * 1.1)
  controls.update()
}

watch(
  () => props.roadnet,
  (net) => {
    rebuildRoadNet(net)
    fitView()
  },
  { deep: false }
)

watch(
  () => props.agvs,
  (list) => updateAgvs(list),
  { deep: true }
)

onMounted(() => {
  init()
  rebuildRoadNet(props.roadnet)
  fitView()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  resizeObserver?.disconnect()
  controls?.dispose()
  disposeGroup(netGroup)
  disposeGroup(agvGroup)
  disposeGroup(pathGroup)
  renderer?.dispose()
  if (renderer?.domElement.parentElement) {
    renderer.domElement.parentElement.removeChild(renderer.domElement)
  }
})

defineExpose({ drawPath, clearPath, fitView })
</script>

<template>
  <div ref="container" class="scene"></div>
</template>

<style scoped>
.scene {
  width: 100%;
  height: 100%;
  min-height: 420px;
  border-radius: 8px;
  overflow: hidden;
  background: #0b1526;
}
</style>
