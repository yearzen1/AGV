/** 三维场景画质档位，决定阴影、环境反射、泛光和像素比。 */
export type SceneQuality = 'low' | 'medium' | 'high'

const STORAGE_KEY = 'agv.scene.quality'

export function isSceneQuality(_value: unknown): _value is SceneQuality {
  throw new Error('not implemented')
}

export function loadQuality(_storage: Pick<Storage, 'getItem'> | null = null): SceneQuality {
  throw new Error('not implemented')
}

export function saveQuality(
  _quality: SceneQuality,
  _storage: Pick<Storage, 'setItem'> | null = null
): void {
  throw new Error('not implemented')
}
