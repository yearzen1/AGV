import http from './http'

export interface DictItem {
  id: number
  dictType: string
  dictCode: string
  dictValue: string
  sort: number
  status: number
}

export function listDictAdmin(dictType?: string) {
  return http.get<any, DictItem[]>('/admin/dict', { params: { dictType } })
}

export function createDict(payload: {
  dictType: string
  dictCode: string
  dictValue: string
  sort?: number
  status?: number
}) {
  return http.post<any, DictItem>('/admin/dict', payload)
}

export function updateDict(id: number, payload: {
  dictType: string
  dictCode: string
  dictValue: string
  sort?: number
  status?: number
}) {
  return http.put<any, DictItem>(`/admin/dict/${id}`, payload)
}

export function deleteDict(id: number) {
  return http.delete<any, void>(`/admin/dict/${id}`)
}
