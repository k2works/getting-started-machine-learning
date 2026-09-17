#!/usr/bin/env python3
"""OKF バンドル内の文書に規約を適用する（日常運用向け）。標準ライブラリのみ。

使い方:
  python okf_apply.py apply     <bundle> <file...> --by <actor> [--type T] [--description D] [--tags a,b] [--stale-days N] [--log MSG]
  python okf_apply.py apply     <bundle> --changed --by <actor>      # git で変更・追加された .md をまとめて適用
  python okf_apply.py verify    <bundle> <file...> --by <actor>      # verified に検証イベントを追記
  python okf_apply.py deprecate <bundle> <file...> [--replaced-by PATH] [--by <actor>]
  python okf_apply.py status    <bundle> <file...>                   # フロントマターの要約を表示

apply の動作:
  - フロントマターが無い文書: type（--type または親ディレクトリから推定）・title（H1）・description・tags・
    status: stable・generated { by, at } を付与する
  - フロントマターがある文書: 本文が git HEAD と異なれば generated を { --by, now } に更新する
    （本文が変わっていなければ generated は触らない。誰が書いたかは変わっていないため）
  - 親ディレクトリの index.md にエントリが無ければ末尾に追記する
  - バンドルルートの log.md に当日のエントリを追記する
"""

from __future__ import annotations

import argparse
import datetime as dt
import io
import re
import subprocess
import sys
from pathlib import Path

RESERVED = {"index.md", "log.md"}
TYPE_MAP = {
    "strategy": "Strategy", "requirements": "Requirements", "design": "Design",
    "development": "Plan", "operation": "Playbook", "review": "Review", "adr": "ADR",
    "journal": "Journal", "reference": "Reference", "template": "Template",
    "article": "Article", "manual": "Manual", "report": "Report",
}
NOW = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
NOW_ISO = NOW.strftime("%Y-%m-%dT%H:%M:%SZ")
TODAY = NOW.strftime("%Y-%m-%d")


# --------------------------------------------------------------------------- #
# ファイル I/O（改行コードを保持）
# --------------------------------------------------------------------------- #

def read(p: Path):
    raw = io.open(p, encoding="utf-8", newline="").read()
    nl = "\r\n" if "\r\n" in raw else "\n"
    return raw.replace("\r\n", "\n"), nl


def write(p: Path, text: str, nl: str):
    if not text.endswith("\n"):
        text += "\n"
    io.open(p, "w", encoding="utf-8", newline="").write(text.replace("\n", nl))


def split_fm(text: str):
    """(fm_lines | None, body)"""
    lines = text.split("\n")
    if lines and lines[0].strip() == "---":
        for j in range(1, len(lines)):
            if lines[j].strip() == "---":
                return lines[1:j], "\n".join(lines[j + 1:])
    return None, text


def join_fm(fm: list[str], body: str) -> str:
    return "---\n" + "\n".join(fm) + "\n---\n\n" + body.lstrip("\n")


def yaml_str(s: str) -> str:
    return '"' + s.replace('"', '\\"') + '"'


def get_key(fm: list[str], key: str):
    """トップレベルキーの (index, value) を返す。無ければ (None, None)。"""
    for i, l in enumerate(fm):
        m = re.match(rf"^{key}:\s*(.*)$", l)
        if m:
            return i, m.group(1).strip()
    return None, None


def block_end(fm: list[str], start: int) -> int:
    """start のキーに続くブロック（インデント行）の終端インデックス（排他）"""
    j = start + 1
    while j < len(fm) and (not fm[j].strip() or fm[j].startswith((" ", "\t"))):
        j += 1
    return j


def set_key(fm: list[str], key: str, value: str):
    """スカラー値のキーを置換または追加（ブロック値なら削除して置換）"""
    i, _ = get_key(fm, key)
    if i is None:
        fm.append(f"{key}: {value}")
    else:
        del fm[i:block_end(fm, i)]
        fm.insert(i, f"{key}: {value}")


