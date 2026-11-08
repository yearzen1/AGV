<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RoadNetEditor from '@/components/RoadNetEditor.vue'
import RoadNetScene from '@/components/RoadNetScene.vue'
import {
  createRoadNet,
  deleteRoadNet,
  getRoadNet,
  listRoadNets,
  updateRoadNet,
  validateRoadNet,
  type RoadNetPayload,
  type RoadNetSummary,
  type ValidationError
} from '@/api/roadnet'
import { getTaskSet, listTaskSets, type TaskDto, type TaskSetSummary } from '@/api/sim'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const roadnets = ref<RoadNetSummary[]>([])
const taskSets = ref<TaskSetSummary[]>([])
const currentId = ref<number | null>(null)
const name = ref('')
const isTemplate = ref(false)
const payload = ref<RoadNetPayload>({ nodes: [], edges: [], width: 50, height: 34 })
const errors = ref<ValidationError[]>([])
const editorRef = ref<InstanceType<typeof RoadNetEditor> | null>(null)
const loading = ref(false)
const validating = ref(false)
const autoValidate = ref(true)
const dirty = ref(false)
const previewMode = ref(true)
const fileInput = ref<HTMLInputElement | null>(null)

const selectedTaskSetId = ref<number | null>(null)
const showTaskOverlay = ref(false)
const overlayTasks = ref<TaskDto[]>([])

const preview3DVisible = ref(false)
const preview3DPayload = ref<RoadNetPayload | null>(null)

let validateTimer: number | undefined
let draftTimer: number | undefined

const DEFAULT_SIZE = { width: 50, height: 34 }

function draftKey(id: number | null) {
  return id === null ? 'agv_roadnet_draft_new' : `agv_roadnet_draft_${id}`
}

function isEditorDirty() {
  return editorRef.value?.isDirty() ?? false
}

function readDraft(id: number | null) {
  try {
    const raw = localStorage.getItem(draftKey(id))
    if (!raw) return null
    return JSON.parse(raw) as { payload: RoadNetPayload; name: string; savedAt: number }
  } catch {
    return null
  }
}

function saveDraft() {
  if (!editorRef.value) return
  const data = {
    payload: editorRef.value.getPayload(),
    name: name.value,
    savedAt: Date.now()
  }
  localStorage.setItem(draftKey(currentId.value), JSON.stringify(data))
}

function clearDraft(id: number | null) {
  localStorage.removeItem(draftKey(id))
}

function samePayload(left: RoadNetPayload, right: RoadNetPayload) {
  return JSON.stringify(left) === JSON.stringify(right)
}

async function loadList() {
  try {
    roadnets.value = await listRoadNets()
  } catch (error: any) {
    ElMessage.error(error.message || '加载路网列表失败')
  }
}

async function loadTaskSets() {
  try {
    taskSets.value = await listTaskSets()
    if (taskSets.value.length > 0) {
      selectedTaskSetId.value = taskSets.value[0].id
      await loadOverlayTasks()
    }
  } catch {
    taskSets.value = []
  }
}

async function loadOverlayTasks() {
  if (!selectedTaskSetId.value) {
    overlayTasks.value = []
    return
  }
  try {
    const detail = await getTaskSet(selectedTaskSetId.value)
    overlayTasks.value = detail.tasks
  } catch {
    overlayTasks.value = []
  }
}

function onTaskSetChange() {
  void loadOverlayTasks()
}

function onTaskOverlayToggle() {
  if (showTaskOverlay.value) void loadOverlayTasks()
}

