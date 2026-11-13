import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/examples/jsm/postprocessing/OutputPass.js'
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js'
import { COLORS, QUALITY_SETTINGS } from './constants'
import type { SceneQuality } from './quality'

interface Tween {
  fromPosition: THREE.Vector3
  toPosition: THREE.Vector3
  fromTarget: THREE.Vector3
  toTarget: THREE.Vector3
  startedAt: number
  duration: number
}

/**
 * 三维视图：渲染器、相机、光照、地面、交互和画质档位。
 *
 * 场景内容（路网、车队）由调用方挂到 `scene` 上；本类只负责「怎么画」。
 * 每帧先回调 `onFrame`（让车队更新姿态），再处理相机补间、跟随和渲染。
 */
export class SceneViewer {
  readonly scene = new THREE.Scene()
  readonly camera: THREE.PerspectiveCamera
  readonly renderer: THREE.WebGLRenderer
  readonly controls: OrbitControls

  onFrame?: (dt: number, now: number) => void
  onSelect?: (id: string | null) => void
  onFollowChange?: (id: string | null) => void
  onViewModeChange?: (topView: boolean) => void
  followProvider?: (id: string) => THREE.Vector3 | null

  private readonly container: HTMLElement
  private readonly ground: THREE.Mesh
  private readonly groundTexture: THREE.CanvasTexture
  private readonly vignetteTexture: THREE.CanvasTexture
  private readonly hemisphere: THREE.HemisphereLight
  private readonly sun: THREE.DirectionalLight
  private readonly resizeObserver: ResizeObserver
  private readonly raycaster = new THREE.Raycaster()
  private readonly pointer = new THREE.Vector2()
  private readonly followDelta = new THREE.Vector3()

  private composer: EffectComposer | null = null
  private bloomPass: UnrealBloomPass | null = null
  private environmentTexture: THREE.Texture | null = null
  private quality: SceneQuality
  private bounds = new THREE.Box3(new THREE.Vector3(-5, 0, -5), new THREE.Vector3(5, 0, 5))
  private span = 10
  private shadowMapSize = 0

  private pickTargets: THREE.Object3D | null = null
  private followId: string | null = null
  private topView = false
  private savedView: { position: THREE.Vector3; target: THREE.Vector3 } | null = null
  private tween: Tween | null = null

  private animationFrame = 0
  private lastFrameTime = 0
  private pointerDown: { x: number; y: number; time: number } | null = null

  constructor(container: HTMLElement, quality: SceneQuality) {
    this.container = container
    this.quality = quality

    this.scene.background = new THREE.Color(COLORS.background)
    this.scene.fog = new THREE.Fog(COLORS.background, 90, 220)

    this.camera = new THREE.PerspectiveCamera(
      50,
      Math.max(container.clientWidth, 1) / Math.max(container.clientHeight, 1),
      0.1,
      2000
    )
    this.camera.position.set(25, 32, 42)

    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
    this.renderer.outputColorSpace = THREE.SRGBColorSpace
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = 1.05
    this.renderer.setSize(
      Math.max(container.clientWidth, 1),
      Math.max(container.clientHeight, 1)
    )
    this.renderer.domElement.classList.add('scene-canvas')
    container.appendChild(this.renderer.domElement)

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.08
    this.controls.maxPolarAngle = Math.PI / 2.05
    this.controls.minDistance = 2
    this.controls.maxDistance = 400
    this.controls.target.set(0, 0, 0)

    this.hemisphere = new THREE.HemisphereLight(0x9fc4ff, 0x0a1120, 0.6)
    this.scene.add(this.hemisphere)

    this.sun = new THREE.DirectionalLight(0xffffff, 2.8)
    this.sun.position.set(30, 60, 20)
    this.sun.castShadow = true
    this.scene.add(this.sun)
    this.scene.add(this.sun.target)

    this.groundTexture = makeGridTexture()
    this.vignetteTexture = makeVignetteTexture()
    this.ground = new THREE.Mesh(
      new THREE.PlaneGeometry(1, 1),
      new THREE.MeshStandardMaterial({
        map: this.groundTexture,
        alphaMap: this.vignetteTexture,
        transparent: true,
        roughness: 1,
        metalness: 0,
        color: 0xffffff
      })
    )
    this.ground.rotation.x = -Math.PI / 2
    this.ground.receiveShadow = true
    this.ground.name = 'ground'
    this.scene.add(this.ground)

    this.renderer.domElement.addEventListener('pointerdown', this.handlePointerDown)
    this.renderer.domElement.addEventListener('pointerup', this.handlePointerUp)
    this.controls.addEventListener('start', this.handleControlStart)

    this.resizeObserver = new ResizeObserver(() => this.resize())
    this.resizeObserver.observe(container)

    this.applyQuality(quality)
    this.applyBounds()
    this.resize()
  }

