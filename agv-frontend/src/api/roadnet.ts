import http from './http'

export interface NetNode {
  id: string
  x: number
  y: number
  type: 'WORKSTATION' | 'CHARGER' | 'BUFFER' | 'JUNCTION'
  capacity?: number
}

export interface NetEdge {
  id: string
  from: string
  to: string
  length: number
  direction: 'ONE_WAY' | 'TWO_WAY'
  speed: number
  capacity: number
  laneMode?: 'SINGLE' | 'DUAL'
}

export interface RoadNetPayload {
  nodes: NetNode[]
  edges: NetEdge[]
  width?: number
  height?: number
}

export interface RoadNetSummary {
  id: number
  name: string
  isTemplate: boolean
  createUser: number | null
  createTime: string
  nodeCount: number
  edgeCount: number
}

export interface ValidationError {
  code: string
  message: string
  nodeId: string | null
  edgeId: string | null
}

export function listRoadNets() {
  return http.get<any, RoadNetSummary[]>('/roadnet/list')
}

export function getRoadNet(id: number) {
  return http.get<any, RoadNetPayload>(`/roadnet/${id}`)
}

export function createRoadNet(payload: {
  name: string
  roadnet: RoadNetPayload
  isTemplate?: boolean
}) {
  return http.post<any, RoadNetSummary>('/roadnet', payload)
}

export function updateRoadNet(
  id: number,
  payload: { name: string; roadnet: RoadNetPayload; isTemplate?: boolean }
) {
  return http.put<any, RoadNetSummary>(`/roadnet/${id}`, payload)
}

export function deleteRoadNet(id: number) {
  return http.delete<any, void>(`/roadnet/${id}`)
}

export function validateRoadNet(payload: RoadNetPayload) {
  return http.post<any, ValidationError[]>('/roadnet/validate', payload)
}

export function listDict(dictType: string) {
  return http.get<any, { id: number; dictType: string; dictCode: string; dictValue: string }[]>(
    `/dict/${dictType}`
  )
}
