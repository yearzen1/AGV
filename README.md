# 排策智演

车间 AGV 小车集群路径规划仿真与调度可视化系统（选题 11 毕业设计）。

拓扑路网 + 图上 A\* + 时间窗冲突避障 + 两种调度策略对比，3D 实时展示小车运动与等待冲突事件。

## 目录结构

```
agv-algorithm/   纯 Java 算法包：路网模型、A*、校验、时间窗、仿真引擎（零 Spring 依赖，可单测）
agv-backend/     SpringBoot 后端：认证授权、路网/任务集 CRUD、实验记录、WebSocket 帧推送
agv-frontend/    Vue3 + Element Plus + Three.js + ECharts
specs/           实施计划、时间安排、参考文献
```

分层约定：**算法逻辑只能写在 `agv-algorithm`**，后端只做 IO 与编排。这条是论文里"算法可独立单测"的依据。

## 领域模型

- 术语、限界上下文、聚合边界、不变量和状态机：[docs/reference/领域模型与术语.md](docs/reference/领域模型与术语.md)
- 为什么这样建模、持久化模型与领域模型的偏差：[docs/explanation/领域模型说明.md](docs/explanation/领域模型说明.md)
- `WarehouseMap` / `mapId` 等历史命名的分阶段迁移计划：[docs/how-to/统一领域命名.md](docs/how-to/统一领域命名.md)
- 三维场景的坐标、插值、画质档位与取舍：[docs/explanation/三维场景渲染.md](docs/explanation/三维场景渲染.md)

代码、接口、页面、论文出现术语歧义时，以 reference 文档为准。

## 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Maven | 3.6+ |
| Node.js | 18+ |
| MySQL | 8+（可选，开发期用 H2） |

## 快速开始

```bash
# 1. 编译全部模块并安装到本地仓库（后端依赖 agv-algorithm，这一步不能省）
mvn -DskipTests install

# 2. 启动后端（默认 dev 环境，H2 文件库，免装 MySQL）
cd agv-backend
mvn spring-boot:run

# 3. 另开一个终端（回到项目根目录），启动前端
cd agv-frontend
npm install
npm run dev
```

> 零 Spring Boot / Vue 基础？跟着 [docs/tutorials/01-运行项目.md](docs/tutorials/01-运行项目.md) 走一遍即可。

浏览器打开 <http://localhost:5173>。

**演示账号**

| 账号 | 密码 | 角色 | 可见菜单 |
|---|---|---|---|
| `admin` | `admin123` | 管理员 | 全部 + 用户管理 + 数字字典 |
| `dispatcher` | `dispatcher123` | 调度人员 | 业务五个菜单 |

> 首次登录后请改密码。账号由 `DataInitializer` 自动初始化，幂等。

## 切换到 MySQL

论文与答辩用 MySQL，开发期默认 H2（数据落在 `agv-backend/data/agvdb.mv.db`，重启不丢）。

```sql
CREATE DATABASE agv_sim DEFAULT CHARSET utf8mb4;
```