  start() {
    if (this.animationFrame) return
    this.lastFrameTime = 0
    this.animationFrame = requestAnimationFrame(this.animate)
  }

  stop() {
    if (!this.animationFrame) return
    cancelAnimationFrame(this.animationFrame)
    this.animationFrame = 0
  }

  setRoadNet(bounds: THREE.Box3) {
    this.bounds.copy(bounds)
    this.applyBounds()
  }

  fitView() {
    if (this.topView) this.setTopView(false)
    const center = this.bounds.getCenter(new THREE.Vector3())
    const span = this.span
    this.tween = null
    this.controls.target.set(center.x, 0, center.z)
    this.camera.position.set(center.x + span * 0.6, span * 0.9, center.z + span * 0.95)
    this.camera.up.set(0, 1, 0)
    this.controls.update()
  }

  setQuality(quality: SceneQuality) {
    if (quality === this.quality) return
    this.quality = quality
    this.applyQuality(quality)
  }

  getQuality(): SceneQuality {
    return this.quality
  }

  setPickTargets(target: THREE.Object3D | null) {
    this.pickTargets = target
  }

  setFollow(id: string | null) {
    if (id === this.followId) return
    this.followId = id
    this.onFollowChange?.(id)
  }

  getFollowId(): string | null {
    return this.followId
  }

  setTopView(on: boolean) {
    if (on === this.topView) return
    this.topView = on
    this.onViewModeChange?.(on)

    if (on) {
      const center = this.controls.target.clone()
      this.savedView = {
        position: this.camera.position.clone(),
        target: center.clone()
      }
      this.camera.up.set(0, 0, -1)
      this.controls.enableRotate = false
      this.startTween(
        new THREE.Vector3(center.x, Math.max(this.span * 1.5, 12), center.z + 0.01),
        center,
        450
      )
    } else {
      this.controls.enableRotate = true
      this.camera.up.set(0, 1, 0)
      if (this.savedView) {
        this.startTween(this.savedView.position, this.savedView.target, 450)
        this.savedView = null
      }
    }
  }

  isTopView(): boolean {
    return this.topView
  }

  private applyBounds() {
    const center = this.bounds.getCenter(new THREE.Vector3())
    const size = this.bounds.getSize(new THREE.Vector3())
    this.span = Math.max(size.x, size.z, 10)

    const groundSize = Math.max(this.span * 3, 30)
    this.ground.scale.set(groundSize, groundSize, 1)
    this.ground.position.set(center.x, 0, center.z)
    this.groundTexture.repeat.set(groundSize / 2, groundSize / 2)

    this.scene.fog = new THREE.Fog(COLORS.background, this.span * 2.2, this.span * 5.5)
    this.camera.near = Math.max(0.1, this.span * 0.004)
    this.camera.far = this.span * 14 + 200
    this.camera.updateProjectionMatrix()

    this.controls.minDistance = Math.max(1.5, this.span * 0.08)
    this.controls.maxDistance = Math.max(this.span * 4, 40)

    const extent = this.span * 0.9 + 3
    this.sun.position.set(
      center.x + this.span * 0.8,
      this.span * 1.6 + 10,
      center.z + this.span * 0.6
    )
    this.sun.target.position.set(center.x, 0, center.z)
    this.sun.target.updateMatrixWorld()
    const shadowCamera = this.sun.shadow.camera
    shadowCamera.left = -extent
    shadowCamera.right = extent
    shadowCamera.top = extent
    shadowCamera.bottom = -extent
    shadowCamera.near = 0.5
    shadowCamera.far = this.span * 5 + 40
    shadowCamera.updateProjectionMatrix()
  }

