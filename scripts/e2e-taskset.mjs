// 任务集页面 Playwright 回归脚本。
// 前置：后端 http://localhost:8080 与前端 http://localhost:5173 已启动。
// 首次运行需安装浏览器：cd agv-frontend && npx playwright install chromium
// 用法：cd agv-frontend && npm run e2e:taskset
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

async function launch() {
  try {
    return await chromium.launch({ headless: true })
  } catch {
    return chromium.launch({ headless: true, channel: 'msedge' })
  }
}

async function taskRows(dialog) {
  const rows = dialog.locator('.task-row')
  const count = await rows.count()
  const result = []
  for (let index = 0; index < count; index += 1) {
    const row = rows.nth(index)
    result.push({
      seq: (await row.locator('.task-seq').innerText()).trim(),
      from: (await row.locator('.station-chip').nth(0).innerText()).trim(),
      to: (await row.locator('.station-chip').nth(1).innerText()).trim()
    })
  }
  return result
}

async function activateRow(dialog, index) {
  await dialog.locator('.task-row').nth(index).locator('.task-seq').click()
}

async function pickOnMap(page, dialog, nodeId) {
  await dialog.locator(`.task-map .node[data-id="${nodeId}"]`).click()
  await page.waitForTimeout(80)
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

  const name = `E2E 任务集 ${Date.now()}`

  try {
    console.log('登录并打开任务集')
    await page.goto(BASE, { waitUntil: 'networkidle' })
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL('**/dashboard', { timeout: 15000 })
    await page.goto(`${BASE}/tasks`, { waitUntil: 'networkidle' })

    console.log('新建任务集与地图选点')
    await page.getByRole('button', { name: '新建任务集' }).click()
    const dialog = page.getByRole('dialog', { name: '新建任务集' })
    await dialog.waitFor({ state: 'visible' })
    let rows = await taskRows(dialog)
    assert(rows.length === 1, `新任务集默认 ${rows.length} 行任务`)
    assert(rows[0].from === '选起点' && rows[0].to === '选终点', '默认行等待地图选点')
    assert((await dialog.locator('.task-row.active').count()) === 1, '默认选中第一行')

    await pickOnMap(page, dialog, 'W1')
    rows = await taskRows(dialog)
    assert(rows[0].from === 'W1' && rows[0].to === '选终点', '点地图工位写入起点')
    assert(
      (await dialog.locator('.map-toolbar .mode-badge').innerText()).includes('终点'),
      '起点选完自动切到终点'
    )
    await pickOnMap(page, dialog, 'W2')
    rows = await taskRows(dialog)
    assert(rows[0].from === 'W1' && rows[0].to === 'W2', '连续点两个工位完成一条任务')

    console.log('非工位不可选')
    await pickOnMap(page, dialog, 'CH1')
    rows = await taskRows(dialog)
    assert(rows[0].from === 'W1' && rows[0].to === 'W2', '点充电位不会写入端点')
    await dialog.locator('.map-foot .map-notice').waitFor({ state: 'visible' })
    assert(
      (await dialog.locator('.map-foot .map-notice').innerText()).includes('工位'),
      '选择非工位时给出提示'
    )

    console.log('新增行与行号重排')
    await dialog.getByRole('button', { name: /在地图上新增任务/ }).click()
    await dialog.getByRole('button', { name: /在地图上新增任务/ }).click()
    rows = await taskRows(dialog)
    assert(rows.length === 3, `连续新增后共 ${rows.length} 行任务`)
    assert(
      rows.every((row, index) => row.seq === String(index + 1)),
      '行号连续为 1…N'
    )
    assert(rows[2].from === '选起点' && rows[2].to === '选终点', '新增行等待地图选点')
    assert(
      (await dialog.locator('.task-row.active .task-seq').innerText()).trim() === '3',
      '新增后自动选中新行'
    )

    await activateRow(dialog, 2)
    await pickOnMap(page, dialog, 'W2')
    await pickOnMap(page, dialog, 'W3')
    rows = await taskRows(dialog)
    assert(rows[2].from === 'W2' && rows[2].to === 'W3', '新增行可以通过地图补齐起终点')
    await page.screenshot({ path: path.join(OUT, 'taskset-editor-1440x900.png') })

    await dialog.locator('.task-row').first().locator('.row-del').click()
    rows = await taskRows(dialog)
    assert(rows.length === 2, `删除后剩余 ${rows.length} 行任务`)
    assert(
      rows.every((row, index) => row.seq === String(index + 1)),
      '删除首行后序号仍为 1…N'
    )

    console.log('行与地图互相高亮')
    await activateRow(dialog, 1)
    assert(
      (await dialog.locator('.task-map .node.selected-from').getAttribute('data-id')) ===
        rows[1].from,
      '选中行后地图高亮起点'
    )
    assert(
      (await dialog.locator('.task-map .node.selected-to').getAttribute('data-id')) === rows[1].to,
      '选中行后地图高亮终点'
    )
    await dialog.locator('.task-row').nth(1).locator('.station-chip').first().click()
    await pickOnMap(page, dialog, 'W3')
    rows = await taskRows(dialog)
    assert(rows[1].from === 'W3', '点行内起点按钮后可在地图上改起点')

    console.log('批量填充与清空')
    await dialog.getByRole('button', { name: '填充 20 单演示数据' }).click()
    await page
      .getByRole('dialog', { name: '填充演示数据' })
      .getByRole('button', { name: '覆盖并填充' })
      .click()
    rows = await taskRows(dialog)
    assert(rows.length === 20, `填充后共 ${rows.length} 行任务`)
    assert(
      rows.every((row, index) => row.seq === String(index + 1)),
      '填充后序号连续为 1…20'
    )

    await dialog.getByRole('button', { name: '清空' }).click()
    await page
      .getByRole('dialog', { name: '清空任务' })
      .getByRole('button', { name: '清空' })
      .click()
    rows = await taskRows(dialog)
    assert(rows.length === 0, '清空后任务行数为 0')
    assert(
      (await dialog.locator('.task-empty').innerText()).includes('暂无任务'),
      '空列表显示引导文案'
    )

    console.log('保存失败时列出全部错误')
    await dialog.getByRole('button', { name: /在地图上新增任务/ }).click()
    await dialog.getByRole('button', { name: /在地图上新增任务/ }).click()
    await activateRow(dialog, 0)
    await pickOnMap(page, dialog, 'W1')
    await pickOnMap(page, dialog, 'W2')
    await activateRow(dialog, 1)
    await pickOnMap(page, dialog, 'W2')
    await pickOnMap(page, dialog, 'W3')
    await dialog.getByLabel('名称').fill(name)
    await page.route('**/api/taskset', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue()
        return
      }
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 4000,
          message: '任务 1：起点 W9 不在该路网中；任务 2：W1 → W9 不可达'
        })
      })
    })
    await dialog.getByRole('button', { name: '保存' }).click()
    await dialog.locator('.form-errors').waitFor({ state: 'visible' })
    assert(
      (await dialog.locator('.form-errors .error-list li').count()) === 2,
      '弹窗顶部列出全部 2 条错误'
    )
    assert(
      (await dialog.locator('.task-row.invalid-row').count()) === 2,
      '两条错误对应的任务行都标红'
    )
    await page.screenshot({ path: path.join(OUT, 'taskset-errors-1440x900.png') })
    await page.unroute('**/api/taskset')

    console.log('保存并在列表中核对')
    await dialog.getByRole('button', { name: '保存' }).click()
    await dialog.waitFor({ state: 'hidden' })
    const listRows = page.locator('.panel .el-table__body tbody tr')
    const created = listRows.filter({ hasText: name }).first()
    await created.waitFor({ state: 'visible' })
    assert(
      (await created.locator('td').nth(3).innerText()).trim() === '2',
      '列表中的任务数为 2'
    )

    console.log('重新打开并清理')
    await created.getByRole('button', { name: '编辑' }).click()
    const editDialog = page.getByRole('dialog', { name: '编辑任务集' })
    await editDialog.waitFor({ state: 'visible' })
    rows = await taskRows(editDialog)
    assert(rows.length === 2, `重新打开后仍有 ${rows.length} 行任务`)
    assert(
      rows.every((row, index) => row.seq === String(index + 1)),
      '重新打开后序号连续'
    )
    await editDialog.getByRole('button', { name: '取消' }).click()
    await editDialog.waitFor({ state: 'hidden' })

    await created.getByRole('button', { name: '删除' }).click()
    await page
      .getByRole('dialog', { name: '删除确认' })
      .getByRole('button', { name: '确定' })
      .click()
    await page.waitForTimeout(500)
    assert(
      (await listRows.filter({ hasText: name }).count()) === 0,
      '回归脚本已清理临时任务集'
    )

    // 上面故意 mock 了一次 400 响应，浏览器会记录一条资源加载失败，属于预期。
    const unexpectedErrors = errors.filter((entry) => !entry.includes('status of 400'))
    assert(
      unexpectedErrors.length === 0,
      `没有浏览器控制台错误${unexpectedErrors.length ? `：${unexpectedErrors[0]}` : ''}`
    )
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