```powershell
$env:MYSQL_PASSWORD="你的密码"
cd agv-backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

建表由 Flyway 迁移脚本管理（`agv-backend/src/main/resources/db/migration/`），共 7 张表：
`sys_user`、`sys_dict`、`roadnet`、`task_set`、`experiment`、`experiment_metric`、`sys_audit_log`。
（`sim_event` 为可选降级项，默认不建，事件只走 WebSocket。）

所有表的主键 `id` 都是 `BIGINT NOT NULL PRIMARY KEY`，由应用侧雪花算法生成，**数据库不使用自增**。
雪花 ID 为 53 位（41 位毫秒 + 2 位 worker + 10 位序列），前端可直接当 `number` 使用；
多实例部署时用 `AGV_WORKER_ID`（0～3）区分实例。详见 [数据库设计](docs/reference/数据库设计.md)。

升级已有数据库时 Flyway 会自动 `baseline`，不会清空数据；脚本只做增量变更。
本次主键改造修改了 V1/V3 的建表脚本，已有的 H2 或 MySQL 库需要按数据库设计文档重建。

## 部署与安全

仓库里的默认配置只用于本地演示，公网部署前必须处理：

- **JWT 密钥**：`application.yml` 里的默认值仅供开发使用。部署时设置长度为 32 字节以上的随机值：

  ```powershell
  $env:AGV_JWT_SECRET="用随机生成的至少32字节字符串"
  ```

- **演示账号**：`admin/admin123`、`dispatcher/dispatcher123` 会在首次启动时自动初始化。部署前必须修改密码，或移除 `DataInitializer` 中的初始化逻辑。
- **H2 控制台**：`dev` profile 下 `/h2-console` 免鉴权，H2 用户 `sa` 密码为空。不要用 `dev` profile 公网启动；生产使用 `mysql` profile。
- **端口**：公网只暴露前端或反向代理，不要把 `8080` 直接暴露出去。
- **WebSocket**：`/ws/sim?token=<jwt>` 的令牌会出现在代理访问日志里，生产环境建议缩短令牌有效期或改为一次性票据。
- **并发上限**：`AGV_SIM_MAX_CONCURRENT` 控制同时运行的仿真数（默认 5）。按单机 CPU 和内存调整，不要直接放开。
- **雪花 ID 实例号**：多实例部署时设置 `AGV_WORKER_ID`（0～3），每个实例必须不同；单实例保持默认 0 即可。

## 接口约定

- 统一返回 `{ code, message, data }`，`code = 0` 表示成功。
- 鉴权：`Authorization: Bearer <token>`，登录接口 `POST /api/auth/login`。
- HTTP 状态由业务码决定：`code / 10` 是目标状态码（如 `4012 → 401`、`4040 → 404`、`4090 → 409`），没有对应状态时按 `400` 返回。
- 路网校验失败返回 `code = 4001`，`data` 是带 `nodeId` / `edgeId` 的错误明细，前端据此标红。
- 不存在的路径返回 HTTP 404 与 `{ "code": 4040, "message": "接口不存在：<path>" }`，不会返回 500，也不会把异常堆栈暴露给调用方。
- 未预期的 500 只返回通用文案与请求编号（响应头 `X-Request-Id`），堆栈只写服务端日志。
- 列表接口默认不强制分页；实验历史与审计日志使用 `page`（从 0 起）和 `size`（1–100，默认 20），返回 `{ total, page, size, records }`。
- 任务集接口是 `/api/taskset`，不是 `/api/task-set`。
- 任务集必须绑定一个路网。任务的起点与终点只能是该路网的**工位**（`WORKSTATION`），保存时校验节点存在、类型正确、起终点不同且 A* 可达；仿真与对比实验启动前会再次校验，避免旧数据或改图后残留的非法任务进入引擎。新建或修改任务集的载荷：

  ```json
  {
    "name": "演示任务集",
    "roadnetId": 1,
    "taskSet": {
      "tasks": [
        { "id": 1, "from": "W1", "to": "W2" }
      ]
    }
  }
  ```

  删除仍被任务集引用的路网会被拒绝，需先删除任务集或把它改绑到其他路网。操作步骤见 [docs/how-to/维护任务集.md](docs/how-to/维护任务集.md)。

### 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 4000 | 400 | 参数校验失败（含请求体格式、参数类型错误） |
| 4001 | 400 | 路网校验未通过，`data` 为逐条错误 |
| 4010 | 401 | 未登录或令牌过期 |
| 4011 | 401 | 账号已停用 |
| 4012 | 401 | 用户名或密码错误 |
| 4030 | 403 | 无权限 |
| 4040 | 404 | 资源或接口不存在 |
| 4090 | 409 | 数据冲突（唯一键或引用约束） |
| 4290 | 429 | 并发仿真数已达上限 |
| 5000 | 500 | 服务器内部错误，消息附带请求编号 |

主要端点：

```
POST   /api/auth/login          登录
GET    /api/auth/me             当前用户
GET    /api/roadnet/list        路网列表（概要）
GET    /api/roadnet/{id}        路网详情
POST   /api/roadnet             新建
PUT    /api/roadnet/{id}        保存
DELETE /api/roadnet/{id}        删除
POST   /api/roadnet/import      导入 JSON
GET    /api/roadnet/{id}/export 导出 JSON
POST   /api/roadnet/validate    仅校验不落库
GET    /api/taskset/list        任务集列表（可选 roadnetId 过滤）
GET    /api/taskset/{id}        任务集详情
POST   /api/taskset             新建任务集
PUT    /api/taskset/{id}        保存任务集
DELETE /api/taskset/{id}        删除任务集
GET    /api/dict/{dictType}     下拉字典（只返回启用项；algorithm 已接入，metric 预留）
POST   /api/experiment/{id}/replay  按实验输入快照重跑并比对指标
GET    /api/experiment/list?page=0&size=20  实验历史（分页）
GET    /api/admin/users         用户管理（仅管理员）
GET    /api/admin/dict          字典项列表（仅管理员）
PUT    /api/admin/dict/{id}     修改显示名/排序/启停（仅管理员）
DELETE /api/admin/dict/{id}     删除字典项（仅管理员；界面不提供新增）
GET    /api/admin/audit?page=0&size=20  审计日志（仅管理员，分页）
WS     /ws/sim?token=<jwt>       仿真帧通道（按用户隔离，推送帧与状态，支持 echo 自检）
```

数字字典的维护范围与限制见 [维护数字字典](docs/how-to/维护数字字典.md)。

### 实验快照与回放

实验创建时会把当时的整张路网和任务集 JSON 存入 `experiment.roadnet_snapshot` / `taskset_snapshot`。
即使之后路网或任务集被编辑、删除，历史实验仍可用 `POST /api/experiment/{id}/replay` 按原输入重跑，
并返回新旧两次的四项指标与 `consistent` 比对结果。

- 回放只重跑输入、不重放逐帧画面；事件仍然只在 WebSocket 中推送。
- 快照功能上线前的历史实验没有快照，回放返回 `code = 4005`。
- 没有指标记录（`FAILED` / `ABORTED`）的实验不能回放，返回 `code = 4006`。

细节见 [实验快照与回放](docs/reference/实验快照与回放.md)。

### 审计日志

登录、路网/任务集/用户变更、仿真启动与实验运行会写入 `sys_audit_log`。
审计写入失败只记服务端警告，不影响业务操作。管理员用 `GET /api/admin/audit` 分页查询。
动作清单与字段见 [审计日志](docs/reference/审计日志.md)。

### 多用户并发仿真

后端按用户隔离仿真运行态，多个调度人员可以同时启动各自的仿真：

- 每个用户同一时刻只有一个仿真；再次启动只替换自己的，**不会中断别人的仿真**。
- `pause` / `resume` / `reset` / `speed` / `status` 都只作用于当前登录用户。
- 帧只推送给仿真发起人已连接的 WebSocket 会话；同一用户打开多个标签页，每个标签页都能收到。
- 全局并发上限由 `agv.sim.max-concurrent` 控制，默认 5；超过返回 `code = 4290` / HTTP 429，不排队。
- 仿真运行态保存在内存中，因此当前版本按**单后端实例**部署；多实例需要粘性会话或把运行态外置。

细节见 [多用户仿真](docs/reference/多用户仿真.md)。

### 实时推送（WebSocket）

- 连接 `/ws/sim?token=<jwt>`。握手时校验 JWT 与账号状态，失败返回 401 并拒绝连接；连接成功后服务端先回一条 `{"type":"connected"}`。
- 发送任意文本会收到 `{"type":"echo"}`，用于连通性自检。
- 仿真运行期间，每个 tick 只向**仿真发起人**推送 `{"type":"frame","frame":{...},"speed":1.0,"paused":false}`；控制类操作推送 `{"type":"status",...}`。
- 每个会话的发送由 Spring 的 `ConcurrentWebSocketSessionDecorator` 串行化，避免同一连接上并发写导致 `TEXT_PARTIAL_WRITING`。
- 单个会话发送失败只移除该会话并记一条警告，**不会中断仿真**，也不会把仿真状态置为失败。
- 事件不落库，只在帧里推送；实验结束后仅保存四项汇总指标。
- 页面刷新或重新进入「仿真运行」时，前端调用 `GET /api/sim/status` 恢复**当前用户**的运行状态；`status` 为 `RUNNING` / `FINISHED` / `FAILED` 时同步启动参数、速度、暂停状态、tick 与指标。`RUNNING`（含暂停）时 `status` 还带当前帧的 `agvs` 快照，刷新后 3D 场景与「车辆状态」不会丢车；结束后返回空数组。
- 仿真自然结束时会收到 `status = FINISHED` 的状态消息，随后连接保持可用；离开页面会主动断开，意外断开时自动重试 5 次（2/4/8/16/30 秒退避），仍失败则停止重试，可点「重试」手动恢复。
- 历史实验列表用 `RUNNING` / `FINISHED` / `FAILED` / `ABORTED` 标记状态；被新仿真替换或手动重置的旧实验记为 `ABORTED`（已中断），未完成实验的汇总指标显示为 `-`。后端重启时，遗留的 `RUNNING` 实验也会在启动时标记为 `ABORTED`。列表时间统一格式化为 `YYYY-MM-DD HH:mm:ss`。

## 进度

| 阶段 | 状态 |
|---|---|
| P0 环境搭建（登录、双角色、路网 CRUD、WebSocket） | 已完成 |
| P1a 3D 场景渲染 | 已完成 |
| P1b 画布建图编辑器 | 已完成 |
| P1c 仿真三维真实感（实体路面、阴影、画质档位、选中跟随） | 已完成 |
| P2 算法核心（A\* + 时间窗引擎 + 死锁处理） | 已完成 |
| P3 联调（实时推送、暂停调速、事件日志） | 已完成 |
| P4 调度对比（双算法 + 指标报表 + 历史回看） | 已完成 |
| P5 管理模块与演示数据 | 已完成 |
| 论文初稿（摘要 + 7 章） | 已完成，见 `paper/thesis/` |

详细排期见 [specs/毕设实施计划.md](specs/毕设实施计划.md) 与 [specs/时间安排表.md](specs/时间安排表.md)；
执行结果与证据见时间安排表第六节。

## 一键启动

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
```

