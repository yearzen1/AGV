"""生成答辩 PPT。"""
from pathlib import Path
import sys

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.oxml.ns import qn
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parent.parent
FIG = ROOT / "paper" / "thesis" / "figures"
OUT = ROOT / "paper" / "答辩PPT.pptx"

PRIMARY = RGBColor(0x1F, 0x3B, 0x73)
ACCENT = RGBColor(0x40, 0x9E, 0xFF)
WARNING = RGBColor(0xE6, 0xA2, 0x3C)
DANGER = RGBColor(0xF5, 0x6C, 0x6C)
TEXT = RGBColor(0x30, 0x31, 0x33)
MUTED = RGBColor(0x90, 0x93, 0x99)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
LIGHT = RGBColor(0xF2, 0xF6, 0xFC)

W = Inches(13.333)
H = Inches(7.5)

prs = Presentation()
prs.slide_width = W
prs.slide_height = H
BLANK = prs.slide_layouts[6]


def style_run(run, size=18, bold=False, color=TEXT):
    run.font.name = "Microsoft YaHei"
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    rpr = run._r.get_or_add_rPr()
    ea = rpr.find(qn("a:ea"))
    if ea is None:
        ea = rpr.makeelement(qn("a:ea"), {})
        rpr.append(ea)
    ea.set("typeface", "Microsoft YaHei")


def add_textbox(slide, left, top, width, height, text, size=18, bold=False,
                color=TEXT, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    box = slide.shapes.add_textbox(left, top, width, height)
    frame = box.text_frame
    frame.word_wrap = True
    frame.vertical_anchor = anchor
    frame.margin_left = Inches(0.08)
    frame.margin_right = Inches(0.08)
    lines = text.split("\n")
    for index, line in enumerate(lines):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.alignment = align
        paragraph.space_after = Pt(6)
        run = paragraph.add_run()
        run.text = line
        style_run(run, size, bold, color)
    return box


def add_header(slide, title, subtitle=None):
    bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, W, Inches(0.95))
    bar.fill.solid()
    bar.fill.fore_color.rgb = PRIMARY
    bar.line.fill.background()
    add_textbox(slide, Inches(0.5), Inches(0.18), Inches(9.5), Inches(0.6),
                title, 26, True, WHITE)
    if subtitle:
        add_textbox(slide, Inches(0.5), Inches(1.05), Inches(12.3), Inches(0.45),
                    subtitle, 14, False, MUTED)
    # 页脚
    add_textbox(slide, Inches(0.5), Inches(7.05), Inches(8), Inches(0.3),
                "排策智演 · 车间 AGV 集群路径规划仿真与调度可视化系统", 10, False, MUTED)


def add_bullets(slide, items, left=Inches(0.7), top=Inches(1.55),
                width=Inches(12), size=18, gap=10):
    box = slide.shapes.add_textbox(left, top, width, Inches(5.2))
    frame = box.text_frame
    frame.word_wrap = True
    for index, item in enumerate(items):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.space_after = Pt(gap)
        run = paragraph.add_run()
        run.text = "• " + item
        style_run(run, size)
    return box


def add_picture_slide(title, image_name, bullets=None, subtitle=None):
    slide = prs.slides.add_slide(BLANK)
    add_header(slide, title, subtitle)
    image = FIG / image_name
    top = Inches(1.55)
    max_h = Inches(5.2)
    max_w = Inches(12.2)
    pic = slide.shapes.add_picture(str(image), 0, top, width=max_w)
    ratio = pic.height / pic.width
    if pic.height > max_h:
        pic.height = max_h
        pic.width = int(max_h / ratio)
    pic.left = int((W - pic.width) / 2)
    if bullets:
        add_textbox(slide, Inches(0.7), Inches(6.55), Inches(12), Inches(0.5),
                    " | ".join(bullets), 12, False, MUTED)
    return slide


