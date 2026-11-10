import http from './http'

export interface SimMetrics {
  makespan: number
  totalPathLen: number
  totalWaitTick: number
  conflictCount: number
  completedTasks: number
  totalTasks: number
}

export interface AgvSnapshot {
  id: string
  status: 'IDLE' | 'MOVING' | 'WAITING' | 'LOADING' | 'CHARGING' | 'DONE'
  arcId: string | null
  s: number
  x: number
  y: number
  heading: number
  taskId: number
  waitTicks: number
  nodeId?: string
}

export interface SimEvent {
  tick: number
  agvId: string
  type: string
  place: string
  detail: string
}

export interface SimFrame {
  tick: number
  agvs: AgvSnapshot[]
  events: SimEvent[]
  finished: boolean
  metrics: SimMetrics
}

export interface SimStatus {
  status: string
  paused: boolean
  speed: number
  experimentId: number | null
  roadnetId: number | null
  taskSetId: number | null
  algorithm: string | null
  agvCount: number | null
  seed: number | null
  tick: number
  metrics: SimMetrics | null
}

export function startSim(payload: {
  roadnetId: number
  taskSetId: number
  algorithm: string
  agvCount: number
  seed?: number
}) {
  return http.post<any, SimStatus>('/sim/start', payload)
}

export function pauseSim() {
  return http.post<any, SimStatus>('/sim/pause')
}

export function resumeSim() {
  return http.post<any, SimStatus>('/sim/resume')
}

export function resetSim() {
  return http.post<any, SimStatus>('/sim/reset')
}

export function setSpeed(value: number) {
  return http.post<any, SimStatus>('/sim/speed', null, { params: { value } })
}

export function simStatus() {
  return http.get<any, SimStatus>('/sim/status')
}

export interface TaskDto {
  id: number
  from: string
  to: string
}

export interface TaskSetSummary {
  id: number
  name: string
  roadnetId: number | null
  roadnetName: string | null
  taskCount: number
  createUser: number | null
  createTime: string
}

export function listTaskSets(roadnetId?: number) {
  return http.get<any, TaskSetSummary[]>('/taskset/list', {
    params: roadnetId === undefined ? undefined : { roadnetId }
  })
}

export function getTaskSet(id: number) {
  return http.get<any, { tasks: TaskDto[] }>(`/taskset/${id}`)
}

export function createTaskSet(payload: { name: string; roadnetId: number; taskSet: { tasks: TaskDto[] } }) {
  return http.post<any, TaskSetSummary>('/taskset', payload)
}

export function updateTaskSet(
  id: number,
  payload: { name: string; roadnetId: number; taskSet: { tasks: TaskDto[] } }
) {
  return http.put<any, TaskSetSummary>(`/taskset/${id}`, payload)
}

export function deleteTaskSet(id: number) {
  return http.delete<any, void>(`/taskset/${id}`)
}

export interface ExperimentReport {
  id: number
  roadnetId: number
  taskSetId: number
  algorithm: string
  algorithmName: string
  agvCount: number
  seed: number
  status: string
  makespan: number
  totalPathLen: number
  totalWaitTick: number
  conflictCount: number
  createTime: string
}

export function runExperiment(payload: {
  roadnetId: number
  taskSetId: number
  algorithm?: string
  agvCount: number
  seed?: number
}) {
  return http.post<any, ExperimentReport[]>('/experiment/run', payload)
}

export function compareExperiment(payload: {
  roadnetId: number
  taskSetId: number
  agvCount: number
  seed?: number
}) {
  return http.post<any, ExperimentReport[]>('/experiment/compare', payload)
}

export function experimentHistory() {
  return http.get<any, ExperimentReport[]>('/experiment/list')
}
