<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
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

const statusMeta: Record<string, { label: string; variant: 'success' | 'neutral' | 'warning' | 'danger' }> = {
  RUNNING: { label: '运行中', variant: 'warning' },
  FINISHED: { label: '已完成', variant: 'success' },
  FAILED: { label: '失败', variant: 'danger' },
  ABORTED: { label: '已中断', variant: 'neutral' }
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
  <div class="space-y-5">
    <section class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-indigo-600 via-indigo-700 to-violet-700 p-6 text-white shadow-float md:p-8">
      <div
        class="pointer-events-none absolute inset-0 opacity-20"
        style="background-image: radial-gradient(circle at 1px 1px, #fff 1px, transparent 0); background-size: 24px 24px"
        aria-hidden="true"
      ></div>
      <div class="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10 blur-2xl" aria-hidden="true"></div>

      <div class="relative flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
        <div class="max-w-xl">
          <p class="text-xs font-semibold uppercase tracking-[0.2em] text-indigo-200">Scheduling Workspace</p>
          <h2 class="mt-3 text-2xl font-semibold tracking-tight md:text-[28px]">从路网到指标，一屏掌握</h2>
          <p class="mt-2 text-sm leading-6 text-indigo-100">
            查看当前资产与最近实验；需要观察车辆、冲突与等待时，直接进入仿真工作台。
          </p>
          <div class="mt-6 flex flex-wrap gap-2.5">
            <router-link
              to="/simulation"
              class="inline-flex h-10 items-center gap-2 rounded-xl bg-white px-4 text-sm font-semibold text-indigo-700 shadow-sm transition hover:bg-indigo-50"
            >
              <AppIcon name="simulation" :size="17" />
              启动仿真
            </router-link>
            <router-link
              to="/tasks"
              class="inline-flex h-10 items-center gap-2 rounded-xl border border-white/25 bg-white/10 px-4 text-sm font-semibold text-white backdrop-blur transition hover:bg-white/15"
            >
              <AppIcon name="tasks" :size="17" />
              维护任务集
            </router-link>
          </div>
        </div>

        <div class="relative w-full shrink-0 rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur md:w-64">
          <div class="flex items-center justify-between text-xs text-indigo-100">
            <span>实时通道</span>
            <span class="inline-flex items-center gap-1.5 rounded-full bg-white/15 px-2 py-0.5 font-semibold">
              <span class="h-1.5 w-1.5 rounded-full" :class="connected ? 'bg-emerald-300' : 'bg-slate-300'"></span>
              {{ connected ? '已连接' : '未连接' }}
            </span>
          </div>
          <p class="mt-3 text-2xl font-semibold tabular-nums">{{ taskCount }}</p>
          <p class="text-xs text-indigo-100">条搬运任务待调度</p>
          <div class="mt-4 h-1.5 overflow-hidden rounded-full bg-white/15">
            <div
              class="h-full rounded-full bg-emerald-300 transition-all duration-500"
              :style="{ width: connected ? '100%' : '0%' }"
            ></div>
          </div>
          <p class="mt-2 text-[11px] text-indigo-200">WebSocket /ws/sim</p>
        </div>
      </div>
    </section>

    <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="系统概况">
      <Card class="flex items-start gap-4">
        <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-indigo-50 text-indigo-600">
          <AppIcon name="map" :size="19" />
        </span>
        <div class="min-w-0">
          <p class="text-xs font-medium text-slate-500">路网</p>
          <Skeleton v-if="loading" class="mt-2 h-7 w-12" />
          <p v-else class="mt-1 text-[26px] font-semibold leading-none tracking-tight text-slate-900 tabular-nums">{{ maps.length }}</p>
          <p class="mt-1.5 text-xs text-slate-500">含预置模板 {{ templateCount }} 套</p>
        </div>
      </Card>

      <Card class="flex items-start gap-4">
        <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-violet-50 text-violet-600">
          <AppIcon name="tasks" :size="19" />
        </span>
        <div class="min-w-0">
          <p class="text-xs font-medium text-slate-500">任务集</p>
          <Skeleton v-if="loading" class="mt-2 h-7 w-12" />
          <p v-else class="mt-1 text-[26px] font-semibold leading-none tracking-tight text-slate-900 tabular-nums">{{ taskSets.length }}</p>
          <p class="mt-1.5 text-xs text-slate-500">共 {{ taskCount }} 条搬运任务</p>
        </div>
      </Card>

      <Card class="flex items-start gap-4">
        <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-600">
          <AppIcon name="bolt" :size="19" />
        </span>
        <div class="min-w-0">
          <p class="text-xs font-medium text-slate-500">实时通道</p>
          <div class="mt-2">
            <Badge :variant="connected ? 'success' : 'neutral'" dot>{{ connected ? '已连接' : '未连接' }}</Badge>
          </div>
          <p class="mt-2 text-xs text-slate-500">WebSocket /ws/sim</p>
        </div>
      </Card>

      <Card class="flex items-start gap-4">
        <span class="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-amber-50 text-amber-600">
          <AppIcon name="check" :size="19" />
        </span>
        <div class="min-w-0">
          <p class="text-xs font-medium text-slate-500">仿真服务</p>
          <div class="mt-2"><Badge variant="success" dot>已就绪</Badge></div>
          <p class="mt-2 text-xs text-slate-500">可启动仿真与对比实验</p>
        </div>
      </Card>
    </section>

    <section class="grid gap-4 md:grid-cols-3" aria-label="快捷入口">
      <router-link to="/simulation" class="group">
        <Card hover class="flex h-full items-center gap-4">
          <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 text-white shadow-sm">
            <AppIcon name="simulation" :size="20" />
          </span>
          <span class="min-w-0 flex-1">
            <strong class="block text-sm font-semibold text-slate-900">仿真运行</strong>
            <span class="mt-0.5 block text-xs leading-5 text-slate-500">实时观察车辆、冲突与指标</span>
          </span>
          <AppIcon name="arrow" :size="16" class="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-indigo-500" />
        </Card>
      </router-link>

      <router-link to="/tasks" class="group">
        <Card hover class="flex h-full items-center gap-4">
          <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-sky-500 to-indigo-500 text-white shadow-sm">
            <AppIcon name="tasks" :size="20" />
          </span>
          <span class="min-w-0 flex-1">
            <strong class="block text-sm font-semibold text-slate-900">维护任务集</strong>
            <span class="mt-0.5 block text-xs leading-5 text-slate-500">录入起终点，修正不可达任务</span>
          </span>
          <AppIcon name="arrow" :size="16" class="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-indigo-500" />
        </Card>
      </router-link>

      <router-link to="/experiments" class="group">
        <Card hover class="flex h-full items-center gap-4">
          <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-500 text-white shadow-sm">
            <AppIcon name="chart" :size="20" />
          </span>
          <span class="min-w-0 flex-1">
            <strong class="block text-sm font-semibold text-slate-900">对比实验</strong>
            <span class="mt-0.5 block text-xs leading-5 text-slate-500">两种策略的指标图表与回放</span>
          </span>
          <AppIcon name="arrow" :size="16" class="text-slate-300 transition group-hover:translate-x-0.5 group-hover:text-indigo-500" />
        </Card>
      </router-link>
    </section>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)]">
      <Card>
        <div class="mb-4 flex items-center justify-between gap-3">
          <h2 class="text-[15px] font-semibold tracking-tight text-slate-900">最近实验</h2>
          <router-link to="/experiments" class="text-xs font-semibold text-indigo-600 hover:text-indigo-700">查看全部</router-link>
        </div>
        <el-table
          v-loading="experimentLoading"
          :data="experiments"
          size="small"
          empty-text="还没有实验记录，运行一次对比实验后会显示在这里"
        >
          <el-table-column prop="algorithmName" label="算法" min-width="120" />
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <Badge :variant="statusMeta[row.status]?.variant ?? 'neutral'">
                {{ statusMeta[row.status]?.label ?? row.status }}
              </Badge>
            </template>
          </el-table-column>
          <el-table-column label="完成时间" width="100">
            <template #default="{ row }">{{ formatMetric(row.makespan) }}</template>
          </el-table-column>
          <el-table-column label="冲突" width="80">
            <template #default="{ row }">{{ formatMetric(row.conflictCount) }}</template>
          </el-table-column>
          <el-table-column label="时间" min-width="150">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
      </Card>

      <Card>
        <div class="mb-4 flex items-center justify-between gap-3">
          <h2 class="text-[15px] font-semibold tracking-tight text-slate-900">路网清单</h2>
          <div class="flex items-center gap-3">
            <button
              type="button"
              class="text-xs font-semibold text-slate-500 transition hover:text-slate-800"
              :disabled="loading"
              @click="load"
            >
              {{ loading ? '刷新中…' : '刷新' }}
            </button>
            <router-link to="/roadnet" class="text-xs font-semibold text-indigo-600 hover:text-indigo-700">打开地图</router-link>
          </div>
        </div>
        <el-table v-loading="loading" :data="maps" size="small" empty-text="还没有路网，去「车间地图」新建一张">
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="nodeCount" label="节点" width="70" />
          <el-table-column prop="edgeCount" label="路段" width="70" />
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <Badge :variant="row.isTemplate ? 'warning' : 'success'">
                {{ row.isTemplate ? '预置模板' : '自建' }}
              </Badge>
            </template>
          </el-table-column>
        </el-table>
      </Card>
    </div>

    <details class="group rounded-2xl border border-border bg-white p-5 shadow-card">
      <summary class="flex cursor-pointer list-none items-center justify-between text-sm font-medium text-slate-600">
        连接自检（开发者）
        <AppIcon name="arrow" :size="16" class="rotate-90 text-slate-400 transition group-open:-rotate-90" />
      </summary>
      <div class="mt-4 border-t border-border pt-4">
        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            class="h-9 rounded-xl bg-indigo-600 px-4 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="connected"
            @click="connect"
          >
            连接
          </button>
          <button
            type="button"
            class="h-9 rounded-xl border border-border bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="!connected"
            @click="send('ping-' + Date.now())"
          >
            发送 ping
          </button>
        </div>
        <div class="mt-3 max-h-44 overflow-auto" role="status" aria-live="polite">
          <p v-if="messages.length === 0" class="text-xs text-slate-500">
            暂无消息。点击「连接」后发送一次 ping，应收到 echo。
          </p>
          <p
            v-for="(message, index) in messages"
            :key="index"
            class="border-b border-dashed border-border py-1.5 font-mono text-[11px] text-slate-500"
          >
            {{ message }}
          </p>
        </div>
      </div>
    </details>
  </div>
</template>
