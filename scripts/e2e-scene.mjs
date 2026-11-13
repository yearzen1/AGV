// 双车道 3D 场景回归。
// 前置：后端 http://localhost:8080 与前端 dev server（默认 http://localhost:5173）已启动。
// 用法：cd agv-frontend && npm run e2e:scene
// 可选：$env:AGV_WEB='http://localhost:5188' 指定前端地址。
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
const TEMP_NAME = `E2E 双车道 ${Date.now()}`

let failures = 0
function assert(condition, message) {
  if (condition) {
    console.log(`  ✓ ${message}`)
    return
  }
  failures += 1
  console.error(`  ✗ ${message}`)
}

function close(actual, expected, message) {
  assert(Math.abs(actual - expected) < 1e-6, `${message}：${actual} vs ${expected}`)
}

/** 与 roadGeometry.ts 中的箭头密度保持一致。 */
function arrowsOn(length) {
  if (length < 0.5) return 0
  let count = 0
  for (let distance = 2; distance <= length - 0.6; distance += 4) count += 1
  if (count === 0 && length >= 1.4) count = 1
  return count
}

/** 与 constants.ts 的 LANE 保持一致：直角路口铺装沿每条边伸出的距离。 */
const CORNER_TRIM = 0.7 + 1.3 / 2

function expectedStats(net) {
  const nodeMap = new Map(net.nodes.map((node) => [node.id, node]))
  const degree = new Map()
  let ribbons = 0
  let dividers = 0
  let arrows = 0

  for (const edge of net.edges) {
    const from = nodeMap.get(edge.from)
    const to = nodeMap.get(edge.to)
    if (!from || !to) continue
    const length = Math.hypot(to.x - from.x, to.y - from.y)
    if (length < 0.01) continue
    const dual = edge.direction === 'TWO_WAY' && edge.laneMode === 'DUAL'
    degree.set(edge.from, (degree.get(edge.from) ?? 0) + 1)
    degree.set(edge.to, (degree.get(edge.to) ?? 0) + 1)

    // 这张网每个节点都是直角转角，标线两端各裁掉一个铺装半径
    const markingLength = Math.max(0, length - CORNER_TRIM * 2)
    if (dual) {
      ribbons += 2
      dividers += 1
      arrows += arrowsOn(markingLength) * 2
    } else {
      ribbons += 1
      if (edge.direction === 'TWO_WAY') {
        dividers += 1
        arrows += 2
      } else {
        arrows += arrowsOn(markingLength)
      }
    }
  }

  // 直角节点度数 ≥ 2 就生成一块铺装；直行贯通的节点不生成
  const junctions = [...degree.values()].filter((count) => count >= 2).length
  return { ribbons, dividers, arrows, junctions }
}

/** 一张 10 米见方的临时双车道路网：三边 DUAL、一边 SINGLE。 */
const DUAL_NET = {
  nodes: [
    { id: 'CH1', x: 0, y: 0, type: 'CHARGER', capacity: 4 },
    { id: 'J1', x: 10, y: 0, type: 'JUNCTION', capacity: 1 },
    { id: 'J2', x: 10, y: 10, type: 'JUNCTION', capacity: 1 },
    { id: 'W1', x: 0, y: 10, type: 'WORKSTATION', capacity: 1 }
  ],
  edges: [
    { id: 'E1', from: 'CH1', to: 'J1', length: 10, direction: 'TWO_WAY', speed: 1, capacity: 1, laneMode: 'DUAL' },
    { id: 'E2', from: 'J1', to: 'J2', length: 10, direction: 'TWO_WAY', speed: 1, capacity: 1, laneMode: 'DUAL' },
    { id: 'E3', from: 'J2', to: 'W1', length: 10, direction: 'TWO_WAY', speed: 1, capacity: 1, laneMode: 'DUAL' },
    { id: 'E4', from: 'W1', to: 'CH1', length: 10, direction: 'TWO_WAY', speed: 1, capacity: 1, laneMode: 'SINGLE' }
  ]
}

async function launch() {
  try {
    return await chromium.launch({ headless: true })
  } catch {
    return chromium.launch({ headless: true, channel: 'msedge' })
  }
}

/** 用页面里的 token 调接口，避免在脚本里重复登录逻辑。 */
async function api(page, pathname, options = {}) {
  return page.evaluate(
    async ({ pathname: url, options: init }) => {
      const token = localStorage.getItem('agv_token')
      const response = await fetch(`/api${url}`, {
        ...init,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(init.headers ?? {})
        }
      })
      const body = await response.json().catch(() => null)
      return { status: response.status, body }
    },
    { pathname, options }
  )
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

  let createdId = null
  try {
    console.log('登录并准备临时双车道路网')
    await page.goto(BASE, { waitUntil: 'networkidle' })
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL('**/dashboard', { timeout: 15000 })

    const created = await api(page, '/roadnet', {
      method: 'POST',
      body: JSON.stringify({ name: TEMP_NAME, roadnet: DUAL_NET, isTemplate: false })
    })
    assert(created.status === 200 && created.body?.code === 0, '创建临时双车道路网')
    createdId = created.body?.data?.id
    if (!createdId) throw new Error('创建路网后没有返回 id')

    console.log('打开 3D 预览并读取场景统计')
    await page.goto(`${BASE}/roadnet`, { waitUntil: 'networkidle' })
    await page.getByText(TEMP_NAME).first().click()
    await page.waitForFunction(
      (name) => document.querySelector('input[placeholder="路网名称"]')?.value === name,
      TEMP_NAME,
      { timeout: 15000 }
    )
    await page.getByRole('button', { name: '3D 预览' }).click()
    await page.waitForFunction(() => Boolean(window.__AGV_SCENE_DEBUG__?.road), null, {
      timeout: 15000
    })

    const stats = await page.evaluate(() => window.__AGV_SCENE_DEBUG__.road)
    const agvs = await page.evaluate(() => window.__AGV_SCENE_DEBUG__.agvs)
    const expected = expectedStats(DUAL_NET)

    close(stats.ribbons, expected.ribbons, '路面数量')
    close(stats.dividers, expected.dividers, '分隔线数量')
    close(stats.arrows, expected.arrows, '箭头数量')
    close(stats.junctions, expected.junctions, '路口铺装数量')
    assert(stats.ribbons === 7, '三条双车道边各 2 条路面，单车道边 1 条')
    assert(stats.dividers === 4, '三条双车道和一条单车道双向各有分隔线')
    assert(stats.junctions === 4, '四个直角节点各有一块路口铺装')
    assert(agvs.length === 0, '3D 预览不渲染车辆')

    const canvas = await page.locator('canvas').boundingBox()
    assert(canvas != null && canvas.width > 200 && canvas.height > 200, '场景 canvas 已挂载')

    await page.waitForTimeout(600)
    await page.screenshot({ path: path.join(OUT, 'dual-scene.png') })
    console.log(`  截图：${path.join(OUT, 'dual-scene.png')}`)

    await page.getByText('关闭', { exact: true }).last().click()
    assert(errors.length === 0, `控制台无错误${errors.length ? `：${errors[0]}` : ''}`)
  } finally {
    if (createdId) {
      const removed = await api(page, `/roadnet/${createdId}`, { method: 'DELETE' }).catch(() => null)
      if (removed?.status === 200) console.log('已删除临时路网')
      else console.error('临时路网删除失败，请手动清理 id=' + createdId)
    }
    await browser.close()
  }

  if (failures > 0) {
    console.error(`\n${failures} 项断言失败`)
    process.exit(1)
  }
  console.log('\n双车道 3D 场景回归通过')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
