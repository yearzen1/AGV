<script setup lang="ts">
import { ref } from 'vue'
import { useSimSocket } from '@/composables/useSimSocket'

const { connected, messages, connect, send } = useSimSocket()
const pingText = ref('ping')

function doSend() {
  send(pingText.value || 'ping')
}
</script>

<template>
  <div>
    <h2 class="page-title">仿真运行</h2>

    <div class="panel">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="3D 场景与实时帧推送将在第 2、6 周接入"
        description="当前页面先把 WebSocket 通道打通：连接后发送任意文本，后端会原样 echo 回来。"
      />

      <div style="margin-top: 16px">
        <el-space wrap>
          <el-button type="primary" :disabled="connected" @click="connect">连接 /ws/sim</el-button>
          <el-tag :type="connected ? 'success' : 'info'">{{ connected ? '已连接' : '未连接' }}</el-tag>
          <el-input v-model="pingText" style="width: 220px" placeholder="发送内容" />
          <el-button :disabled="!connected" @click="doSend">发送</el-button>
        </el-space>
      </div>

      <el-divider />
      <div class="muted" style="margin-bottom: 8px">最近消息（最多 50 条）</div>
      <div v-if="messages.length === 0" class="muted">暂无消息</div>
      <div v-for="(message, index) in messages" :key="index" class="ws-line">{{ message }}</div>
    </div>
  </div>
</template>

<style scoped>
.ws-line {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
  word-break: break-all;
}
</style>
