<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getRoadNet,
  listRoadNets,
  type RoadNetPayload,
  type RoadNetSummary
} from '@/api/roadnet'

const maps = ref<RoadNetSummary[]>([])
const loading = ref(false)
const detail = ref<RoadNetPayload | null>(null)
const detailName = ref('')
const dialogVisible = ref(false)

async function load() {
  loading.value = true
  try {
    maps.value = await listRoadNets()
  } catch (error: any) {
    ElMessage.error(error.message || '加载路网失败')
  } finally {
    loading.value = false
  }
}

async function openDetail(row: RoadNetSummary) {
  try {
    detail.value = await getRoadNet(row.id)
    detailName.value = row.name
    dialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '读取路网失败')
  }
}

/** 把坐标映射到 SVG 视口，仅用于快速核对数据是否合理。 */
const preview = computed(() => {
  if (!detail.value || detail.value.nodes.length === 0) {
    return { nodes: [], edges: [], width: 400, height: 300 }
  }
  const xs = detail.value.nodes.map((n) => n.x)
  const ys = detail.value.nodes.map((n) => n.y)
  const minX = Math.min(...xs)
  const maxX = Math.max(...xs)
  const minY = Math.min(...ys)
  const maxY = Math.max(...ys)
  const spanX = Math.max(maxX - minX, 1)
  const spanY = Math.max(maxY - minY, 1)
  const scale = Math.min(560 / spanX, 360 / spanY)
  const width = spanX * scale + 80
  const height = spanY * scale + 80

  const point = (x: number, y: number) => ({
    x: 40 + (x - minX) * scale,
    y: height - 40 - (y - minY) * scale
  })

  const nodeMap = new Map(detail.value.nodes.map((n) => [n.id, point(n.x, n.y)]))
  return {
    width,
    height,
    nodes: detail.value.nodes.map((n) => ({ ...n, ...point(n.x, n.y) })),
    edges: detail.value.edges
      .map((e) => ({ id: e.id, from: nodeMap.get(e.from), to: nodeMap.get(e.to) }))
      .filter((e) => e.from && e.to)
  }
})

const typeColor: Record<string, string> = {
  CHARGER: '#67c23a',
  WORKSTATION: '#409eff',
  BUFFER: '#e6a23c',
  JUNCTION: '#909399'
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="page-title">车间地图</h2>

    <div class="panel">
      <div class="toolbar">
        <el-button type="primary" disabled>新建路网（W3 画布编辑器）</el-button>
        <el-button disabled>导入 JSON</el-button>
        <span class="muted">当前先用预置模板；画布建图将在第 3 周接入。</span>
      </div>

      <el-table v-loading="loading" :data="maps" size="default" style="margin-top: 16px">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="nodeCount" label="节点数" width="100" />
        <el-table-column prop="edgeCount" label="路段数" width="100" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.isTemplate" type="warning" size="small">预置模板</el-tag>
            <el-tag v-else type="success" size="small">自建</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="detailName" width="720px">
      <div v-if="detail" class="preview-wrap">
        <svg :viewBox="`0 0 ${preview.width} ${preview.height}`" class="preview">
          <line
            v-for="edge in preview.edges"
            :key="edge.id"
            :x1="edge.from!.x"
            :y1="edge.from!.y"
            :x2="edge.to!.x"
            :y2="edge.to!.y"
            stroke="#b8c4d9"
            stroke-width="3"
          />
          <g v-for="node in preview.nodes" :key="node.id">
            <circle :cx="node.x" :cy="node.y" r="7" :fill="typeColor[node.type] || '#909399'" />
            <text :x="node.x + 10" :y="node.y + 4" font-size="11" fill="#606266">
              {{ node.id }}
            </text>
          </g>
        </svg>
        <div class="muted">
          节点 {{ detail.nodes.length }} 个，路段 {{ detail.edges.length }} 条。绿色充电位、蓝色工位、橙色缓存位、灰色路口。
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview {
  width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
}
</style>