  private applyQuality(quality: SceneQuality) {
    const settings = QUALITY_SETTINGS[quality]
    const pixelRatio = Math.min(window.devicePixelRatio || 1, settings.pixelRatio)
    this.renderer.setPixelRatio(pixelRatio)
    this.composer?.setPixelRatio(pixelRatio)

    this.renderer.shadowMap.enabled = settings.shadows
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap
    this.sun.castShadow = settings.shadows
    this.setShadowMapSize(settings.shadowMapSize)

    this.setEnvironment(settings.environment)
    this.setBloom(settings.bloom)
  }

  private setShadowMapSize(size: number) {
    if (this.shadowMapSize === size) return
    this.shadowMapSize = size
    this.sun.shadow.mapSize.set(size, size)
    this.sun.shadow.map?.dispose()
    this.sun.shadow.map = null
  }

  private setEnvironment(enabled: boolean) {
    if (enabled) {
      if (this.environmentTexture) return
      const generator = new THREE.PMREMGenerator(this.renderer)
      const room = new RoomEnvironment()
      this.environmentTexture = generator.fromScene(room, 0.04).texture
      room.dispose()
      generator.dispose()
      this.scene.environment = this.environmentTexture
      this.scene.environmentIntensity = 0.45
      return
    }
    if (!this.environmentTexture) return
    this.scene.environment = null
    this.environmentTexture.dispose()
    this.environmentTexture = null
  }

  private setBloom(enabled: boolean) {
    if (!enabled) {
      this.disposeComposer()
      return
    }
    if (this.composer) return
    const width = Math.max(this.container.clientWidth, 1)
    const height = Math.max(this.container.clientHeight, 1)
    const target = new THREE.WebGLRenderTarget(1, 1, {
      type: THREE.HalfFloatType,
      samples: 4
    })
    const composer = new EffectComposer(this.renderer, target)
    composer.setPixelRatio(this.renderer.getPixelRatio())
    composer.setSize(width, height)
    composer.addPass(new RenderPass(this.scene, this.camera))
    this.bloomPass = new UnrealBloomPass(new THREE.Vector2(width, height), 0.32, 0.35, 1.1)
    composer.addPass(this.bloomPass)
    composer.addPass(new OutputPass())
    this.composer = composer
  }

  private disposeComposer() {
    if (!this.composer) return
    if (this.composer) {
      for (const pass of this.composer.passes) {
        const disposable = pass as unknown as { dispose?: () => void }
        disposable.dispose?.()
      }
    }
    this.composer.dispose()
    this.composer = null
    this.bloomPass = null
    this.composer = null
    this.bloomPass = null
  }

  private resize() {
    const width = this.container.clientWidth
    const height = this.container.clientHeight
    if (width === 0 || height === 0) return
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(width, height)
    this.composer?.setSize(width, height)
  }

  private animate = (now: number) => {
    this.animationFrame = requestAnimationFrame(this.animate)
    const dt = this.lastFrameTime ? Math.min((now - this.lastFrameTime) / 1000, 0.1) : 1 / 60
    this.lastFrameTime = now

    this.onFrame?.(dt, now)
    this.applyTween(now)
    this.applyFollow()
    this.controls.update()

    if (this.composer) this.composer.render(dt)
    else this.renderer.render(this.scene, this.camera)
  }

  private startTween(toPosition: THREE.Vector3, toTarget: THREE.Vector3, duration: number) {
    this.tween = {
      fromPosition: this.camera.position.clone(),
      toPosition,
      fromTarget: this.controls.target.clone(),
      toTarget,
      startedAt: performance.now(),
      duration: Math.max(1, duration)
    }
  }

