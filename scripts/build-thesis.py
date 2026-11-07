"""把 docs/thesis 下的分章 Markdown 合并成单一论文定稿，并补齐图、参考文献与目录。"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
THESIS = ROOT / "docs" / "thesis"

ORDER = [
    "00-摘要.md",
    "01-绪论.md",
    "02-相关技术.md",
    "03-需求分析.md",
    "04-系统设计.md",
    "05-系统实现.md",
    "06-实验与对比分析.md",
    "07-总结与展望.md",
]

TITLE = "排策智演——车间 AGV 小车集群路径规划仿真与调度可视化系统"

COVER = f"""# {TITLE}

**本科毕业设计论文**

| 项目 | 内容 |
|---|---|
| 学生姓名 | |
| 学号 | |
| 专业班级 | |
| 指导教师 | |
| 完成日期 | 2027 年 1 月 |

> 本文档由 `docs/thesis/` 下各章合并生成，插图位于 `docs/thesis/figures/`。
"""

FIGURE_REPLACEMENTS = {
    "> 图 5-1 工程结构（截图位置）":
        "![图 5-1　系统架构与工程结构](figures/fig-5-1-architecture.png)",
    "> 图 5-2 画布建图编辑器（截图位置）":
        "![图 5-2　画布建图编辑器](figures/fig-5-2-editor.png)",
    "> 图 5-3 仿真运行界面（截图位置）":
        "![图 5-3　仿真运行界面](figures/fig-5-3-simulation.png)",
    "> 图 5-4 算法对比报表（截图位置）":
        "![图 5-4　算法对比报表](figures/fig-5-4-experiment.png)",
    "> 图 5-5 用户管理与数字字典（截图位置）":
        "![图 5-5(a)　用户管理](figures/fig-5-5-users.png)\n\n"
        "![图 5-5(b)　数字字典](figures/fig-5-5-dict.png)",
    "> 图 6-2 完成时间随车队规模变化（折线图，数据取自表 6-2）":
        "![图 6-2　完成时间随车队规模变化](figures/fig-6-2-line.png)",
    "> 图 6-3 两种算法的四项指标对比（分组柱状图）":
        "![图 6-3　两种算法的四项指标对比](figures/fig-6-3-bars.png)",
}

# 在正文首次提到作者处补上顺序编码
CITATIONS = [
    ("Digani 等", "Digani 等[1]"),
    ("Cardarelli 等", "Cardarelli 等[2]"),
    ("VDA 5050 标准", "VDA 5050 标准[3]"),
    ("Hart 与 Nilsson", "Hart 与 Nilsson[4]"),
    ("刘春等", "刘春等[5]"),
    ("郭超等", "郭超等[6]"),
    ("Chung 与 Jang", "Chung 与 Jang[7]"),
    ("李昆鹏等", "李昆鹏等[8]"),
    ("张中伟等", "张中伟等[9]"),
    ("Yao 等", "Yao 等[10]"),
    ("石志浩与沈海辉", "石志浩与沈海辉[11]"),
    ("Kim 与 Kim", "Kim 与 Kim[12]"),
    ("ISO 3691-4", "ISO 3691-4[14]"),
]

LIU_ZHISHUO = (
    "刘春等针对大规模 AGV 提出了改进时间窗路径规划方法，通过预先计算路段占用区间来避免冲突，"
    "其核心是把\u0022空间路径\u0022扩展为\u0022空间—时间路径\u0022。"
)
LIU_ZHISHUO_WITH_CITATION = LIU_ZHISHUO + (
    "刘志硕等则从路网负载均衡的角度调整路径代价，避免车辆集中到同一区域，"
    "为大车队下的拥堵治理提供了另一种思路[13]。"
)

REFERENCES = """# 参考文献

[1] DIGANI V, SABATTINI L, SECCHI C, et al. Hierarchical traffic control for partially decentralized coordination of multi AGV systems in industrial environments[C]//2014 IEEE International Conference on Robotics and Automation (ICRA). IEEE, 2014. DOI: 10.1109/ICRA.2014.6907764.

[2] CARDARELLI E, DIGANI V, SABATTINI L, et al. Cooperative cloud robotics architecture for the coordination of multi-AGV systems in industrial warehouses[J]. Mechatronics, 2017, 45: 1-13. DOI: 10.1016/j.mechatronics.2017.04.005.

[3] VDA. VDA 5050: Interface for the communication between automated guided vehicles (AGV) and a master control[S]. Verband der Automobilindustrie, 2025.

[4] HART P E, NILSSON N J. A formal basis for the heuristic determination of minimum cost paths[J]. IEEE Transactions on Systems Science and Cybernetics, 1968, 4(2): 100-107.

[5] 刘春, 彭太平. 大规模AGV的改进时间窗路径规划[J]. 计算机应用研究, 2023, 40(1): 52-56. DOI: 10.19734/j.issn.1001-3695.2022.06.0288.

[6] 郭超, 陈香玲, 郭鹏, 等. 基于时空A*算法的多AGV无冲突路径规划[J]. 计算机系统应用, 2022, 31(4): 360-368. DOI: 10.15888/j.cnki.csa.008454.

