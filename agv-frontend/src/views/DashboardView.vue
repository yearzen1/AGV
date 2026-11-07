<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listRoadNets, type RoadNetSummary } from '@/api/roadnet'
import { useSimSocket } from '@/composables/useSimSocket'

const maps = ref<RoadNetSummary[]>([])
const loading = ref(false)
const { connected, messages, connect, send } = useSimSocket()

async function load() {
  loading.value = true
  try {
    maps.value = await listRoadNets()
  } catch {
    maps.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="page-title">概览</h2>

    <el-row :gutter="16">
      <el-col :span="8">
        <div class="panel stat">
          <div class="stat-label">路网</div>
          <div class="stat-value">{{ maps.length }}</div>
          <div class="muted">含预置模板 {{ maps.filter((m) => m.isTemplate).length }} 套</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel stat">
          <div class="stat-label">仿真通道</div>
          <div class="stat-value">
            <el-tag :type="connected ? 'success' : 'info'" size="large">
              {{ connected ? '已连接' : '未连接' }}
            </el-tag>
          </div>
          <div class="muted">WebSocket /ws/sim</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="panel stat">
          <div class="stat-label">当前阶段</div>
          <div class="stat-value">P0 环境搭建</div>
          <div class="muted">登录、路网接口、帧通道已打通</div>
        </div>
      </el-col>
    </el-row>

    <div class="panel" style="margin-top: 16px">
      <h3 style="margin-top: 0">WebSocket 连通性自检</h3>
      <el-space wrap>
        <el-button type="primary" :disabled="connected" @click="connect">连接</el-button>
        <el-button :disabled="!connected" @click="send('ping-' + Date.now())">发送 ping</el-button>
      </el-space>
      <el-divider />
      <div v-if="messages.length === 0" class="muted">暂无消息。点击「连接」后发送一次 ping，应收到 echo。</div>
      <div v-for="(message, index) in messages" :key="index" class="ws-line">{{ message }}</div>
    </div>

    <div class="panel" style="margin-top: 16px">
      <h3 style="margin-top: 0">路网清单</h3>
      <el-table v-loading="loading" :data="maps" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="nodeCount" label="节点" width="80" />
        <el-table-column prop="edgeCount" label="路段" width="80" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.isTemplate" type="warning" size="small">预置模板</el-tag>
            <el-tag v-else type="success" size="small">自建</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 26px;
  font-weight: 600;
  margin: 6px 0;
  color: #1f3b73;
}

.ws-line {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #475669;
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
  word-break: break-all;
}
</style>
