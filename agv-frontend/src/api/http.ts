import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('agv_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && typeof body.code === 'number') {
      if (body.code !== 0) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    if (status === 401) {
      localStorage.removeItem('agv_token')
      localStorage.removeItem('agv_user')
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
    }
    return Promise.reject(new Error(message))
  }
)

export default http
