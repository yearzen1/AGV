<script setup lang="ts">
import { provide } from 'vue'
import EditorToolbar from './roadnet/components/EditorToolbar.vue'
import EditorCanvas from './roadnet/components/EditorCanvas.vue'
import EditorSidePanel from './roadnet/components/EditorSidePanel.vue'
import EditorDialogs from './roadnet/components/EditorDialogs.vue'
import { EDITOR_KEY } from './roadnet/editorContext'
import { useRoadNetEditor, type EditorEmit, type EditorProps } from './roadnet/useRoadNetEditor'

const props = defineProps<EditorProps>()
const emit = defineEmits<EditorEmit>()
const editor = useRoadNetEditor(props, emit)
provide(EDITOR_KEY, editor)
defineExpose(editor.expose)
</script>

<template>
  <div class="editor">
    <EditorToolbar />
    <div class="workspace">
      <EditorCanvas />
      <EditorSidePanel />
    </div>
    <EditorDialogs />
  </div>
</template>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  min-height: 0;
  --map-cursor-default: default;
  --map-cursor-grab: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 24 24' fill='none' stroke-linecap='round' stroke-linejoin='round'%3E%3Cg stroke='%23ffffff' stroke-width='5'%3E%3Cpath d='M18 11V6a2 2 0 0 0-4 0v5'/%3E%3Cpath d='M14 10V4a2 2 0 0 0-4 0v6'/%3E%3Cpath d='M10 10.5V6a2 2 0 0 0-4 0v8'/%3E%3Cpath d='M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15'/%3E%3C/g%3E%3Cg stroke='%23303133' stroke-width='2.4'%3E%3Cpath d='M18 11V6a2 2 0 0 0-4 0v5'/%3E%3Cpath d='M14 10V4a2 2 0 0 0-4 0v6'/%3E%3Cpath d='M10 10.5V6a2 2 0 0 0-4 0v8'/%3E%3Cpath d='M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15'/%3E%3C/g%3E%3C/svg%3E") 16 16, grab;
  --map-cursor-grabbing: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 24 24' fill='none' stroke-linecap='round' stroke-linejoin='round'%3E%3Cg stroke='%23ffffff' stroke-width='5'%3E%3Cpath d='M8 12V9a2 2 0 0 1 4 0v3'/%3E%3Cpath d='M12 12V8a2 2 0 0 1 4 0v4'/%3E%3Cpath d='M16 12V9a2 2 0 0 1 4 0v7a6 6 0 0 1-6 6h-1.5a6 6 0 0 1-4.9-2.55l-2.2-3.1a2 2 0 0 1 3.2-2.4L11 16'/%3E%3C/g%3E%3Cg stroke='%23303133' stroke-width='2.4'%3E%3Cpath d='M8 12V9a2 2 0 0 1 4 0v3'/%3E%3Cpath d='M12 12V8a2 2 0 0 1 4 0v4'/%3E%3Cpath d='M16 12V9a2 2 0 0 1 4 0v7a6 6 0 0 1-6 6h-1.5a6 6 0 0 1-4.9-2.55l-2.2-3.1a2 2 0 0 1 3.2-2.4L11 16'/%3E%3C/g%3E%3C/svg%3E") 16 16, grabbing;
  --map-cursor-pointer: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3E%3Cpath d='M9 4 L25 18 L18 19 L21 28 L17 30 L13 21 L9 25 Z' fill='%23303133' stroke='%23ffffff' stroke-width='2.5' stroke-linejoin='round'/%3E%3C/svg%3E") 9 4, pointer;
  --map-cursor-crosshair: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'%3E%3Cg stroke='%23ffffff' stroke-width='5' stroke-linecap='round'%3E%3Cpath d='M16 5v22M5 16h22'/%3E%3C/g%3E%3Cg stroke='%23303133' stroke-width='2.2' stroke-linecap='round'%3E%3Cpath d='M16 5v22M5 16h22'/%3E%3C/g%3E%3Ccircle cx='16' cy='16' r='1.6' fill='%23303133'/%3E%3C/svg%3E") 16 16, crosshair;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  gap: 10px;
  flex: 1;
  min-height: 0;
}
</style>
