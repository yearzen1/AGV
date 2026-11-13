/** 三维场景画质档位，决定阴影、环境反射、泛光和像素比。 */
export type SceneQuality = 'low' | 'medium' | 'high'

const STORAGE_KEY = 'agv.scene.quality'
const DEFAULT_QUALITY: SceneQuality = 'medium'

function resolveStorage<T extends Pick<Storage, 'getItem'> | Pick<Storage, 'setItem'>>(
  storage: T | null
): T | null {
  if (storage) return storage
  try {
    return ((globalThis as { localStorage?: T }).localStorage ?? null) as T | null
  } catch {
    // 隐私模式或禁用存储时 localStorage 访问会抛异常，降级为默认档位。
    return null
  }
}

export function isSceneQuality(value: unknown): value is SceneQuality {
  return value === 'low' || value === 'medium' || value === 'high'
}

export function loadQuality(storage: Pick<Storage, 'getItem'> | null = null): SceneQuality {
  const target = resolveStorage(storage)
  if (!target) return DEFAULT_QUALITY
  try {
    const value = target.getItem(STORAGE_KEY)
    return isSceneQuality(value) ? value : DEFAULT_QUALITY
  } catch {
    return DEFAULT_QUALITY
  }
}

export function saveQuality(
  quality: SceneQuality,
  storage: Pick<Storage, 'setItem'> | null = null
): void {
  if (!isSceneQuality(quality)) return
  const target = resolveStorage(storage)
  if (!target) return
  try {
    target.setItem(STORAGE_KEY, quality)
  } catch {
    // 存储失败不影响当前会话，保持内存里的档位即可。
  }
}