def add_table(slide, data, left, top, width, height, col_widths=None, font_size=12):
    rows = len(data)
    cols = len(data[0])
    table = slide.shapes.add_table(rows, cols, left, top, width, height).table
    if col_widths:
        for index, w in enumerate(col_widths):
            table.columns[index].width = w
    for r, row in enumerate(data):
        for c, value in enumerate(row):
            cell = table.cell(r, c)
            cell.text = ""
            paragraph = cell.text_frame.paragraphs[0]
            paragraph.alignment = PP_ALIGN.CENTER
            run = paragraph.add_run()
            run.text = str(value)
            style_run(run, font_size, r == 0, WHITE if r == 0 else TEXT)
            cell.fill.solid()
            cell.fill.fore_color.rgb = PRIMARY if r == 0 else (
                LIGHT if r % 2 == 0 else WHITE)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
    return table


# ---------------------------------------------------------------- 1 封面
slide = prs.slides.add_slide(BLANK)
bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, W, H)
bg.fill.solid()
bg.fill.fore_color.rgb = PRIMARY
bg.line.fill.background()

add_textbox(slide, Inches(1.0), Inches(1.9), Inches(11.3), Inches(1.6),
            "排策智演", 54, True, WHITE, PP_ALIGN.CENTER)
add_textbox(slide, Inches(1.0), Inches(3.1), Inches(11.3), Inches(1.0),
            "车间 AGV 小车集群路径规划仿真与调度可视化系统", 28, False, WHITE, PP_ALIGN.CENTER)

line = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(5.4), Inches(4.25),
                              Inches(2.5), Inches(0.06))
line.fill.solid()
line.fill.fore_color.rgb = WARNING
line.line.fill.background()

add_textbox(slide, Inches(2.0), Inches(4.6), Inches(9.3), Inches(1.6),
            "答辩人：____________　　学号：____________\n指导教师：____________\n2027 年 1 月",
            18, False, WHITE, PP_ALIGN.CENTER)

# ---------------------------------------------------------------- 2 目录
slide = prs.slides.add_slide(BLANK)
add_header(slide, "汇报提纲")
agenda = [
    ("01", "研究背景与问题"),
    ("02", "系统定位与总体架构"),
    ("03", "关键技术设计"),
    ("04", "系统演示"),
    ("05", "对比实验与结果分析"),
    ("06", "结论与展望"),
]
for index, (num, text) in enumerate(agenda):
    top = Inches(1.7 + index * 0.85)
    add_textbox(slide, Inches(1.2), top, Inches(1.0), Inches(0.6), num, 28, True, ACCENT)
    add_textbox(slide, Inches(2.2), top + Inches(0.05), Inches(8), Inches(0.6), text, 22)

# ---------------------------------------------------------------- 3 背景
slide = prs.slides.add_slide(BLANK)
add_header(slide, "研究背景：为什么需要仿真", "AGV 集群调度的三个现实约束")
add_bullets(slide, [
    "AGV 集群已成为车间物料搬运的核心装备，效率取决于任务分配、路径规划与冲突消解",
    "三者耦合后组合空间指数增长，属于 NP-hard 问题，工程上普遍采用启发式方法",
    "真实环境做对比实验：设备与场地投入高，换算法重跑一次往往需要数小时到数天",
    "冲突与死锁在真车上表现为碰撞，试错成本不可接受",
    "本科阶段不具备实体车队条件，需要一套可复现、零成本的验证环境",
], size=19, gap=14)

add_textbox(slide, Inches(0.9), Inches(6.2), Inches(11.5), Inches(0.8),
            "结论：用软件仿真调度算法的运行环境，是成本与可复现性之间的最优解。",
            20, True, PRIMARY)

# ---------------------------------------------------------------- 4 系统定位
slide = prs.slides.add_slide(BLANK)
add_header(slide, "系统定位：仿的是什么，真的是什么", "答辩常见问题：\"你在仿真什么？\"")
left = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.7), Inches(1.6),
                              Inches(5.8), Inches(3.2))
