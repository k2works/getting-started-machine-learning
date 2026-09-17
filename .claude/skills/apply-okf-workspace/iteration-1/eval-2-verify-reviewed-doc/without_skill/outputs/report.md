## 実行完了

### 変更内容

- `docs/design/data-model.md` のフロントマターに `verified: { by: human:kakimomokuri, at: 2026-08-25T02:53:08Z }` を追加（OKF §5.2 の裸マッピング形式、§7 の `human:<id>` アクター規約に準拠）。これにより信頼ティアは unverified → **human-reviewed**（§5.3）になる。
- `generated`・`status: stable` は変更していない（`verified` は `generated.at` と独立、§5.2）。
- `docs/log.md` に `## 2026-08-25` 見出しを新設し、レビュー完了の Update エントリを追加（§9）。
- `index.md` は description に変更がないため更新不要。

### 判断したこと

- 検証日時はユーザーからの指示がないため、作業時点の UTC（`date -u`）を採用。
- レビュー結果「問題なし」のため本文は無変更。

### 次のステップ

- 変更をコミットする（`docs(design): data-model を human-reviewed に更新`）。
- 定期見直しが必要なら `stale_after` の設定を検討する。
