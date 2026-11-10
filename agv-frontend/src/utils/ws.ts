/**
 * 仿真 WebSocket 地址。浏览器不能给 WebSocket 设置 Authorization 头，
 * 令牌通过查询参数传递，后端在握手时校验。
 */
export function simSocketUrl() {
  const token = localStorage.getItem('agv_token') ?? ''
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  return `${protocol}://${location.host}/ws/sim${query}`
}
