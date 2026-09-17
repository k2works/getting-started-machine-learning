"""notebooks/ 配下の Notebook を実行・出力削除・検査する。

使い方:
    uv run python tools/notebooks.py execute  # すべて実行する（出力は保存しない）
    uv run python tools/notebooks.py strip    # 出力セルを消す
    uv run python tools/notebooks.py verify   # 出力セルが残っていたら失敗する
"""

import os
import subprocess
import sys
from pathlib import Path

NOTEBOOK_DIR = Path(__file__).resolve().parents[1] / "notebooks"


def notebooks() -> list[Path]:
    return sorted(NOTEBOOK_DIR.glob("*.ipynb"))


def run(args: list[str], cwd: Path | None = None) -> int:
    env = {**os.environ, "MPLBACKEND": "Agg"}
    return subprocess.run([sys.executable, "-m", *args], cwd=cwd, env=env).returncode


def execute() -> int:
    for notebook in notebooks():
        print(f"実行: {notebook.name}")
        code = run(
            ["nbconvert", "--to", "notebook", "--execute", "--stdout", notebook.name],
            cwd=NOTEBOOK_DIR,
        )
        if code != 0:
            return code
    return 0


def strip() -> int:
    paths = [str(p) for p in notebooks()]
    return run(["nbstripout", *paths]) if paths else 0


def verify() -> int:
    paths = [str(p) for p in notebooks()]
    return run(["nbstripout", "--verify", *paths]) if paths else 0


COMMANDS = {"execute": execute, "strip": strip, "verify": verify}

if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in COMMANDS:
        print(__doc__)
        sys.exit(2)
    sys.exit(COMMANDS[sys.argv[1]]())
