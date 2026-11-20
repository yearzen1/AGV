<script setup lang="ts">
import { useEditorContext } from '../editorContext'
import { ZONE_TYPE_LABEL, ZONE_TYPES, zoneTypeColor, zoneTypeLabel } from '../editorZones'

const {
  TYPE_LABEL,
  applyBatchEdge,
  applyBatchNodeType,
  batchEdgeCapacity,
  batchEdgeDirection,
  batchEdgeLaneMode,
  batchEdgeSpeed,
  batchNodeCapacity,
  batchNodeType,
  clampZone,
  deleteZone,
  edges,
  editable,
  errors,
  focusError,
  focusZone,
  idEditOriginal,
  mapHeight,
  mapWidth,
  nodes,
  onEdgeDirectionChange,
  onEdgeIdChange,
  onEdgeLaneChange,
  onNodeCoordChange,
  onNodeIdChange,
  onNodeIdOriginal,
  reverseEdge,
  selectedCount,
  selectedEdge,
  selectedEdgeIds,
  selectedNode,
  selectedNodeIds,
  selectedZone,
  selectedZoneId,
  sideTab,
  startZoneTool,
  stats,
  zoneType,
  zones
} = useEditorContext()
</script>

<template>
      <div class="side">
        <div class="side-inner">
          <div class="side-tabs" role="tablist">
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'props' }"
              :aria-selected="sideTab === 'props'"
              @click="sideTab = 'props'"
            >
              属性
            </button>
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'zones' }"
              :aria-selected="sideTab === 'zones'"
              @click="sideTab = 'zones'"
            >
              区域
              <span v-if="zones.length > 0" class="tab-count">{{ zones.length }}</span>
            </button>
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'stats' }"
              :aria-selected="sideTab === 'stats'"
              @click="sideTab = 'stats'"
            >
              统计
            </button>
            <button
              type="button"
              role="tab"
              :class="{ active: sideTab === 'errors' }"
              :aria-selected="sideTab === 'errors'"
              @click="sideTab = 'errors'"
            >
              校验
              <span v-if="errors.length > 0" class="tab-count">{{ errors.length }}</span>
            </button>
          </div>

          <div v-show="sideTab === 'props'" class="side-pane">
          <div class="side-block">
            <template v-if="selectedNode">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="编号">
                  <el-input
                    v-model="selectedNode.id"
                    @focus="onNodeIdOriginal(selectedNode)"
                    @change="onNodeIdChange(selectedNode, idEditOriginal)"
                  />
                </el-form-item>
                <el-form-item label="类型">
                  <el-select v-model="selectedNode.type" style="width: 100%">
                    <el-option
                      v-for="(label, key) in TYPE_LABEL"
                      :key="key"
                      :label="label"
                      :value="key"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number
                    v-model="selectedNode.capacity"
                    :min="1"
                    :max="99"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="X(m)">
                  <el-input-number
                    v-model="selectedNode.x"
                    :step="0.5"
                    :min="0"
                    :max="mapWidth"
                    style="width: 100%"
                    @change="onNodeCoordChange(selectedNode)"
                  />
                </el-form-item>
                <el-form-item label="Y(m)">
                  <el-input-number
                    v-model="selectedNode.y"
                    :step="0.5"
                    :min="0"
                    :max="mapHeight"
                    style="width: 100%"
                    @change="onNodeCoordChange(selectedNode)"
                  />
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="selectedEdge">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="编号">
                  <el-input
                    v-model="selectedEdge.id"
                    @focus="idEditOriginal = selectedEdge.id"
                    @change="onEdgeIdChange(selectedEdge, idEditOriginal)"
                  />
                </el-form-item>
                <el-form-item label="方向">
                  <el-select
                    v-model="selectedEdge.direction"
                    style="width: 100%"
                    @change="onEdgeDirectionChange(selectedEdge)"
                  >
                    <el-option label="双向" value="TWO_WAY" />
                    <el-option label="单向" value="ONE_WAY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="朝向">
                  <div class="orient-row">
                    <span class="orient-text">
                      {{
                        selectedEdge.direction === 'ONE_WAY'
                          ? `${selectedEdge.from} → ${selectedEdge.to}`
                          : '双向无固定朝向'
                      }}
                    </span>
                    <el-button
                      size="small"
                      :disabled="selectedEdge.direction !== 'ONE_WAY'"
                      @click="reverseEdge(selectedEdge)"
                    >
                      交换
                    </el-button>
                  </div>
                </el-form-item>
                <el-form-item label="车道">
                  <el-select
                    v-model="selectedEdge.laneMode"
                    style="width: 100%"
                    :disabled="selectedEdge.direction === 'ONE_WAY'"
                    @change="onEdgeLaneChange(selectedEdge)"
                  >
                    <el-option label="单车道" value="SINGLE" />
                    <el-option label="双车道" value="DUAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="长度(m)">
                  <el-input-number
                    :model-value="selectedEdge.length"
                    :step="0.1"
                    :min="0.1"
                    disabled
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="速度">
                  <el-input-number
                    v-model="selectedEdge.speed"
                    :step="0.1"
                    :min="0.1"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number
                    v-model="selectedEdge.capacity"
                    :min="1"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="selectedNodeIds.length > 1">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="类型">
                  <el-select v-model="batchNodeType" style="width: 100%">
                    <el-option
                      v-for="(label, key) in TYPE_LABEL"
                      :key="key"
                      :label="label"
                      :value="key"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number v-model="batchNodeCapacity" :min="1" :max="99" style="width: 100%" />
                </el-form-item>
              </el-form>
              <el-button size="small" type="primary" @click="applyBatchNodeType">
                应用到 {{ selectedNodeIds.length }} 个节点
              </el-button>
            </template>
            <template v-else-if="selectedEdgeIds.length > 1">
              <el-form label-width="64px" size="small" :disabled="!editable">
                <el-form-item label="方向">
                  <el-select v-model="batchEdgeDirection" style="width: 100%">
                    <el-option label="双向" value="TWO_WAY" />
                    <el-option label="单向" value="ONE_WAY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="车道">
                  <el-select v-model="batchEdgeLaneMode" style="width: 100%">
                    <el-option label="单车道" value="SINGLE" />
                    <el-option label="双车道" value="DUAL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="速度">
                  <el-input-number v-model="batchEdgeSpeed" :step="0.1" :min="0.1" style="width: 100%" />
                </el-form-item>
                <el-form-item label="容量">
                  <el-input-number v-model="batchEdgeCapacity" :min="1" style="width: 100%" />
                </el-form-item>
              </el-form>
              <el-button size="small" type="primary" @click="applyBatchEdge">
                应用到 {{ selectedEdgeIds.length }} 条路段
              </el-button>
            </template>
            <div v-else-if="selectedCount > 0" class="muted">
              已选 {{ selectedNodeIds.length }} 个节点、{{ selectedEdgeIds.length }} 条路段
            </div>
            <div v-else class="muted">选中节点或路段后可编辑属性</div>
          </div>
          </div>

          <div v-show="sideTab === 'zones'" class="side-pane">
          <div class="side-block">
            <div class="side-title">
              区域
              <el-tag v-if="zones.length > 0" size="small" type="info">{{ zones.length }}</el-tag>
            </div>
            <div class="zone-toolbar">
              <el-select v-model="zoneType" size="small" :disabled="!editable" style="flex: 1">
                <el-option
                  v-for="type in ZONE_TYPES"
                  :key="type"
                  :label="ZONE_TYPE_LABEL[type]"
                  :value="type"
                />
              </el-select>
              <el-button size="small" type="primary" :disabled="!editable" @click="startZoneTool">
                画区域
              </el-button>
            </div>
            <div class="zone-list">
              <div v-if="zones.length === 0" class="muted">还没有区域，先选类型再点「画区域」</div>
              <div
                v-for="zone in zones"
                :key="zone.id"
                class="zone-row"
                :class="{ active: selectedZoneId === zone.id }"
                @click="focusZone(zone)"
              >
                <span class="zone-swatch" :style="{ background: zoneTypeColor(zone.type) }" />
                <span class="zone-name">{{ zone.name }}</span>
                <span class="zone-meta">{{ zoneTypeLabel(zone.type) }} · {{ zone.width }}×{{ zone.height }}m</span>
                <el-button
                  v-if="editable"
                  link
                  type="danger"
                  size="small"
                  @click.stop="deleteZone(zone)"
                >
                  删除
                </el-button>
              </div>
            </div>
            <div class="muted zone-hint">区域只是地图注释，不参与校验、寻路和仿真。</div>
          </div>

          <div v-if="selectedZone" class="side-block">
            <div class="side-title">选中区域</div>
            <el-form label-width="64px" size="small" :disabled="!editable">
              <el-form-item label="名称">
                <el-input v-model="selectedZone.name" />
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="selectedZone.type" style="width: 100%">
                  <el-option
                    v-for="type in ZONE_TYPES"
                    :key="type"
                    :label="ZONE_TYPE_LABEL[type]"
                    :value="type"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="X(m)">
                <el-input-number
                  v-model="selectedZone.x"
                  :step="0.5"
                  :min="0"
                  :max="mapWidth"
                  style="width: 100%"
                  @change="clampZone(selectedZone)"
                />
              </el-form-item>
              <el-form-item label="Y(m)">
                <el-input-number
                  v-model="selectedZone.y"
                  :step="0.5"
                  :min="0"
                  :max="mapHeight"
                  style="width: 100%"
                  @change="clampZone(selectedZone)"
                />
              </el-form-item>
              <el-form-item label="宽(m)">
                <el-input-number
                  v-model="selectedZone.width"
                  :step="0.5"
                  :min="1"
                  :max="mapWidth"
                  style="width: 100%"
                  @change="clampZone(selectedZone)"
                />
              </el-form-item>
              <el-form-item label="高(m)">
                <el-input-number
                  v-model="selectedZone.height"
                  :step="0.5"
                  :min="1"
                  :max="mapHeight"
                  style="width: 100%"
                  @change="clampZone(selectedZone)"
                />
              </el-form-item>
            </el-form>
            <el-button v-if="editable" size="small" type="danger" plain @click="deleteZone(selectedZone)">
              删除区域
            </el-button>
          </div>
          </div>

          <div v-show="sideTab === 'stats'" class="side-pane">
          <div class="side-block">
            <div class="side-title">统计</div>
            <div class="stat-row"><span>节点</span><b>{{ stats.nodes }}</b></div>
            <div class="stat-row"><span>路段</span><b>{{ stats.edges }}</b></div>
            <div class="stat-row"><span>区域</span><b>{{ stats.zones }}</b></div>
            <div class="stat-row"><span>总长度</span><b>{{ stats.length.toFixed(1) }} m</b></div>
            <div class="stat-row"><span>充电位</span><b>{{ stats.chargers }}</b></div>
            <div class="stat-row"><span>选中</span><b>{{ selectedCount }}</b></div>
          </div>
          </div>

          <div v-show="sideTab === 'errors'" class="side-pane">
          <div class="side-block">
            <div class="side-title">
              校验
              <el-tag v-if="errors.length > 0" type="danger" size="small">{{ errors.length }}</el-tag>
            </div>
            <div v-if="errors.length === 0" class="muted">未发现问题</div>
            <div v-else class="error-list">
              <div
                v-for="(error, index) in errors"
                :key="index"
                class="error-row"
                @click="focusError(error)"
              >
                <el-tag size="small" type="danger">{{ error.code }}</el-tag>
                <span>{{ error.message }}</span>
              </div>
            </div>
          </div>
          </div>
        </div>
      </div>
