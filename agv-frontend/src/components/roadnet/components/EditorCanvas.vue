<script setup lang="ts">
import { useEditorContext } from '../editorContext'
import { zoneTypeColor, zoneTypeLabel } from '../editorZones'

const {
  SCALE,
  SNAP,
  TYPE_COLOR,
  beginZoneResizeSelected,
  canvasClass,
  canvasWrap,
  clearHover,
  connectFrom,
  contentRef,
  cursorWorld,
  edgeArrows,
  edgeLanes,
  edgeMid,
  edgeStroke,
  edges,
  editable,
  endMini,
  errorNodeIds,
  guides,
  hovering,
  isFullscreen,
  isMaximized,
  isSelectedEdge,
  isSelectedNode,
  legendCollapsed,
  mapHeight,
  mapWidth,
  marquee,
  marqueeBox,
  miniDragging,
  miniHeight,
  miniRef,
  miniScale,
  miniViewport,
  miniWidth,
  minimapCollapsed,
  moveMini,
  nodeMap,
  nodes,
  onCanvasClick,
  onCanvasContextMenu,
  onCanvasLeave,
  onCanvasMouseDown,
  onCanvasMouseMove,
  onCanvasMouseUp,
  onEdgeContextMenu,
  onEdgeDoubleClick,
  onEdgeEnter,
  onEdgeMouseDown,
  onNodeClick,
  onNodeContextMenu,
  onNodeEnter,
  onNodeMouseDown,
  onWheel,
  onZoneMouseDown,
  px,
  scaleBarMeters,
  scaleBarPx,
  searchHitEdgeIds,
  searchHitNodeIds,
  selectedZone,
  selectedZoneHandleRects,
  selectedZoneId,
  showEdgeLabels,
  showGrid,
  showNodeLabels,
  showTasks,
  showZones,
  startMini,
  svgRef,
  taskBadges,
  taskFlows,
  tasks,
  toggleFullscreen,
  toggleLegend,
  toggleMinimap,
  tool,
  transform,
  xTicks,
  yTicks,
  zoneDraftRect,
  zoneLegend,
  zoneType,
  zones
} = useEditorContext()
</script>