  private applyTween(now: number) {
    if (!this.tween) return
    const progress = Math.min(Math.max((now - this.tween.startedAt) / this.tween.duration, 0), 1)
    const eased = progress < 0.5 ? 2 * progress * progress : 1 - (-2 * progress + 2) ** 2 / 2
    this.camera.position.lerpVectors(this.tween.fromPosition, this.tween.toPosition, eased)
    this.controls.target.lerpVectors(this.tween.fromTarget, this.tween.toTarget, eased)
    if (progress >= 1) this.tween = null
  }

  private applyFollow() {
    if (!this.followId || !this.followProvider || this.tween) return
    const target = this.followProvider(this.followId)
    if (!target) {
      this.setFollow(null)
      return
    }
    this.followDelta.copy(target).sub(this.controls.target)
    this.camera.position.add(this.followDelta)
    this.controls.target.copy(target)
  }

  private handleControlStart = () => {
    this.tween = null
    if (this.followId) this.setFollow(null)
  }

  private handlePointerDown = (event: PointerEvent) => {
    if (event.button !== 0) return
    this.pointerDown = { x: event.clientX, y: event.clientY, time: performance.now() }
  }

  private handlePointerUp = (event: PointerEvent) => {
    if (event.button !== 0 || !this.pointerDown) return
    const moved = Math.hypot(event.clientX - this.pointerDown.x, event.clientY - this.pointerDown.y)
    const elapsed = performance.now() - this.pointerDown.time
    this.pointerDown = null
    if (moved > 6 || elapsed > 500) return

    const id = this.pickAgv(event)
    this.onSelect?.(id)
    if (id && id === this.followId) this.setFollow(null)
    else this.setFollow(id)
  }

  private pickAgv(event: PointerEvent): string | null {
    if (!this.pickTargets) return null
    const rect = this.renderer.domElement.getBoundingClientRect()
    if (rect.width === 0 || rect.height === 0) return null
    this.pointer.set(
      ((event.clientX - rect.left) / rect.width) * 2 - 1,
      -((event.clientY - rect.top) / rect.height) * 2 + 1
    )
    this.raycaster.setFromCamera(this.pointer, this.camera)
    const hits = this.raycaster.intersectObjects(this.pickTargets.children, true)
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

  dispose() {
    this.stop()
    this.resizeObserver.disconnect()
    this.renderer.domElement.removeEventListener('pointerdown', this.handlePointerDown)
    this.renderer.domElement.removeEventListener('pointerup', this.handlePointerUp)
    this.controls.removeEventListener('start', this.handleControlStart)
    this.controls.dispose()
    this.disposeComposer()
    this.setEnvironment(false)

    this.ground.geometry.dispose()
    const groundMaterial = this.ground.material as THREE.MeshStandardMaterial
    groundMaterial.dispose()
    this.groundTexture.dispose()
    this.vignetteTexture.dispose()

    this.renderer.dispose()
    this.renderer.forceContextLoss()
    if (this.renderer.domElement.parentElement) {
      this.renderer.domElement.parentElement.removeChild(this.renderer.domElement)
    }
  }
}

/** 地面网格：一张 2 米 × 2 米的瓦片，靠 repeat 铺满整个地面。 */
function makeGridTexture(): THREE.CanvasTexture {
  const canvas = document.createElement('canvas')
  canvas.width = 128
  canvas.height = 128
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = `#${COLORS.ground.toString(16).padStart(6, '0')}`
  ctx.fillRect(0, 0, 128, 128)
  ctx.strokeStyle = `#${COLORS.gridLine.toString(16).padStart(6, '0')}`
  ctx.lineWidth = 1
  ctx.strokeRect(0.5, 0.5, 127, 127)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.RepeatWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.anisotropy = 4
  return texture
}

/** 径向渐隐的 alpha 贴图，让方形地面在远处自然消失。 */
function makeVignetteTexture(): THREE.CanvasTexture {
  const size = 512
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  const gradient = ctx.createRadialGradient(
    size / 2,
    size / 2,
    size * 0.12,
    size / 2,
    size / 2,
    size * 0.5
  )
  gradient.addColorStop(0, '#ffffff')
  gradient.addColorStop(0.62, '#b4b4b4')
  gradient.addColorStop(1, '#000000')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  const texture = new THREE.CanvasTexture(canvas)
  texture.minFilter = THREE.LinearFilter
  return texture
}