# --------------------------------------------------------------------------- #
# git
# --------------------------------------------------------------------------- #

def git(args: list[str], cwd: Path) -> str:
    try:
        return subprocess.run(["git", "-c", "core.quotepath=false", *args], capture_output=True,
                              text=True, encoding="utf-8", cwd=cwd, check=True).stdout
    except Exception:
        return ""


def head_body(p: Path) -> str | None:
    """git HEAD 時点の本文。追跡外なら None"""
    prefix = git(["rev-parse", "--show-prefix"], p.parent).strip()
    rel = git(["ls-files", "--full-name", "--", str(p.resolve())], p.parent).strip()
    if not rel:
        return None
    text = git(["show", f"HEAD:{rel}"], p.parent)
    if not text:
        return None
    _, body = split_fm(text.replace("\r\n", "\n"))
    return body.strip()


def changed_files(bundle: Path) -> list[Path]:
    # porcelain のパスはリポジトリルート基準なので、ルートからの相対に直す
    root = Path(git(["rev-parse", "--show-toplevel"], bundle).strip() or ".")
    out = git(["status", "--porcelain", "--", str(bundle.resolve())], bundle)
    files = []
    for line in out.splitlines():
        path = line[3:].strip().strip('"')
        if " -> " in path:
            path = path.split(" -> ")[-1]
        if path.endswith(".md"):
            files.append(root / path)
    return files


# --------------------------------------------------------------------------- #
# 文書操作
# --------------------------------------------------------------------------- #

def infer_type(bundle: Path, p: Path, override: str | None) -> str:
    if override:
        return override
    rel = p.resolve().relative_to(bundle.resolve())
    top = rel.parts[0] if len(rel.parts) > 1 else ""
    return TYPE_MAP.get(top, "Document")


def h1(body: str) -> str | None:
    m = re.search(r"^#\s+(.+?)\s*$", body, re.M)
    return m.group(1).strip() if m else None


def lead(body: str) -> str:
    """H1 直後（または概要節）の散文 1 文"""
    started, intro = False, False
    for line in body.split("\n"):
        l = line.strip()
        if not started:
            started = bool(re.match(r"^#\s", l))
            continue
        if re.match(r"^#{2,}\s", l) or l.startswith("```"):
            if not intro and re.search(r"概要|はじめに|Overview|Summary", l):
                intro = True
                continue
            return ""
        if not l or l.startswith(("|", "-", "*", ">", "!", "@", "<", "1.")):
            continue
        l = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", l)
        l = re.sub(r"[*_`]", "", l).strip()
        m = re.match(r"^(.+?[。．])", l)
        return (m.group(1) if m else l)[:120]
    return ""


def apply_one(bundle: Path, p: Path, a) -> str:
    text, nl = read(p)
    fm, body = split_fm(text)
    note = ""
    if fm is None:
        fm = [f"type: {infer_type(bundle, p, a.type)}"]
        title = h1(body) or p.stem
        fm.append(f"title: {yaml_str(title)}")
        desc = a.description or lead(body)
        if desc:
            fm.append(f"description: {yaml_str(desc)}")
        tags = a.tags or infer_type(bundle, p, a.type).lower()
        fm.append(f"tags: [{tags}]")
        # 人が書いた文書は stable、エージェント生成で未レビューなら draft（verify で stable に昇格）
        fm.append("status: stable" if a.by.startswith("human:") else "status: draft")
        fm.append(f"generated: {{ by: {a.by}, at: {NOW_ISO} }}")
        note = "creation"
    else:
        if a.type:
            set_key(fm, "type", a.type)
        if a.description:
            set_key(fm, "description", yaml_str(a.description))
        if a.tags:
            set_key(fm, "tags", f"[{a.tags}]")
        _, gen = get_key(fm, "generated")
        prev = head_body(p)
        body_changed = prev is None or prev != body.strip()
        if gen is None or body_changed:
            set_key(fm, "generated", f"{{ by: {a.by}, at: {NOW_ISO} }}")
            note = "update"
        else:
            note = "unchanged"
    if a.stale_days:
        stale = (NOW + dt.timedelta(days=a.stale_days)).strftime("%Y-%m-%dT00:00:00Z")
        set_key(fm, "stale_after", stale)
    write(p, join_fm(fm, body), nl)
    return note