<template>
      <div
        ref="canvasWrap"
        class="canvas-wrap"
        :class="{ 'is-maximized': isMaximized }"
      >
        <div v-if="editable && tool === 'connect' && connectFrom" class="connect-hint">
          已选起点 {{ connectFrom }}，再点一个节点完成连接，Esc 取消
        </div>
        <div v-if="editable && tool === 'zone'" class="connect-hint">
          拖拽绘制{{ zoneTypeLabel(zoneType) }}，单击创建默认 6×4 米；Esc 取消
        </div>
        <svg
          ref="svgRef"
          :viewBox="`0 0 ${mapWidth * SCALE} ${mapHeight * SCALE}`"
          class="canvas"
          :class="canvasClass"
          @click="onCanvasClick"
          @wheel.prevent="onWheel"
          @mousedown="onCanvasMouseDown"
          @mousemove="onCanvasMouseMove"
          @mouseup="onCanvasMouseUp"
          @mouseenter="hovering = true"
          @mouseleave="onCanvasLeave"
          @contextmenu.prevent="onCanvasContextMenu"
        >
          <defs>
            <pattern
              id="smallGrid"
              :width="SNAP * SCALE"
              :height="SNAP * SCALE"
              patternUnits="userSpaceOnUse"
            >
              <path
                :d="`M ${SNAP * SCALE} 0 L 0 0 0 ${SNAP * SCALE}`"
                fill="none"
                stroke="#eef2f7"
                stroke-width="1"
              />
            </pattern>
            <pattern id="grid" :width="SCALE * 5" :height="SCALE * 5" patternUnits="userSpaceOnUse">
              <rect :width="SCALE * 5" :height="SCALE * 5" fill="url(#smallGrid)" />
              <path
                :d="`M ${SCALE * 5} 0 L 0 0 0 ${SCALE * 5}`"
                fill="none"
                stroke="#dde5ef"
                stroke-width="1"
              />
            </pattern>
            <marker
              id="arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerUnits="userSpaceOnUse"
              markerWidth="12"
              markerHeight="12"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#8fa3bd" />
            </marker>
            <marker
              id="arrow-selected"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerUnits="userSpaceOnUse"
              markerWidth="12"
              markerHeight="12"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#1f3b73" />
            </marker>
            <marker
              id="arrow-error"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerUnits="userSpaceOnUse"
              markerWidth="12"
              markerHeight="12"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#f56c6c" />
            </marker>
            <marker
              id="task-arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="5"
              markerHeight="5"
              orient="auto-start-reverse"
            >
              <path d="M0,0 L10,5 L0,10 z" fill="#a06a1f" />
            </marker>
          </defs>

          <g ref="contentRef" data-content="true" :transform="transform">
            <rect
              :width="mapWidth * SCALE"
              :height="mapHeight * SCALE"
              :fill="showGrid ? 'url(#grid)' : '#fbfdff'"
            />
            <rect
              class="map-border"
              x="1"
              y="1"
              :width="mapWidth * SCALE - 2"
              :height="mapHeight * SCALE - 2"
              vector-effect="non-scaling-stroke"
              pointer-events="none"
            />
            <g class="map-ticks" pointer-events="none">
              <g v-for="tick in xTicks" :key="`tx-${tick}`">
                <line :x1="tick * SCALE" y1="0" :x2="tick * SCALE" y2="8" />
                <text
                  :x="tick * SCALE"
                  y="19"
                  :text-anchor="tick === 0 ? 'start' : tick === mapWidth ? 'end' : 'middle'"
                >
                  {{ tick }}
                </text>
              </g>
              <g v-for="tick in yTicks" :key="`ty-${tick}`">
                <line x1="0" :y1="tick * SCALE" x2="8" :y2="tick * SCALE" />
                <text
                  x="11"
                  :y="Math.min(tick * SCALE + 4, mapHeight * SCALE - 3)"
                >
                  {{ tick }}
                </text>
              </g>
            </g>

            <g v-if="showZones" class="zone-layer">
              <g v-for="zone in zones" :key="`zone-${zone.id}`">
                <rect
                  :x="zone.x * SCALE"
                  :y="zone.y * SCALE"
                  :width="zone.width * SCALE"
                  :height="zone.height * SCALE"
                  rx="2"
                  :fill="zoneTypeColor(zone.type)"
                  fill-opacity="0.12"
                  :stroke="zoneTypeColor(zone.type)"
                  stroke-width="1.5"
                  stroke-dasharray="6 4"
                  vector-effect="non-scaling-stroke"
                  class="zone-fill"
                  :class="{ selected: selectedZoneId === zone.id }"
                  :style="{ pointerEvents: tool === 'select' ? 'auto' : 'none' }"
                  @mousedown="onZoneMouseDown($event, zone)"
                  @click.stop
                />
                <text
                  :x="zone.x * SCALE + 6"
                  :y="zone.y * SCALE + 14"
                  :fill="zoneTypeColor(zone.type)"
                  class="zone-label"
                  pointer-events="none"
                >
                  {{ zone.name }}
                </text>
              </g>
            </g>

            <rect
              v-if="zoneDraftRect"
              :x="zoneDraftRect.x * SCALE"
              :y="zoneDraftRect.y * SCALE"
              :width="zoneDraftRect.width * SCALE"
              :height="zoneDraftRect.height * SCALE"
              rx="2"
              :fill="zoneTypeColor(zoneType)"
              fill-opacity="0.1"
              :stroke="zoneTypeColor(zoneType)"
              stroke-width="1.5"
              stroke-dasharray="4 4"
              vector-effect="non-scaling-stroke"
              class="zone-draft"
              data-export="ignore"
              pointer-events="none"
            />

            <g v-if="showTasks && taskFlows.length > 0">
              <path
                v-for="flow in taskFlows"
                :key="`task-${flow.key}`"
                :d="flow.d"
                class="task-flow"
                marker-end="url(#task-arrow)"
              />
              <g v-for="flow in taskFlows.filter((item) => item.count > 1)" :key="`task-count-${flow.key}`">
                <rect
                  :x="flow.labelX - 11"
                  :y="flow.labelY - 8"
                  width="22"
                  height="16"
                  rx="8"
                  class="task-count"
                />
                <text :x="flow.labelX" :y="flow.labelY + 3" text-anchor="middle" class="task-count-text">
                  {{ flow.count }}
                </text>
              </g>
              <g v-for="badge in taskBadges" :key="`badge-${badge.id}`">
                <rect
                  :x="badge.x - 4"
                  :y="badge.y - 10"
                  width="42"
                  height="16"
                  rx="8"
                  class="task-badge"
                />
                <text :x="badge.x + 17" :y="badge.y + 2" text-anchor="middle" class="task-badge-text">
                  {{ badge.from }}/{{ badge.to }}
                </text>
              </g>
            </g>

            <g v-for="edge in edges" :key="edge.id">
              <line
                v-for="lane in edgeLanes(edge)"
                :key="lane.key"
                :x1="lane.x1"
                :y1="lane.y1"
                :x2="lane.x2"
                :y2="lane.y2"
                :stroke="edgeStroke(edge)"
                :stroke-width="isSelectedEdge(edge.id) ? 4 : 3"
                pointer-events="none"
              />
              <line
                v-for="arrow in edgeArrows(edge)"
                :key="arrow.key"
                :x1="arrow.x1"
                :y1="arrow.y1"
                :x2="arrow.x2"
                :y2="arrow.y2"
                :stroke="edgeStroke(edge)"
                :stroke-width="isSelectedEdge(edge.id) ? 4 : 3"
                :marker-end="arrow.markerEnd"
                pointer-events="none"
              />
              <line
                :x1="px(nodeMap.get(edge.from)?.x ?? 0)"
                :y1="px(nodeMap.get(edge.from)?.y ?? 0)"
                :x2="px(nodeMap.get(edge.to)?.x ?? 0)"
                :y2="px(nodeMap.get(edge.to)?.y ?? 0)"
                class="edge-hit"
                @mousedown="onEdgeMouseDown($event, edge)"
                @dblclick="onEdgeDoubleClick($event, edge)"
                @mouseenter="onEdgeEnter($event, edge)"
                @mouseleave="clearHover"
                @contextmenu.prevent.stop="onEdgeContextMenu($event, edge)"
              />
              <line
                v-if="searchHitEdgeIds.has(edge.id)"
                :x1="px(nodeMap.get(edge.from)?.x ?? 0)"
                :y1="px(nodeMap.get(edge.from)?.y ?? 0)"
                :x2="px(nodeMap.get(edge.to)?.x ?? 0)"
                :y2="px(nodeMap.get(edge.to)?.y ?? 0)"
                class="search-hit-edge"
                pointer-events="none"
              />
              <text
                v-if="showEdgeLabels"
                :x="edgeMid(edge).x"
                :y="edgeMid(edge).y - 6"
                text-anchor="middle"
                font-size="10"
                fill="#7a8aa3"
                pointer-events="none"
              >
                {{ edge.id }} · {{ edge.length }}m
              </text>
            </g>

            <g v-for="node in nodes" :key="node.id">
              <circle
                v-if="searchHitNodeIds.has(node.id)"
                :cx="px(node.x)"
                :cy="px(node.y)"
                r="14"
                class="search-ring"
                pointer-events="none"
              />
              <circle
                :cx="px(node.x)"
                :cy="px(node.y)"
                r="9"
                :fill="errorNodeIds.has(node.id) ? '#f56c6c' : TYPE_COLOR[node.type]"
                :stroke="
                  connectFrom === node.id
                    ? '#ffd666'
                    : isSelectedNode(node.id)
                      ? '#1f3b73'
                      : '#ffffff'
                "
                :stroke-width="connectFrom === node.id || isSelectedNode(node.id) ? 4 : 2"
                class="node-dot"
                :class="{ selected: isSelectedNode(node.id) }"
                @click="onNodeClick($event, node)"
                @mousedown="onNodeMouseDown($event, node)"
                @mouseenter="onNodeEnter($event, node)"
                @mouseleave="clearHover"
                @contextmenu.prevent.stop="onNodeContextMenu($event, node)"
              />
              <text
                v-if="showNodeLabels"
                :x="px(node.x) + 12"
                :y="px(node.y) + 4"
                font-size="11"
                fill="#606266"
                pointer-events="none"
              >
                {{ node.id }}
              </text>
            </g>

            <g
              v-if="showZones && selectedZone"
              class="zone-selection"
              data-export="ignore"
              pointer-events="none"
            >
              <rect
                :x="selectedZone.x * SCALE"
                :y="selectedZone.y * SCALE"
                :width="selectedZone.width * SCALE"
                :height="selectedZone.height * SCALE"
                rx="2"
                fill="none"
                stroke="#1f3b73"
                stroke-width="2"
                vector-effect="non-scaling-stroke"
              />
              <rect
                v-for="handle in selectedZoneHandleRects"
                :key="`zone-handle-${handle.handle}`"
                :x="handle.x"
                :y="handle.y"
                :width="handle.width"
                :height="handle.height"
                class="zone-handle"
                :style="{ cursor: handle.cursor }"
                @mousedown.stop.prevent="beginZoneResizeSelected($event, handle.handle)"
              />
            </g>

            <line
              v-if="guides.x !== undefined"
              :x1="guides.x"
              y1="0"
              :x2="guides.x"
              :y2="mapHeight * SCALE"
              class="guide"
              data-export="ignore"
            />
            <line
              v-if="guides.y !== undefined"
              x1="0"
              :y1="guides.y"
              :x2="mapWidth * SCALE"
              :y2="guides.y"
              class="guide"
              data-export="ignore"
            />
          </g>

          <rect
            v-if="marquee"
            :x="marqueeBox.x"
            :y="marqueeBox.y"
            :width="marqueeBox.width"
            :height="marqueeBox.height"
            class="marquee"
            data-export="ignore"
          />
        </svg>

        <button
          v-if="legendCollapsed"
          class="overlay-toggle legend-toggle"
          type="button"
          @click="toggleLegend"
        >
          图例
        </button>
        <div v-else class="map-legend">
          <div class="legend-head">
            <span>图例</span>
            <button type="button" title="收起图例" @click="toggleLegend">−</button>
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.WORKSTATION }"></span>工位
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.CHARGER }"></span>充电位
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.BUFFER }"></span>缓存位
          </div>
          <div class="legend-row">
            <span class="dot" :style="{ background: TYPE_COLOR.JUNCTION }"></span>路口
          </div>
          <div class="legend-row">
            <span class="legend-symbol">→</span>单车道单向
          </div>
          <div class="legend-row">
            <span class="legend-symbol">↔</span>单车道双向
          </div>
          <div class="legend-row">
            <span class="legend-symbol dual">＝</span>双车道双向
          </div>
          <template v-if="showZones && zoneLegend.length > 0">
            <div class="legend-divider">区域</div>
            <div v-for="item in zoneLegend" :key="item.type" class="legend-row">
              <span class="dot" :style="{ background: item.color }"></span>{{ item.label }}
            </div>
          </template>
          <div class="legend-row">
            <span class="dot error"></span>校验错误
          </div>
          <div v-if="showTasks && tasks && tasks.length > 0" class="legend-row">
            <span class="badge-sample">7/6</span>任务 发/收
          </div>
        </div>

        <div class="scale-bar">
          <div class="scale-line" :style="{ width: `${scaleBarPx}px` }"></div>
          <span>{{ scaleBarMeters }} m</span>
        </div>

        <div class="coords">
          {{ cursorWorld ? `X ${cursorWorld.x.toFixed(1)} m · Y ${cursorWorld.y.toFixed(1)} m` : '鼠标移入画布查看坐标' }}
        </div>

        <button
          v-if="minimapCollapsed"
          class="overlay-toggle minimap-toggle"
          type="button"
          @click="toggleMinimap"
        >
          概览
        </button>
        <div
          v-else
          class="minimap"
          :class="{ 'is-dragging': miniDragging }"
          @mousedown.prevent="startMini"
          @mousemove="moveMini"
          @mouseup="endMini"
          @mouseleave="endMini"
        >
          <button
            class="minimap-collapse"
            type="button"
            title="收起小地图"
            @mousedown.stop
            @click.stop="toggleMinimap"
          >
            −
          </button>
          <svg
            ref="miniRef"
            :width="miniWidth"
            :height="miniHeight"
            :viewBox="`0 0 ${miniWidth} ${miniHeight}`"
          >
            <rect :width="miniWidth" :height="miniHeight" fill="#f7fbff" />
            <line
              v-for="edge in edges"
              :key="`mini-${edge.id}`"
              :x1="(nodeMap.get(edge.from)?.x ?? 0) * SCALE * miniScale"
              :y1="(nodeMap.get(edge.from)?.y ?? 0) * SCALE * miniScale"
              :x2="(nodeMap.get(edge.to)?.x ?? 0) * SCALE * miniScale"
              :y2="(nodeMap.get(edge.to)?.y ?? 0) * SCALE * miniScale"
              stroke="#b6cbe8"
              stroke-width="1"
            />
            <circle
              v-for="node in nodes"
              :key="`mini-${node.id}`"
              :cx="node.x * SCALE * miniScale"
              :cy="node.y * SCALE * miniScale"
              r="1.8"
              :fill="TYPE_COLOR[node.type]"
            />
            <rect
              :x="miniViewport.x"
              :y="miniViewport.y"
              :width="miniViewport.width"
              :height="miniViewport.height"
              class="mini-viewport"
            />
          </svg>
        </div>

        <el-button class="fullscreen-btn" size="small" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </el-button>
      </div>
