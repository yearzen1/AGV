import * as THREE from 'three'

interface LabelStyle {
  color: string
  height: number
}

const DEFAULT_STYLE: LabelStyle = { color: '#dbe6f5', height: 0.8 }

/**
 * 标签工厂：按「文本 + 颜色」缓存 CanvasTexture 和材质。
 *
 * 同一编号会在节点和车辆之间复用，频繁重建 canvas 既浪费显存，
 * 也会在车辆上下线时产生纹理泄漏。材质由工厂统一释放。
 */
export class LabelFactory {
  private readonly materials = new Map<string, THREE.SpriteMaterial>()

  create(text: string, style: Partial<LabelStyle> = {}): THREE.Sprite {
    const { color, height } = { ...DEFAULT_STYLE, ...style }
    const key = `${color}|${text}`
    let material = this.materials.get(key)
    if (!material) {
      material = new THREE.SpriteMaterial({
        map: this.makeTexture(text, color),
        transparent: true,
        depthWrite: false,
        depthTest: true,
        toneMapped: false
      })
      this.materials.set(key, material)
    }

    const sprite = new THREE.Sprite(material)
    const image = material.map?.image as HTMLCanvasElement | undefined
    const aspect = image ? image.width / image.height : 2
    sprite.scale.set(height * aspect, height, 1)
    sprite.center.set(0.5, 0)
    sprite.renderOrder = 20
    return sprite
  }

  private makeTexture(text: string, color: string): THREE.CanvasTexture {
    const font = '600 40px "Microsoft YaHei", "PingFang SC", sans-serif'
    const probe = document.createElement('canvas').getContext('2d')!
    probe.font = font
    const width = Math.ceil(probe.measureText(text).width) + 28
    const height = 56

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')!

    ctx.fillStyle = 'rgba(8, 17, 30, 0.72)'
    roundRect(ctx, 1, 1, width - 2, height - 2, 12)
    ctx.fill()

    ctx.font = font
    ctx.fillStyle = color
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(text, width / 2, height / 2 + 1)

    const texture = new THREE.CanvasTexture(canvas)
    texture.colorSpace = THREE.SRGBColorSpace
    texture.minFilter = THREE.LinearFilter
    return texture
  }

  dispose(): void {
    for (const material of this.materials.values()) {
      material.map?.dispose()
      material.dispose()
    }
    this.materials.clear()
  }
}

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
) {
  const r = Math.min(radius, width / 2, height / 2)
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + width, y, x + width, y + height, r)
  ctx.arcTo(x + width, y + height, x, y + height, r)
  ctx.arcTo(x, y + height, x, y, r)
  ctx.arcTo(x, y, x + width, y, r)
  ctx.closePath()
}
