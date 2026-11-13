<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import type { RoadNetPayload } from '@/api/roadnet'
import type { AgvView } from '@/types'
import { AgvFleet } from '@/three/fleet'
import { LabelFactory } from '@/three/labels'
import { loadQuality, type SceneQuality } from '@/three/quality'
import { buildRoadNet, disposeGroup, type RoadNetRender } from '@/three/road'
import { SceneViewer } from '@/three/viewer'
import type { AgvPose } from '@/three/motion'
import { COLORS } from '@/three/constants'

const props = withDefaults(
  defineProps<{
    roadnet: RoadNetPayload | null
    agvs?: AgvView[]
    tick?: number
    quality?: SceneQuality
    showLabels?: boolean
    selectedId?: string | null
  }>(),
  {
    showLabels: true,
    selectedId: null
  }
)

const emit = defineEmits<{
  select: [id: string | null]
  viewMode: [topView: boolean]
}>()

const container = ref<HTMLDivElement | null>(null)
const pathGroup = new THREE.Group()

let viewer: SceneViewer | null = null
let fleet: AgvFleet | null = null
let labels: LabelFactory | null = null
let netRender: RoadNetRender | null = null

function init() {
  const element = container.value
  if (!element) return

  labels = new LabelFactory()
  viewer = new SceneViewer(element, props.quality ?? loadQuality())
  fleet = new AgvFleet(labels)

  viewer.scene.add(fleet.group)
  viewer.scene.add(pathGroup)
  viewer.setPickTargets(fleet.group)
  viewer.followProvider = (id) => fleet?.positionOf(id) ?? null
  viewer.onFrame = (dt, now) => fleet?.update(dt, now)
  viewer.onSelect = (id) => {
    fleet?.setSelected(id)
    emit('select', id)
  }
  viewer.onViewModeChange = (topView) => emit('viewMode', topView)
  viewer.start()

  applyLabels()
}

function rebuildRoadNet(net: RoadNetPayload | null) {
  if (!viewer || !fleet) return
  if (netRender) {
    viewer.scene.remove(netRender.group)
    netRender.dispose()
    netRender = null
  }
  fleet.clear()
  fleet.setRoadNet(new Map())
  clearPath()

  if (!net || net.nodes.length === 0) return

  netRender = buildRoadNet(net, labels!)
  viewer.scene.add(netRender.group)
  fleet.setRoadNet(netRender.edges)
  fleet.setLabelsVisible(props.showLabels)
  viewer.setRoadNet(netRender.bounds)
  viewer.fitView()
  applyLabels()
}

function applyLabels() {
  const visible = props.showLabels
  fleet?.setLabelsVisible(visible)
  netRender?.group.traverse((object) => {
    if (object instanceof THREE.Sprite) object.visible = visible
  })
}

/** 画一条规划路径（用于单车路径演示）。 */
function drawPath(points: Array<{ x: number; y: number }>) {
  if (!viewer) return
  clearPath()
  if (!points || points.length < 2) return
  const geometry = new THREE.BufferGeometry().setFromPoints(
    points.map((point) => new THREE.Vector3(point.x, 0.35, point.y))
  )
  const material = new THREE.LineBasicMaterial({ color: COLORS.path })
  pathGroup.add(new THREE.Line(geometry, material))
}

function clearPath() {
  disposeGroup(pathGroup)
}

function fitView() {
  viewer?.fitView()
}

function setTopView(on: boolean) {
  viewer?.setTopView(on)
}

function focus(id: string | null) {
  fleet?.setSelected(id)
  viewer?.setFollow(id)
}

function setQuality(quality: SceneQuality) {
  viewer?.setQuality(quality)
}

function pushFrame(tick: number | undefined, list: AgvView[] | undefined) {
  if (!fleet || tick === undefined) return
  if (!list || list.length === 0) {
    fleet.clear()
    return
  }
  fleet.pushFrame(tick, list as AgvPose[], performance.now())
}

watch(
  () => props.roadnet,
  (net) => rebuildRoadNet(net),
  { deep: false }
)

watch(
  [() => props.tick, () => props.agvs],
  ([tick, list]) => pushFrame(tick, list ?? undefined),
  { deep: false }
)

watch(
  () => props.quality,
  (quality) => {
    if (quality) viewer?.setQuality(quality)
  }
)

watch(
  () => props.showLabels,
  () => applyLabels()
)

watch(
  () => props.selectedId,
  (id) => fleet?.setSelected(id ?? null)
)

onMounted(() => {
  init()
  rebuildRoadNet(props.roadnet)
  pushFrame(props.tick, props.agvs)
})

onBeforeUnmount(() => {
  fleet?.dispose()
  if (netRender && viewer) viewer.scene.remove(netRender.group)
  netRender?.dispose()
  netRender = null
  clearPath()
  labels?.dispose()
  viewer?.dispose()
  fleet = null
  labels = null
  viewer = null
})

defineExpose({ drawPath, clearPath, fitView, setTopView, focus, setQuality })
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

.scene :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