</template>

<style scoped>
.node-dot-mini {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.connect-hint {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 6;
  font-size: 12px;
  color: #a06a1f;
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 999px;
  padding: 4px 10px;
  white-space: nowrap;
  pointer-events: none;
}

.canvas-wrap {
  position: relative;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fbfdff;
  min-height: 0;
}

.canvas-wrap.is-maximized {
  position: fixed;
  inset: 0;
  z-index: 2600;
  border: none;
  border-radius: 0;
  background: #fbfdff;
}

.canvas-wrap:fullscreen {
  background: #fbfdff;
}

.canvas {
  width: 100%;
  height: 100%;
  display: block;
  cursor: var(--map-cursor-default);
  user-select: none;
}

.canvas.is-drawing {
  cursor: var(--map-cursor-crosshair);
}

.canvas.is-drawing .node-dot,
.canvas.is-drawing .edge-hit {
  cursor: var(--map-cursor-crosshair);
}

.canvas.can-pan {
  cursor: var(--map-cursor-grab);
}

.canvas.is-pan-tool {
  cursor: var(--map-cursor-grab);
}

.canvas.is-pan-tool .node-dot,
.canvas.is-pan-tool .edge-hit {
  cursor: var(--map-cursor-grab);
}

.canvas.is-readonly .node-dot,
.canvas.is-readonly .edge-hit {
  cursor: var(--map-cursor-pointer);
}

.canvas.is-readonly.is-pan-tool .node-dot,
.canvas.is-readonly.is-pan-tool .edge-hit {
  cursor: var(--map-cursor-grab);
}

.canvas.is-panning,
.canvas.is-dragging {
  cursor: var(--map-cursor-grabbing);
}

.node-dot {
  cursor: var(--map-cursor-grab);
}

.canvas.is-dragging .node-dot {
  cursor: var(--map-cursor-grabbing);
}

.edge-hit {
  stroke: rgba(0, 0, 0, 0.001);
  stroke-width: 14;
  cursor: var(--map-cursor-pointer);
}

.marquee {
  fill: rgba(31, 59, 115, 0.08);
  stroke: #1f3b73;
  stroke-width: 1;
  stroke-dasharray: 4 3;
  pointer-events: none;
}

.guide {
  stroke: #e6a23c;
  stroke-width: 1;
  stroke-dasharray: 4 3;
  pointer-events: none;
}

.map-border {
  fill: none;
  stroke: #7f9cc0;
  stroke-width: 1.5;
}

.map-ticks line {
  stroke: #c5d3e4;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.map-ticks text {
  font-size: 9px;
  fill: #8fa3bd;
}

.task-flow {
  fill: none;
  stroke: #e6a23c;
  stroke-width: 1.4;
  stroke-dasharray: 5 4;
  opacity: 0.55;
  pointer-events: none;
}

.task-badge {
  fill: #fdf6ec;
  stroke: #e6a23c;
  stroke-width: 1;
  pointer-events: none;
}

.task-badge-text {
  font-size: 10px;
  fill: #a06a1f;
  pointer-events: none;
}

.task-count {
  fill: #ffffff;
  stroke: #e6a23c;
  stroke-width: 1;
  pointer-events: none;
}

.task-count-text {
  font-size: 10px;
  font-weight: 600;
  fill: #a06a1f;
  pointer-events: none;
}

.search-ring {
  fill: rgba(230, 162, 60, 0.15);
  stroke: #e6a23c;
  stroke-width: 2;
  stroke-dasharray: 3 2;
}

.search-hit-edge {
  stroke: rgba(230, 162, 60, 0.5);
  stroke-width: 8;
  stroke-linecap: round;
}

.map-legend {
  position: absolute;
  left: 12px;
  bottom: 38px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  color: #606266;
  pointer-events: auto;
}

.legend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 2px;
  font-weight: 600;
  color: #1f3b73;
}

