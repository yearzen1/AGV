import { onBeforeUnmount, ref } from 'vue'
import { simSocketUrl } from '@/utils/ws'

/**
 * 仿真帧通道。W1 只做 echo 验证；W6 起接收 { tick, agvs, events } 帧并驱动 3D 场景。
 */
export function useSimSocket() {
  const connected = ref(false)
  const messages = ref<string[]>([])
  let socket: WebSocket | null = null

  function connect() {
    if (socket && socket.readyState === WebSocket.OPEN) {
      return
    }
    socket = new WebSocket(simSocketUrl())
    socket.onopen = () => {
      connected.value = true
    }
    socket.onmessage = (event) => {
      messages.value.unshift(String(event.data))
      if (messages.value.length > 50) {
        messages.value.pop()
      }
    }
    socket.onclose = () => {
      connected.value = false
    }
    socket.onerror = () => {
      connected.value = false
    }
  }

  function send(text: string) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(text)
    }
  }

  function disconnect() {
    socket?.close()
    socket = null
    connected.value = false
  }

  onBeforeUnmount(disconnect)

  return { connected, messages, connect, send, disconnect }
}
