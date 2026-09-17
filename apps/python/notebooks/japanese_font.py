"""Notebook のグラフで日本語を表示するためのフォント設定。"""

import matplotlib.pyplot as plt
from matplotlib import font_manager

CANDIDATES = ["Yu Gothic", "Hiragino Sans", "Noto Sans CJK JP", "IPAexGothic"]


def use_japanese_font() -> str | None:
    """インストール済みの日本語フォントを探して設定し、そのフォント名を返す。"""
    available = {font.name for font in font_manager.fontManager.ttflist}
    for name in CANDIDATES:
        if name in available:
            plt.rcParams["font.family"] = name
            return name
    return None
