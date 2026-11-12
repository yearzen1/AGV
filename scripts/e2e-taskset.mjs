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

function stationOf(text) {
  const matched = /([A-Za-z0-9_]+)\s*·/.exec(text)
  return matched ? matched[1] : ''
}

async function launch() {
  try {
    return await chromium.launch({ headless: true })
  } catch {
    return chromium.launch({ headless: true, channel: 'msedge' })
  }
}

async function taskRows(dialog) {
  const rows = dialog.locator('.el-table__body tbody tr')
  const count = await rows.count()
  const result = []
  for (let index = 0; index < count; index += 1) {
    const cells = rows.nth(index).locator('td')
    result.push({
      seq: (await cells.nth(0).innerText()).trim(),
      from: stationOf(await cells.nth(1).innerText()),
      to: stationOf(await cells.nth(2).innerText())
    })
  }
  return result
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

    console.log('新建任务集与加行')
    await page.getByRole('button', { name: '新建任务集' }).click()
    const dialog = page.getByRole('dialog', { name: '新建任务集' })
    await dialog.waitFor({ state: 'visible' })
    let rows = await taskRows(dialog)
    assert(rows.length === 1, `新任务集默认 ${rows.length} 行任务`)
    const firstPair = `${rows[0].from}->${rows[0].to}`

    await dialog.getByRole('button', { name: '加一条' }).click()
    await dialog.getByRole('button', { name: '加一条' }).click()
    rows = await taskRows(dialog)
    assert(rows.length === 3, `连续加行后共 ${rows.length} 行任务`)
    assert(
      rows.slice(1).every((row) => `${row.from}->${row.to}` !== firstPair),
      '新增行没有重复第一组起终点'
    )
    assert(
      rows.every((row, index) => row.seq === String(index + 1)),
      '行号连续为 1…N'
    )
    await page.screenshot({ path: path.join(OUT, 'taskset-editor-1440x900.png') })

    console.log('删除行后序号重排')
    await dialog.getByRole('button', { name: '删除' }).first().click()
    rows = await taskRows(dialog)
    assert(rows.length === 2, `删除后剩余 ${rows.length} 行任务`)
    assert(
      rows.every((row, index) => row.seq === String(index + 1)),
      '删除首行后序号仍为 1…N'
    )

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
      (await dialog.locator('.el-table__empty-text').innerText()).includes('暂无任务'),
      '空表显示引导文案'
    )

    console.log('保存失败时列出全部错误')
    await dialog.getByRole('button', { name: '加一条' }).click()
    await dialog.getByRole('button', { name: '加一条' }).click()
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
      (await dialog.locator('.invalid-row').count()) === 2,
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
