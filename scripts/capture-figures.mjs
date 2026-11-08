// 用 Edge 的 CDP 接口截图，用于论文插图。需要先以 --remote-debugging-port=9222 启动无头 Edge。
import fs from 'node:fs'
import path from 'node:path'

const CDP = 'http://127.0.0.1:9222'
const BASE = 'http://localhost:5173'
const API = 'http://localhost:8080'
const OUT = path.resolve('paper/thesis/figures')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function login(username, password) {
  const res = await fetch(`${API}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const json = await res.json()
  if (!json.data?.token) throw new Error('登录失败: ' + JSON.stringify(json))
  return json.data
}

async function connect() {
  const list = await (await fetch(`${CDP}/json/list`)).json()
  const page = list.find((t) => t.type === 'page')
  const ws = new WebSocket(page.webSocketDebuggerUrl)
  const pending = new Map()
  let seq = 0
  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data)
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg)
      pending.delete(msg.id)
    }
  }
  await new Promise((resolve, reject) => {
    ws.onopen = resolve
    ws.onerror = reject
  })
  const send = (method, params = {}) =>
    new Promise((resolve) => {
      const id = ++seq
      pending.set(id, resolve)
      ws.send(JSON.stringify({ id, method, params }))
    })
  return { ws, send }
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const admin = await login('admin', 'admin123')
  const dispatcher = await login('dispatcher', 'dispatcher123')

  const { ws, send } = await connect()
  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', {
    width: 1680,
    height: 1050,
    deviceScaleFactor: 1,
    mobile: false
  })

  const evaluate = (expression) =>
    send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })

  const goto = async (url) => {
    await send('Page.navigate', { url })
    await sleep(2500)
  }

  const useSession = async (session, route) => {
    await goto(`${BASE}/login`)
    await evaluate(
      `localStorage.setItem('agv_token', ${JSON.stringify(session.token)});` +
        `localStorage.setItem('agv_user', ${JSON.stringify(JSON.stringify(session.user))});`
    )
    await goto(BASE + route)
    await sleep(1500)
  }

  const capture = async (name) => {
    const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
    if (!result.result?.data) throw new Error('截图失败: ' + JSON.stringify(result).slice(0, 200))
    fs.writeFileSync(path.join(OUT, name), Buffer.from(result.result.data, 'base64'))
    console.log('saved', name)
  }

  const clickByText = async (text) => {
    await evaluate(`(() => {
      const el = [...document.querySelectorAll('button, .el-button')]
        .find((b) => b.textContent.trim() === ${JSON.stringify(text)});
      if (el) { el.click(); return true; }
      return false;
    })()`)
  }

  // 图 5-2 画布建图编辑器
  await useSession(dispatcher, '/roadnet')
  await sleep(1500)
  await capture('fig-5-2-editor.png')

  // 图 6-1 标准车间路网（3D 场景，未启动仿真）
  await useSession(dispatcher, '/simulation')
  await sleep(3000)
  await capture('fig-6-1-roadnet.png')

  // 图 5-3 仿真运行界面
  await useSession(dispatcher, '/simulation')
  await sleep(2000)
  await clickByText('开始')
  await sleep(6000)
  await capture('fig-5-3-simulation.png')

  // 图 5-4 / 6-3 算法对比报表
  await useSession(dispatcher, '/experiments')
  await sleep(1800)
  await clickByText('运行对比（两种算法）')
  await sleep(5000)
  await capture('fig-5-4-experiment.png')
  await capture('fig-6-3-bars.png')

  // 图 5-5 用户管理与数字字典
  await useSession(admin, '/admin/users')
  await sleep(1200)
  await capture('fig-5-5-users.png')
  await useSession(admin, '/admin/dict')
  await sleep(1200)
  await capture('fig-5-5-dict.png')

  // 图 5-1 工程结构（自绘 SVG）
  const archHtml = `<!doctype html><html><head><meta charset="utf-8"></head>
<body style="margin:0;background:#ffffff;font-family:'Microsoft YaHei',sans-serif">
<svg width="1680" height="1050" viewBox="0 0 1680 1050" xmlns="http://www.w3.org/2000/svg">
  <rect width="1680" height="1050" fill="#ffffff"/>
  <text x="60" y="80" font-size="34" font-weight="bold" fill="#1f3b73">排策智演 · 系统架构与工程结构</text>
  <g font-size="22" fill="#303133">
    <rect x="60" y="130" width="1560" height="150" rx="14" fill="#f2f6fc" stroke="#1f3b73" stroke-width="2"/>
    <text x="90" y="180" font-weight="bold" fill="#1f3b73">前端 agv-frontend</text>
    <text x="90" y="228">Vue 3 + TypeScript + Element Plus　|　Three.js 三维场景　|　ECharts 报表　|　WebSocket 客户端</text>
  </g>
  <g>
    <line x1="840" y1="280" x2="840" y2="360" stroke="#909399" stroke-width="3" marker-end="url(#arrow)"/>
    <text x="860" y="330" font-size="20" fill="#606266">REST /api　+　WebSocket /ws/sim</text>
  </g>
  <defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#909399"/></marker></defs>
  <g font-size="22" fill="#303133">
    <rect x="60" y="370" width="1560" height="150" rx="14" fill="#f0f9eb" stroke="#67c23a" stroke-width="2"/>
    <text x="90" y="420" font-weight="bold" fill="#3f7f2a">后端 agv-backend</text>
    <text x="90" y="468">Spring Boot 3 + JWT 双角色　|　路网/任务集/实验/字典/用户接口　|　仿真编排与帧推送　|　MySQL / H2</text>
  </g>
  <g>
    <line x1="840" y1="520" x2="840" y2="600" stroke="#909399" stroke-width="3" marker-end="url(#arrow)"/>
    <text x="860" y="570" font-size="20" fill="#606266">纯 Java 调用（零 Spring 依赖）</text>
  </g>
  <g font-size="22" fill="#303133">
    <rect x="60" y="610" width="1560" height="150" rx="14" fill="#fdf6ec" stroke="#e6a23c" stroke-width="2"/>
    <text x="90" y="660" font-weight="bold" fill="#a06a1f">算法 agv-algorithm</text>
    <text x="90" y="708">拓扑路网与 7 条校验　|　A*（节点+入边状态、转弯惩罚）　|　逐段滚动预约与冲突消解　|　双调度策略　|　离散 tick 引擎</text>
  </g>
  <g font-size="20" fill="#606266">
    <rect x="60" y="800" width="1560" height="150" rx="14" fill="#f4f4f5" stroke="#c0c4cc" stroke-width="2"/>
    <text x="90" y="850" font-weight="bold" fill="#606266">论文数据来源</text>
    <text x="90" y="896">30 个自动化测试：路网校验 7　A* 6　引擎 9　接口 7　规模矩阵 1</text>
    <text x="90" y="930">规模实验：2/3/5/8 台车 × 就近/贪心 ETA × 20 单，seed=20261107</text>
  </g>
</svg></body></html>`

  await send('Page.navigate', { url: 'about:blank' })
  await sleep(500)
  await send('Page.setDocumentContent', {
    frameId: (await send('Page.getFrameTree')).result.frameTree.frame.id,
    html: archHtml
  })
  await sleep(800)
  await capture('fig-5-1-architecture.png')

  // 图 6-2 完成时间折线图（自绘 SVG）
  const data = [
    { n: 2, nearest: 3948, greedy: 3320 },
    { n: 3, nearest: 3114, greedy: 2968 },
    { n: 5, nearest: 2476, greedy: 2478 },
    { n: 8, nearest: 3018, greedy: 3061 }
  ]
  const W = 1680, H = 1050, L = 160, R = 120, T = 150, B = 150
  const maxY = 4000
  const x = (i) => L + (i * (W - L - R)) / (data.length - 1)
  const y = (v) => H - B - (v / maxY) * (H - T - B)
  const poly = (key) => data.map((d, i) => `${x(i)},${y(d[key])}`).join(' ')
  const dots = (key, color) =>
    data.map((d, i) => `<circle cx="${x(i)}" cy="${y(d[key])}" r="7" fill="${color}"/>`).join('')
  const labels = (key, color, dy) =>
    data.map((d, i) => `<text x="${x(i)}" y="${y(d[key]) + dy}" font-size="20" fill="${color}" text-anchor="middle">${d[key]}</text>`).join('')
  const yTicks = Array.from({ length: 9 }, (_, i) => i * 500)
    .map((v) => `<line x1="${L}" y1="${y(v)}" x2="${W - R}" y2="${y(v)}" stroke="#ebeef5" stroke-width="1"/>
      <text x="${L - 16}" y="${y(v) + 7}" font-size="18" fill="#909399" text-anchor="end">${v}</text>`).join('')
  const xTicks = data.map((d, i) => `<text x="${x(i)}" y="${H - B + 40}" font-size="20" fill="#606266" text-anchor="middle">${d.n} 台</text>`).join('')

  const lineHtml = `<!doctype html><html><head><meta charset="utf-8"></head>
<body style="margin:0;background:#fff;font-family:'Microsoft YaHei',sans-serif">
<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg">
  <rect width="${W}" height="${H}" fill="#ffffff"/>
  <text x="80" y="70" font-size="30" font-weight="bold" fill="#1f3b73">图 6-2　完成时间随车队规模变化</text>
  <text x="80" y="110" font-size="18" fill="#909399">20 单任务集，seed = 20261107，单位：tick（1 tick = 100 ms）</text>
  ${yTicks}
  <line x1="${L}" y1="${H - B}" x2="${W - R}" y2="${H - B}" stroke="#c0c4cc" stroke-width="2"/>
  <line x1="${L}" y1="${T}" x2="${L}" y2="${H - B}" stroke="#c0c4cc" stroke-width="2"/>
  ${xTicks}
  <polyline points="${poly('nearest')}" fill="none" stroke="#1f3b73" stroke-width="4"/>
  ${dots('nearest', '#1f3b73')}
  ${labels('nearest', '#1f3b73', -18)}
  <polyline points="${poly('greedy')}" fill="none" stroke="#e6a23c" stroke-width="4" stroke-dasharray="10 6"/>
  ${dots('greedy', '#e6a23c')}
  ${labels('greedy', '#e6a23c', 34)}
  <rect x="${W - R - 300}" y="${T + 10}" width="20" height="6" fill="#1f3b73"/>
  <text x="${W - R - 270}" y="${T + 20}" font-size="20" fill="#606266">就近分配</text>
  <rect x="${W - R - 300}" y="${T + 50}" width="20" height="6" fill="#e6a23c"/>
  <text x="${W - R - 270}" y="${T + 60}" font-size="20" fill="#606266">贪心 ETA</text>
</svg></body></html>`

  await send('Page.navigate', { url: 'about:blank' })
  await sleep(400)
  await send('Page.setDocumentContent', {
    frameId: (await send('Page.getFrameTree')).result.frameTree.frame.id,
    html: lineHtml
  })
  await sleep(600)
  await capture('fig-6-2-line.png')

  ws.close()
  console.log('done')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
