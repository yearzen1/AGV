import { inject, type InjectionKey } from 'vue'
import type { useRoadNetEditor } from './useRoadNetEditor'

export type EditorContext = ReturnType<typeof useRoadNetEditor>

export const EDITOR_KEY: InjectionKey<EditorContext> = Symbol('RoadNetEditor')

export function useEditorContext(): EditorContext {
  const context = inject(EDITOR_KEY)
  if (!context) throw new Error('RoadNetEditor context is not provided')
  return context
}
