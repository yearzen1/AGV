// 车间地图编辑器 Playwright 回归脚本。
// 前置：后端 http://localhost:8080 与前端 http://localhost:5173 已启动。
// 首次运行需安装浏览器：cd agv-frontend && npx playwright install chromium
// 用法：cd agv-frontend && npm run e2e:roadnet
// 覆盖：左侧导航收起、布局与工具栏、节点/路段编辑、区域创建/改名/移动/缩放/删除/撤销、搜索、任务流向、3D 区域与空格平移、全屏。
import { createRequire } from 'node:module'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const require = createRequire(path.join(root, 'agv-frontend', 'package.json'))
const { chromium } = require('playwright')

const BASE = process.env.AGV_WEB ?? 'http://localhost:5173'
const OUT = path.join(root, '.playwright-mcp', 'e2e')
const VIEWPORT = { width: 1440, height: 900 }

let failures = 0
function assert(condition, message) {
  if (condition) {
    console.log(`  ✓ ${message}`)
    return
  }
  failures += 1
  console.error(`  ✗ ${message}`)
}

function rectOf(element) {
  if (!element) return null
  const box = element.getBoundingClientRect()
  return { x: box.x, y: box.y, width: box.width, height: box.height }
}

function overlaps(a, b) {
  if (!a || !b) return false
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y
}

async function launch() {
  try {
    return await chromium.launch({ headless: true })
  } catch {
    return chromium.launch({ headless: true, channel: 'msedge' })
  }
}

async function nodeCenter(page, id) {
  const point = await page.evaluate((nodeId) => {
    for (const group of document.querySelectorAll('svg.canvas g')) {
      const text = group.querySelector('text')
      const circle = group.querySelector('circle.node-dot')
      if (!text || !circle || text.textContent.trim() !== nodeId) continue
      const box = circle.getBoundingClientRect()
      return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
    }
    return null
  }, id)
  if (!point) throw new Error(`找不到节点 ${id}`)
  return point
}

/** 在可见地图区域里找一个离所有节点都远的空白点，避免误点节点。 */
async function emptyMapPoint(page) {
  const point = await page.evaluate(() => {
    const svg = document.querySelector('svg.canvas')
    const content = document.querySelector('svg.canvas g[data-content]')
    if (!svg || !content) return null
    const box = svg.getBoundingClientRect()
    const viewBox = svg.viewBox.baseVal
    const renderScale = Math.min(box.width / viewBox.width, box.height / viewBox.height)
    const offsetX = box.x + (box.width - viewBox.width * renderScale) / 2
    const offsetY = box.y + (box.height - viewBox.height * renderScale) / 2
    const match = content.getAttribute('transform')?.match(/translate\(([-\d.]+) ([-\d.]+)\) scale\(([-\d.]+)\)/)
    if (!match) return null
    const tx = Number(match[1])
    const ty = Number(match[2])
    const scale = Number(match[3])
    const nodes = [...document.querySelectorAll('circle.node-dot')].map((item) => item.getBoundingClientRect())
    for (let y = 2; y <= 32; y += 2) {
      for (let x = 2; x <= 48; x += 2) {
        const screenX = offsetX + (x * 20 * scale + tx) * renderScale
        const screenY = offsetY + (y * 20 * scale + ty) * renderScale
        if (
          screenX < box.x + 20 ||
          screenX > box.x + box.width - 20 ||
          screenY < box.y + 20 ||
          screenY > box.y + box.height - 20
        ) {
          continue
        }
        const clear = nodes.every((item) => {
          const cx = item.x + item.width / 2
          const cy = item.y + item.height / 2
          return Math.hypot(screenX - cx, screenY - cy) > 50
        })
        if (clear) return { x: screenX, y: screenY, worldX: x, worldY: y }
      }
    }
    return null
  })
  if (!point) throw new Error('找不到可用的空白点')
  return point
}