left.fill.solid()
left.fill.fore_color.rgb = LIGHT
left.line.color.rgb = PRIMARY
add_textbox(slide, Inches(1.0), Inches(1.8), Inches(5.2), Inches(0.5),
            "被仿真的：车间与车队", 20, True, PRIMARY)
add_bullets(slide, [
    "拓扑路网与货架布局",
    "AGV 的移动与时间流逝",
    "路口争用、拥堵与死锁",
], left=Inches(1.0), top=Inches(2.4), width=Inches(5.2), size=16)

right = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(6.9), Inches(1.6),
                               Inches(5.8), Inches(3.2))
right.fill.solid()
right.fill.fore_color.rgb = LIGHT
right.line.color.rgb = ACCENT
add_textbox(slide, Inches(7.2), Inches(1.8), Inches(5.2), Inches(0.5),
            "真实运行的：调度算法", 20, True, ACCENT)
add_bullets(slide, [
    "图上 A* 路径规划",
    "任务分配与优先级",
    "时间窗冲突消解与重规划",
], left=Inches(7.2), top=Inches(2.4), width=Inches(5.2), size=16)

add_textbox(slide, Inches(0.9), Inches(5.15), Inches(11.6), Inches(1.2),
            "类比飞行模拟器：被仿真的是空气动力学与飞机，控制律是真实运行的代码。\n"
            "本系统对应现实中的中央调度层（Fleet Manager），车端传感安全层不在研究范围。",
            18, False, TEXT)

# ---------------------------------------------------------------- 5 架构
add_picture_slide("总体架构", "fig-5-1-architecture.png",
                  ["前端 Vue3 + Three.js", "后端 SpringBoot", "算法纯 Java 可单测"])

# ---------------------------------------------------------------- 6 路网
slide = prs.slides.add_slide(BLANK)
add_header(slide, "关键技术一：拓扑路网建模", "为什么不用栅格地图")
add_bullets(slide, [
    "工业 AGV 沿固定车道行驶：车道有方向、限速、容量，栅格表达不了",
    "新路网全部单向：一条物理路段只对应一条有向弧，相向冲突从模型中消失",
    "节点显式建模为工位、充电位、缓存位、路口，冲突判定可直接落在节点上",
    "保存前执行 11 条校验，其中\"全单向、无叶子、强连通\"是结构无死锁的硬约束",
    "三种建图方式：套用模板、画布可视化建图、JSON 导入导出",
], size=18, gap=12)
add_textbox(slide, Inches(0.9), Inches(6.4), Inches(11.5), Inches(0.6),
            "搜索规模只与路口数量有关，不随面积分辨率膨胀。", 18, True, PRIMARY)

# ---------------------------------------------------------------- 7 A*
slide = prs.slides.add_slide(BLANK)
add_header(slide, "关键技术二：图上 A*", "代价含转弯惩罚时，状态必须带\"入边\"")
add_bullets(slide, [
    "代价 g = Σ(路段长度 / 速度) + 转弯惩罚，单位为秒",
    "启发 h = 节点到终点的欧氏距离 / 全图最大速度，满足可采纳性",
    "问题：从不同入边到达同一节点，后续代价不同 —— 只按节点关闭会得到次优解",
    "做法：搜索状态改为 (nodeId, inArcId)，起点入边为空，关闭集合按二元组去重",
    "验证：另写状态空间 Bellman-Ford 参考解，30 组随机图对拍，误差 < 1e-6",
], size=18, gap=12)
add_textbox(slide, Inches(0.9), Inches(6.3), Inches(11.5), Inches(0.7),
            "f(n) = g(n) + h(n)　　g 含转弯惩罚　　h 不高估", 20, True, PRIMARY)

# ---------------------------------------------------------------- 8 冲突消解
slide = prs.slides.add_slide(BLANK)
add_header(slide, "关键技术三：冲突消解", "逐段滚动预约，而不是出发前预约全程")
add_table(slide, [
    ["冲突类型", "判定依据", "消解手段"],
    ["节点冲突", "目标节点占用数已达容量", "后到车辆等待"],
    ["追尾冲突", "同一弧上并发占用达路段容量", "后到车辆等待"],
], Inches(0.7), Inches(1.6), Inches(11.9), Inches(1.4),
    [Inches(2.4), Inches(5.6), Inches(3.9)], 14)

