#!/usr/bin/env python3
"""把思源宋体裁成只含本工程用得到的字，放进 res/font/ 当标题字体。

为什么要裁：完整中文字体 17-24MB，而整个 release 包才 2MB。不裁的话包会胖九倍。
裁完通常 200-400KB。

为什么字表取「全工程所有字符串字面量里的汉字」，而不是只取标题的：
标题里会出现跑起来才知道的内容（词书名之类）。只按标题那几行裁，
换个词书名就可能碰上没裁进去的字，手机上显示成空白——而这种缺字
在电脑上跑单测、看截图都发现不了，只有用户装上才看得见。
宁可多裁几百个字（大不了多几十 KB），也不能让缺字漏到手机上。

用法（在 android/ 目录下）：
    python tools/subset_title_font.py

改了界面文案之后要重跑一次。忘了重跑的话，TitleFontCoverageTest 这条单测会红。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    from fontTools import subset
    from fontTools.ttLib import TTFont
except ImportError:
    sys.exit("缺 fonttools。先跑：python -m pip install fonttools")

ANDROID_DIR = Path(__file__).resolve().parent.parent
SOURCE_FONT = Path(r"C:\Windows\Fonts\NotoSerifSC-VF.ttf")
OUTPUT_FONT = ANDROID_DIR / "app/src/main/res/font/title_serif.ttf"
KOTLIN_ROOT = ANDROID_DIR / "app/src/main/java"

# Kotlin 双引号字符串。够用就行——目的是把字捞全，不是写一个 Kotlin 解析器，
# 多捞进来一些非文案的字（比如日志、键名）只是让字体大几 KB，没有坏处。
STRING_LITERAL = re.compile(r'"([^"\\\n]*(?:\\.[^"\\\n]*)*)"')

# 数字、拉丁字母、常见标点：界面上到处都是，无条件带上
ALWAYS_INCLUDE = set(
    "0123456789"
    "abcdefghijklmnopqrstuvwxyz"
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    " ．·%／/：:，,。.、；;！!？?（）()「」【】—-–~〜"
    "＋+－−×÷=＝<>《》\"'’‘“”…　"
)


def collect_chars() -> set[str]:
    """把工程里所有 Kotlin 字符串字面量中的字符捞出来。"""
    chars: set[str] = set(ALWAYS_INCLUDE)
    files = 0
    for kt in KOTLIN_ROOT.rglob("*.kt"):
        files += 1
        text = kt.read_text(encoding="utf-8", errors="replace")
        for literal in STRING_LITERAL.findall(text):
            chars.update(literal)
    if files == 0:
        sys.exit(f"没在 {KOTLIN_ROOT} 下找到任何 .kt，路径是不是不对？")
    print(f"扫了 {files} 个 Kotlin 文件")
    return chars


def main() -> None:
    if not SOURCE_FONT.is_file():
        sys.exit(
            f"找不到源字体 {SOURCE_FONT}。\n"
            "这是 Windows 自带的思源宋体（SIL OFL 授权，可以随应用分发）。\n"
            "系统里没有的话，从 https://github.com/notofonts/noto-cjk 下载 NotoSerifSC。"
        )

    chars = collect_chars()
    # 只留字体里真有的字形，免得 subset 因为找不到某个字符报错
    with TTFont(SOURCE_FONT, lazy=True) as probe:
        available = {chr(c) for table in probe["cmap"].tables for c in table.cmap}
    wanted = sorted(c for c in chars if c in available)
    dropped = sorted(c for c in chars if c not in available and not c.isspace())
    if dropped:
        print(f"⚠️ 源字体里没有这 {len(dropped)} 个字符，跳过：{''.join(dropped[:40])}")

    print(f"要保留 {len(wanted)} 个字形")

    OUTPUT_FONT.parent.mkdir(parents=True, exist_ok=True)

    # 先把可变字体钉成固定字重再裁。标题只用一个字重，留着整根 wght 轴既占体积，
    # 又要靠运行时去插值——固定下来，手机上渲染出的粗细就和这里看到的一样。
    from fontTools.varLib import instancer as varlib_instancer

    with TTFont(SOURCE_FONT) as var_font:
        static_font = varlib_instancer.instantiateVariableFont(
            var_font, {"wght": 500}, updateFontNames=False
        )
        tmp_static = OUTPUT_FONT.parent / "_title_static.tmp.ttf"
        static_font.save(tmp_static)

    try:
        subset.main([
            str(tmp_static),
            f"--text={''.join(wanted)}",
            f"--output-file={OUTPUT_FONT}",
            "--layout-features=*",
            "--drop-tables+=DSIG",
            "--name-IDs=*",
            "--recalc-bounds",
        ])
    finally:
        tmp_static.unlink(missing_ok=True)

    size_kb = OUTPUT_FONT.stat().st_size / 1024
    print(f"✅ 写出 {OUTPUT_FONT.relative_to(ANDROID_DIR)}  {size_kb:.0f} KB")
    if size_kb > 1500:
        print("⚠️ 比预期大不少，检查一下是不是把整本字体裁进来了")


if __name__ == "__main__":
    main()
