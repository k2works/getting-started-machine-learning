# eval-1: docs/ を OKF 知識バンドルへ移行（without_skill）

## 1. 提示した移行案

**方針**：`docs/` そのものを OKF v0.2 バンドルのルートにする（`knowledge/` は別バンドルとして触らない）。
`docs/reference/OKF導入ガイド_V0.2.md` §14.2 の Step 1〜3 を適用。Step 4（Attested Computation）は数値を扱う文書がないため対象外。

| 対象 | 現状 | 移行後 |
| :--- | :--- | :--- |
| `docs/index.md` | 単純なリンク 2 行 | `okf_version: "0.2"` を宣言（§12）。セクション見出し + `* [Title](path) - description` 形式（§8） |
| `docs/log.md` | なし | 新規作成。`YYYY-MM-DD` 見出しで移行を記録（§9） |
| `docs/reference/index.md` | なし | 新規作成。各コンセプトの `description` を反映 |
| `docs/adr/index.md` | 表形式の一覧 | §8 の箇条書き形式へ整形。ADR コンセプトの `type` 規約（`Architecture Decision Record`）を明記 |
| `reference/リリースガイド.md` | フロントマターなし | `type: Reference` + title/description/tags/status。相対リンクをバンドル相対（`/reference/...`）へ |
| `reference/環境変数管理ガイド.md` | フロントマターなし | 同上。12-Factor App を `sources` に登録 |
| `reference/OKF導入ガイド_V0.2.md` | フロントマターなし | 同上。仕様正本 SPEC.md を `sources` に登録 |
| `reference/よいソフトウェアとは.md` | Wiki.js 形式（`published`/`editor`/`dateCreated` 等） | `type` を追加し、Wiki.js 固有キーを OKF の `generated.at` に置き換え |

**判断事項**

- `generated.by` は「内容を書いた者」を表すため、原著者不明のガイドには付けない（省略は §5.3 で許容）。移行イベントは `log.md` に記録する。人が書いた文書にエージェントを `generated.by` とするのは来歴の誤記になる。
- 本文の内容は変更しない（すでに構造化 Markdown で書かれている）。
- 未作成ガイドへの壊れたリンクは §6.1 により許容されるので残す。

## 2. 完了報告

### 変更内容

- `docs/index.md`：ルート `index.md` に `okf_version: "0.2"` を宣言し、§8 形式のセクション/箇条書きに整形
- `docs/log.md`（新規）：2026-08-25 の移行を記録
- `docs/reference/index.md`（新規）：「原則」「ガイド」セクションで 4 コンセプトを description 付きで列挙
- `docs/adr/index.md`：箇条書き形式へ整形し、ADR コンセプトの `type` 規約を記載（ADR 実体は 0 件）
- `docs/reference/リリースガイド.md`：`type: Reference` フロントマター付与。関連ドキュメント 3 リンクをバンドル相対パスへ変更
- `docs/reference/環境変数管理ガイド.md`：`type: Reference` フロントマター付与、`sources` に 12-Factor App
- `docs/reference/OKF導入ガイド_V0.2.md`：`type: Reference` フロントマター付与、`sources` に SPEC.md
- `docs/reference/よいソフトウェアとは.md`：Wiki.js フロントマターを OKF 形式へ置換（`generated: { by: human:k2works, at: 2025-06-27T09:48:46Z }`）

### 検証結果（§11 適合性）

- 予約ファイル以外の全 `.md`（4 件）が `---` で始まるフロントマターと空でない `type` を持つ
- `log.md` の日付見出しはすべて `YYYY-MM-DD`
- フロントマターを持つ `index.md` はルートのみ（`okf_version` のみ）
- `knowledge/` は無変更

### 前提・留意点

- `よいソフトウェアとは.md` の `generated.by: human:k2works` はリポジトリの git ユーザーからの推定。原著者が異なる場合は修正が必要
- 他 3 ガイドは原著者不明のため `generated` を省略（unverified 扱い）。人がレビューしたら `verified: { by: human:<id>, at: ... }` を追加する運用を推奨
- `docs/` をバンドルルートにしたため、既存の相対リンクはそのまま機能する。ガイド Step 1 の例示パス `docs/knowledge/` とは異なるが、ユーザーの依頼（docs/ 配下を移行）に合わせた判断
