// 仿真运行页 Playwright 回归脚本。
// 前置：后端 http://localhost:8080 与前端 http://localhost:5173 已启动。
// 首次运行需安装浏览器：cd agv-frontend && npx playwright install chromium
// 用法：cd agv-frontend && npm run e2e:sim
//
// 覆盖：参数标签、空闲时倍速禁用、启动后参数锁定、暂停冻结、刷新恢复车辆位置、
// 继续、重置回初始状态。仿真只跑前若干个 tick，不等自然结束。
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

async function waitForTick(page, predicate, timeout = 20000) {
  await page.waitForFunction(
    (source) => {
      const value = Number(document.querySelector('.metric-grid .metric-value')?.textContent ?? -1)
      // eslint-disable-next-line no-new-func
      return new Function('tick', `return (${source})`)(value)
    },
    predicate,
    { timeout, polling: 200 }
  )
  return page.evaluate(() => Number(document.querySelector('.metric-grid .metric-value')?.textContent))
}

async function tickOf(page) {
  return page.evaluate(() => Number(document.querySelector('.metric-grid .metric-value')?.textContent ?? -1))
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
    console.log('登录并打开仿真运行')
    await page.goto(BASE, { waitUntil: 'networkidle' })
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL('**/dashboard', { timeout: 15000 })
    await page.goto(`${BASE}/simulation`, { waitUntil: 'networkidle' })
    await page.waitForFunction(() => document.querySelector('.toolbar') != null, null, { timeout: 15000 })

    console.log('检查参数标签')
    const labels = await page.locator('.toolbar .field-label').allInnerTexts()
    assert(
      JSON.stringify(labels.map((text) => text.trim())) ===
        JSON.stringify(['路网', '任务集', '算法', 'AGV 数量', '随机种子']),
      `工具栏五个参数都有文字标签：${labels.join(' / ')}`
    )

    console.log('检查空闲状态')
    const idle = await page.evaluate(() => ({
      speedDisabled: document.querySelectorAll('.speed-group .el-radio-button.is-disabled').length,
      speedChecked: document.querySelector('.speed-group .el-radio-button.is-active')?.textContent?.trim(),
      rows: document.querySelectorAll('.agv-row').length,
      hint: document.querySelector('.agv-panel')?.textContent ?? ''
    }))
    assert(idle.speedDisabled === 4, '空闲时倍速按钮全部禁用')
    assert(idle.speedChecked === '1x', '空闲时倍速停在 1x')
    assert(idle.rows === 0 && idle.hint.includes('尚未启动仿真'), '空闲时不显示车辆')

    await page.screenshot({ path: path.join(OUT, 'sim-idle-1440x900.png') })

    console.log('启动仿真')
    await page.getByRole('button', { name: '开始', exact: true }).click()
    const firstTick = await waitForTick(page, 'tick > 0')
    assert(firstTick > 0, `启动后 tick 开始递增（tick=${firstTick}）`)
    await page.waitForFunction(() => document.querySelectorAll('.agv-row').length === 3, null, { timeout: 15000 })
    assert((await page.locator('.agv-row').count()) === 3, '车辆状态显示 3 台车')
    const runningState = await page.evaluate(() => ({
      selects: [...document.querySelectorAll('.toolbar .el-select')].map(
        (el) => el.querySelector('.el-select__wrapper.is-disabled') != null
      ),
      numbers: [...document.querySelectorAll('.toolbar .el-input-number')].map((el) => el.classList.contains('is-disabled')),
      speedDisabled: document.querySelectorAll('.speed-group .el-radio-button.is-disabled').length
    }))
    assert(runningState.selects.length === 3 && runningState.selects.every(Boolean), '运行时路网/任务集/算法下拉锁定')
    assert(runningState.numbers.length === 2 && runningState.numbers.every(Boolean), '运行时 AGV 数量与随机种子锁定')
    assert(runningState.speedDisabled === 0, '运行时倍速可选')

    console.log('倍速与暂停')
    await page.locator('.speed-group').getByText('4x', { exact: true }).click()
    await page.waitForTimeout(800)
    await page.getByRole('button', { name: '暂停', exact: true }).click()
    await page.waitForTimeout(500)
    const pausedTick = await tickOf(page)
    await page.waitForTimeout(1500)
    assert((await tickOf(page)) === pausedTick, `暂停后 tick 冻结（${pausedTick}）`)
    assert((await page.locator('.el-tag').filter({ hasText: '已暂停' }).count()) === 1, '状态标签显示「已暂停」')
    await page.screenshot({ path: path.join(OUT, 'sim-paused-1440x900.png') })

    console.log('暂停后刷新恢复')
    await page.reload({ waitUntil: 'networkidle' })
    await page.waitForFunction(() => document.querySelectorAll('.agv-row').length > 0, null, { timeout: 15000 })
    const restored = await page.evaluate(() => ({
      tick: Number(document.querySelector('.metric-grid .metric-value')?.textContent ?? -1),
      rows: document.querySelectorAll('.agv-row').length,
      paused: [...document.querySelectorAll('.el-tag')].some((el) => el.textContent.includes('已暂停')),
      speed: document.querySelector('.speed-group .el-radio-button.is-active')?.textContent?.trim()
    }))
    assert(restored.tick === pausedTick, `刷新后 tick 与暂停时一致（${restored.tick}）`)
    assert(restored.paused, '刷新后仍显示「已暂停」')
    assert(restored.rows === 3, '刷新后车辆状态恢复 3 台车')
    assert(restored.speed === '4x', '刷新后倍速恢复 4x')
    await page.screenshot({ path: path.join(OUT, 'sim-paused-reload-1440x900.png') })

    console.log('继续与重置')
    await page.getByRole('button', { name: '继续', exact: true }).click()
    const resumedTick = await waitForTick(page, `tick > ${pausedTick}`)
    assert(resumedTick > pausedTick, `继续后 tick 恢复递增（${pausedTick} → ${resumedTick}）`)
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await page.waitForTimeout(800)
    const reset = await page.evaluate(() => ({
      tick: Number(document.querySelector('.metric-grid .metric-value')?.textContent ?? -1),
      rows: document.querySelectorAll('.agv-row').length,
      hint: document.querySelector('.agv-panel')?.textContent ?? '',
      speed: document.querySelector('.speed-group .el-radio-button.is-active')?.textContent?.trim(),
      speedDisabled: document.querySelectorAll('.speed-group .el-radio-button.is-disabled').length
    }))
    assert(reset.tick === 0, '重置后 tick 归零')
    assert(reset.rows === 0 && reset.hint.includes('尚未启动仿真'), '重置后车辆列表清空')
    assert(reset.speed === '1x' && reset.speedDisabled === 4, '重置后倍速回到 1x 并禁用')
    await page.screenshot({ path: path.join(OUT, 'sim-reset-1440x900.png') })

    console.log('跑完 20 单，检查结束态与通道')
    await page.getByRole('button', { name: '开始', exact: true }).click()
    await page.locator('.speed-group').getByText('4x', { exact: true }).click()
    await page.waitForFunction(
      () => [...document.querySelectorAll('.toolbar .el-tag')].some((el) => el.textContent.includes('已完成')),
      null,
      { timeout: 240000, polling: 500 }
    )
    const finishedState = await page.evaluate(() => ({
      done: document.querySelectorAll('.metric-grid .metric-value')[1]?.textContent?.trim(),
      progress: document.querySelector('.el-progress__text')?.textContent?.trim(),
      tags: [...document.querySelectorAll('.toolbar .el-tag')].map((el) => el.textContent.trim()),
      paramEnabled: [...document.querySelectorAll('.toolbar .el-select')].every(
        (el) => el.querySelector('.el-select__wrapper.is-disabled') == null
      ),
      speedDisabled: document.querySelectorAll('.speed-group .el-radio-button.is-disabled').length
    }))
    assert(finishedState.done === '20/20', `自然结束后完成数为 ${finishedState.done}`)
    assert(finishedState.progress === '100%', '自然结束后进度条到 100%')
    assert(finishedState.tags.includes('已完成'), '自然结束后状态标签显示「已完成」')
    assert(finishedState.tags.includes('实时通道已连接'), '结束消息没有踢掉实时通道')
    assert(finishedState.paramEnabled, '结束后参数重新可编辑')
    assert(finishedState.speedDisabled === 4, '结束后倍速禁用')
    await page.screenshot({ path: path.join(OUT, 'sim-finished-1440x900.png') })
    await page.getByRole('button', { name: '重置', exact: true }).click()
    await page.waitForTimeout(500)

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
