#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path


START_RE = re.compile(r"^@(startuml|startmindmap)\b.*$", re.MULTILINE)
END_RE = re.compile(r"^@(enduml|endmindmap)\b.*$", re.MULTILINE)


def run(cmd: list[str], cwd: Path) -> None:
    print("+", " ".join(cmd), f"(cwd={cwd})")
    subprocess.run(cmd, cwd=cwd, check=True)


def extract(slide_dir: Path) -> None:
    src = slide_dir / "スライド.md"
    dst = slide_dir / "スライド.images.md"
    img_dir = slide_dir / "images"
    if not src.exists():
        raise SystemExit(f"missing canonical markdown: {src}")
    img_dir.mkdir(exist_ok=True)
    text = src.read_text(encoding="utf-8")
    out: list[str] = []
    cursor = 0
    index = 0
    fence_open_re = re.compile(r"```[a-zA-Z]*\s*\n")
    fence_close_re = re.compile(r"\n```")
    while True:
        start = START_RE.search(text, cursor)
        if not start:
            out.append(text[cursor:])
            break
        end = END_RE.search(text, start.end())
        if not end:
            raise SystemExit(f"missing @enduml/@endmindmap after offset {start.start()}")
        index += 1
        block = text[start.start() : end.end()]
        (img_dir / f"diagram-{index:02d}.puml").write_text(block + "\n", encoding="utf-8")
        before = text[cursor : start.start()]
        m_open = None
        for match in fence_open_re.finditer(before):
            m_open = match
        m_close = fence_close_re.search(text, end.end())
        if m_open and m_close:
            replace_start = cursor + m_open.start()
            replace_end = m_close.end()
            out.append(text[cursor:replace_start])
            out.append(f"![](images/diagram-{index:02d}.png)")
            cursor = replace_end
        else:
            out.append(text[cursor : start.start()])
            out.append(f"![](images/diagram-{index:02d}.png)")
            cursor = end.end()
    dst.write_text("".join(out), encoding="utf-8")
    print(f"Extracted {index} diagrams -> {img_dir}")
    print(f"Wrote {dst}")


def render_plantuml(slide_dir: Path) -> None:
    puml_files = sorted((slide_dir / "images").glob("*.puml"))
    if puml_files:
        run(["plantuml", "-tpng", *[str(p.relative_to(slide_dir)) for p in puml_files]], slide_dir)


def make_ai(slide_dir: Path) -> None:
    src = slide_dir / "スライド.images.md"
    dst = slide_dir / "スライド.images.ai.md"
    if not src.exists():
        extract(slide_dir)
    text = src.read_text(encoding="utf-8")
    text = re.sub(r"images/diagram-(\d\d)\.png", r"images-ai/diagram-ai-\1.png", text)
    text = text.replace(
        "section img {\n    max-height: 420px;\n  }",
        "section img {\n    display: block;\n    max-width: 100%;\n    max-height: 420px;\n    height: auto;\n    object-fit: contain;\n    margin: 0 auto;\n  }",
    )
    dst.write_text(text, encoding="utf-8")
    print(f"Wrote {dst}")


MANGA_REPLACEMENTS = {
    "01": ("manga-01-overview.png", "diagram wide", "概要を描いた漫画"),
    "02": ("manga-02-side-effect.png", "diagram", "副作用を描いた漫画"),
    "03": ("manga-03-hard-to-test.png", "diagram", "テスト困難性を描いた漫画"),
    "04": ("manga-04-core-shell.png", "diagram", "純粋なコアと不純なシェルを描いた漫画"),
    "05": ("manga-05-io-plan.png", "diagram", "IO の実行計画を描いた漫画"),
    "06": ("manga-06-execution-order.png", "diagram wide", "実行順序を描いた漫画"),
    "07": ("manga-07-resource-safety.png", "diagram", "Resource の安全性を描いた漫画"),
}


def manga_css(text: str) -> str:
    text = text.replace("font-size: 0.82em;", "font-size: 0.85em;")
    text = text.replace("font-size: 0.68em;", "font-size: 0.7em;")
    needle = "section img {\n    max-height: 420px;\n  }"
    repl = """section img {
    display: block;
    max-width: 100%;
    height: auto;
  }
  section .diagram {
    margin: 10px auto 14px;
    text-align: center;
  }
  section .diagram img {
    width: 100%;
    max-width: 980px;
    max-height: 420px;
    object-fit: contain;
    margin: 0 auto;
  }
  section .diagram.wide img {
    max-width: 1040px;
    max-height: 470px;
  }
  section .diagram.compact img {
    max-width: 760px;
    max-height: 320px;
  }
  section .columns .diagram {
    margin-top: 0;
  }
  section .columns .diagram img {
    max-height: 330px;
  }"""
    return text.replace(needle, repl)


