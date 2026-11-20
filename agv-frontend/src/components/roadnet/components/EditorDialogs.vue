<script setup lang="ts">
import { useEditorContext } from '../editorContext'

const {
  NODE_TOOLS,
  TYPE_LABEL,
  contextAddNode,
  contextDelete,
  contextMenu,
  contextReverse,
  contextSplit,
  fitSelection,
  helpVisible,
  hover,
  hoverInfo,
  px
} = useEditorContext()
</script>

<template>
    <teleport to="body">
      <div
        v-if="hoverInfo && hover"
        class="map-tooltip"
        :style="{ left: `${hover.x + 14}px`, top: `${hover.y + 14}px` }"
      >
        <div class="tooltip-title">{{ hoverInfo.title }}</div>
        <div v-for="row in hoverInfo.rows" :key="row" class="tooltip-row">{{ row }}</div>
      </div>
    </teleport>

    <div
      v-if="contextMenu"
      class="context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
    >
      <template v-if="contextMenu.kind === 'canvas'">
        <button v-for="type in NODE_TOOLS" :key="type" @click="contextAddNode(type)">
          新建{{ TYPE_LABEL[type] }}
        </button>
      </template>
      <template v-else-if="contextMenu.kind === 'edge'">
        <button @click="contextSplit">拆分路段</button>
        <button @click="contextReverse">反转方向</button>
        <button @click="contextDelete">删除路段</button>
      </template>
      <template v-else>
        <button @click="contextDelete">删除节点</button>
        <button @click="fitSelection">适应选中</button>
      </template>
    </div>

    <el-dialog v-model="helpVisible" title="快捷键与操作" width="560px">
      <table class="help-table">
        <tbody>
          <tr><td>滚轮</td><td>以指针为中心缩放</td></tr>
          <tr><td>`空格` + 左键 / 中键</td><td>平移画布</td></tr>
          <tr><td>`Ctrl/Cmd+A`</td><td>全选</td></tr>
          <tr><td>`Ctrl/Cmd+C` / `V` / `D`</td><td>复制 / 粘贴 / 再制</td></tr>
          <tr><td>`Ctrl/Cmd+Z`</td><td>撤销</td></tr>
          <tr><td>`Ctrl/Cmd+Shift+Z` / `Ctrl+Y`</td><td>重做</td></tr>
          <tr><td>`Ctrl/Cmd+S`</td><td>保存</td></tr>
          <tr><td>`Delete` / `Backspace`</td><td>删除选中</td></tr>
          <tr><td>`Esc`</td><td>清空选择 / 退出页面内全屏 / 关闭菜单</td></tr>
          <tr><td>方向键</td><td>选中节点移动 0.5 米</td></tr>
          <tr><td>`Shift` + 方向键</td><td>选中节点移动 2.5 米</td></tr>
          <tr><td>`Shift` + 拖动</td><td>锁定水平 / 垂直</td></tr>
          <tr><td>`?`</td><td>打开本帮助</td></tr>
        </tbody>
      </table>
    </el-dialog>
</template>

<style scoped>
.map-tooltip {
  position: fixed;
  z-index: 3000;
  background: rgba(32, 41, 56, 0.94);
  color: #fff;
  border-radius: 6px;
  padding: 6px 9px;
  font-size: 12px;
  pointer-events: none;
  max-width: 260px;
}

.tooltip-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.tooltip-row {
  color: #dbe4ef;
}

.context-menu {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  box-shadow: 0 6px 20px rgba(31, 59, 115, 0.16);
  padding: 4px;
  display: flex;
  flex-direction: column;
  min-width: 128px;
}

.context-menu button {
  border: none;
  background: transparent;
  text-align: left;
  padding: 6px 10px;
  font-size: 12px;
  color: #303133;
  cursor: pointer;
  border-radius: 4px;
}

.context-menu button:hover {
  background: #f2f6fc;
  color: #1f3b73;
}

.help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.help-table td {
  padding: 6px 8px;
  border-bottom: 1px solid #f0f2f5;
  color: #606266;
}

.help-table td:first-child {
  width: 42%;
  color: #303133;
  font-weight: 600;
}
</style>