启动时先在根目录 `mvn install` 算法包，再打开后端（8080）与前端（5173）两个窗口。脚本以 **UTF-8 with BOM** 保存，`powershell.exe`（5.1）与 `pwsh`（7）下中文提示都正常；新增 `.ps1` 时请保留 BOM，否则 5.1 会按 GBK 解码而乱码。

## 论文与演示

- 论文定稿：`paper/thesis/论文定稿.md`（9 张插图已入库）；`论文定稿.docx` 为本地生成产物，不入库
- 分章源文件：`paper/thesis/00-摘要.md` ~ `07-总结与展望.md`
- 答辩 PPT：运行 `python scripts/build-ppt.py` 生成 `paper/答辩PPT.pptx`（生成产物，不入库），大纲见 `paper/答辩PPT大纲.md`
- 答辩演示脚本：`paper/答辩演示脚本.md`

## 重新生成论文与 PPT

```powershell
node scripts/capture-figures.mjs      # 先启动前后端与无头 Edge，重拍 9 张插图
python scripts/build-thesis.py        # 合并分章 -> 论文定稿.md
python scripts/build-thesis-docx.py   # 生成 docx
python scripts/build-ppt.py           # 生成 pptx
```

> `论文定稿.docx`、`reference.docx`、`答辩PPT.pptx` 是生成产物，已在 `.gitignore` 中忽略；9 张 PNG 插图保留入库，GitHub 上可直接查看论文。

