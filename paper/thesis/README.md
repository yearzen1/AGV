# 论文稿（Markdown 源）

本目录是论文正文的 Markdown 草稿与定稿产物。

| 文件 | 说明 |
|---|---|
| [论文定稿.md](论文定稿.md) | **合并定稿**：封面 + 摘要 + 目录 + 第 1–7 章 + 参考文献 + 致谢，9 张插图已嵌入 |
| `论文定稿.docx` | 由定稿 Markdown 经 pandoc 转换，含分页、宋体小四正文、黑体标题、15 个表格；本地生成，不入库 |
| `00-摘要.md` ~ `07-总结与展望.md` | 分章源文件，便于单独修改后重新合并 |
| `figures/` | 论文插图（Edge 无头模式实拍 + 自绘图表），已入库，供 GitHub 渲染 |

## 重新生成

```powershell
python scripts/build-thesis.py        # 合并分章 -> 论文定稿.md
python scripts/build-thesis-docx.py   # 生成 reference.docx 并转出 论文定稿.docx
python scripts/capture-figures.mjs    # 需要先启动前后端与无头 Edge，重新截图
```

> `论文定稿.docx` 和 `reference.docx` 是生成产物，已在 `.gitignore` 中忽略；`论文定稿.md` 与 `figures/*.png` 保留入库。

## 插图清单

| 编号 | 文件 | 内容 |
|---|---|---|
| 图 5-1 | `figures/fig-5-1-architecture.png` | 系统架构与工程结构 |
| 图 5-2 | `figures/fig-5-2-editor.png` | 画布建图编辑器 |
| 图 5-3 | `figures/fig-5-3-simulation.png` | 仿真运行界面 |
| 图 5-4 | `figures/fig-5-4-experiment.png` | 算法对比报表 |
| 图 5-5 | `figures/fig-5-5-users.png`、`fig-5-5-dict.png` | 用户管理 / 数字字典 |
| 图 6-1 | `figures/fig-6-1-roadnet.png` | 标准车间路网（三维） |
| 图 6-2 | `figures/fig-6-2-line.png` | 完成时间随车队规模变化 |
| 图 6-3 | `figures/fig-6-3-bars.png` | 四项指标对比 |

## 数据来源

第 6 章表 6-2 的数值由 `EngineScaleTest` 产出，落盘于 `agv-backend/target/scale-matrix.txt`；也可通过 `/api/experiment/compare` 复现，参数为 `roadnetId=1, taskSetId=1, seed=20261107`。

## 待人工完成

- 官方查重：需学校指定系统与账号，建议按 ≤15% 目标先自查。
- 封面表头（姓名、学号、班级、导师）需填写。
- 若学校模板对页边距、页眉页脚、参考文献格式有额外要求，在 Word 中套用模板即可，正文内容无需改动。

