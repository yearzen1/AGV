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

建表由 Hibernate `ddl-auto=update` 自动完成，共 6 张表：
`sys_user`、`sys_dict`、`warehouse_map`、`task_set`、`experiment`、`experiment_metric`。
（`sim_event` 为可选降级项，默认不建，事件只走 WebSocket。）

## 部署与安全

仓库里的默认配置只用于本地演示，公网部署前必须处理：

- **JWT 密钥**：`application.yml` 里的默认值仅供开发使用。部署时设置长度为 32 字节以上的随机值：

  ```powershell
  $env:AGV_JWT_SECRET="用随机生成的至少32字节字符串"
  ```

- **演示账号**：`admin/admin123`、`dispatcher/dispatcher123` 会在首次启动时自动初始化。部署前必须修改密码，或移除 `DataInitializer` 中的初始化逻辑。
- **H2 控制台**：`dev` profile 下 `/h2-console` 免鉴权，H2 用户 `sa` 密码为空。不要用 `dev` profile 公网启动；生产使用 `mysql` profile。
- **端口**：公网只暴露前端或反向代理，不要把 `8080` 直接暴露出去。

## 接口约定

- 统一返回 `{ code, message, data }`，`code = 0` 表示成功。
- 鉴权：`Authorization: Bearer <token>`，登录接口 `POST /api/auth/login`。
- 路网校验失败返回 `code = 4001`，`data` 是带 `nodeId` / `edgeId` 的错误明细，前端据此标红。
- 不存在的路径返回 HTTP 404 与 `{ "code": 4040, "message": "接口不存在：<path>" }`，不会返回 500，也不会把异常堆栈暴露给调用方。
- 任务集接口是 `/api/taskset`，不是 `/api/task-set`。

### 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 4000 | 400 | 参数校验失败 |
| 4001 | 400 | 路网校验未通过，`data` 为逐条错误 |
| 4010 | 401 | 未登录或令牌过期 |
| 4030 | 403 | 无权限 |
| 4040 | 404 | 资源或接口不存在 |
| 5000 | 500 | 服务器内部错误 |

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
GET    /api/dict/{dictType}     下拉字典（algorithm / metric）
GET    /api/admin/users         用户管理（仅管理员）
GET    /api/admin/dict          字典管理（仅管理员）
WS     /ws/sim                  仿真帧通道（推送帧与状态，支持 echo 自检）
```

### 实时推送（WebSocket）

- 连接 `/ws/sim` 后，服务端先回一条 `{"type":"connected"}`；发送任意文本会收到 `{"type":"echo"}`，用于连通性自检。
- 仿真运行期间，每个 tick 推送 `{"type":"frame","frame":{...},"speed":1.0,"paused":false}`；控制类操作推送 `{"type":"status",...}`。
- 每个会话的发送由 Spring 的 `ConcurrentWebSocketSessionDecorator` 串行化，避免同一连接上并发写导致 `TEXT_PARTIAL_WRITING`。
- 单个会话发送失败只移除该会话并记一条警告，**不会中断仿真**，也不会把仿真状态置为失败。
- 事件不落库，只在帧里推送；实验结束后仅保存四项汇总指标。
- 页面刷新或重新进入「仿真运行」时，前端调用 `GET /api/sim/status` 恢复运行状态；`status` 为 `RUNNING` / `FINISHED` / `FAILED` 时同步启动参数、速度、暂停状态、tick 与指标。
- 历史实验列表用 `RUNNING` / `FINISHED` / `FAILED` / `ABORTED` 标记状态；被新仿真替换或手动重置的旧实验记为 `ABORTED`（已中断），未完成实验的汇总指标显示为 `-`。后端重启时，遗留的 `RUNNING` 实验也会在启动时标记为 `ABORTED`。列表时间统一格式化为 `YYYY-MM-DD HH:mm:ss`。

## 进度

| 阶段 | 状态 |
|---|---|
| P0 环境搭建（登录、双角色、路网 CRUD、WebSocket） | 已完成 |
| P1a 3D 场景渲染 | 已完成 |
| P1b 画布建图编辑器 | 已完成 |
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

- `AStarPlannerTest`：直线路网、不可达、封锁绕行、转弯惩罚、与状态空间 Bellman-Ford 对拍
- `LaneModeTest`：单车道双向串行、双车道双向并行、路口临界区
- `RoadNetValidatorTest`：8 条校验规则
- `ApiSmokeTest`：登录、401/403、路网校验拦截、模板校验
- `SimSocketHandlerTest`：WebSocket 连接、echo 与广播
