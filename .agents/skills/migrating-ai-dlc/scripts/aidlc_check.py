#!/usr/bin/env python3
"""AI-DLC 移行の適合性検査。

対象プロジェクトの docs/ と CLAUDE.md、apps/、各スキルの PROJECT.md を読み、
AI-DLC の運用に必要な成果物とガードレールが揃っているかを報告する。

ERROR: 移行が完了していない（必須の成果物が無い）
WARN : 運用の中で埋めればよい（欄の欠け、割合の不足、承認記録・索引の漏れ、成果物間の矛盾の一部）

本スクリプトはファイルとキーワードの有無を見る。成果物同士の矛盾や中身の妥当性の多くは
見ないので、SKILL.md の「スクリプトが見ない点を読んで確かめる」に従って人が読む。

使い方:
  python aidlc_check.py --project <project> [--docs docs] [--apps apps] [--claude-md CLAUDE.md] [--skills .claude/skills]
  --project を省略すると docs/<category>/ 直下を単一プロジェクトとして検査する。
終了コード: ERROR があれば 1、無ければ 0。
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REQUIRED_GUIDES = [
    "AI-DLC導入ガイド.md",
    "開発ガイド_AI-DLC版.md",
    "リリース・イテレーション計画ガイド_AI-DLC版.md",
    "ユースケース作成ガイド_AI-DLC版.md",
    "コーディングとテストガイド_AI-DLC版.md",
    "AI-DLC用語集.md",
]

UNIT_FIELDS = ["目的", "ストーリー", "NFR", "リスク", "測定基準", "推奨 Bolt", "エントロピー", "AI の仮定"]

TDD_KEYWORDS = ["三原則", "Three Laws", "失敗するテスト"]

FRONTEND_FILES = {"package.json"}
FRONTEND_EXTS = {".tsx", ".jsx", ".vue", ".svelte"}

NON_STORY_DOCS = {"index.md", "units.md", "risk_register.md"}


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warns: list[str] = []
        self.oks: list[str] = []

    def error(self, msg: str) -> None:
        self.errors.append(msg)

    def warn(self, msg: str) -> None:
        self.warns.append(msg)

    def ok(self, msg: str) -> None:
        self.oks.append(msg)

    def print(self) -> None:
        for m in self.oks:
            print(f"OK    {m}")
        for m in self.warns:
            print(f"WARN  {m}")
        for m in self.errors:
            print(f"ERROR {m}")
        print()
        print(f"ERROR {len(self.errors)} / WARN {len(self.warns)}")


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (FileNotFoundError, IsADirectoryError, UnicodeDecodeError):
        return ""


def project_dir(base: Path, project: str | None) -> Path:
    return base / project if project else base


def frontmatter(text: str) -> str:
    if not text.startswith("---"):
        return ""
    parts = text.split("---", 2)
    return parts[1] if len(parts) >= 3 else ""


def check_claude_md(rep: Report, claude_md: Path) -> None:
    text = read(claude_md)
    if not text:
        rep.error(f"{claude_md}: 見つからない")
        return
    if "AI-DLC を採用" in text:
        rep.ok(f"{claude_md}: AI-DLC 採用の節がある")
    else:
        rep.error(f"{claude_md}: 「AI-DLC を採用する」節が無い（移行ステップ 1）")
    if "開発ガイド_AI-DLC版.md" in text:
        rep.ok(f"{claude_md}: ペルソナが開発ガイド（AI-DLC 版）を参照している")
    else:
        rep.warn(f"{claude_md}: ペルソナの参照先が開発ガイド（AI-DLC 版）になっていない（移行ステップ 1）")


def check_guides(rep: Report, docs: Path) -> None:
    ref = docs / "reference"
    missing = [g for g in REQUIRED_GUIDES if not (ref / g).exists()]
    if missing:
        rep.error(f"{ref}: AI-DLC ガイドが不足: {', '.join(missing)}（npx boost --update を先に実行する）")
    else:
        rep.ok(f"{ref}: AI-DLC ガイド {len(REQUIRED_GUIDES)} 本が揃っている")


def check_migration_plan(rep: Report, docs: Path, project: str | None) -> None:
    path = project_dir(docs / "development", project) / "aidlc_migration_plan.md"
    if path.exists():
        rep.ok(f"{path}: 移行計画がある")
    else:
        rep.warn(f"{path}: 移行計画が無い（--plan で作る）")


def check_risk_register(rep: Report, docs: Path, project: str | None) -> None:
    path = project_dir(docs / "requirements", project) / "risk_register.md"
    text = read(path)
    if not text:
        rep.error(f"{path}: 見つからない（移行ステップ 2）")
        return
    rep.ok(f"{path}: ある")
    if "In/Out" in text or "渡せる範囲" in text or "渡せない" in text:
        rep.ok(f"{path}: AI に渡せる範囲が書かれている")
    else:
        rep.error(f"{path}: AI に渡せる範囲（In/Out）が書かれていない（移行ステップ 2）")
    if "確認必須" in text:
        rep.ok(f"{path}: 確認必須の操作が書かれている")
    else:
        rep.error(f"{path}: 確認必須の操作が書かれていない（移行ステップ 2）")


def check_glossary(rep: Report, docs: Path, project: str | None) -> None:
    ddir = project_dir(docs / "design", project)
    candidates = (list(ddir.glob("*glossary*.md")) + list(ddir.glob("*用語集*.md"))) if ddir.exists() else []
    if candidates:
        rep.ok(f"{candidates[0]}: ドメイン用語集がある")
    else:
        rep.warn(f"{ddir}: ドメイン用語集（glossary.md）が無い（移行ステップ 3）")


def app_dirs(apps: Path, project: str | None) -> list[Path]:
    if not apps.exists():
        return []
    if project:
        return [d for d in sorted(apps.iterdir()) if d.is_dir() and d.name.startswith(project)]
    return [apps]


def has_frontend(dirs: list[Path]) -> bool:
    for d in dirs:
        for p in d.rglob("*"):
            if "node_modules" in p.parts:
                return True
            if p.is_file() and (p.name in FRONTEND_FILES or p.suffix in FRONTEND_EXTS):
                return True
    return False


def check_brownfield(rep: Report, docs: Path, dirs: list[Path], project: str | None) -> None:
    if not any(any(p.is_file() for p in d.rglob("*")) for d in dirs):
        rep.ok("apps: コード無し（グリーンフィールド）。静的・動的モデルは不要")
        return
    ddir = project_dir(docs / "design", project)
    static = (list(ddir.glob("*static*.md")) + list(ddir.glob("*静的モデル*.md"))) if ddir.exists() else []
    dynamic = (list(ddir.glob("*dynamic*.md")) + list(ddir.glob("*動的モデル*.md"))) if ddir.exists() else []
    if static and dynamic:
        rep.ok(f"{ddir}: 静的モデルと動的モデルがある")
    else:
        rep.warn(f"{ddir}: ブラウンフィールドだが静的・動的モデルが揃っていない（移行ステップ 4）")


def story_sections(text: str, id_pattern: re.Pattern) -> dict[str, str]:
    """見出しにストーリー ID を含む節を ID ごとに返す。"""
    sections: dict[str, str] = {}
    parts = re.split(r"^(#{1,4}\s+.*)$", text, flags=re.M)
    # parts = [前置き, 見出し1, 本文1, 見出し2, 本文2, ...]
    for i in range(1, len(parts) - 1, 2):
        heading, body = parts[i], parts[i + 1]
        m = id_pattern.search(heading)
        if m:
            sections[m.group(0)] = sections.get(m.group(0), "") + body
    return sections


def check_stories(rep: Report, docs: Path, project: str | None, id_pattern: re.Pattern) -> None:
    rdir = project_dir(docs / "requirements", project)
    if not rdir.exists():
        rep.warn(f"{rdir}: requirements ディレクトリが無い")
        return
    files = [p for p in rdir.glob("*.md") if p.name not in NON_STORY_DOCS]
    all_ids: set[str] = set()
    sections: dict[str, str] = {}
    for p in files:
        t = read(p)
        all_ids.update(id_pattern.findall(t))
        for k, v in story_sections(t, id_pattern).items():
            sections[k] = sections.get(k, "") + v
    if not all_ids:
        rep.warn(f"{rdir}: ストーリー ID（{id_pattern.pattern}）が見つからない。--story-id で ID の形式を指定する")
        return
    with_ac = sorted(i for i, s in sections.items() if "受入条件" in s or "受け入れ条件" in s)
    with_gwt = sorted(i for i in with_ac if re.search(r"Given.*When.*Then", sections[i], flags=re.S))
    total = len(all_ids)
    msg = f"{rdir}: ストーリー {total} 本のうち受入条件あり {len(with_ac)} 本、Given/When/Then {len(with_gwt)} 本"
    if len(with_gwt) == total:
        rep.ok(msg)
    else:
        rep.warn(msg + "（移行ステップ 5）")


def check_units(rep: Report, docs: Path, project: str | None, id_pattern: re.Pattern) -> set[str]:
    """units.md を検査し、未配置のストーリー ID を返す。"""
    units = project_dir(docs / "requirements", project) / "units.md"
    text = read(units)
    if not text:
        rep.error(f"{units}: 見つからない（移行ステップ 6）")
        return set()
    rep.ok(f"{units}: ある")
    sections = re.split(r"^###\s+", text, flags=re.M)[1:]
    unit_sections = [s for s in sections if re.match(r"(U-\d+|Unit)", s)]
    if not unit_sections:
        rep.warn(f"{units}: Unit の見出し（### U-01：…）が見つからない")
    for s in unit_sections:
        name = s.splitlines()[0].strip()
        lacking = [f for f in UNIT_FIELDS if f not in s]
        if lacking:
            rep.warn(f"{units}: {name} に欄が欠けている: {', '.join(lacking)}（移行ステップ 6）")
    if "依存 DAG" in text or "依存DAG" in text:
        rep.ok(f"{units}: 依存 DAG がある")
    else:
        rep.warn(f"{units}: 依存 DAG が無い（移行ステップ 6）")
    if "ストーリーマップ" in text:
        rep.ok(f"{units}: ストーリーマップがある")
    else:
        rep.warn(f"{units}: ストーリーマップが無い（移行ステップ 6）")
    unplaced: set[str] = set()
    for line in text.splitlines():
        if "未配置" in line:
            unplaced.update(id_pattern.findall(line))
    return unplaced


def check_release_plan(rep: Report, docs: Path, project: str | None, unplaced: set[str]) -> None:
    ddir = project_dir(docs / "development", project)
    path = ddir / "release_plan.md"
    text = read(path)
    if not text:
        rep.warn(f"{path}: 見つからない")
        return
    if "エントロピー" in text:
        rep.ok(f"{path}: エントロピー評価がある")
    else:
        rep.warn(f"{path}: エントロピー評価が無い（移行ステップ 7）")
    if "テスト戦略" in text:
        rep.ok(f"{path}: テスト戦略の選択がある")
    else:
        rep.warn(f"{path}: テスト戦略の選択が無い（移行ステップ 7）")
    scheduled = sorted(i for i in unplaced if re.search(re.escape(i) + r"(?!\d)", text))
    if scheduled:
        rep.warn(f"{path}: units.md で未配置のストーリーが計画に割り当てられている: {', '.join(scheduled)}（移行ステップ 6・7）")


def check_bolt_plans(rep: Report, docs: Path, project: str | None) -> None:
    ddir = project_dir(docs / "development", project)
    plans = sorted(ddir.glob("bolt_plan-*.md")) if ddir.exists() else []
    if not plans:
        rep.warn(f"{ddir}: bolt_plan-N.md が無い（移行ステップ 8。進行中のイテレーションが無ければ不要）")
        return
    for p in plans:
        t = read(p)
        lacking = [label for key, label in (("仮説", "確認したい仮説"), ("ゲート", "ゲート密度")) if key not in t]
        if lacking:
            rep.warn(f"{p}: 欠けている: {', '.join(lacking)}（移行ステップ 8）")
        else:
            rep.ok(f"{p}: 仮説とゲート密度がある")
        n = p.stem.split("-")[-1]
        old = ddir / f"iteration_plan-{n}.md"
        if old.exists() and "deprecated" not in frontmatter(read(old)):
            rep.warn(f"{old}: bolt_plan-{n}.md に読み替えたが deprecated になっていない（移行ステップ 8）")
    if not any("ウォーキングスケルトン" in read(p) for p in plans):
        rep.warn(f"{ddir}: ウォーキングスケルトンを明記した Bolt 計画が無い（移行ステップ 8）")


def migration_artifacts(docs: Path, project: str | None) -> list[Path]:
    req = project_dir(docs / "requirements", project)
    des = project_dir(docs / "design", project)
    dev = project_dir(docs / "development", project)
    paths = [req / "units.md", req / "risk_register.md", dev / "aidlc_migration_plan.md"]
    if des.exists():
        paths += list(des.glob("*glossary*.md")) + list(des.glob("*static*.md")) + list(des.glob("*dynamic*.md"))
    if dev.exists():
        paths += sorted(dev.glob("bolt_plan-*.md"))
    return [p for p in paths if p.exists()]


def check_verified_and_index(rep: Report, docs: Path, project: str | None) -> None:
    artifacts = migration_artifacts(docs, project)
    unverified = [p.name for p in artifacts if "verified" not in frontmatter(read(p))]
    if not artifacts:
        return
    if unverified:
        rep.warn(f"移行成果物に人の承認記録（verified）が無い: {', '.join(unverified)}（apply-okf verify）")
    else:
        rep.ok(f"移行成果物 {len(artifacts)} 件すべてに verified がある")
    unindexed = []
    for p in artifacts:
        index = p.parent / "index.md"
        if index.exists() and p.name not in read(index):
            unindexed.append(f"{p.parent.name}/{p.name}")
    if unindexed:
        rep.warn(f"index.md に登録されていない移行成果物: {', '.join(unindexed)}（移行ステップ 10）")
    else:
        rep.ok("移行成果物はすべて index.md に登録されている")


def check_guardrails(rep: Report, skills: Path, frontend: bool) -> None:
    targets = ["developing-backend"] + (["developing-frontend"] if frontend else [])
    if not frontend:
        rep.ok("apps: フロントエンドが見つからないため developing-frontend は検査対象外")
    for s in targets:
        path = skills / s / "PROJECT.md"
        text = read(path)
        if not text:
            rep.warn(f"{path}: 無い（移行ステップ 9）")
            continue
        if any(k in text for k in TDD_KEYWORDS):
            rep.ok(f"{path}: TDD の三原則が書かれている")
        else:
            rep.warn(f"{path}: TDD の三原則が書かれていない（移行ステップ 9）")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="AI-DLC 移行の適合性検査")
    ap.add_argument("--project", default=None, help="プロジェクト識別子（docs/<category>/<project>/ のサブディレクトリ名）")
    ap.add_argument("--docs", default="docs", help="ドキュメントバンドルのルート（既定 docs）")
    ap.add_argument("--apps", default="apps", help="アプリケーションのルート（既定 apps）")
    ap.add_argument("--claude-md", default="CLAUDE.md", help="CLAUDE.md のパス")
    ap.add_argument("--skills", default=".claude/skills", help="スキルディレクトリ")
    ap.add_argument("--story-id", default=r"US-\d+", help="ストーリー ID の正規表現（既定 US-\\d+）")
    args = ap.parse_args()

    docs = Path(args.docs)
    id_pattern = re.compile(args.story_id)
    dirs = app_dirs(Path(args.apps), args.project)
    rep = Report()
    check_claude_md(rep, Path(args.claude_md))
    check_guides(rep, docs)
    check_migration_plan(rep, docs, args.project)
    check_risk_register(rep, docs, args.project)
    check_glossary(rep, docs, args.project)
    check_brownfield(rep, docs, dirs, args.project)
    check_stories(rep, docs, args.project, id_pattern)
    unplaced = check_units(rep, docs, args.project, id_pattern)
    check_release_plan(rep, docs, args.project, unplaced)
    check_bolt_plans(rep, docs, args.project)
    check_verified_and_index(rep, docs, args.project)
    check_guardrails(rep, Path(args.skills), has_frontend(dirs))
    rep.print()
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
