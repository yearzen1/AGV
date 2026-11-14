<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { listRoadNets, type RoadNetSummary } from '@/api/roadnet'
import { experimentHistory, listTaskSets, type ExperimentReport, type TaskSetSummary } from '@/api/sim'
import { useSimSocket } from '@/composables/useSimSocket'
import { formatDateTime, formatMetric } from '@/utils/format'

const maps = ref<RoadNetSummary[]>([])
const taskSets = ref<TaskSetSummary[]>([])
const experiments = ref<ExperimentReport[]>([])
const loading = ref(false)
const experimentLoading = ref(false)
const { connected, messages, connect, send } = useSimSocket()

const templateCount = computed(() => maps.value.filter((map) => map.isTemplate).length)
const taskCount = computed(() => taskSets.value.reduce((sum, set) => sum + set.taskCount, 0))

const statusMeta: Record<string, { label: string; type: 'success' | 'info' | 'warning' | 'danger' }> = {
  RUNNING: { label: '运行中', type: 'warning' },
  FINISHED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  ABORTED: { label: '已中断', type: 'info' }
}

async function load() {
  loading.value = true
  try {
    const [roadnets, sets] = await Promise.all([listRoadNets(), listTaskSets()])
    maps.value = roadnets
    taskSets.value = sets
  } catch {
    maps.value = []
    taskSets.value = []
  } finally {
    loading.value = false
  }
}

async function loadExperiments() {
  experimentLoading.value = true
  try {
    experiments.value = (await experimentHistory(0, 5)).records
  } catch {
    experiments.value = []
  } finally {
    experimentLoading.value = false
  }
}

onMounted(() => {
  connect()
  void load()
  void loadExperiments()
})
</script>