def make_manga(slide_dir: Path) -> None:
    src = slide_dir / "スライド.images.ai.md"
    dst = slide_dir / "スライド.images.ai.manga.md"
    if not src.exists():
        make_ai(slide_dir)
    text = manga_css(src.read_text(encoding="utf-8"))
    for num, (filename, klass, alt) in MANGA_REPLACEMENTS.items():
        image = slide_dir / "images-manga" / filename
        if not image.exists():
            continue
        block = f'<figure class="{klass}">\n  <img src="images-manga/{filename}" alt="{alt}">\n</figure>'
        text = text.replace(f"![](images-ai/diagram-ai-{num}.png)", block)
    dst.write_text(text, encoding="utf-8")
    print(f"Wrote {dst}")


def marp(slide_dir: Path) -> None:
    targets = [
        ("スライド.images.md", "スライド.html", ["--html"]),
        ("スライド.images.md", "スライド.pptx", ["--pptx", "--allow-local-files"]),
        ("スライド.images.ai.md", "スライド.images.ai.html", ["--html"]),
        ("スライド.images.ai.md", "スライド.images.ai.pptx", ["--pptx", "--allow-local-files"]),
        ("スライド.images.ai.manga.md", "スライド.images.ai.manga.html", ["--html"]),
        ("スライド.images.ai.manga.md", "スライド.images.ai.manga.pptx", ["--pptx", "--allow-local-files"]),
    ]
    for src, dst, flags in targets:
        if (slide_dir / src).exists():
            run(["npx", "--yes", "@marp-team/marp-cli", src, "-o", dst, *flags], slide_dir)


def count(slide_dir: Path) -> None:
    for pptx in sorted(slide_dir.glob("*.pptx")):
        proc = subprocess.run(
            ["unzip", "-Z1", str(pptx), "ppt/slides/slide*.xml"],
            check=True,
            text=True,
            capture_output=True,
        )
        print(f"{pptx.name}: {len(proc.stdout.splitlines())}")


def split_summary(slide_dir: Path, image_rel: str, parts: int) -> None:
    src = slide_dir / image_rel
    if not src.exists():
        raise SystemExit(f"missing image: {src}")
    try:
        proc = subprocess.run(
            ["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(src)],
            check=True,
            text=True,
            capture_output=True,
        )
    except FileNotFoundError as exc:
        raise SystemExit("split-summary requires macOS sips") from exc
    nums = [int(x) for x in re.findall(r"pixel(?:Width|Height):\s+(\d+)", proc.stdout)]
    if len(nums) < 2:
        raise SystemExit(f"could not read image size from sips output: {proc.stdout}")
    width, height = nums[0], nums[1]
    stem = src.stem
    chunk = height // parts
    for idx in range(parts):
        offset = idx * chunk if idx else 1
        crop_h = chunk if idx < parts - 1 else height - idx * chunk - 1
        out = src.with_name(f"{stem}-{idx + 1:02d}.png")
        shutil.copy2(src, out)
        run(["sips", "-c", str(crop_h), str(width), "--cropOffset", str(offset), "0", str(out)], slide_dir)
        print(f"Wrote {out.relative_to(slide_dir)}")


def build(slide_dir: Path) -> None:
    extract(slide_dir)
    render_plantuml(slide_dir)
    make_ai(slide_dir)
    make_manga(slide_dir)
    marp(slide_dir)
    count(slide_dir)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["extract", "make-ai", "make-manga", "marp", "count", "build", "split-summary"])
    parser.add_argument("slide_dir")
    parser.add_argument("image", nargs="?")
    parser.add_argument("--parts", type=int, default=5)
    args = parser.parse_args()
    slide_dir = Path(args.slide_dir).expanduser().resolve()
    if args.command == "extract":
        extract(slide_dir)
    elif args.command == "make-ai":
        make_ai(slide_dir)
    elif args.command == "make-manga":
        make_manga(slide_dir)
    elif args.command == "marp":
        marp(slide_dir)
    elif args.command == "count":
        count(slide_dir)
    elif args.command == "build":
        build(slide_dir)
    elif args.command == "split-summary":
        if not args.image:
            raise SystemExit("split-summary requires an image path relative to slide-dir")
        split_summary(slide_dir, args.image, args.parts)


if __name__ == "__main__":
    main()