async function main() {
  await mkdir(OUT, { recursive: true })
  const browser = await launch()
  const page = await browser.newPage({ viewport: VIEWPORT })
  const errors = []
  page.on('pageerror', (error) => errors.push(String(error)))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })

  try {
    console.log('登录并打开车间地图')
    await page.goto(BASE, { waitUntil: 'networkidle' })
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL('**/dashboard', { timeout: 15000 })

    console.log('左侧导航可收起')
    await page.getByRole('button', { name: '收起侧边栏' }).click()
    await page.waitForTimeout(200)
    assert(!(await page.locator('.aside').isVisible()), '侧边栏可以收起')
    await page.reload({ waitUntil: 'networkidle' })
    await page.waitForFunction(() => document.querySelector('.header-title') != null, null, {
      timeout: 15000
    })
    assert(!(await page.locator('.aside').isVisible()), '刷新后仍保持收起')
    await page.getByRole('button', { name: '展开侧边栏' }).click()
    await page.waitForTimeout(200)
    assert(await page.locator('.aside').isVisible(), '侧边栏可以重新展开')

    await page.evaluate(() => {
      localStorage.setItem('agv_roadnet_minimap_collapsed', '0')
      localStorage.setItem('agv_roadnet_legend_collapsed', '0')
      localStorage.removeItem('agv_roadnet_draft_1')
      localStorage.removeItem('agv_roadnet_draft_new')
    })
    await page.goto(`${BASE}/roadnet`, { waitUntil: 'networkidle' })
    await page.waitForFunction(() => document.querySelectorAll('circle.node-dot').length > 0, null, {
      timeout: 15000
    })
    const subtitle = await page.locator('.header-subtitle').textContent().catch(() => null)
    assert(subtitle?.trim() === '路网编辑', '页头显示「路网编辑」副标题')

    console.log('检查布局与悬浮层')
    let metrics = await page.evaluate(() => {
      const box = (selector) => {
        const element = document.querySelector(selector)
        if (!element) return null
        const rect = element.getBoundingClientRect()
        return { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
      }
      return {
        scrollHeight: document.documentElement.scrollHeight,
        viewportHeight: window.innerHeight,
        canvas: box('.canvas-wrap'),
        toolbarRows: [...document.querySelectorAll('.toolbar-row')].map((row) => {
          const rect = row.getBoundingClientRect()
          return { height: rect.height, top: rect.top }
        }),
        minimap: box('.minimap'),
        legend: box('.map-legend'),
        fullscreen: box('.fullscreen-btn'),
        border: box('.map-border'),
        zoom: document.querySelector('.zoom-group .el-button:nth-child(2)')?.innerText
      }
    })
    assert(metrics.scrollHeight <= metrics.viewportHeight + 1, '页面没有纵向滚动')
    assert(metrics.zoom === '100%', '默认显示整张画布（100%）')
    assert(
      metrics.border.x >= metrics.canvas.x - 1 &&
        metrics.border.y >= metrics.canvas.y - 1 &&
        metrics.border.x + metrics.border.width <= metrics.canvas.x + metrics.canvas.width + 1 &&
        metrics.border.y + metrics.border.height <= metrics.canvas.y + metrics.canvas.height + 1,
      '可画布边框完整出现在画布内'
    )
    assert(metrics.canvas.height >= 420, `画布高度 ${Math.round(metrics.canvas.height)}px ≥ 420px`)
    assert(
      metrics.toolbarRows.every((row) => row.height <= 44),
      '工具栏每个分组行都没有换行'
    )
    assert(
      metrics.toolbarRows.length === 2,
      `工具栏共 ${metrics.toolbarRows.length} 行`
    )
    assert(metrics.minimap != null && metrics.legend != null, '小地图与图例已展开')
    assert(!overlaps(metrics.minimap, metrics.fullscreen), '小地图没有压住全屏按钮')
    assert(!overlaps(metrics.legend, metrics.minimap), '图例没有压住小地图')
    const coveredLabels = await page.evaluate(() => {
      const overlays = [document.querySelector('.minimap'), document.querySelector('.map-legend')]
        .filter(Boolean)
        .map((element) => element.getBoundingClientRect())
      const labels = [...document.querySelectorAll('circle.node-dot')]
        .map((circle) => circle.nextElementSibling)
        .filter((element) => element && element.tagName.toLowerCase() === 'text')
        .map((element) => ({ id: element.textContent.trim(), rect: element.getBoundingClientRect() }))
      return labels
        .filter(({ rect }) =>
          overlays.some(
            (overlay) =>
              rect.x < overlay.x + overlay.width &&
              rect.x + rect.width > overlay.x &&
              rect.y < overlay.y + overlay.height &&
              rect.y + rect.height > overlay.y
          )
        )
        .map(({ id }) => id)
    })
    assert(coveredLabels.length === 0, `小地图和图例没有遮挡节点标签${coveredLabels.length ? `：${coveredLabels.join(', ')}` : ''}`)

    await page.screenshot({ path: path.join(OUT, 'roadnet-preview-1440x900.png') })

    console.log('进入编辑模式并检查工具栏')
    await page.getByRole('button', { name: '编辑', exact: true }).click()
    await page.waitForTimeout(300)
    metrics = await page.evaluate(() => ({
      scrollHeight: document.documentElement.scrollHeight,
      viewportHeight: window.innerHeight,
      rows: [...document.querySelectorAll('.toolbar-row')].map((row) => row.getBoundingClientRect().height)
    }))
    assert(metrics.scrollHeight <= metrics.viewportHeight + 1, '编辑模式下页面没有纵向滚动')
    assert(metrics.rows.every((height) => height <= 44), '编辑模式下工具栏仍是两行')

    console.log('新建节点并撤销')
    const beforeNodes = await page.locator('circle.node-dot').count()
    await page.locator('.toolbar').getByText('工位', { exact: true }).click()
    const spot = await emptyMapPoint(page)
    await page.mouse.click(spot.x, spot.y)
    await page.waitForTimeout(600)
    assert(
      (await page.locator('circle.node-dot').count()) === beforeNodes + 1,
      `空白处新增节点（${beforeNodes} → ${beforeNodes + 1}）`
    )
    assert((await page.locator('.dirty').count()) === 1, '新增节点后显示未保存')
    await page.getByTitle('撤销 (Ctrl+Z)').click()
    await page.waitForTimeout(400)
    assert((await page.locator('circle.node-dot').count()) === beforeNodes, '撤销后节点数恢复')
    assert((await page.locator('.dirty').count()) === 0, '撤销回原始状态后不再显示未保存')
    await page.locator('.toolbar').getByText('选择', { exact: true }).click()

    console.log('区域注释')
    await page.locator('.toolbar').getByText('区域', { exact: true }).click()
    await page.waitForTimeout(200)
    const zoneSpot = await emptyMapPoint(page)
    await page.mouse.move(zoneSpot.x, zoneSpot.y)
    await page.mouse.down()
    await page.mouse.move(zoneSpot.x + 100, zoneSpot.y + 70, { steps: 6 })
    await page.mouse.up()
    await page.waitForTimeout(500)
    assert((await page.locator('.zone-fill').count()) === 1, '画布上创建一个区域')
    assert(
      (await page.locator('.side-pane').filter({ hasText: '选中区域' }).count()) === 1,
      '创建区域后自动切到「区域」页签'
    )
    assert((await page.locator('.zone-row').count()) === 1, '区域列表出现一行')
    await page
      .locator('.side-pane .el-form-item')
      .filter({ hasText: '名称' })
      .locator('input')
      .first()
      .fill('测试区域')
    await page.keyboard.press('Tab')
    await page.waitForTimeout(300)
    assert(
      (await page.locator('.zone-row').first().innerText()).includes('测试区域'),
      '区域名称同步到列表'
    )
    await page.getByTitle('撤销 (Ctrl+Z)').click()
    await page.waitForTimeout(400)
    assert((await page.locator('.zone-fill').count()) === 1, '第一次撤销只回退区域名称')
    await page.getByTitle('撤销 (Ctrl+Z)').click()
    await page.waitForTimeout(400)
    assert((await page.locator('.zone-fill').count()) === 0, '再次撤销后区域消失')
    await page.locator('.toolbar').getByText('区域', { exact: true }).click()
    await page.mouse.click(zoneSpot.x, zoneSpot.y)
    await page.waitForTimeout(300)
    await page.locator('.toolbar').getByText('选择', { exact: true }).click()
    await page.mouse.click(zoneSpot.x + 20, zoneSpot.y + 20)
    await page.waitForTimeout(200)
    await page.keyboard.press('Delete')
    await page.waitForTimeout(300)
    assert((await page.locator('.zone-fill').count()) === 0, 'Delete 删除选中区域')

    console.log('区域移动与缩放')
    await page.locator('.toolbar').getByText('区域', { exact: true }).click()
    await page.mouse.click(zoneSpot.x, zoneSpot.y)
    await page.waitForTimeout(300)
    await page.locator('.toolbar').getByText('选择', { exact: true }).click()
    await page.mouse.click(zoneSpot.x, zoneSpot.y)
    await page.waitForTimeout(200)
    const zoneBefore = await page.locator('.zone-fill').first().evaluate((element) => ({
      x: Number(element.getAttribute('x')),
      width: Number(element.getAttribute('width'))
    }))
    await page.mouse.move(zoneSpot.x, zoneSpot.y)
    await page.mouse.down()
    await page.mouse.move(zoneSpot.x + 30, zoneSpot.y + 20, { steps: 5 })
    await page.mouse.up()
    await page.waitForTimeout(300)
    const zoneMoved = await page.locator('.zone-fill').first().evaluate((element) => ({
      x: Number(element.getAttribute('x')),
      width: Number(element.getAttribute('width'))
    }))
    assert(zoneMoved.x > zoneBefore.x, '区域可以拖动')
    const handles = await page.locator('.zone-handle').evaluateAll((list) =>
      list.map((element) => {
        const rect = element.getBoundingClientRect()
        return { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 }
      })
    )
    assert(handles.length === 8, '选中区域显示 8 个缩放手柄')
    const southeast = handles[4]
    await page.mouse.move(southeast.x, southeast.y)
    await page.mouse.down()
    await page.mouse.move(southeast.x + 30, southeast.y + 20, { steps: 5 })
    await page.mouse.up()
    await page.waitForTimeout(300)
    const zoneResized = await page.locator('.zone-fill').first().evaluate((element) => ({
      width: Number(element.getAttribute('width'))
    }))
    assert(zoneResized.width > zoneMoved.width, '拖动右下角手柄可以放大区域')
    await page.keyboard.press('Delete')
    await page.waitForTimeout(300)
    assert((await page.locator('.zone-fill').count()) === 0, '清理移动/缩放测试区域')
    await page.locator('.toolbar').getByText('选择', { exact: true }).click()

    console.log('重复连边提示')
    const beforeEdges = await page.locator('.edge-hit').count()
    await page.locator('.toolbar').getByText('连路段', { exact: true }).click()
    const j2 = await nodeCenter(page, 'J2')
    await page.mouse.click(j2.x, j2.y)
    await page.waitForTimeout(250)
    assert((await page.locator('.connect-hint').innerText()).includes('已选起点 J2'), '显示连接起点提示')
    const j3 = await nodeCenter(page, 'J3')
    await page.mouse.click(j3.x, j3.y)
    await page.waitForTimeout(250)
    assert((await page.locator('body').innerText()).includes('已有路段'), '重复连边给出提示')
    assert((await page.locator('.edge-hit').count()) === beforeEdges, '重复连边没有新增路段')
    await page.keyboard.press('Escape')

    console.log('搜索与高亮')
    await page.getByPlaceholder('搜索编号').fill('J')
    await page.waitForTimeout(400)
    assert((await page.locator('.search-ring').count()) === 5, '搜索 J 高亮 5 个节点')
    assert(
      (await page.locator('.search-input .el-input-group__append button').innerText()) === '1/5',
      '搜索按钮显示第 1/5 个命中'
    )
    await page.locator('.search-input .el-input-group__append button').click()
    await page.waitForTimeout(300)
    assert(
      (await page.locator('.search-input .el-input-group__append button').innerText()) === '2/5',
      '再次点击循环到第 2/5 个命中'
    )
    await page.getByPlaceholder('搜索编号').fill('')
    await page.waitForTimeout(200)

    console.log('侧栏页签')
    await page.getByRole('tab', { name: '统计' }).click()
    assert((await page.locator('.side-pane').filter({ hasText: '总长度' }).count()) === 1, '统计页签可见')
    await page.getByRole('tab', { name: '校验' }).click()
    assert((await page.locator('.side-pane').filter({ hasText: '未发现问题' }).count()) === 1, '校验页签可见')
    await page.getByRole('tab', { name: '属性' }).click()

    console.log('任务流向聚合')
    await page.getByText('在地图上显示任务流向').click()
    await page.waitForTimeout(1200)
    const flows = await page.evaluate(() => ({
      paths: document.querySelectorAll('.task-flow').length,
      counts: [...document.querySelectorAll('.task-count-text')].map((item) => Number(item.textContent)),
      badges: document.querySelectorAll('.task-badge-text').length
    }))
    assert(flows.paths === 6, `20 条任务聚合成 ${flows.paths} 条流向`)
    assert(flows.counts.reduce((sum, value) => sum + value, 0) === 20, '流向数量合计为 20')
    assert(flows.badges === 3, '工位角标数量为 3')
    await page.screenshot({ path: path.join(OUT, 'roadnet-tasks-1440x900.png') })
    await page.getByText('在地图上显示任务流向').click()

    console.log('3D 区域注释')
    // 搜索定位会平移视图，先回到 100% 再画区域，避免拖拽终点跑出画布
    await page.locator('.toolbar').getByTitle('重置为 100%').click()
    await page.waitForTimeout(300)
    await page.locator('.toolbar').getByText('区域', { exact: true }).click()
    await page.waitForTimeout(200)
    const zone3DSpot = await emptyMapPoint(page)
    await page.mouse.move(zone3DSpot.x, zone3DSpot.y)
    await page.mouse.down()
    await page.mouse.move(zone3DSpot.x + 80, zone3DSpot.y + 60, { steps: 6 })
    await page.mouse.up()
    await page.waitForTimeout(200)
    const zoneCountBefore3D = await page.locator('.zone-fill').count()
    assert(zoneCountBefore3D === 1, `3D 前创建一个测试区域（实际 ${zoneCountBefore3D} 个）`)
    await page.locator('.toolbar').getByText('选择', { exact: true }).click()

    console.log('3D 预览')
    await page.getByRole('button', { name: '3D 预览' }).click()
    await page.waitForSelector('.preview-3d canvas', { timeout: 15000 })
    await page.waitForFunction(() => Boolean(window.__AGV_SCENE_DEBUG__?.road), null, {
      timeout: 15000
    })
    const zoneStats = await page.evaluate(() => ({
      zones: window.__AGV_SCENE_DEBUG__.road.zones,
      visible: window.__AGV_SCENE_DEBUG__.zonesVisible
    }))
    assert(zoneStats.zones === 1, '3D 场景渲染区域色块')
    assert(zoneStats.visible === true, '3D 默认显示区域')
    const footer = page.locator('.preview-3d-footer')
    assert((await footer.innerText()).includes('左键旋转'), '3D 弹窗显示操作提示')
    await footer.getByRole('button', { name: '全屏', exact: true }).click()
    await page.waitForTimeout(400)
    assert(
      (await page.locator('.el-dialog').getAttribute('class')).includes('is-fullscreen'),
      '3D 弹窗可全屏'
    )
    assert(
      (await page.locator('.preview-3d').evaluate((element) => element.getBoundingClientRect().height)) > 600,
      '全屏后 3D 画面填满弹窗'
    )
    await footer.getByRole('button', { name: '退出全屏' }).click()

    console.log('3D 空格平移')
    const sceneBox = await page.locator('.preview-3d canvas').boundingBox()
    assert(sceneBox != null, '3D 画布可定位')
    const centerX = sceneBox.x + sceneBox.width / 2
    const centerY = sceneBox.y + sceneBox.height / 2
    await page.mouse.move(centerX, centerY)
    const cameraBefore = await page.evaluate(() => window.__AGV_SCENE_DEBUG__.camera?.target ?? null)
    assert(cameraBefore != null, '调试对象暴露相机目标')
    await page.keyboard.down('Space')
    await page.mouse.down()
    await page.mouse.move(centerX + 120, centerY + 60, { steps: 8 })
    await page.mouse.up()
    await page.keyboard.up('Space')
    await page.waitForTimeout(1200)
    const cameraAfterPan = await page.evaluate(() => window.__AGV_SCENE_DEBUG__.camera?.target ?? null)
    const panDistance = cameraAfterPan
      ? Math.hypot(
          cameraAfterPan[0] - cameraBefore[0],
          cameraAfterPan[1] - cameraBefore[1],
          cameraAfterPan[2] - cameraBefore[2]
        )
      : 0
    assert(panDistance > 0.5, `空格+左键拖动平移视角（目标移动 ${panDistance.toFixed(2)} 米）`)

    await page.mouse.move(centerX, centerY)
    await page.mouse.down()
    await page.mouse.move(centerX - 80, centerY - 40, { steps: 8 })
    await page.mouse.up()
    await page.waitForTimeout(400)
    const cameraAfterRotate = await page.evaluate(
      () => window.__AGV_SCENE_DEBUG__.camera?.target ?? null
    )
    const rotateDistance = cameraAfterRotate
      ? Math.hypot(
          cameraAfterRotate[0] - cameraAfterPan[0],
          cameraAfterRotate[1] - cameraAfterPan[1],
          cameraAfterRotate[2] - cameraAfterPan[2]
        )
      : 0
    assert(rotateDistance < 0.05, '松开空格后左键恢复旋转，相机目标不变')

    await footer.getByRole('button', { name: '关闭' }).click()
    await page.waitForTimeout(300)

    console.log('3D 区域开关')
    await page.locator('.toolbar').getByText('显示', { exact: true }).click()
    await page.locator('.popover-list:visible').getByText('区域注释', { exact: true }).click()
    await page.waitForTimeout(200)
    await page.getByRole('button', { name: '3D 预览' }).click()
    await page.waitForSelector('.preview-3d canvas', { timeout: 15000 })
    await page.waitForFunction(() => Boolean(window.__AGV_SCENE_DEBUG__?.road), null, {
      timeout: 15000
    })
    assert(
      (await page.evaluate(() => window.__AGV_SCENE_DEBUG__.zonesVisible)) === false,
      '关闭「区域注释」后 3D 区域层隐藏'
    )
    await page.locator('.preview-3d-footer').getByRole('button', { name: '关闭' }).click()
    await page.waitForTimeout(300)
    await page.locator('.toolbar').getByText('显示', { exact: true }).click()
    await page.locator('.popover-list:visible').getByText('区域注释', { exact: true }).click()
    await page.waitForTimeout(200)
    await page.locator('.toolbar').getByText('显示', { exact: true }).click()
    await page.waitForTimeout(200)

    console.log('画布全屏与退出')
    // 强制进入页面内全屏分支，保证在无头环境下也能回归降级逻辑
    await page.evaluate(() => {
      Element.prototype.requestFullscreen = () => Promise.reject(new Error('unsupported'))
    })
    await page.locator('.fullscreen-btn').click()
    await page.waitForTimeout(500)
    const fullscreenState = await page.evaluate(() => ({
      native: !!document.fullscreenElement,
      css: document.querySelector('.canvas-wrap')?.classList.contains('is-maximized')
    }))
    assert(fullscreenState.css && !fullscreenState.native, '全屏 API 不可用时切换为页面内全屏')
    await page.keyboard.press('Escape')
    await page.waitForTimeout(400)
    const exited = await page.evaluate(() => ({
      native: !!document.fullscreenElement,
      css: document.querySelector('.canvas-wrap')?.classList.contains('is-maximized')
    }))
    assert(!exited.native && !exited.css, 'Esc 退出全屏')

    await page.screenshot({ path: path.join(OUT, 'roadnet-edit-1440x900.png') })
    assert(errors.length === 0, `没有浏览器控制台错误${errors.length ? `：${errors[0]}` : ''}`)
  } finally {
    await browser.close()
  }

  if (failures > 0) {
    console.error(`\n${failures} 项检查失败`)
    process.exitCode = 1
    return
  }
  console.log('\n全部检查通过')
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
