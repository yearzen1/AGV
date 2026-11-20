<script setup lang="ts">
import { useEditorContext } from '../editorContext'

const {
  ICONS,
  MAX_SIZE,
  MIN_SIZE,
  NODE_TOOLS,
  TYPE_COLOR,
  TYPE_LABEL,
  applyMapSize,
  canRedo,
  canUndo,
  clearSelection,
  clipboard,
  copySelection,
  deleteSelected,
  editable,
  errors,
  fitView,
  handleAlign,
  hasNodeEdgeSelection,
  hasSelectedOneWay,
  mapHeight,
  mapWidth,
  mergeSelectedEdges,
  nextSearch,
  onToolbarMore,
  pasteClipboard,
  redo,
  resetView,
  reverseSelectedEdges,
  searchLabel,
  searchQuery,
  selectAll,
  selectedCount,
  selectedEdgeIds,
  selectedNodeIds,
  showEdgeLabels,
  showGrid,
  showNodeLabels,
  showTasks,
  showZones,
  startPanRepeat,
  stopPanRepeat,
  tool,
  undo,
  zoomIn,
  zoomOut,
  zoomPercent
} = useEditorContext()
</script>

<template>
    <div class="toolbar">
      <div class="toolbar-row toolbar-main">
        <template v-if="editable">
          <el-radio-group v-model="tool" size="small">
            <el-radio-button value="select">选择</el-radio-button>
            <el-radio-button value="pan">平移</el-radio-button>
            <el-radio-button value="connect">连路段</el-radio-button>
            <el-radio-button value="zone">区域</el-radio-button>
          </el-radio-group>
          <el-radio-group v-model="tool" size="small" class="node-tools">
            <el-radio-button v-for="type in NODE_TOOLS" :key="type" :value="type">
              <span class="node-dot-mini" :style="{ background: TYPE_COLOR[type] }" />
              {{ TYPE_LABEL[type] }}
            </el-radio-button>
          </el-radio-group>
          <el-button-group class="icon-group">
            <el-button size="small" :disabled="!canUndo" title="撤销 (Ctrl+Z)" @click="undo">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.undo" />
              </svg>
            </el-button>
            <el-button size="small" :disabled="!canRedo" title="重做 (Ctrl+Shift+Z)" @click="redo">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.redo" />
              </svg>
            </el-button>
          </el-button-group>
        </template>
        <el-tag v-else type="info" size="small">预览模式 · 点「编辑」可修改</el-tag>
        <div class="spacer" />
        <span class="hint">滚轮缩放 · 空格/中键平移</span>
      </div>

      <div class="toolbar-row toolbar-secondary">
        <template v-if="editable">
          <el-button-group class="icon-group">
            <el-button size="small" :disabled="!hasNodeEdgeSelection" title="复制 (Ctrl+C)" @click="copySelection">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.copy" />
              </svg>
            </el-button>
            <el-button size="small" :disabled="!clipboard" title="粘贴 (Ctrl+V)" @click="pasteClipboard">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.paste" />
              </svg>
            </el-button>
            <el-button size="small" :disabled="selectedCount === 0" title="删除 (Delete)" @click="deleteSelected">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.trash" />
              </svg>
            </el-button>
          </el-button-group>
          <el-dropdown @command="handleAlign">
            <el-button size="small" :disabled="selectedNodeIds.length < 2" title="对齐与分布">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.align" />
              </svg>
              对齐
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="left">左对齐</el-dropdown-item>
                <el-dropdown-item command="center-x">水平居中</el-dropdown-item>
                <el-dropdown-item command="right">右对齐</el-dropdown-item>
                <el-dropdown-item command="top">顶对齐</el-dropdown-item>
                <el-dropdown-item command="middle-y">垂直居中</el-dropdown-item>
                <el-dropdown-item command="bottom">底对齐</el-dropdown-item>
                <el-dropdown-item command="distribute-x" divided>水平等距</el-dropdown-item>
                <el-dropdown-item command="distribute-y">垂直等距</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" :disabled="selectedEdgeIds.length !== 2" title="合并两条共享端点的路段" @click="mergeSelectedEdges">
            合并
          </el-button>
          <el-button size="small" :disabled="!hasSelectedOneWay" title="仅单向路段可反转" @click="reverseSelectedEdges">
            反转
          </el-button>
          <el-divider direction="vertical" />
        </template>

        <el-input
          v-model="searchQuery"
          class="search-input"
          size="small"
          placeholder="搜索编号"
          clearable
          @keyup.enter="nextSearch"
        >
          <template #append>
            <el-button :disabled="!searchQuery.trim()" title="Enter 定位下一个命中" @click="nextSearch">
              {{ searchLabel }}
            </el-button>
          </template>
        </el-input>

        <el-popover placement="bottom" trigger="click" width="230">
          <template #reference>
            <el-button size="small" title="显示选项与画布尺寸">
              <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path :d="ICONS.eye" />
              </svg>
              显示
            </el-button>
          </template>
          <div class="popover-list">
            <el-checkbox v-model="showNodeLabels">节点标签</el-checkbox>
            <el-checkbox v-model="showEdgeLabels">路段标签</el-checkbox>
            <el-checkbox v-model="showGrid">背景网格</el-checkbox>
            <el-checkbox v-model="showTasks">任务流向</el-checkbox>
            <el-checkbox v-model="showZones">区域注释</el-checkbox>
            <div class="popover-divider" />
            <div class="side-title">画布尺寸</div>
            <div class="size-editor">
              <label>
                宽（米）
                <el-input-number
                  v-model="mapWidth"
                  :min="MIN_SIZE"
                  :max="MAX_SIZE"
                  :disabled="!editable"
                  size="small"
                  @change="applyMapSize"
                />
              </label>
              <label>
                高（米）
                <el-input-number
                  v-model="mapHeight"
                  :min="MIN_SIZE"
                  :max="MAX_SIZE"
                  :disabled="!editable"
                  size="small"
                  @change="applyMapSize"
                />
              </label>
            </div>
          </div>
        </el-popover>

        <el-button size="small" @click="selectAll">全选</el-button>
        <el-button size="small" :disabled="selectedCount === 0" @click="clearSelection">清空</el-button>

        <el-button-group class="zoom-group">
          <el-button size="small" title="缩小" @click="zoomOut">−</el-button>
          <el-button size="small" title="重置为 100%" @click="resetView">{{ zoomPercent }}%</el-button>
          <el-button size="small" title="放大" @click="zoomIn">+</el-button>
        </el-button-group>
        <el-button-group class="pan-buttons">
          <el-button size="small" title="向左移动" @mousedown.prevent="startPanRepeat(-80, 0)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">←</el-button>
          <el-button size="small" title="向右移动" @mousedown.prevent="startPanRepeat(80, 0)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">→</el-button>
          <el-button size="small" title="向上移动" @mousedown.prevent="startPanRepeat(0, -80)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">↑</el-button>
          <el-button size="small" title="向下移动" @mousedown.prevent="startPanRepeat(0, 80)" @mouseup="stopPanRepeat" @mouseleave="stopPanRepeat">↓</el-button>
        </el-button-group>
        <el-button size="small" title="适应窗口" @click="fitView">
          <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
            <path :d="ICONS.fit" />
          </svg>
        </el-button>
        <el-dropdown trigger="click" @command="onToolbarMore">
          <el-button size="small" title="更多操作">
            <svg class="tool-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path :d="ICONS.more" stroke-width="2.6" />
            </svg>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="fit-selection" :disabled="selectedCount === 0">适应选中</el-dropdown-item>
              <el-dropdown-item command="fit-error" :disabled="errors.length === 0">适应错误</el-dropdown-item>
              <el-dropdown-item command="png" divided>导出 PNG</el-dropdown-item>
              <el-dropdown-item command="svg">导出 SVG</el-dropdown-item>
              <el-dropdown-item command="help" divided>快捷键与操作</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.toolbar-main {
  flex-wrap: wrap;
}

.toolbar-secondary {
  flex-wrap: wrap;
  padding-top: 6px;
  border-top: 1px solid #eef2f7;
}

.spacer {
  flex: 1;
}

.hint {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.tool-icon {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
  vertical-align: -2px;
}

.icon-group :deep(.el-button) {
  padding-left: 7px;
  padding-right: 7px;
}

.node-tools :deep(.el-radio-button__inner) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.search-input {
  width: 140px;
}

.popover-divider {
  height: 1px;
  background: #eef2f7;
  margin: 2px 0;
}

.popover-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.size-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.size-editor label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #606266;
  gap: 8px;
}

@media (max-width: 1600px) {
  .pan-buttons {
    display: none;
  }
}

@media (max-width: 1400px) {
  .hint {
    display: none;
  }

  .search-input {
    width: 110px;
  }

  .toolbar-secondary :deep(.el-button) {
    padding-left: 8px;
    padding-right: 8px;
  }
}
</style>
