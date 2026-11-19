"""从 RoadNetTemplates 的仓储模板生成论文图 6-1（二维布局）。

直接解析 Java 文本块里的 JSON，避免在脚本里再抄一份坐标。
"""
from pathlib import Path
import json
import re

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, FancyBboxPatch, Rectangle

ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "agv-backend/src/main/java/com/agv/backend/support/RoadNetTemplates.java"
OUT = ROOT / "paper/thesis/figures/fig-6-1-roadnet.png"

NODE_COLORS = {
    "PICKING": "#409eff",
    "LOADING": "#2ec4b6",
    "STORAGE": "#9b59b6",
    "CHARGER": "#67c23a",
    "PARKING": "#d4a017",
    "BUFFER": "#e6a23c",
    "JUNCTION": "#909399",
}
ZONE_COLORS = {
    "CHARGING": "#67c23a",
    "PARKING": "#d4a017",
    "STORAGE": "#9b59b6",
    "PICKING": "#409eff",
    "LOADING": "#2ec4b6",
}


def load_template():
    text = TEMPLATE.read_text(encoding="utf-8")
    match = re.search(r'WAREHOUSE_WORKSHOP\s*=\s*"""\n(.*?)\n\s*""";', text, re.S)
    if not match:
        raise SystemExit("找不到 WAREHOUSE_WORKSHOP 模板")
    return json.loads(match.group(1))


def main():
    net = load_template()
    plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "Noto Sans CJK SC"]
    plt.rcParams["axes.unicode_minus"] = False

    fig, ax = plt.subplots(figsize=(12, 7.6), dpi=150)
    ax.set_facecolor("#fbfcfe")

    for zone in net.get("zones", []):
        color = ZONE_COLORS.get(zone["kind"], "#909399")
        ax.add_patch(Rectangle((zone["x"], zone["y"]), zone["width"], zone["height"],
                               facecolor=color, alpha=0.10, edgecolor=color,
                               linewidth=1.1, linestyle="--", zorder=0))
        ax.text(zone["x"] + 0.8, zone["y"] + 1.8,
                zone.get("label") or zone["kind"], color=color, fontsize=9,
                alpha=0.9, zorder=1)

    nodes = {node["id"]: node for node in net["nodes"]}
    for edge in net["edges"]:
        start, end = nodes[edge["from"]], nodes[edge["to"]]
        dual = edge.get("laneMode") == "DUAL"
        ax.plot([start["x"], end["x"]], [start["y"], end["y"]],
                color="#8fa3bd" if dual else "#c0c4cc",
                linewidth=2.4 if dual else 1.2, zorder=2,
                solid_capstyle="round")

    # 存储区货架：画成浅色小矩形阵列，示意不可通行的货架。
    for zone in net.get("zones", []):
        if zone["kind"] != "STORAGE":
            continue
        step_x, step_y = 3.2, 4.2
        rack_w, rack_d = 1.8, 2.6
        y = zone["y"] + 3
        while y + rack_d < zone["y"] + zone["height"] - 2:
            x = zone["x"] + 3
            while x + rack_w < zone["x"] + zone["width"] - 2:
                ax.add_patch(FancyBboxPatch((x, y), rack_w, rack_d,
                                            boxstyle="round,pad=0.05,rounding_size=0.15",
                                            facecolor="#d9d2e9", edgecolor="#b3a6cc",
                                            linewidth=0.6, zorder=1))
                x += step_x
            y += step_y

    for node in net["nodes"]:
        color = NODE_COLORS.get(node["type"], "#909399")
        radius = 0.85 if node["type"] != "JUNCTION" else 0.55
        ax.add_patch(Circle((node["x"], node["y"]), radius, facecolor=color,
                            edgecolor="white", linewidth=1.2, zorder=3))
        ax.text(node["x"], node["y"] - radius - 0.9, node["id"], fontsize=8.5,
                color="#303133", ha="center", va="top", zorder=4)

    ax.set_xlim(-1, 61)
    ax.set_ylim(38, -1)
    ax.set_aspect("equal")
    ax.set_xlabel("X（米）")
    ax.set_ylabel("Y（米）")
    ax.grid(color="#ebeef5", linewidth=0.6)
    ax.set_title("图 6-1　仓储车间路网（26 节点 / 31 路段 / 5 区域）",
                 fontsize=15, loc="left")

    handles = [plt.Line2D([], [], marker="o", linestyle="", markersize=7,
                          color=color, label=label)
               for label, color in (("拣货位", NODE_COLORS["PICKING"]),
                                     ("上货位", NODE_COLORS["LOADING"]),
                                     ("存储位", NODE_COLORS["STORAGE"]),
                                     ("充电位", NODE_COLORS["CHARGER"]),
                                     ("停放位", NODE_COLORS["PARKING"]),
                                     ("缓存位", NODE_COLORS["BUFFER"]),
                                     ("路口", NODE_COLORS["JUNCTION"]))]
    ax.legend(handles=handles, loc="upper center", bbox_to_anchor=(0.5, -0.10),
              ncol=7, frameon=False, fontsize=9)
    fig.tight_layout()
    fig.savefig(OUT, facecolor="white")
    print(f"写入 {OUT}")


if __name__ == "__main__":
    main()
