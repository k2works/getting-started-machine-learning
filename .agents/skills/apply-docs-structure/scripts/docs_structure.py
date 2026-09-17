#!/usr/bin/env python3
"""ドキュメント構成ガイド（単一企業・統合戦略・複数プロジェクト）の適用スクリプト。

サブコマンド:
  add-project <project>  apps/<project>/ とプロジェクト別カテゴリの <project>/index.md を一括作成
  check                  docs/ と apps/ の構成がガイドに適合しているか検証

規約の正: docs/reference/ドキュメント構成ガイド.md
"""
import argparse
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

PER_PROJECT_CATEGORIES = {
    "requirements": "要件",
    "design": "設計",
    "development": "開発",
    "operation": "運用",
    "adr": "ADR",
    "journal": "ジャーナル",
    "review": "レビュー",
}
SINGLE_CATEGORIES = ["strategy", "reference", "template", "article", "assets"]
PROJECT_ID_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

PROJECT_INDEX_TEMPLATE = """# {project} — {label}

{project} プロジェクトの{label}ドキュメントです。

## ドキュメント一覧

（未作成）

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
"""


def project_dirs(category_dir: Path):
    """カテゴリ直下のサブディレクトリ（= プロジェクトディレクトリ扱い）を返す。"""
    if not category_dir.is_dir():
        return []
    return sorted(p for p in category_dir.iterdir() if p.is_dir())


def cmd_add_project(args) -> int:
    docs = Path(args.docs)
    apps = Path(args.apps)
    project = args.project

    if not PROJECT_ID_RE.match(project):
        print(f"ERROR: プロジェクト識別子 '{project}' がケバブケース（英小文字とハイフン）ではない")
        return 1
    if not docs.is_dir():
        print(f"ERROR: docs ディレクトリが見つからない: {docs}")
        return 1

    created, skipped = [], []

    if "apps" not in args.skip:
        app_dir = apps / project
        if app_dir.exists():
            skipped.append(str(app_dir))
        else:
            app_dir.mkdir(parents=True)
            (app_dir / ".gitkeep").write_text("", encoding="utf-8")
            created.append(str(app_dir))

    for category, label in PER_PROJECT_CATEGORIES.items():
        if category in args.skip:
            continue
        cat_dir = docs / category
        proj_dir = cat_dir / project
        proj_index = proj_dir / "index.md"
        if proj_index.exists():
            skipped.append(str(proj_index))
        else:
            proj_dir.mkdir(parents=True, exist_ok=True)
            proj_index.write_text(
                PROJECT_INDEX_TEMPLATE.format(project=project, label=label),
                encoding="utf-8",
            )
            created.append(str(proj_index))

        # カテゴリ索引にエントリを追加（末尾追記。セクション位置の調整は呼び出し側で行う）
        cat_index = cat_dir / "index.md"
        entry = f"- [{project}](./{project}/index.md)"
        if cat_index.exists():
            body = cat_index.read_text(encoding="utf-8")
            if entry not in body:
                if not body.endswith("\n"):
                    body += "\n"
                cat_index.write_text(body + entry + "\n", encoding="utf-8")
                created.append(f"{cat_index}（エントリ追加）")
        else:
            cat_index.write_text(
                f"# {label}\n\n## プロジェクト一覧\n\n{entry}\n", encoding="utf-8"
            )
            created.append(str(cat_index))

    for path in created:
        print(f"作成: {path}")
    for path in skipped:
        print(f"既存のためスキップ: {path}")
    print("次: 各カテゴリ index.md のエントリ位置を確認し、check で検証する")
    return 0


def cmd_check(args) -> int:
    docs = Path(args.docs)
    apps = Path(args.apps)
    errors, warns = [], []

    if not docs.is_dir():
        print(f"ERROR: docs ディレクトリが見つからない: {docs}")
        return 1

    apps_projects = (
        {p.name for p in apps.iterdir() if p.is_dir()} if apps.is_dir() else None
    )
    docs_projects = set()

    for category in PER_PROJECT_CATEGORIES:
        cat_dir = docs / category
        if not cat_dir.is_dir():
            continue
        # カテゴリ直下は索引 index.md のみ。実ドキュメントの直置きは規約違反
        for f in sorted(cat_dir.glob("*.md")):
            if f.name != "index.md":
                warns.append(
                    f"{category}/{f.name}: カテゴリ直下に直置き（{category}/<project>/ 配下に置く）"
                )
        for proj_dir in project_dirs(cat_dir):
            name = proj_dir.name
            docs_projects.add(name)
            if not PROJECT_ID_RE.match(name):
                errors.append(
                    f"{category}/{name}/: 識別子がケバブケース（英小文字とハイフン）ではない"
                )
            if not (proj_dir / "index.md").exists():
                warns.append(f"{category}/{name}/: index.md が無い")
            if apps_projects is not None and name not in apps_projects:
                warns.append(f"{category}/{name}/: 対応する apps/{name}/ が無い")

    # strategy は単一管理。プロジェクト名のサブディレクトリは分割の兆候
    strategy = docs / "strategy"
    if strategy.is_dir():
        for sub in project_dirs(strategy):
            if sub.name in docs_projects or (
                apps_projects is not None and sub.name in apps_projects
            ):
                warns.append(
                    f"strategy/{sub.name}/: strategy は統合戦略として単一管理（プロジェクト別に分割しない）"
                )

    # apps 側にだけ存在するプロジェクト
    if apps_projects:
        for name in sorted(apps_projects - docs_projects):
            warns.append(
                f"apps/{name}/: docs 側にプロジェクトディレクトリが無い（add-project で作成する）"
            )

    for msg in errors:
        print(f"ERROR {msg}")
    for msg in warns:
        print(f"WARN  {msg}")
    print(f"\nERROR {len(errors)} / WARN {len(warns)}")
    if args.check and errors:
        return 1
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    p_add = sub.add_parser("add-project", help="新プロジェクトのディレクトリ一式を作成")
    p_add.add_argument("project", help="プロジェクト識別子（ケバブケース）")
    p_add.add_argument("--docs", default="docs", help="docs ディレクトリ（既定: docs）")
    p_add.add_argument("--apps", default="apps", help="apps ディレクトリ（既定: apps）")
    p_add.add_argument(
        "--skip",
        nargs="*",
        default=[],
        help="スキップする対象（apps または カテゴリ名）",
    )
    p_add.set_defaults(func=cmd_add_project)

    p_check = sub.add_parser("check", help="構成の適合性を検証")
    p_check.add_argument("--docs", default="docs", help="docs ディレクトリ（既定: docs）")
    p_check.add_argument("--apps", default="apps", help="apps ディレクトリ（既定: apps）")
    p_check.add_argument(
        "--check", action="store_true", help="ERROR があれば exit 1 で終了"
    )
    p_check.set_defaults(func=cmd_check)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