def verify_one(p: Path, by: str):
    text, nl = read(p)
    fm, body = split_fm(text)
    if fm is None:
        raise SystemExit(f"{p}: フロントマターが無い。先に apply を実行する")
    i, val = get_key(fm, "verified")
    entry = f"  - {{ by: {by}, at: {NOW_ISO} }}"
    if i is None:
        fm += ["verified:", entry]
    elif val:  # 単一マッピング → リスト化
        fm[i:i + 1] = ["verified:", f"  - {val}", entry]
    else:
        fm.insert(block_end(fm, i), entry)
    _, st = get_key(fm, "status")
    if st == "draft":
        set_key(fm, "status", "stable")  # 検証されたので消費可能
    write(p, join_fm(fm, body), nl)


def normalize_ref(value: str | None) -> str | None:
    """後継パスをバンドル相対（/ 始まり）に正規化する。
    Git Bash は `/operation/deploy.md` を `C:/Program Files/Git/operation/deploy.md` に変換してしまうため、
    その形も元に戻す。URL と相対パス（./ ../）はそのまま。"""
    if not value:
        return value
    m = re.match(r"^[A-Za-z]:/(?:Program Files/Git|msys64|Git)/(.+)$", value)
    if m:
        return "/" + m.group(1)
    if value.startswith(("/", "http://", "https://", "./", "../")):
        return value
    return "/" + value


def deprecate_one(p: Path, replaced_by: str | None):
    replaced_by = normalize_ref(replaced_by)
    text, nl = read(p)
    fm, body = split_fm(text)
    if fm is None:
        raise SystemExit(f"{p}: フロントマターが無い。先に apply を実行する")
    set_key(fm, "status", "deprecated")
    if replaced_by:
        set_key(fm, "replaced_by", replaced_by)
    write(p, join_fm(fm, body), nl)
    # index.md の該当エントリに廃止を付記（一覧で現行と区別できるように）
    idx = p.parent / "index.md"
    if idx.exists():
        it, inl = read(idx)
        mark = "（廃止" + (f" → {replaced_by}" if replaced_by else "") + "）"
        lines = it.split("\n")
        for k, l in enumerate(lines):
            if re.search(r"\]\(\./?" + re.escape(p.name) + r"\)", l) and mark not in l:
                lines[k] = l.rstrip() + " " + mark
        write(idx, "\n".join(lines), inl)


def status_one(p: Path):
    text, _ = read(p)
    fm, _ = split_fm(text)
    if fm is None:
        print(f"{p}: フロントマター無し")
        return
    keys = {}
    for k in ("type", "title", "status", "generated", "verified", "stale_after"):
        i, v = get_key(fm, k)
        if i is not None:
            keys[k] = v or "(block)"
    print(f"{p}: " + ", ".join(f"{k}={v}" for k, v in keys.items()))


# --------------------------------------------------------------------------- #
# index.md / log.md
# --------------------------------------------------------------------------- #

def ensure_index_entry(bundle: Path, p: Path):
    idx = p.parent / "index.md"
    if not idx.exists() or p.name in RESERVED:
        return False
    text, nl = read(idx)
    if re.search(r"\]\(\./?" + re.escape(p.name) + r"\)", text) or p.name in text:
        return False
    doc_text, _ = read(p)
    fm, body = split_fm(doc_text)
    _, title = get_key(fm or [], "title")
    _, desc = get_key(fm or [], "description")
    title = (title or "").strip('"') or h1(body) or p.stem
    desc = (desc or "").strip('"')
    entry = f"* [{title}](./{p.name})" + (f" - {desc}" if desc else "")
    write(idx, text.rstrip("\n") + "\n" + entry + "\n", nl)
    return True