[7] CHUNG C H, JANG Y J. Deadlock prevention and multi agent path finding algorithm considering physical constraint for a massive fleet AGV system[J]. Applied Soft Computing, 2024, 161: 111725. DOI: 10.1016/j.asoc.2024.111725.

[8] 李昆鹏, 刘腾博, 贺冰倩, 等. “货到人”拣选系统中AGV路径规划与调度研究[J]. 中国管理科学, 2022, 30(4): 240-251. DOI: 10.16381/j.cnki.issn1003-207x.2019.1408.

[9] 张中伟, 高增恩, 王菁锐, 等. 分布式AGV调度研究综述与发展趋势分析[J]. 制造技术与机床, 2024(11): 55-61. DOI: 10.19287/j.mtmt.1005-2402.2024.11.008.

[10] YAO Y, LIU Q, LI X, et al. Constraint programming for AGV and machine integrated scheduling problem in flexible manufacturing system[J]. IEEE Transactions on Automation Science and Engineering, 2026, 23: 2378-2390. DOI: 10.1109/TASE.2025.3650678.

[11] 石志浩, 沈海辉. 不确定情景下AGV系统调度算法的仿真平台[J]. 系统仿真学报, 2024, 36(2): 385-404. DOI: 10.16182/j.issn1004731x.joss.22-1214.

[12] KIM J, KIM J. Congestion-aware scheduling for large fleets of AGVs using discrete event simulation[J]. Electronics, 2026, 15(1): 139. DOI: 10.3390/electronics15010139.

[13] 刘志硕, 许俊哲, 李艳华, 等. 考虑路网负载均衡的AGV分拣系统动态路径规划方法[J]. 交通运输工程学报, 2026, 26(6): 209-220. DOI: 10.19818/j.cnki.1671-1637.2026.120.

[14] ISO. ISO 3691-4:2023 Industrial trucks — Safety requirements and verification — Part 4: Driverless industrial trucks and their systems[S]. International Organization for Standardization, 2023.
"""

ACKNOWLEDGEMENT = """# 致谢

本课题从选题、方案设计到系统实现与论文撰写，得到了指导教师的悉心指导。在选题阶段，老师帮助我把“看起来很大的题目”收敛为可交付的系统边界，明确了仿真对象与算法定位；在实现阶段，老师对冲突消解方案提出的质疑促使我重新审视资源占用模型，最终定位到“等待车辆仍持有节点”这一根本原因。

感谢实验室同学在演示脚本与界面细节上的建议。感谢家人一直以来的支持。

最后，感谢各位评审老师在百忙之中评阅本文，恳请批评指正。
"""


def build_toc(text: str) -> str:
    lines = []
    for raw in text.splitlines():
        if raw.startswith("## "):
            lines.append("    " + raw[3:].strip())
        elif raw.startswith("# "):
            lines.append(raw[2:].strip())
    body = "\n".join(lines)
    return "# 目录\n\n" + body + "\n"


def main() -> int:
    parts = []
    warnings = []

    for name in ORDER:
        path = THESIS / name
        if not path.exists():
            warnings.append(f"缺少文件：{name}")
            continue
        parts.append(path.read_text(encoding="utf-8").strip())

    # 摘要单独成篇，目录放在摘要之后、正文之前
    abstract = parts[0]
    chapters = "\n\n".join(parts[1:])
    body = abstract + "\n\n" + chapters

    for old, new in FIGURE_REPLACEMENTS.items():
        if old not in chapters:
            warnings.append(f"未找到图占位：{old[:20]}")
        chapters = chapters.replace(old, new)

    # 图 6-1 紧跟路网段落
    roadnet_para = "容量为 1。\n\n**任务集**"
    if roadnet_para in chapters:
        chapters = chapters.replace(
            roadnet_para,
            "容量为 1。\n\n![图 6-1　标准车间路网](figures/fig-6-1-roadnet.png)\n\n**任务集**",
        )
    else:
        warnings.append("未找到图 6-1 插入点")

    if LIU_ZHISHUO in chapters:
        chapters = chapters.replace(LIU_ZHISHUO, LIU_ZHISHUO_WITH_CITATION, 1)
    else:
        warnings.append("未找到刘志硕插入点")

    for old, new in CITATIONS:
        if old in chapters:
            chapters = chapters.replace(old, new, 1)
        else:
            warnings.append(f"未找到引用锚点：{old}")

    toc = build_toc(body)
    # 目录只列摘要之外的章节，且位于摘要之后
    toc = build_toc(chapters)
    final = "\n\n".join([COVER.strip(), abstract, toc, chapters,
                         REFERENCES.strip(), ACKNOWLEDGEMENT.strip()]) + "\n"

    out = THESIS / "论文定稿.md"
    out.write_text(final, encoding="utf-8")

    print(f"写入 {out}")
    print(f"字符数 {len(final)}")
    print(f"图片引用 {final.count('](figures/')} 处")
    print(f"表格数量 {len(re.findall(r'^\\|', final, re.M))} 行表格源")
    if warnings:
        print("警告：")
        for w in warnings:
            print("  -", w)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