add_bullets(slide, [
    "车辆只预约下一段弧与目标节点，等待自然发生在节点上，不会停在路段中间",
    "节点容量严格约束物理停靠：车没有离开节点就不释放预约",
    "会不会卡死由拓扑决定，而不是由等待策略决定（下一页）",
], top=Inches(3.3), size=17, gap=10)

# ---------------------------------------------------------------- 9 死锁
slide = prs.slides.add_slide(BLANK)
add_header(slide, "关键技术四：结构无死锁", "从\"运行时兜底\"到\"拓扑上不可能\"")
steps = [
    ("条件", "死锁需要互斥、持有并等待、不可抢占、循环等待；逐段预约天然满足前三条", WARNING),
    ("旧做法", "等待环检测 + 倒车让行 + 超时重规划，在运行时打断环", MUTED),
    ("硬伤", "倒车要求身后有空位，而成环的定义就是身后有车；检测也看不见装货与停靠的车", DANGER),
    ("新做法", "路网全单向、无叶子、强连通；车队数小于加权环容量，等待图不可能成环", PRIMARY),
    ("上限", "min(加权环容量 − 1, Σ 充电位过道位)，标准车间为 6 台，超编启动即拒绝", PRIMARY),
]
for index, (label, text, color) in enumerate(steps):
    top = Inches(1.5 + index * 1.05)
    badge = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.8), top,
                                   Inches(1.3), Inches(0.55))
    badge.fill.solid()
    badge.fill.fore_color.rgb = color
    badge.line.fill.background()
    add_textbox(slide, Inches(0.8), top + Inches(0.08), Inches(1.3), Inches(0.4),
                label, 15, True, WHITE, PP_ALIGN.CENTER)
    add_textbox(slide, Inches(2.4), top + Inches(0.06), Inches(10), Inches(0.5),
                text, 16)

add_textbox(slide, Inches(0.9), Inches(6.7), Inches(11.5), Inches(0.6),
            "结果：1~6 台全部完成 20 单；同一引擎下旧双向图在 2 台时就会互等", 15, False, MUTED)

# ---------------------------------------------------------------- 10 编辑器
add_picture_slide("系统演示：画布建图编辑器", "fig-5-2-editor.png",
                  ["点选加节点", "连线成路段", "拖动改坐标", "前后端双校验", "JSON 导入导出"])

# ---------------------------------------------------------------- 11 仿真
add_picture_slide("系统演示：三维实时仿真", "fig-5-3-simulation.png",
                  ["100ms 帧推送", "路段插值平滑运动", "等待变红", "冲突事件日志"])

# ---------------------------------------------------------------- 12 实验设计
slide = prs.slides.add_slide(BLANK)
add_header(slide, "对比实验设计", "保证对比公平的三个\"同一\"")
add_bullets(slide, [
    "同一路网：标准车间（单向环）8 节点 / 8 条单向路段，车队上限 6 台",
    "同一任务集：演示任务集 20 单，覆盖取货、送货、空驶与回充",
    "同一参数：seed = 20261107；引擎不含随机源，换种子结果也相同",
    "两种策略共用同一避障逻辑，差异只来自任务分配",
    "就近分配：按欧氏距离派给最近的空闲车",
    "贪心 ETA：按 A* 估计的\"空驶 + 送货\"总耗时派单",
], size=18, gap=12)
add_textbox(slide, Inches(0.9), Inches(6.35), Inches(11.5), Inches(0.7),
            "指标口径：完成时间 / 总路径长（含空驶）/ 总等待 / 冲突次数", 17, True, PRIMARY)