.legend-head button {
  border: none;
  background: transparent;
  color: #8fa3bd;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 2px;
}

.overlay-toggle {
  position: absolute;
  left: 12px;
  bottom: 38px;
  border: 1px solid #e4e9f0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: #5c7392;
  font-size: 11px;
  line-height: 1;
  padding: 5px 10px;
  cursor: pointer;
}

.overlay-toggle:hover {
  color: #1f3b73;
  border-color: #b6cbe8;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot.error {
  background: #f56c6c;
}

.legend-line {
  width: 18px;
  height: 0;
  border-top: 2px solid #8fa3bd;
  display: inline-block;
}

.legend-line.one-way {
  border-top: 2px dashed #8fa3bd;
}

.legend-symbol {
  width: 18px;
  text-align: center;
  color: #5c7392;
  font-weight: 700;
  line-height: 12px;
}

.legend-symbol.dual {
  letter-spacing: -1px;
}

.badge-sample {
  font-size: 9px;
  color: #a06a1f;
  background: #fdf6ec;
  border: 1px solid #e6a23c;
  border-radius: 6px;
  padding: 0 3px;
  line-height: 12px;
}

.scale-bar {
  position: absolute;
  left: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #606266;
  background: rgba(255, 255, 255, 0.9);
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}

.scale-line {
  height: 6px;
  border-left: 1px solid #606266;
  border-right: 1px solid #606266;
  border-bottom: 1px solid #606266;
}

