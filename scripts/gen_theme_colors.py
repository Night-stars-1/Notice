# /// script
# requires-python = ">=3.10"
# dependencies = ["material-color-utilities-python==0.1.5"]
# ///
"""生成 app/src/main/java/moe/notice/filter/ui/theme/ThemePresets.kt。

用法：uv run scripts/gen_theme_colors.py

每个预设都是由一个种子色通过 material-color-utilities（与 Material Theme Builder 背后相同的算法）
派生出的完整 Material 3 浅色 + 深色 ColorScheme。Surface container 角色遵循 2023 年的 M3 色调映射。
默认的 "blue" 预设不会被生成——它沿用 Theme.kt 中手工调校的配色方案，以保持应用默认外观不变。
"""
from __future__ import annotations

from pathlib import Path

from material_color_utilities_python import argbFromHex, hexFromArgb, themeFromSourceColor

PRESETS = [
    # id、中文名称、种子色
    ("green", "绿", "#2E7D32"),
    ("teal", "青", "#00897B"),
    ("purple", "紫", "#6750A4"),
    ("orange", "橙", "#E65100"),
    ("red", "红", "#B3261E"),
]

OUT = Path(__file__).resolve().parent.parent / "app/src/main/java/moe/notice/filter/ui/theme/ThemePresets.kt"

# role -> (调色板, 浅色色调, 深色色调)，依据 M3 基线色调映射。
ROLES = {
    "primary": ("primary", 40, 80), "onPrimary": ("primary", 100, 20),
    "primaryContainer": ("primary", 90, 30), "onPrimaryContainer": ("primary", 10, 90),
    "inversePrimary": ("primary", 80, 40),
    "secondary": ("secondary", 40, 80), "onSecondary": ("secondary", 100, 20),
    "secondaryContainer": ("secondary", 90, 30), "onSecondaryContainer": ("secondary", 10, 90),
    "tertiary": ("tertiary", 40, 80), "onTertiary": ("tertiary", 100, 20),
    "tertiaryContainer": ("tertiary", 90, 30), "onTertiaryContainer": ("tertiary", 10, 90),
    "error": ("error", 40, 80), "onError": ("error", 100, 20),
    "errorContainer": ("error", 90, 30), "onErrorContainer": ("error", 10, 90),
    "surface": ("neutral", 98, 6), "onSurface": ("neutral", 10, 90),
    "background": ("neutral", 98, 6), "onBackground": ("neutral", 10, 90),
    "surfaceDim": ("neutral", 87, 6), "surfaceBright": ("neutral", 98, 24),
    "surfaceContainerLowest": ("neutral", 100, 4), "surfaceContainerLow": ("neutral", 96, 10),
    "surfaceContainer": ("neutral", 94, 12), "surfaceContainerHigh": ("neutral", 92, 17),
    "surfaceContainerHighest": ("neutral", 90, 22),
    "surfaceVariant": ("neutralVariant", 90, 30), "onSurfaceVariant": ("neutralVariant", 30, 80),
    "outline": ("neutralVariant", 50, 60), "outlineVariant": ("neutralVariant", 80, 30),
    "inverseSurface": ("neutral", 20, 90), "inverseOnSurface": ("neutral", 95, 20),
    "scrim": ("neutral", 0, 0),
}


def kcolor(argb: int) -> str:
    return "Color(0xFF" + hexFromArgb(argb)[1:].upper() + ")"


def scheme_block(fn: str, palettes, dark: bool) -> str:
    lines = [f"    {fn}("]
    for role, (palette, lt, dt) in ROLES.items():
        lines.append(f"        {role} = {kcolor(palettes[palette].tone(dt if dark else lt))},")
    lines.append("    )")
    return "\n".join(lines)


def main() -> None:
    out = [
        "package moe.notice.filter.ui.theme",
        "",
        "import androidx.compose.material3.darkColorScheme",
        "import androidx.compose.material3.lightColorScheme",
        "import androidx.compose.ui.graphics.Color",
        "",
        "// 由 scripts/gen_theme_colors.py 生成——请勿手动编辑。",
        "",
        "internal val GeneratedThemePresets: List<ThemePreset> = listOf(",
    ]
    for pid, name, seed in PRESETS:
        palettes = themeFromSourceColor(argbFromHex(seed))["palettes"]
        out.append("    ThemePreset(")
        out.append(f'        id = "{pid}",')
        out.append(f'        name = "{name}",')
        out.append(f"        seed = {kcolor(argbFromHex(seed))},")
        out.append("        light =")
        out.append(scheme_block("lightColorScheme", palettes, False).replace("\n", "\n    ") + ",")
        out.append("        dark =")
        out.append(scheme_block("darkColorScheme", palettes, True).replace("\n", "\n    ") + ",")
        out.append("    ),")
    out.append(")")
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
