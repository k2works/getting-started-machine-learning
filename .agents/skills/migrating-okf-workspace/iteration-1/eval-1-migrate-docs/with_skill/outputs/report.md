# eval-1: docs/ の OKF 移行（with_skill）

## 1. 提示した移行案（`--plan`）

**バンドルルート**: `docs/`（既存構造を尊重。移動なし）。`okf_version: "0.2"` は `docs/index.md` にのみ置く。

| 文書 | 扱い | `type` | 主な付与内容 |
| :--- | :--- | :--- | :--- |
| `docs/index.md` | 予約名（ルート index） | — | `okf_version` フロントマター、各エントリに description |
| `docs/adr/index.md` | 予約名 | — | フロントマター無しのまま。ADR 本体は 0 件 |
| `docs/reference/OKF導入ガイド_V0.2.md` | コンセプト | `Reference` | `sources`: 英語版 SPEC.md（`[^okf-spec]` 脚注） |
| `docs/reference/よいソフトウェアとは.md` | コンセプト | `Reference` | Wiki.js 由来フロントマター（`date`/`dateCreated`/`editor`/`published`）を OKF に置換。`sources`: Clean Craftsmanship |
| `docs/reference/リリースガイド.md` | コンセプト | `Reference` | `sources`: SemVer・Conventional Commits |
| `docs/reference/環境変数管理ガイド.md` | コンセプト | `Reference` | `sources`: Twelve-Factor App。環境手順なので `stale_after`（6 か月後） |

- `generated.by` は全件 `claude-code/claude-fable-5`（移行で内容を触ったエージェント）。`verified` は付けない（人の確認を経ていないため）。`status` は全件 `stable`
- `resource` は付けない（実在資産を説明する文書がない）
- `knowledge/` は別バンドルとして触らない
- `docs/log.md` を新規作成し移行エントリを記録、`docs/reference/index.md` を新規作成

ユーザー不在のため承認済みとして `--migrate` に進んだ。

## 2. 完了報告

### OKF 移行結果

- バンドル: `docs/`（okf_version: "0.2"）
- コンセプト: 4 件（Reference: 4）
- 除外: 0 件（`docs/index.md`・`docs/adr/index.md` は予約名。ADR 本体は存在せず 0 件）
- 新規: `docs/log.md`、`docs/reference/index.md`
- 検証: ERROR 0 / WARN 26
- 要確認:
  - `docs/reference/リリースガイド.md` の関連ドキュメントリンク 3 件（`リリース・イテレーション計画ガイド.md`・`コーディングとテストガイド.md`・`開発ガイド.md`）は本プロジェクトに存在しない。仕様上は「まだ書かれていない知識」として有効なので本文は変更していない。文書を追加するかリンクを外すかは別作業
  - `docs/reference/OKF導入ガイド_V0.2.md` の WARN 23 件はすべてガイド本文中のコードフェンス内サンプル（`/tables/customers.md` 等の例示リンク、`[^ga4-schema]` 等の例示脚注、v0.1 形式例の `# Citations` 見出し）に対するもの。検証スクリプトがコードフェンスを除外しないための誤検知であり、本文は仕様の引用なので変更していない
  - `よいソフトウェアとは.md` にあった Wiki.js 由来のフロントマター（`published`/`date`/`editor`/`dateCreated`/空の `description`/空の `tags`）は削除し OKF フィールドに置き換えた。元の作成日 2025-06-27 は git 履歴では追えない（本リポジトリの初回コミットは 2026-08-25）ため、`generated.at` は移行時刻とした
  - 内容の疑義: `よいソフトウェアとは.md` の「3. ユーザー中心設計 - 使いやすさ、問題解決力4. 健全なチーム文化」は改行漏れとみられる（意味を変えないため未修正）

### 移行時に行った判断

- `type` は `docs/reference/` に対応する `Reference` の 1 種類に絞った
- `stale_after` は環境手順である `環境変数管理ガイド.md` のみ（`2027-02-25T00:00:00Z`）。他 3 件は定期見直し対象ではないため付けていない
- 本文の変更は根拠箇所への `[^id]` 脚注追加と文末の脚注定義のみ。既存の相対リンク・外部リンクはそのまま（相対リンクも仕様上有効）
- `docs/index.md` は MkDocs のトップページ兼用のため既存の本文リンクを残し、OKF の index 形式（セクション見出し + description 付きエントリ）に整えた
- コミットはしていない（ユーザーの指示待ち）

## 3. 検証スクリプト出力（全文）

コマンド: `python .claude/skills/migrating-okf/scripts/okf_check.py --check docs`（終了コード 0）

```text
WARN  reference/OKF導入ガイド_V0.2.md: v0.1 の # Citations 節が残っている（--upgrade 0.2 で sources に移行）
WARN  reference/OKF導入ガイド_V0.2.md: 脚注 [^fpa-handbook] に対応する sources[].id が無い
WARN  reference/OKF導入ガイド_V0.2.md: 脚注 [^exec-rev-dash] に対応する sources[].id が無い
WARN  reference/OKF導入ガイド_V0.2.md: 脚注 [^cost-alloc] に対応する sources[].id が無い
WARN  reference/OKF導入ガイド_V0.2.md: 脚注 [^ga4-schema] に対応する sources[].id が無い
WARN  reference/OKF導入ガイド_V0.2.md: 脚注 [^id] に対応する sources[].id が無い
WARN  reference/OKF導入ガイド_V0.2.md: 脚注 [^rev-policy] に対応する sources[].id が無い
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: /tables/customers.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: /tables/customers.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: /tables/orders.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: /tables/customers.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: ./other.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: relative-url-1
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: relative-url-2
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: subdir/
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: /tables/customer-metrics.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: /playbooks/dataplex.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: ../computations/revenue.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: tables/
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: playbooks/
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: computations/
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: ../computations/revenue.md
WARN  reference/OKF導入ガイド_V0.2.md: リンク切れ: ../computations/profit.md
WARN  reference/リリースガイド.md: リンク切れ: リリース・イテレーション計画ガイド.md
WARN  reference/リリースガイド.md: リンク切れ: コーディングとテストガイド.md
WARN  reference/リリースガイド.md: リンク切れ: 開発ガイド.md

ERROR 0 / WARN 26
```

## 4. 作業メモ（スキル改善の材料）

- 検証スクリプトの脚注・リンク・`# Citations` 検査がコードフェンス内を除外しないため、仕様ガイド自体をバンドルに含めると 23 件の誤 WARN が出る。フェンス内を無視できると報告が楽になる
- 原文に NBSP（U+00A0）が混在しており、文字列一致で脚注を挿入する際に一度失敗した。本文置換は正規化してから照合したほうがよい
- Wiki.js エクスポート形式のフロントマター（`date`/`dateCreated`/`editor`/`published`）が既存文書にあった場合の扱い（削除するか、未知キーとして保持するか）が SKILL.md に無い。今回は削除した
