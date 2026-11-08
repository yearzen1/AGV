"""生成论文定稿 docx：先做样式模板，再由 pandoc 转换，章节间插入分页符。"""
from pathlib import Path
import subprocess
import sys

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt

ROOT = Path(__file__).resolve().parent.parent
THESIS = ROOT / "paper" / "thesis"
MD = THESIS / "论文定稿.md"
TEMP = THESIS / ".论文定稿-排版.md"
REF = THESIS / "reference.docx"
OUT = THESIS / "论文定稿.docx"

PAGE_BREAK = '```{=openxml}\n<w:p><w:r><w:br w:type="page"/></w:r></w:p>\n```\n\n'


def set_east_asian(style, font_name):
    style.font.name = font_name
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = rpr.makeelement(qn("w:rFonts"), {})
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), font_name)
    rfonts.set(qn("w:ascii"), font_name)
    rfonts.set(qn("w:hAnsi"), font_name)


def build_reference_doc():
    doc = Document()

    normal = doc.styles["Normal"]
    set_east_asian(normal, "宋体")
    normal.font.size = Pt(12)
    normal.paragraph_format.line_spacing = 1.5
    normal.paragraph_format.space_after = Pt(0)

    title = doc.styles["Title"]
    set_east_asian(title, "黑体")
    title.font.size = Pt(22)
    title.font.bold = True
    title.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER

    h1 = doc.styles["Heading 1"]
    set_east_asian(h1, "黑体")
    h1.font.size = Pt(16)
    h1.font.bold = True
    h1.paragraph_format.space_before = Pt(18)
    h1.paragraph_format.space_after = Pt(12)

    h2 = doc.styles["Heading 2"]
    set_east_asian(h2, "黑体")
    h2.font.size = Pt(14)
    h2.font.bold = True
    h2.paragraph_format.space_before = Pt(12)
    h2.paragraph_format.space_after = Pt(6)

    h3 = doc.styles["Heading 3"]
    set_east_asian(h3, "黑体")
    h3.font.size = Pt(12)
    h3.font.bold = True
    h3.paragraph_format.space_before = Pt(8)
    h3.paragraph_format.space_after = Pt(4)

    if "Image Caption" in [s.name for s in doc.styles]:
        cap = doc.styles["Image Caption"]
        set_east_asian(cap, "宋体")
        cap.font.size = Pt(10.5)

    doc.save(REF)


def build_paginated_markdown():
    text = MD.read_text(encoding="utf-8")
    lines = text.splitlines()
    out = []
    first_heading = True
    for line in lines:
        if line.startswith("# ") and not line.startswith("## "):
            if first_heading:
                first_heading = False
            else:
                out.append(PAGE_BREAK.rstrip("\n"))
        out.append(line)
    TEMP.write_text("\n".join(out) + "\n", encoding="utf-8")


def main() -> int:
    if not MD.exists():
        print("缺少 论文定稿.md，请先运行 scripts/build-thesis.py")
        return 1

    build_reference_doc()
    build_paginated_markdown()

    cmd = [
        "pandoc",
        str(TEMP),
        "-o",
        str(OUT),
        "--from",
        "markdown+implicit_figures+tex_math_dollars",
        "--reference-doc",
        str(REF),
        "--resource-path",
        str(THESIS),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr)
        return result.returncode

    TEMP.unlink(missing_ok=True)
    size = OUT.stat().st_size
    print(f"生成 {OUT.name}，{size / 1024:.0f} KB")

    check = Document(str(OUT))
    headings = [p.text for p in check.paragraphs if p.style.name.startswith("Heading")]
    print(f"段落 {len(check.paragraphs)}，标题 {len(headings)}，表格 {len(check.tables)}")
    print("前 6 个标题：")
    for h in headings[:6]:
        print("  -", h)
    return 0


if __name__ == "__main__":
    sys.exit(main())