</template>

<style scoped>
.side {
  position: relative;
  min-height: 0;
}

.side-inner {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.side-tabs {
  display: flex;
  gap: 3px;
  padding: 3px;
  background: #eef2f7;
  border-radius: 8px;
  flex-shrink: 0;
}

.side-tabs button {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: #5c7392;
  font-size: 12px;
  padding: 5px 6px;
  border-radius: 6px;
  cursor: pointer;
}

.side-tabs button:hover {
  color: #1f3b73;
}

.side-tabs button.active {
  background: #fff;
  color: #1f3b73;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(31, 59, 115, 0.12);
}

.tab-count {
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 500;
}

.side-pane {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.side-block {
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.side-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #1f3b73;
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
  color: #606266;
}

.orient-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.orient-text {
  font-size: 12px;
  color: #303133;
  white-space: nowrap;
}

.error-list {
  max-height: 240px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.error-row {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  font-size: 12px;
  color: #606266;
  padding: 4px 0;
  line-height: 1.4;
  cursor: pointer;
}

.error-row:hover {
  color: #1f3b73;
}

.muted {
  font-size: 12px;
  color: #909399;
}

.zone-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.zone-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 220px;
  overflow: auto;
}

.zone-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 6px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}

.zone-row:hover {
  background: #f2f6fc;
}

.zone-row.active {
  background: #e8f0fe;
}

.zone-swatch {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex: none;
  opacity: 0.85;
}

.zone-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zone-meta {
  color: #909399;
  font-size: 11px;
  white-space: nowrap;
}

.zone-hint {
  margin-top: 8px;
  font-size: 11px;
  line-height: 1.4;
}
</style>
