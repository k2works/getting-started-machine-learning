# 完了報告: deploy-old.md の整理

## 判断

OKF v0.2 ガイド（§5.4、§14.2 Step 3）に従い、置き換え済み文書は削除せず `status: deprecated` にしてリンクを保つ方針を採用しました。

## 変更内容

- `docs/operation/deploy-old.md`: `status: stable` → `deprecated`。description に置き換え先を明記し、本文冒頭に廃止注記と `/operation/deploy.md` へのリンクを追加。`generated` は内容変更ではなく状態変更のため据え置き。
- `docs/operation/index.md`: 現行の deploy.md を先頭に並べ替え、deploy-old.md のエントリに「廃止済み」を明記。
- `docs/log.md`: 2026-08-25 の `**Deprecation**` エントリを追加。

## 削除しなかった理由

deploy-old.md への既存リンク（operation/index.md）と履歴を保つため。完全に不要と判断した場合は、ファイル削除と index.md のエントリ除去、log.md への記録を追加で行ってください。