async function confirmDiscard() {
  if (!isEditorDirty()) return true
  try {
    await ElMessageBox.confirm('当前路网有未保存修改，确定放弃吗？', '未保存修改', {
      type: 'warning',
      confirmButtonText: '放弃修改',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}

async function open(id: number) {
  if (currentId.value === id) return
  const wasDirty = isEditorDirty()
  if (!(await confirmDiscard())) return
  if (wasDirty) clearDraft(currentId.value)
  loading.value = true
  try {
    const detail = await getRoadNet(id)
    const summary = roadnets.value.find((item) => item.id === id)
    currentId.value = id
    name.value = summary?.name ?? `路网 ${id}`
    isTemplate.value = !!summary?.isTemplate
    payload.value = detail
    editorRef.value?.setPayload(detail)
    previewMode.value = true
    errors.value = []
    dirty.value = false

    const draft = readDraft(id)
    if (draft && !samePayload(draft.payload, detail)) {
      try {
        await ElMessageBox.confirm(
          `检测到 ${new Date(draft.savedAt).toLocaleString()} 的本地草稿，是否恢复？`,
          '恢复草稿',
          { confirmButtonText: '恢复', cancelButtonText: '忽略' }
        )
        name.value = draft.name || name.value
        editorRef.value?.setPayload(draft.payload)
        editorRef.value?.markDirty()
        previewMode.value = false
        dirty.value = true
      } catch {
        clearDraft(id)
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '读取路网失败')
  } finally {
    loading.value = false
  }
}

async function createNew() {
  const wasDirty = isEditorDirty()
  if (!(await confirmDiscard())) return
  if (wasDirty) clearDraft(currentId.value)
  currentId.value = null
  name.value = '新路网'
  isTemplate.value = false
  const next: RoadNetPayload = {
    nodes: [{ id: 'CH1', x: 4, y: 4, type: 'CHARGER' }],
    edges: [],
    ...DEFAULT_SIZE
  }
  payload.value = next
  editorRef.value?.setPayload(next)
  previewMode.value = false
  errors.value = []
  dirty.value = false
}

function currentPayload(): RoadNetPayload {
  return editorRef.value?.getPayload() ?? payload.value
}

async function validate() {
  validating.value = true
  try {
    errors.value = await validateRoadNet(currentPayload())
    if (errors.value.length === 0) {
      ElMessage.success('校验通过')
    } else {
      ElMessage.warning(`发现 ${errors.value.length} 条问题`)
    }
    return errors.value.length === 0
  } catch (error: any) {
    ElMessage.error(error.message || '校验失败')
    return false
  } finally {
    validating.value = false
  }
}

function scheduleValidate() {
  window.clearTimeout(validateTimer)
  if (!autoValidate.value) return
  validateTimer = window.setTimeout(async () => {
    try {
      validating.value = true
      errors.value = await validateRoadNet(currentPayload())
    } catch {
      // 自动校验失败不打断编辑
    } finally {
      validating.value = false
    }
  }, 600)
}

function onEditorChange() {
  dirty.value = isEditorDirty()
  scheduleValidate()
  window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(saveDraft, 1000)
}

function onAutoValidateChange() {
  if (autoValidate.value) scheduleValidate()
}

async function save() {
  const body = currentPayload()
  if (!name.value.trim()) {
    ElMessage.warning('请先填写路网名称')
    return
  }
  try {
    const summary = currentId.value
      ? await updateRoadNet(currentId.value, {
          name: name.value,
          roadnet: body,
          isTemplate: isTemplate.value
        })
      : await createRoadNet({ name: name.value, roadnet: body })
    const previousId = currentId.value
    currentId.value = summary.id
    isTemplate.value = !!summary.isTemplate
    editorRef.value?.markSaved()
    dirty.value = false
    clearDraft(previousId)
    clearDraft(summary.id)
    errors.value = []
    ElMessage.success('已保存')
    await loadList()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function saveAs() {
  try {
    const result = await ElMessageBox.prompt('另存为新的路网名称', '另存为', {
      inputValue: `${name.value} 副本`,
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const summary = await createRoadNet({ name: result.value, roadnet: currentPayload() })
    currentId.value = summary.id
    name.value = summary.name
    isTemplate.value = false
    editorRef.value?.markSaved()
    dirty.value = false
    clearDraft(null)
    ElMessage.success('已另存为')
    await loadList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '另存为失败')
    }
  }
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该路网吗？', '删除确认', { type: 'warning' })
    await deleteRoadNet(id)
    clearDraft(id)
    ElMessage.success('已删除')
    if (currentId.value === id) {
      currentId.value = null
      const empty: RoadNetPayload = { nodes: [], edges: [], ...DEFAULT_SIZE }
      payload.value = empty
      editorRef.value?.setPayload(empty)
      previewMode.value = true
      errors.value = []
      dirty.value = false
    }
    await loadList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

function exportJson() {
  const data = JSON.stringify(currentPayload(), null, 2)
  const blob = new Blob([data], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${name.value || 'roadnet'}.json`
  link.click()
  URL.revokeObjectURL(url)
}

function triggerImport() {
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const parsed = JSON.parse(text)
    if (!Array.isArray(parsed.nodes) || !Array.isArray(parsed.edges)) {
      throw new Error('JSON 必须包含 nodes 与 edges 数组')
    }
    const next: RoadNetPayload = {
      nodes: parsed.nodes,
      edges: parsed.edges,
      width: parsed.width,
      height: parsed.height
    }
    payload.value = next
    editorRef.value?.setPayload(next)
    name.value = file.name.replace(/\.json$/i, '')
    currentId.value = null
    previewMode.value = false
    dirty.value = false
    const ok = await validate()
    if (ok) {
      ElMessage.success('导入成功，请确认后保存')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    input.value = ''
  }
}

function open3D() {
  preview3DPayload.value = currentPayload()
  preview3DVisible.value = true
}

function togglePreview(next: boolean) {
  previewMode.value = next
}

function onWindowKeyDown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void save()
  }
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!isEditorDirty()) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (!isEditorDirty()) return true
  try {
    await ElMessageBox.confirm('当前路网有未保存修改，确定离开吗？', '未保存修改', {
      type: 'warning',
      confirmButtonText: '离开',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
})

onMounted(async () => {
  window.addEventListener('keydown', onWindowKeyDown)
  window.addEventListener('beforeunload', onBeforeUnload)
  await loadList()
  await loadTaskSets()
  if (roadnets.value.length > 0) {
    await open(roadnets.value[0].id)
  } else {
    await createNew()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeyDown)
  window.removeEventListener('beforeunload', onBeforeUnload)
  window.clearTimeout(validateTimer)
  window.clearTimeout(draftTimer)
})
</script>

<template>
  <div>
    <h2 class="page-title">车间地图</h2>

    <div class="layout">
      <div class="panel list-panel">
        <div class="list-actions">
          <el-button size="small" type="primary" @click="createNew">新建</el-button>
          <el-button size="small" @click="loadList">刷新</el-button>
        </div>
        <div
          v-for="item in roadnets"
          :key="item.id"
          class="list-item"
          :class="{ active: currentId === item.id }"
          @click="open(item.id)"
        >
          <div class="list-title">
            {{ item.name }}
            <el-tag v-if="item.isTemplate" size="small" type="warning">模板</el-tag>
          </div>
          <div class="muted">{{ item.nodeCount }} 节点 · {{ item.edgeCount }} 路段</div>
          <el-button
            v-if="!item.isTemplate || auth.isAdmin"
            link
            type="danger"
            size="small"
            class="list-delete"
            @click.stop="remove(item.id)"
          >
            删除
          </el-button>
        </div>
        <el-empty v-if="roadnets.length === 0" description="还没有路网" :image-size="60" />

        <div class="task-box">
          <div class="side-title">任务流向</div>
          <el-select
            v-model="selectedTaskSetId"
            size="small"
            placeholder="选择任务集"
            style="width: 100%"
            @change="onTaskSetChange"
          >
            <el-option
              v-for="item in taskSets"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-checkbox v-model="showTaskOverlay" @change="onTaskOverlayToggle">
            在地图上显示任务流向
          </el-checkbox>
        </div>
      </div>

      <div class="panel editor-panel" v-loading="loading">
        <div class="editor-head">
          <el-input v-model="name" style="width: 220px" placeholder="路网名称" />
          <el-tag v-if="isTemplate" type="warning" size="small">
            预置模板{{ auth.isAdmin ? '' : '（只读，请另存为）' }}
          </el-tag>
          <span v-if="dirty" class="dirty">未保存</span>
          <el-button-group>
            <el-button :type="previewMode ? 'primary' : ''" size="small" @click="togglePreview(true)">
              预览
            </el-button>
            <el-button :type="!previewMode ? 'primary' : ''" size="small" @click="togglePreview(false)">
              编辑
            </el-button>
          </el-button-group>
          <div class="spacer" />
          <el-tag v-if="errors.length > 0" type="danger" size="small">{{ errors.length }} 个问题</el-tag>
          <el-checkbox v-model="autoValidate" size="small" @change="onAutoValidateChange">
            自动校验
          </el-checkbox>
          <el-button size="small" :loading="validating" @click="validate">校验</el-button>
          <el-button size="small" @click="open3D">3D 预览</el-button>
          <el-button size="small" @click="triggerImport">导入 JSON</el-button>
          <el-button size="small" @click="exportJson">导出 JSON</el-button>
          <el-button size="small" @click="saveAs">另存为</el-button>
          <el-button size="small" type="primary" @click="save">保存</el-button>
          <input ref="fileInput" type="file" accept=".json" hidden @change="onFileChange" />
        </div>

        <RoadNetEditor
          ref="editorRef"
          :initial="payload"
          :errors="errors"
          :readonly="previewMode"
          :tasks="showTaskOverlay ? overlayTasks : []"
          :name="name"
          @change="onEditorChange"
          @save="save"
        />
      </div>
    </div>

    <el-dialog v-model="preview3DVisible" title="3D 预览" width="80%" top="5vh" destroy-on-close>
      <div class="preview-3d">
        <RoadNetScene v-if="preview3DPayload" :roadnet="preview3DPayload" :agvs="[]" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.list-panel {
  padding: 12px;
  height: fit-content;
  max-height: 860px;
  overflow: auto;
}

.list-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.list-item {
  position: relative;
  padding: 10px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.list-item:hover {
  border-color: #b6cbe8;
}

.list-item.active {
  border-color: #1f3b73;
  background: #f2f6fc;
}

.list-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.list-delete {
  position: absolute;
  right: 6px;
  bottom: 4px;
}

.task-box {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #eef2f7;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f3b73;
}

.editor-panel {
  padding: 14px;
  display: flex;
  flex-direction: column;
  min-height: 720px;
}

.editor-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.dirty {
  color: #e6a23c;
  font-size: 12px;
}

.preview-3d {
  height: 70vh;
  min-height: 480px;
}

.muted {
  font-size: 12px;
  color: #909399;
}
</style>
