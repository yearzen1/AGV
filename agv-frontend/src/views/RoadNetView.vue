<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RoadNetEditor from '@/components/RoadNetEditor.vue'
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
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const roadnets = ref<RoadNetSummary[]>([])
const currentId = ref<number | null>(null)
const name = ref('')
const isTemplate = ref(false)
const payload = ref<RoadNetPayload>({ nodes: [], edges: [] })
const errors = ref<ValidationError[]>([])
const editorRef = ref<InstanceType<typeof RoadNetEditor> | null>(null)
const loading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

async function loadList() {
  try {
    roadnets.value = await listRoadNets()
  } catch (error: any) {
    ElMessage.error(error.message || '加载路网列表失败')
  }
}

async function open(id: number) {
  loading.value = true
  try {
    const detail = await getRoadNet(id)
    const summary = roadnets.value.find((m) => m.id === id)
    currentId.value = id
    name.value = summary?.name ?? `路网 ${id}`
    isTemplate.value = !!summary?.isTemplate
    payload.value = detail
    editorRef.value?.setPayload(detail)
    errors.value = []
  } catch (error: any) {
    ElMessage.error(error.message || '读取路网失败')
  } finally {
    loading.value = false
  }
}

function createNew() {
  currentId.value = null
  name.value = '新路网'
  isTemplate.value = false
  const next: RoadNetPayload = {
    nodes: [{ id: 'CH1', x: 4, y: 4, type: 'CHARGER' }],
    edges: []
  }
  payload.value = next
  editorRef.value?.setPayload(next)
  errors.value = []
}

function currentPayload(): RoadNetPayload {
  return editorRef.value?.getPayload() ?? payload.value
}

async function validate() {
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
  }
}

async function save() {
  const body = currentPayload()
  try {
    const summary = currentId.value
      ? await updateRoadNet(currentId.value, { name: name.value, roadnet: body, isTemplate: isTemplate.value })
      : await createRoadNet({ name: name.value, roadnet: body })
    currentId.value = summary.id
    isTemplate.value = !!summary.isTemplate
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
    const body = currentPayload()
    const summary = await createRoadNet({ name: result.value, roadnet: body })
    currentId.value = summary.id
    name.value = summary.name
    isTemplate.value = false
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
    ElMessage.success('已删除')
    if (currentId.value === id) {
      currentId.value = null
      const empty: RoadNetPayload = { nodes: [], edges: [] }
      payload.value = empty
      editorRef.value?.setPayload(empty)
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
    payload.value = { nodes: parsed.nodes, edges: parsed.edges }
    editorRef.value?.setPayload(payload.value)
    name.value = file.name.replace(/\.json$/i, '')
    currentId.value = null
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

onMounted(async () => {
  await loadList()
  if (roadnets.value.length > 0) {
    await open(roadnets.value[0].id)
  } else {
    createNew()
  }
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
      </div>

      <div class="panel editor-panel" v-loading="loading">
        <div class="editor-head">
          <el-input v-model="name" style="width: 220px" placeholder="路网名称" />
          <el-tag v-if="isTemplate" type="warning" size="small">预置模板{{ auth.isAdmin ? '' : '（只读，请另存为）' }}</el-tag>
          <div class="spacer" />
          <el-button size="small" @click="validate">校验</el-button>
          <el-button size="small" @click="triggerImport">导入 JSON</el-button>
          <el-button size="small" @click="exportJson">导出 JSON</el-button>
          <el-button size="small" @click="saveAs">另存为</el-button>
          <el-button size="small" type="primary" @click="save">保存</el-button>
          <input ref="fileInput" type="file" accept=".json" hidden @change="onFileChange" />
        </div>

        <RoadNetEditor ref="editorRef" :initial="payload" :errors="errors" />
      </div>
    </div>
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
  max-height: 760px;
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
</style>
