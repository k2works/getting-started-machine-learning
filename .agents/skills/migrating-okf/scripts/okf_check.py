#!/usr/bin/env python3
"""OKF バンドルの適合性検証とバージョンアップ。

標準ライブラリのみで動作する（PyYAML 不要）。

使い方:
  python okf_check.py --check <bundle_root>
  python okf_check.py --upgrade 0.2 <bundle_root> [--by <actor>] [--dry-run]

検証（--check）はガイド §11 の適合条件を ERROR、§5〜§7 の推奨事項を WARN として報告する。
アップグレード（--upgrade）はガイド §13 の破壊的変更を機械的に適用する。
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

RESERVED = {"index.md", "log.md"}
ACTOR_RE = re.compile(r"^([A-Za-z_][\w-]*:[^\s]+|[^\s/]+/[^\s]+)$")  # human:/process:/team: 等の <kind>:<id>、または <producer>/<version>
ISO_TZ_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$")
DATE_HEADING_RE = re.compile(r"^## (.+)$")
FOOTNOTE_REF_RE = re.compile(r"\[\^([^\]]+)\](?!:)")
LINK_RE = re.compile(r"(?<!\!)\[[^\]]*\]\(([^)\s]+)\)")
SUPPORTED_UPGRADES = {"0.2"}
FENCE_RE = re.compile(r"^[ \t]*(```|~~~)[^\n]*\n.*?^[ \t]*\1[ \t]*$", re.M | re.S)


def strip_fences(body: str) -> str:
    """コードフェンスとインラインコードを除去する。例示リンク・脚注を検査対象から外すため。"""
    body = FENCE_RE.sub("", body)
    return re.sub(r"`[^`\n]*`", "", body)  # インラインコードも除外


# --------------------------------------------------------------------------- #
# 最小 YAML パーサ（OKF フロントマターで使う範囲のみ）
# --------------------------------------------------------------------------- #

def _scalar(s: str):
    s = s.strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in "'\"":
        return s[1:-1]
    if s.startswith("[") and s.endswith("]"):
        inner = s[1:-1].strip()
        return [_scalar(x) for x in _split_top(inner)] if inner else []
    if s.startswith("{") and s.endswith("}"):
        return _flow_map(s[1:-1])
    return s


def _split_top(s: str) -> list[str]:
    out, depth, cur, quote = [], 0, "", None
    for ch in s:
        if quote:
            cur += ch
            if ch == quote:
                quote = None
            continue
        if ch in "'\"":
            quote = ch
        elif ch in "[{":
            depth += 1
        elif ch in "]}":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return out


def _flow_map(s: str) -> dict:
    d = {}
    for part in _split_top(s):
        if ":" in part:
            k, v = part.split(":", 1)
            d[k.strip()] = _scalar(v)
    return d


def parse_frontmatter(lines: list[str]) -> dict:
    """フロントマター行（区切り `---` を除く）を dict にする。"""
    data: dict = {}
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i]
        if not line.strip() or line.lstrip().startswith("#"):
            i += 1
            continue
        m = re.match(r"^([A-Za-z_][\w-]*):(.*)$", line)
        if not m:
            i += 1
            continue
        key, rest = m.group(1), m.group(2).strip()
        rest = re.sub(r"\s+#.*$", "", rest) if not rest.startswith(("'", '"')) else rest
        if rest:
            data[key] = _scalar(rest)
            i += 1
            continue
        # ブロック値（リストまたはマッピング）
        block: list[str] = []
        i += 1
        while i < n and (not lines[i].strip() or lines[i].startswith((" ", "\t"))):
            block.append(lines[i])
            i += 1
        block = [b for b in block if b.strip()]
        if not block:
            data[key] = None
        elif block[0].lstrip().startswith("- "):
            data[key] = _parse_block_list(block)
        else:
            data[key] = _parse_block_map(block)
    return data


def _dedent(block: list[str]) -> list[str]:
    indent = min(len(b) - len(b.lstrip()) for b in block)
    return [b[indent:] for b in block]


def _parse_block_list(block: list[str]) -> list:
    items: list = []
    block = _dedent(block)
    cur: list[str] = []
    for b in block:
        if b.startswith("- "):
            if cur:
                items.append(_list_item(cur))
            cur = [b[2:]]
        else:
            cur.append(b)
    if cur:
        items.append(_list_item(cur))
    return items


def _list_item(lines: list[str]):
    first = lines[0].strip()
    if len(lines) == 1 and not re.match(r"^[A-Za-z_][\w-]*:\s", first + " "):
        return _scalar(first)
    if first.startswith("{"):
        return _scalar(first)
    # 「- key: value」形式のマッピング。後続行のインデントを揃える
    rest = [re.sub(r"^\s{2}", "", l) if l.startswith("  ") else l.strip() for l in lines[1:]]
    return parse_frontmatter([first] + rest)


def _parse_block_map(block: list[str]) -> dict:
    return parse_frontmatter(_dedent(block))


def split_document(text: str):
    """(frontmatter_lines | None, body_text, fm_end_line_index) を返す。"""
    lines = text.splitlines()
    if not lines or lines[0].strip() != "---":
        return None, text, -1
    for j in range(1, len(lines)):
        if lines[j].strip() == "---":
            return lines[1:j], "\n".join(lines[j + 1:]), j
    return None, text, -1


# --------------------------------------------------------------------------- #
# 検証
# --------------------------------------------------------------------------- #

class Report:
    def __init__(self):
        self.errors: list[str] = []
        self.warns: list[str] = []

    def error(self, path, msg):
        self.errors.append(f"{path}: {msg}")

    def warn(self, path, msg):
        self.warns.append(f"{path}: {msg}")


def iter_md(root: Path):
    for p in sorted(root.rglob("*.md")):
        if any(part.startswith(".") for part in p.relative_to(root).parts):
            continue
        yield p


def check_actor(rep, rel, field, value):
    if not isinstance(value, str) or not ACTOR_RE.match(value):
        rep.warn(rel, f"{field} '{value}' がアクター規約（<producer>/<version> | human:<id> | process:<id> など <kind>:<id>）に合わない")


def check_ts(rep, rel, field, value):
    if not isinstance(value, str) or not ISO_TZ_RE.match(value):
        rep.warn(rel, f"{field} '{value}' が UTC オフセット付き ISO 8601 ではない")


def check_bundle(root: Path) -> Report:
    rep = Report()
    now = dt.datetime.now(dt.timezone.utc)
    all_paths = {p.relative_to(root).as_posix() for p in iter_md(root)}
    for p in iter_md(root):
        rel = p.relative_to(root).as_posix()
        text = p.read_text(encoding="utf-8")
        fm_lines, body, _ = split_document(text)
        body = strip_fences(body)
        if p.name == "log.md":
            for line in text.splitlines():
                m = DATE_HEADING_RE.match(line)
                if m and not re.match(r"^\d{4}-\d{2}-\d{2}$", m.group(1).strip()):
                    rep.error(rel, f"log.md の日付見出し '{m.group(1)}' が YYYY-MM-DD 形式ではない")
            continue
        if p.name == "index.md":
            if fm_lines is not None and p.parent != root:
                rep.error(rel, "ルート以外の index.md にフロントマターがある")
            elif fm_lines is not None:
                fm = parse_frontmatter(fm_lines)
                extra = set(fm) - {"okf_version"}
                if extra:
                    rep.warn(rel, f"ルート index.md に okf_version 以外のキー {sorted(extra)} がある")
            _check_links(rep, rel, p, body, root, all_paths)
            continue

        if fm_lines is None:
            rep.error(rel, "フロントマターが無い")
            continue
        fm = parse_frontmatter(fm_lines)
        t = fm.get("type")
        if not t or not str(t).strip():
            rep.error(rel, "type が空")

        gen = fm.get("generated")
        if gen is None:
            rep.warn(rel, "generated が無い（誰がいつ書いたか不明）")
        elif isinstance(gen, dict):
            if "by" not in gen:
                rep.warn(rel, "generated.by が無い")
            else:
                check_actor(rep, rel, "generated.by", gen["by"])
            if "at" in gen:
                check_ts(rep, rel, "generated.at", gen["at"])
        else:
            rep.warn(rel, "generated がマッピングではない")

        ver = fm.get("verified")
        if ver is not None:
            entries = ver if isinstance(ver, list) else [ver]
            for e in entries:
                if not isinstance(e, dict):
                    rep.warn(rel, "verified の要素がマッピングではない")
                    continue
                if "by" in e:
                    check_actor(rep, rel, "verified.by", e["by"])
                if "at" in e:
                    check_ts(rep, rel, "verified.at", e["at"])

        if "timestamp" in fm:
            rep.warn(rel, "v0.1 の timestamp が残っている（--upgrade 0.2 で generated.at に移行）")
        if re.search(r"^#+\s*Citations\s*$", body, re.M):
            rep.warn(rel, "v0.1 の # Citations 節が残っている（--upgrade 0.2 で sources に移行）")

        status = fm.get("status")
        if status not in (None, "draft", "stable", "deprecated"):
            rep.warn(rel, f"status '{status}' は draft|stable|deprecated のいずれでもない")

        sa = fm.get("stale_after")
        if sa is not None:
            check_ts(rep, rel, "stale_after", sa)
            try:
                when = dt.datetime.fromisoformat(str(sa).replace("Z", "+00:00"))
                if when <= now:
                    rep.warn(rel, f"stale_after {sa} を過ぎている")
            except ValueError:
                pass

        ids = set()
        sources = fm.get("sources")
        if sources is not None:
            if not isinstance(sources, list):
                rep.warn(rel, "sources がリストではない")
            else:
                for s in sources:
                    if not isinstance(s, dict) or "resource" not in s:
                        rep.warn(rel, "sources の要素に resource が無い")
                        continue
                    if "id" in s:
                        ids.add(str(s["id"]))
                    if "author" in s:
                        check_actor(rep, rel, "sources[].author", s["author"])
                    if "last_modified" in s:
                        check_ts(rep, rel, "sources[].last_modified", s["last_modified"])
        for label in set(FOOTNOTE_REF_RE.findall(body)):
            if label not in ids:
                rep.warn(rel, f"脚注 [^{label}] に対応する sources[].id が無い")

        if str(t) == "Attested Computation":
            if "runtime" not in fm:
                rep.error(rel, "Attested Computation に runtime が無い")
            has_inline = re.search(r"^#\s*Computation\s*$", body, re.M) is not None
            if "computation" in fm and has_inline:
                rep.warn(rel, "computation ファイルと本文 # Computation の両方がある")
            if "computation" not in fm and not has_inline:
                rep.warn(rel, "computation ファイルも本文 # Computation も無い")

        _check_links(rep, rel, p, body, root, all_paths)
    return rep


def _check_links(rep, rel, p: Path, body: str, root: Path, all_paths: set[str]):
    for target in LINK_RE.findall(body):
        if re.match(r"^[a-z]+:", target) or target.startswith("#"):
            continue
        target = target.split("#")[0]
        if not target:
            continue
        if target.startswith("/"):
            resolved = (root / target.lstrip("/")).resolve()
        else:
            resolved = (p.parent / target).resolve()
        try:
            r = resolved.relative_to(root.resolve()).as_posix()
        except ValueError:
            continue
        if r.endswith("/") or resolved.is_dir():
            continue
        if not resolved.exists():
            rep.warn(rel, f"リンク切れ: {target}")


# --------------------------------------------------------------------------- #
# アップグレード v0.1 → v0.2
# --------------------------------------------------------------------------- #

def git_author_actor(path: Path) -> str | None:
    try:
        out = subprocess.run(
            ["git", "log", "--diff-filter=A", "--format=%ae", "--", str(path.resolve())],
            capture_output=True, text=True, check=True, cwd=path.resolve().parent,
        ).stdout.strip().splitlines()
    except Exception:
        return None
    if not out:
        return None
    email = out[-1].strip()
    local = email.split("@")[0]
    return f"human:{local}" if local else None


def upgrade_file_to_0_2(p: Path, default_by: str | None, dry_run: bool) -> list[str]:
    """変更内容の説明リストを返す。"""
    notes: list[str] = []
    text = p.read_text(encoding="utf-8")
    fm_lines, body, end = split_document(text)
    if fm_lines is None:
        return notes
    fm = parse_frontmatter(fm_lines)
    new_fm = list(fm_lines)

    if "timestamp" in fm and "generated" not in fm:
        ts = str(fm["timestamp"])
        by = default_by or git_author_actor(p) or "unknown/legacy"
        if by == "unknown/legacy":
            notes.append("generated.by を推定できず unknown/legacy を設定（要確認）")
        new_fm = [l for l in new_fm if not re.match(r"^timestamp:", l)]
        new_fm.append(f"generated: {{ by: {by}, at: {ts} }}")
        notes.append(f"timestamp → generated.at（by: {by}）")

    m = re.search(r"^#+\s*Citations\s*$\n((?:\s*[-*]\s+.+\n?)+)", body, re.M)
    if m and "sources" not in fm:
        items = re.findall(r"^\s*[-*]\s+(.+?)\s*$", m.group(1), re.M)
        if items:
            new_fm.append("sources:")
            for idx, item in enumerate(items, 1):
                link = re.match(r"\[(.+?)\]\((.+?)\)", item)
                if link:
                    title, res = link.group(1), link.group(2)
                    new_fm.append(f"  - id: cite-{idx}")
                    new_fm.append(f"    resource: {res}")
                    new_fm.append(f"    title: \"{title}\"")
                else:
                    new_fm.append(f"  - id: cite-{idx}")
                    new_fm.append(f"    resource: {item}")
            body = body[: m.start()] + body[m.end():]
            body = body.rstrip("\n") + "\n"
            notes.append(f"# Citations（{len(items)} 件）→ sources")

    if notes and not dry_run:
        out = "---\n" + "\n".join(new_fm) + "\n---\n" + body.rstrip("\n") + "\n"
        p.write_text(out, encoding="utf-8")
    return notes


def upgrade_bundle(root: Path, version: str, default_by: str | None, dry_run: bool):
    if version not in SUPPORTED_UPGRADES:
        print(f"未対応のバージョン: {version}。ガイドの変更点節を読んで本スクリプトに変換を追加してから実行する。")
        sys.exit(2)
    changed = 0
    for p in iter_md(root):
        if p.name in RESERVED:
            continue
        notes = upgrade_file_to_0_2(p, default_by, dry_run)
        if notes:
            changed += 1
            rel = p.relative_to(root).as_posix()
            print(f"{'[dry-run] ' if dry_run else ''}{rel}")
            for n in notes:
                print(f"  - {n}")
    idx = root / "index.md"
    if not dry_run:
        _set_okf_version(idx, version)
    print(f"\n{changed} 件を更新{'（予定）' if dry_run else ''}。okf_version: \"{version}\" を {idx.name} に設定{'（予定）' if dry_run else ''}。")
    print("log.md に **Upgrade** エントリを追記し、--check で確認すること。")


def _set_okf_version(idx: Path, version: str):
    if idx.exists():
        text = idx.read_text(encoding="utf-8")
        fm_lines, body, _ = split_document(text)
        if fm_lines is not None:
            fm_lines = [l for l in fm_lines if not l.startswith("okf_version:")]
            fm_lines.append(f'okf_version: "{version}"')
            idx.write_text("---\n" + "\n".join(fm_lines) + "\n---\n" + body.lstrip("\n"), encoding="utf-8")
            return
        idx.write_text(f'---\nokf_version: "{version}"\n---\n\n' + text, encoding="utf-8")
    else:
        idx.write_text(f'---\nokf_version: "{version}"\n---\n\n# Index\n', encoding="utf-8")


# --------------------------------------------------------------------------- #

def main():
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("root", help="バンドルルート")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--check", action="store_true", help="適合性を検証する")
    g.add_argument("--upgrade", metavar="VERSION", help="指定バージョンへアップグレードする（例: 0.2）")
    ap.add_argument("--by", help="--upgrade 時の generated.by 既定値（git から推定できない場合）")
    ap.add_argument("--dry-run", action="store_true", help="--upgrade 時にファイルを変更しない")
    args = ap.parse_args()

    root = Path(args.root)
    if not root.is_dir():
        print(f"ディレクトリが無い: {root}")
        sys.exit(2)

    if args.check:
        rep = check_bundle(root)
        for e in rep.errors:
            print(f"ERROR {e}")
        for w in rep.warns:
            print(f"WARN  {w}")
        print(f"\nERROR {len(rep.errors)} / WARN {len(rep.warns)}")
        sys.exit(1 if rep.errors else 0)
    else:
        upgrade_bundle(root, args.upgrade, args.by, args.dry_run)


if __name__ == "__main__":
    main()