# ---------------------------------------------------------------- 13 结果表
slide = prs.slides.add_slide(BLANK)
add_header(slide, "实验结果：规模矩阵", "20 单，1~6 台全部完成，单位 tick（1 tick = 100 ms）")
data = [
    ["AGV", "算法", "完成时间", "总路径(m)", "总等待", "冲突", "完成"],
    ["1", "就近 / 贪心", "6420", "630.0", "0", "0", "20/20"],
    ["2", "就近", "3972", "750.0", "0", "0", "20/20"],
    ["2", "贪心 ETA", "3366", "661.1", "0", "0", "20/20"],
    ["3", "就近", "2760", "803.1", "128", "10", "20/20"],
    ["3", "贪心 ETA", "2668", "718.0", "99", "5", "20/20"],
    ["6", "就近", "2234", "1130.2", "880", "29", "20/20"],
    ["6", "贪心 ETA", "1970", "935.0", "948", "21", "20/20"],
]
add_table(slide, data, Inches(0.9), Inches(1.6), Inches(11.5), Inches(4.4),
          [Inches(1.0), Inches(2.2), Inches(2.0), Inches(2.2), Inches(1.8), Inches(1.2), Inches(1.3)],
          14)

# ---------------------------------------------------------------- 14 折线
add_picture_slide("结果分析一：规模收益在第 5 台后饱和", "fig-6-2-line.png",
                  ["1→2 台下降 38.1% / 47.6%", "5→6 台就近只快 1 tick", "却多跑 192 米空驶"])

# ---------------------------------------------------------------- 15 柱状
add_picture_slide("结果分析二：贪心 ETA 全面占优", "fig-6-3-bars.png",
                  ["2 台快 15.3%，6 台快 11.8%", "单向环放大了欧氏距离偏差", "结论不能外推到双向布局"])

# ---------------------------------------------------------------- 16 结论
slide = prs.slides.add_slide(BLANK)
add_header(slide, "结论")
add_bullets(slide, [
    "可复现性成立：引擎不含随机源，20 个种子下结果完全一致",
    "结构无死锁：1~6 台全部完成 20 单，死锁不可表达",
    "车队上限可事先算出并在启动时强制，超编直接拒绝",
    "规模收益在第 5 台后饱和：就近 5→6 台只快 1 tick，多跑 192 米",
    "贪心 ETA 在 2~6 台上全面占优（3.3%~20.0%），原因是单向环放大距离偏差",
    "代价：单向环绕行变长，第 6 台对就近分配已无收益",
], size=19, gap=14)

# ---------------------------------------------------------------- 17 展望
slide = prs.slides.add_slide(BLANK)
add_header(slide, "不足与展望")
add_bullets(slide, [
    "路口相位与动态车道未建模，后续可扩展",
    "任务集静态，后续可加入插单、取消与优先级变化",
    "未建模电量与充电时长，可研究\"何时回充\"对吞吐的影响",
    "未对接真实设备协议，可参考 VDA 5050 向工程原型演进",
    "可作为强化学习调度的可复现训练与评测环境",
], size=19, gap=13)

# ---------------------------------------------------------------- 18 致谢
slide = prs.slides.add_slide(BLANK)
bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, W, H)
bg.fill.solid()
bg.fill.fore_color.rgb = PRIMARY
bg.line.fill.background()
add_textbox(slide, Inches(1), Inches(2.4), Inches(11.3), Inches(1.2),
            "感谢各位老师", 52, True, WHITE, PP_ALIGN.CENTER)
add_textbox(slide, Inches(1), Inches(3.7), Inches(11.3), Inches(0.8),
            "恳请批评指正", 26, False, WHITE, PP_ALIGN.CENTER)
add_textbox(slide, Inches(1), Inches(5.2), Inches(11.3), Inches(0.8),
            "排策智演 · 车间 AGV 集群路径规划仿真与调度可视化系统", 16, False, WHITE, PP_ALIGN.CENTER)

prs.save(OUT)
print(f"生成 {OUT.name}：{len(prs.slides)} 页")
print(f"路径 {OUT}")
print(f"大小 {OUT.stat().st_size / 1024:.0f} KB")
