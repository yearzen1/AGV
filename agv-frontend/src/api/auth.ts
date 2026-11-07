import http from './http'

export interface UserInfo {
  id: number
  username: string
  realName: string
  role: 'dispatcher' | 'admin'
  status?: number
  createTime?: string
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export function login(username: string, password: string) {
  return http.post<any, LoginResult>('/auth/login', { username, password })
}

export function fetchMe() {
  return http.get<any, UserInfo>('/auth/me')
}

export function listUsers() {
  return http.get<any, UserInfo[]>('/admin/users')
}

export function createUser(payload: {
  username: string
  password: string
  realName?: string
  role: string
}) {
  return http.post<any, UserInfo>('/admin/users', payload)
}

export function updateUser(
  id: number,
  payload: { realName?: string; role?: string; status?: number; password?: string }
) {
  return http.put<any, UserInfo>(`/admin/users/${id}`, payload)
}

export function deleteUser(id: number) {
  return http.delete<any, void>(`/admin/users/${id}`)
}
