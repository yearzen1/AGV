"""生成论文第 6 章的规模折线图与四项指标柱状图。

数据默认读取 agv-backend/target/warehouse-scale-matrix.txt；
文件不存在时使用文末的冻结数据，保证换机器也能重建插图。
"""
from pathlib import Path
import re

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "paper" / "thesis" / "figures"
MATRIX = ROOT / "agv-backend" / "target" / "warehouse-scale-matrix.txt"

FROZEN = [
    (2, "nearest", 7330, 1426.0, 496, 16),
    (2, "greedy-eta", 7797, 1494.0, 602, 17),
    (3, "nearest", 6282, 1616.0, 1365, 30),
    (3, "greedy-eta", 5792, 1470.0, 1506, 28),
    (5, "nearest", 5719, 2242.0, 4547, 86),
    (5, "greedy-eta", 5469, 2233.6, 4582, 76),
    (8, "nearest", 4878, 2722.8, 10793, 132),
    (8, "greedy-eta", 6005, 3359.4, 13438, 171),
]

LABELS = {"nearest": "就近分配", "greedy-eta": "贪心 ETA"}
COLORS = {"nearest": "#1f3b73", "greedy-eta": "#e6a23c"}


def load_rows():
    if not MATRIX.exists():
        return FROZEN
    rows = []
    pattern = re.compile(
        r"^(\d+)\t(\S+)\tdone=\S+\tcompleted=\d+/\d+\tmakespan=(\d+)\t"
        r"path=([\d.]+)\twait=(\d+)\tconflict=(\d+)\tticks=\d+$"
    )
    for line in MATRIX.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line)
        if match:
            rows.append((int(match[1]), match[2], int(match[3]), float(match[4]),
                         int(match[5]), int(match[6])))
    return rows or FROZEN


def configure_font():
    plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Noto Sans CJK SC"]
    plt.rcParams["axes.unicode_minus"] = False


def draw_line(rows):
    counts = [2, 3, 5, 8]
    fig, ax = plt.subplots(figsize=(11.2, 6.2), dpi=150)
    for algorithm in ("nearest", "greedy-eta"):
        values = [next(r[2] for r in rows if r[0] == n and r[1] == algorithm) for n in counts]
        ax.plot(counts, values, marker="o", linewidth=2.4, markersize=7,
                color=COLORS[algorithm], label=LABELS[algorithm],
                linestyle="-" if algorithm == "nearest" else "--")
        for x, y in zip(counts, values):
            ax.annotate(str(y), (x, y), textcoords="offset points",
                        xytext=(0, 10 if algorithm == "nearest" else -16),
                        ha="center", fontsize=10, color=COLORS[algorithm])
    ax.set_title("图 6-2　完成时间随车队规模变化", fontsize=15, loc="left")
    ax.set_xlabel("AGV 数量")
    ax.set_ylabel("完成时间（tick）")
    ax.set_xticks(counts)
    ax.set_xticklabels([f"{n} 台" for n in counts])
    ax.grid(axis="y", color="#ebeef5")
    ax.legend(frameon=False, loc="upper right")
    fig.tight_layout()
    fig.savefig(OUT / "fig-6-2-line.png", facecolor="white")
    plt.close(fig)


def draw_bars(rows):
    counts = [2, 3, 5, 8]
    metrics = [
        (2, "完成时间（tick）"),
        (3, "总路径长（m）"),
        (4, "总等待（tick）"),
        (5, "冲突次数"),
    ]
    fig, axes = plt.subplots(2, 2, figsize=(11.2, 7.2), dpi=150)
    width = 0.36
    positions = list(range(len(counts)))
    for ax, (index, title) in zip(axes.flat, metrics):
        for offset, algorithm in ((-width / 2, "nearest"), (width / 2, "greedy-eta")):
            values = [next(r[index] for r in rows if r[0] == n and r[1] == algorithm)
                      for n in counts]
            ax.bar([p + offset for p in positions], values, width,
                   color=COLORS[algorithm], label=LABELS[algorithm])
        ax.set_title(title, fontsize=11)
        ax.set_xticks(positions)
        ax.set_xticklabels([f"{n} 台" for n in counts])
        ax.grid(axis="y", color="#ebeef5")
    handles, labels = axes.flat[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper right", frameon=False, ncol=2)
    fig.suptitle("图 6-3　两种算法的四项指标对比", fontsize=15, x=0.02, ha="left")
    fig.tight_layout(rect=(0, 0, 1, 0.95))
    fig.savefig(OUT / "fig-6-3-bars.png", facecolor="white")
    plt.close(fig)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    configure_font()
    rows = load_rows()
    draw_line(rows)
    draw_bars(rows)
    print(f"写入 {OUT / 'fig-6-2-line.png'}")
    print(f"写入 {OUT / 'fig-6-3-bars.png'}")


if __name__ == "__main__":
    main()