## 测试

```bash
mvn test
```

前端三维渲染单元测试（使用 Node 自带 test runner，无需浏览器；需要 Node 22+ 的类型剥离能力）：

```bash
cd agv-frontend
npm test
```

前端地图编辑器回归（需要先启动后端 8080 和前端 5173）：

```bash
cd agv-frontend
npm run e2e:roadnet
```

任务集页面回归（同样需要前后端已启动）：

```bash
cd agv-frontend
npm run e2e:taskset
```

仿真运行页回归（同样需要前后端已启动）：

```bash
cd agv-frontend
npm run e2e:sim
```

双车道 3D 场景回归（同样需要前后端已启动）：

```bash
cd agv-frontend
npm run e2e:scene
```

首次运行需安装浏览器：`npx playwright install chromium`。四个脚本都会把截图写到 `.playwright-mcp/e2e/`。`e2e:roadnet` 验证布局、编辑、连边、搜索、页签、任务聚合、3D 与全屏；`e2e:taskset` 新建并清理一条临时任务集，验证行号连续、加行不重复、批量填充与清空确认、保存失败时列出全部错误并标红多行；`e2e:sim` 验证参数标签、运行时参数锁定、暂停冻结、暂停后刷新恢复车辆位置、继续与重置，最后完整跑完 20 单（约 2 分钟）确认结束态与实时通道；`e2e:scene` 通过接口创建一张临时双车道路网，打开 3D 预览，核对路面、分隔线和箭头数量，结束后删除临时路网。前端不在 5173 时用 `AGV_WEB=http://localhost:5188` 指定地址；`e2e:scene` 依赖开发模式的场景统计，生产构建不暴露。

- `AStarPlannerTest`：直线路网、不可达、封锁绕行、转弯惩罚、与状态空间 Bellman-Ford 对拍
- `LaneModeTest`：单车道双向串行、双车道双向并行、路口临界区
- `RoadNetValidatorTest`：8 条校验规则
- `ApiSmokeTest`：登录、401/403、路网校验拦截、模板校验、错误码映射、实验快照回放、审计查询
- `ExperimentReplayTest`：同 seed 回放指标一致、缺快照与缺指标的回放拒绝
- `AuditLogTest`：动作记录、登录失败记录、管理员分页查询与权限拦截
- `TaskSetValidationTest`：任务集绑定路网、工位校验、跨图启动拦截、删除保护
- `SimSocketHandlerTest`：WebSocket 连接、echo、按用户路由与坏会话隔离
- `JwtHandshakeInterceptorTest`：握手令牌校验与账号状态拦截
- `MultiUserSimulationTest`：两个用户并发仿真互不干扰，暂停/重置只影响自己
- `SimulationConcurrencyLimitTest`：并发上限拦截与 4290 错误码
