# eval-2 (with skill) 完了報告

## OKF 適用結果

- 対象: `docs/design/data-model.md`（操作: verify）
- generated: 変更なし（本文未変更のため `{ by: claude-code/claude-fable-5, at: 2026-08-21T00:00:00Z }` を維持）
- verified: `{ by: human:kakimomokuri, at: 2026-08-25T02:52:50Z }` を追記
- index.md: 更新なし（既存エントリあり、verify では不要） / log.md: `2026-08-25` に Verification を記録
- 検証: ERROR 1 / WARN 0 — ただし ERROR は依頼対象外の `docs/adr/002-cache-strategy.md`（未追跡・フロントマター無し）
- 要確認: `adr/002-cache-strategy.md` は著者不明のため `apply` を実行していない。著者が分かれば `python okf_apply.py apply docs docs/adr/002-cache-strategy.md --by <actor>` で解消できる

## 判断

- `verified` は自分では付けず、ユーザー申告のアクター `human:kakimomokuri` で付与（SKILL.md「verified を付けてよいか」に準拠）
- フロントマターは手書きせず同梱スクリプトで操作
- PROJECT.md は存在しないため既定（バンドル = `docs/`）を使用

## 実行コマンドと出力

```
$ python .claude/skills/apply-okf/scripts/okf_apply.py verify docs docs/design/data-model.md --by human:kakimomokuri
docs\design\data-model.md: verified に human:kakimomokuri を追記
次: okf_check.py --check（または gulp okf:check）で確認する
exit=0

$ python .claude/skills/apply-okf/scripts/okf_apply.py status docs docs/design/data-model.md
docs\design\data-model.md: type=Design, title="データモデル", status=stable, generated={ by: claude-code/claude-fable-5, at: 2026-08-21T00:00:00Z }, verified=(block)

$ python .claude/skills/migrating-okf/scripts/okf_check.py docs --check
ERROR adr/002-cache-strategy.md: フロントマターが無い

ERROR 1 / WARN 0
exit=1

$ git diff --stat
 docs/design/data-model.md | 2 ++
 docs/log.md               | 3 +++
 2 files changed, 5 insertions(+)
```

## 変更後フロントマター（data-model.md）

```yaml
---
type: Design
title: "データモデル"
description: "受注管理のデータモデル。"
tags: [design]
status: stable
generated: { by: claude-code/claude-fable-5, at: 2026-08-21T00:00:00Z }
verified:
  - { by: human:kakimomokuri, at: 2026-08-25T02:52:50Z }
---
```

## log.md 追記

```
## 2026-08-25
* **Verification**: [data-model](/design/data-model.md) を human:kakimomokuri が検証
```