<template>
  <div class="dashboard">
    <p class="page-lead">
      查看当前路网、任务集与最近实验；需要实时观察车辆和冲突事件时进入「仿真运行」。
    </p>

    <section class="stat-grid" aria-label="系统概况">
      <article class="panel stat">
        <span class="stat-icon"><AppIcon name="map" :size="18" /></span>
        <div>
          <p class="stat-label">路网</p>
          <p class="stat-value">{{ loading ? '—' : maps.length }}</p>
          <p class="stat-sub">含预置模板 {{ templateCount }} 套</p>
        </div>
      </article>

      <article class="panel stat">
        <span class="stat-icon"><AppIcon name="tasks" :size="18" /></span>
        <div>
          <p class="stat-label">任务集</p>
          <p class="stat-value">{{ loading ? '—' : taskSets.length }}</p>
          <p class="stat-sub">共 {{ taskCount }} 条搬运任务</p>
        </div>
      </article>

      <article class="panel stat">
        <span class="stat-icon" :class="{ ok: connected }"><AppIcon name="bolt" :size="18" /></span>
        <div>
          <p class="stat-label">实时通道</p>
          <p class="stat-value stat-tag">
            <el-tag :type="connected ? 'success' : 'info'" size="large">
              {{ connected ? '已连接' : '未连接' }}
            </el-tag>
          </p>
          <p class="stat-sub">WebSocket /ws/sim</p>
        </div>
      </article>

      <article class="panel stat">
        <span class="stat-icon ok"><AppIcon name="check" :size="18" /></span>
        <div>
          <p class="stat-label">仿真服务</p>
          <p class="stat-value stat-tag">
            <el-tag type="success" size="large">已就绪</el-tag>
          </p>
          <p class="stat-sub">可启动仿真与对比实验</p>
        </div>
      </article>
    </section>

    <section class="quick-grid" aria-label="快捷入口">
      <router-link class="quick-card" to="/simulation">
        <span class="quick-icon"><AppIcon name="simulation" :size="20" /></span>
        <span class="quick-body">
          <strong>仿真运行</strong>
          <small>选路网与任务集，实时观察车辆、冲突与指标</small>
        </span>
        <AppIcon name="arrow" :size="16" class="quick-arrow" />
      </router-link>
      <router-link class="quick-card" to="/tasks">
        <span class="quick-icon"><AppIcon name="tasks" :size="20" /></span>
        <span class="quick-body">
          <strong>维护任务集</strong>
          <small>为路网录入起终点，或修正不可达的任务</small>
        </span>
        <AppIcon name="arrow" :size="16" class="quick-arrow" />
      </router-link>
      <router-link class="quick-card" to="/experiments">
        <span class="quick-icon"><AppIcon name="chart" :size="20" /></span>
        <span class="quick-body">
          <strong>对比实验</strong>
          <small>同一输入跑两种调度策略，查看历史与回放</small>
        </span>
        <AppIcon name="arrow" :size="16" class="quick-arrow" />
      </router-link>
    </section>

    <section class="panel" aria-labelledby="recent-heading">
      <div class="section-head">
        <h2 id="recent-heading" class="panel-title">最近实验</h2>
        <router-link class="more-link" to="/experiments">查看全部</router-link>
      </div>
      <el-table v-loading="experimentLoading" :data="experiments" size="small" empty-text="还没有实验记录，运行一次对比实验后会显示在这里">
        <el-table-column prop="algorithmName" label="算法" min-width="130" />
        <el-table-column prop="agvCount" label="AGV" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta[row.status]?.type ?? 'info'" size="small">
              {{ statusMeta[row.status]?.label ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="完成时间" width="110">
          <template #default="{ row }">{{ formatMetric(row.makespan) }}</template>
        </el-table-column>
        <el-table-column label="冲突" width="90">
          <template #default="{ row }">{{ formatMetric(row.conflictCount) }}</template>
        </el-table-column>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel" aria-labelledby="roads-heading">
      <div class="section-head">
        <h2 id="roads-heading" class="panel-title">路网清单</h2>
        <div class="head-tools">
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
          <router-link class="more-link" to="/roadnet">打开车间地图</router-link>
        </div>
      </div>
      <el-table v-loading="loading" :data="maps" size="small" empty-text="还没有路网，去「车间地图」新建一张">
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
    </section>

    <el-collapse class="diag">
      <el-collapse-item name="ws">
        <template #title>
          <span class="diag-title">连接自检（开发者）</span>
        </template>
        <div class="diag-actions">
          <el-button type="primary" :disabled="connected" @click="connect">连接</el-button>
          <el-button :disabled="!connected" @click="send('ping-' + Date.now())">发送 ping</el-button>
        </div>
        <div class="ws-log" role="status" aria-live="polite">
          <div v-if="messages.length === 0" class="muted">
            暂无消息。点击「连接」后发送一次 ping，应收到 echo。
          </div>
          <div v-for="(message, index) in messages" :key="index" class="ws-line">{{ message }}</div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.dashboard {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
}

.stat {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--agv-brand);
  background: #eef3fc;
  border-radius: 10px;
  flex: none;
}

.stat-icon.ok {
  color: var(--agv-success);
  background: #ecfdf3;
}

.stat-label {
  margin: 0;
  color: var(--agv-text-muted);
  font-size: 13px;
}

.stat-value {
  margin: 4px 0 2px;
  color: var(--agv-brand);
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
}

.stat-tag {
  margin-top: 8px;
}

.stat-sub {
  margin: 0;
  color: var(--agv-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.quick-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  color: inherit;
  text-decoration: none;
  background: var(--agv-surface);
  border: 1px solid var(--agv-border);
  border-radius: var(--agv-radius-lg);
  box-shadow: var(--agv-shadow);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.quick-card:hover {
  border-color: #b9cdf5;
  box-shadow: var(--agv-shadow-hover);
  transform: translateY(-1px);
}

.quick-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #fff;
  background: linear-gradient(135deg, var(--agv-brand), #2c5297);
  border-radius: 11px;
  flex: none;
}

.quick-body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.quick-body strong {
  font-size: 14px;
  font-weight: 600;
}

.quick-body small {
  color: var(--agv-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.quick-arrow {
  margin-inline-start: auto;
  color: #9aa8bd;
}

.head-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.more-link {
  color: var(--agv-brand);
  font-size: 13px;
  text-decoration: none;
}

.more-link:hover {
  text-decoration: underline;
}

.diag {
  border: 1px solid var(--agv-border);
  border-radius: var(--agv-radius-lg);
  background: var(--agv-surface);
  padding: 4px 16px;
  box-shadow: var(--agv-shadow);
}

.diag-title {
  color: var(--agv-text-muted);
  font-size: 13px;
}

.diag-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.ws-log {
  max-height: 180px;
  overflow: auto;
}

.ws-line {
  padding: 4px 0;
  color: #475569;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  border-bottom: 1px dashed var(--agv-border);
  word-break: break-all;
}

@media (max-width: 640px) {
  .stat {
    padding: 14px;
  }
}
</style>
