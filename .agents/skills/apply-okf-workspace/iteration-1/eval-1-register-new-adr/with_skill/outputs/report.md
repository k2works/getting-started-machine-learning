# eval-1: 新規 ADR の OKF バンドル登録（with_skill）

## OKF 適用結果

- 対象: `docs/adr/002-cache-strategy.md`（操作: apply）
- generated: `{ by: claude-code/claude-fable-5, at: 2026-08-25T02:52:49Z }`（本文はエージェントが本セッションで執筆したため `<producer>/<version>` 形式）
- type: `ADR`（親ディレクトリ `adr/` から推定。既存 ADR-001 と同型）
- description: `--description` で明示指定（冒頭段落と同文）
- stale_after: 付与せず（設計判断は期間で陳腐化しないため）
- verified: 付与せず（自分が書いた直後に自分で検証しない。ユーザーのレビュー後に `verify --by human:<id>` を実施する）
- index.md: `docs/adr/index.md` 末尾に ADR-002 のエントリ追加（セクション分け無しのため位置調整不要）
- log.md: `docs/log.md` に 2026-08-25 の Creation エントリ追加
- 検証: ERROR 0 / WARN 0
- 要確認: なし

## 判断メモ

- `PROJECT.md` は `.claude/skills/apply-okf/` に存在しないため、SKILL.md のみを指針とした。
- `gulp okf:check` はプロジェクトに gulpfile が無いため、代替として `migrating-okf/scripts/okf_check.py --check` を使用した。
- フロントマターは手書きせず、同梱スクリプト `okf_apply.py` で付与した。

## 実行コマンドと出力

```text
$ python .claude/skills/apply-okf/scripts/okf_apply.py apply docs docs/adr/002-cache-strategy.md --by claude-code/claude-fable-5 --description "Redis をセッションと商品マスタのキャッシュに採用する。"
docs\adr\002-cache-strategy.md: creation（index.md にエントリ追加）
次: okf_check.py --check（または gulp okf:check）で確認する

$ python .claude/skills/apply-okf/scripts/okf_apply.py status docs docs/adr/002-cache-strategy.md
docs\adr\002-cache-strategy.md: type=ADR, title="ADR-002: キャッシュ戦略", status=stable, generated={ by: claude-code/claude-fable-5, at: 2026-08-25T02:52:49Z }

$ python .claude/skills/migrating-okf/scripts/okf_check.py docs --check

ERROR 0 / WARN 0
```

## 変更後ファイル

### docs/adr/002-cache-strategy.md（フロントマター部）

```yaml
---
type: ADR
title: "ADR-002: キャッシュ戦略"
description: "Redis をセッションと商品マスタのキャッシュに採用する。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-fable-5, at: 2026-08-25T02:52:49Z }
---
```

### docs/adr/index.md

```markdown
# ADR

* [ADR-001: DB 選定](./001-db.md) - PostgreSQL を採用
* [ADR-002: キャッシュ戦略](./002-cache-strategy.md) - Redis をセッションと商品マスタのキャッシュに採用する。
```

### docs/log.md

```markdown
# Docs Update Log

## 2026-08-25
* **Creation**: [002-cache-strategy](/adr/002-cache-strategy.md) を作成（claude-code/claude-fable-5）

## 2026-08-20
* **Creation**: バンドルを初期化
```

## 次のステップ

- ユーザーが ADR-002 をレビューして承認したら `python okf_apply.py verify docs docs/adr/002-cache-strategy.md --by human:kakimomokuri`
- `git add docs/adr/002-cache-strategy.md docs/adr/index.md docs/log.md` してコミット