.coords {
  position: absolute;
  right: 12px;
  bottom: 12px;
  font-size: 11px;
  color: #606266;
  background: rgba(255, 255, 255, 0.9);
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}

.minimap {
  position: absolute;
  right: 12px;
  top: 44px;
  border: 1px solid #dbe4ef;
  border-radius: 6px;
  overflow: hidden;
  background: #f7fbff;
  cursor: var(--map-cursor-grab);
}

.minimap-collapse {
  position: absolute;
  right: 4px;
  top: 4px;
  z-index: 2;
  width: 18px;
  height: 18px;
  line-height: 1;
  border: 1px solid #dbe4ef;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.92);
  color: #5c7392;
  cursor: pointer;
  padding: 0;
}

.minimap-toggle {
  top: 44px;
  right: 12px;
  left: auto;
  bottom: auto;
}

.minimap.is-dragging {
  cursor: var(--map-cursor-grabbing);
}

.mini-viewport {
  fill: rgba(31, 59, 115, 0.08);
  stroke: #1f3b73;
  stroke-width: 1;
  pointer-events: none;
}

.fullscreen-btn {
  position: absolute;
  right: 12px;
  top: 12px;
}

.legend-divider {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #dbe4f0;
  color: #7a8aa3;
  font-size: 11px;
}

.zone-fill {
  cursor: move;
}

.zone-label {
  font-size: 11px;
  font-weight: 600;
}

.zone-handle {
  fill: #ffffff;
  stroke: #1f3b73;
  stroke-width: 1.5;
  vector-effect: non-scaling-stroke;
  pointer-events: all;
}
</style>