def append_log(bundle: Path, kind: str, message: str):
    logp = bundle / "log.md"
    if logp.exists():
        text, nl = read(logp)
    else:
        text, nl = "# Update Log\n", "\n"
    line = f"* **{kind}**: {message}"
    if f"## {TODAY}" in text:
        text = text.replace(f"## {TODAY}\n", f"## {TODAY}\n{line}\n", 1)
    else:
        head, _, rest = text.partition("\n")
        text = f"{head}\n\n## {TODAY}\n{line}\n" + ("\n" + rest.lstrip("\n") if rest.strip() else "")
    write(logp, text, nl)


# --------------------------------------------------------------------------- #

def rel_link(bundle: Path, p: Path) -> str:
    return "/" + p.resolve().relative_to(bundle.resolve()).as_posix()


def main():
    for s in (sys.stdout, sys.stderr):
        if hasattr(s, "reconfigure"):
            s.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["apply", "verify", "deprecate", "status"])
    ap.add_argument("bundle")
    ap.add_argument("files", nargs="*")
    ap.add_argument("--changed", action="store_true", help="git で変更・追加された .md を対象にする")
    ap.add_argument("--by", help="アクター（<producer>/<version> | human:<id> | process:<id>）")
    ap.add_argument("--type")
    ap.add_argument("--description")
    ap.add_argument("--tags", help="カンマ区切り")
    ap.add_argument("--stale-days", type=int)
    ap.add_argument("--replaced-by")
    ap.add_argument("--log", help="log.md に書くメッセージ（既定は自動生成）")
    ap.add_argument("--no-log", action="store_true")
    a = ap.parse_args()

    bundle = Path(a.bundle)
    if not bundle.is_dir():
        sys.exit(f"バンドルが無い: {bundle}")
    a.replaced_by = normalize_ref(a.replaced_by)
    files = [Path(f) for f in a.files]
    if a.changed:
        files += changed_files(bundle)
    files = [f for f in dict.fromkeys(files) if f.suffix == ".md" and f.name not in RESERVED and f.exists()]
    if not files:
        sys.exit("対象ファイルが無い")
    if a.command in ("apply", "verify") and not a.by:
        sys.exit("--by が必要（例: --by claude-code/claude-fable-5, --by human:alice）")

    for p in files:
        if a.command == "status":
            status_one(p)
            continue
        link = rel_link(bundle, p)
        if a.command == "apply":
            note = apply_one(bundle, p, a)
            added = ensure_index_entry(bundle, p)
            print(f"{p}: {note}" + ("（index.md にエントリ追加）" if added else ""))
            if not a.no_log and note != "unchanged":
                kind = "Creation" if note == "creation" else "Update"
                append_log(bundle, kind, a.log or f"[{p.stem}]({link}) を{'作成' if note == 'creation' else '更新'}（{a.by}）")
        elif a.command == "verify":
            verify_one(p, a.by)
            print(f"{p}: verified に {a.by} を追記")
            if not a.no_log:
                append_log(bundle, "Verification", a.log or f"[{p.stem}]({link}) を {a.by} が検証")
        elif a.command == "deprecate":
            deprecate_one(p, a.replaced_by)
            print(f"{p}: status: deprecated" + (f"（→ {a.replaced_by}）" if a.replaced_by else ""))
            if not a.no_log:
                append_log(bundle, "Deprecation", a.log or f"[{p.stem}]({link}) を廃止" + (f"。後継: {a.replaced_by}" if a.replaced_by else ""))
    if a.command != "status":
        print("次: okf_check.py --check（または gulp okf:check）で確認する")


if __name__ == "__main__":
    try:
        main()
    except BrokenPipeError:
        pass
    except OSError as e:  # Windows でパイプ先が閉じた場合（errno 22）
        if e.errno != 22:
            raise
