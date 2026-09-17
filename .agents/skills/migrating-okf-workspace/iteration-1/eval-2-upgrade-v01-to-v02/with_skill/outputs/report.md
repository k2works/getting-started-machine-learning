# eval-2 (with skill): OKF v0.1 → v0.2 upgrade

## 完了報告

## OKF 移行結果

- バンドル: `knowledge/`（okf_version: "0.2"）
- コンセプト: 2 件（Metric: 2 — `metrics/churn.md`, `metrics/income-statement.md`）
- 除外: 0 件（`index.md`・`log.md` は予約名）
- 検証: ERROR 0 / WARN 0
- 要確認:
  - `generated.by` は git log の著者（`tester <tester@example.com>`、唯一の `init` コミット）から `human:tester` と推定した。実際の執筆者が別なら修正が必要
  - `sources[].id` は機械的に `cite-1` / `cite-2` を付与した（`title` は元の Citations に無いため未設定）
  - `metrics/churn.md` に本文の定義から導いた `description` を追加した（v0.2 の推奨フィールド。内容は変えていない）
  - スクリプト `okf_check.py` の既知の問題: バンドルルートを相対パスで渡すと git 著者推定が失敗し `unknown/legacy` になる（`subprocess` の `cwd=path.parent` に相対パスを渡している）。絶対パスで実行して回避した。また `timestamp`→`generated` 変換後にファイル末尾の改行が落ちるため手で補った
  - コミットは未実施（指示があるまで行わない）

## 実行手順

1. `SKILL.md`・`docs/reference/OKF導入ガイド_V0.2.md` §5/§7/§8/§9/§11/§13 を確認
2. `--check` で現状確認（WARN 5: timestamp 残存 ×2、generated 無し ×2、# Citations 残存 ×1）
3. `--upgrade 0.2 --dry-run` → 本番実行（絶対パス）
4. `log.md` に `## 2026-08-25` の **Upgrade** エントリを追記
5. `churn.md` の末尾改行修正と `description` 追加
6. `--check` で ERROR 0 / WARN 0 を確認

## スクリプト出力

### --check（移行前）

```
WARN  metrics/churn.md: generated が無い（誰がいつ書いたか不明）
WARN  metrics/churn.md: v0.1 の timestamp が残っている（--upgrade 0.2 で generated.at に移行）
WARN  metrics/income-statement.md: generated が無い（誰がいつ書いたか不明）
WARN  metrics/income-statement.md: v0.1 の timestamp が残っている（--upgrade 0.2 で generated.at に移行）
WARN  metrics/income-statement.md: v0.1 の # Citations 節が残っている（--upgrade 0.2 で sources に移行）

ERROR 0 / WARN 5
```

### --upgrade 0.2 --dry-run（相対パス。git 推定失敗）

```
[dry-run] metrics/churn.md
  - generated.by を推定できず unknown/legacy を設定（要確認）
  - timestamp → generated.at（by: unknown/legacy）
[dry-run] metrics/income-statement.md
  - generated.by を推定できず unknown/legacy を設定（要確認）
  - timestamp → generated.at（by: unknown/legacy）
  - # Citations（2 件）→ sources

2 件を更新（予定）。okf_version: "0.2" を index.md に設定（予定）。
log.md に **Upgrade** エントリを追記し、--check で確認すること。
```

### --upgrade 0.2（絶対パス、本番）

```
metrics/churn.md
  - timestamp → generated.at（by: human:tester）
metrics/income-statement.md
  - timestamp → generated.at（by: human:tester）
  - # Citations（2 件）→ sources

2 件を更新。okf_version: "0.2" を index.md に設定。
log.md に **Upgrade** エントリを追記し、--check で確認すること。
```

### --check（log.md 追記後）

```

ERROR 0 / WARN 0
```

### --check（最終）

```

ERROR 0 / WARN 0
```

## git diff

```diff
diff --git a/knowledge/index.md b/knowledge/index.md
index d1e634f..22ca41b 100644
--- a/knowledge/index.md
+++ b/knowledge/index.md
@@ -1,3 +1,7 @@
+---
+okf_version: "0.2"
+---
+
 # Knowledge
 
 * [metrics/](metrics/) - 指標
diff --git a/knowledge/log.md b/knowledge/log.md
index 40aac6d..64ad097 100644
--- a/knowledge/log.md
+++ b/knowledge/log.md
@@ -1,4 +1,7 @@
 # Log
 
+## 2026-08-25
+* **Upgrade**: OKF v0.1 → v0.2 に移行。`timestamp` を `generated: { by, at }` に置き換え、[Income statement](/metrics/income-statement.md) の本文 `# Citations` を `sources` に移行。ルート `index.md` に `okf_version: "0.2"` を宣言。
+
 ## 2026-05-28
 * **Creation**: 損益計算書を追加
diff --git a/knowledge/metrics/churn.md b/knowledge/metrics/churn.md
index 4e80d42..472b53f 100644
--- a/knowledge/metrics/churn.md
+++ b/knowledge/metrics/churn.md
@@ -1,7 +1,8 @@
 ---
 type: Metric
 title: Churn rate
-timestamp: '2026-06-01T00:00:00+00:00'
+description: Monthly customer churn rate, defined as customers lost divided by customers at the start of the month.
+generated: { by: human:tester, at: 2026-06-01T00:00:00+00:00 }
 ---
 
 # Definition
diff --git a/knowledge/metrics/income-statement.md b/knowledge/metrics/income-statement.md
index 348ffa6..9e3554f 100644
--- a/knowledge/metrics/income-statement.md
+++ b/knowledge/metrics/income-statement.md
@@ -3,12 +3,13 @@ type: Metric
 title: Income statement (fiscal year)
 description: Headline income-statement figures for a fiscal year.
 tags: [finance, income-statement]
-timestamp: '2026-05-28T22:53:05+00:00'
+generated: { by: human:tester, at: 2026-05-28T22:53:05+00:00 }
+sources:
+  - id: cite-1
+    resource: https://wiki.acme/finance/fpa-handbook
+  - id: cite-2
+    resource: https://wiki.acme/finance/revenue-recognition
 ---
 
 # Definition
 The income statement reports revenue and gross profit for a fiscal year.
-
-# Citations
-- https://wiki.acme/finance/fpa-handbook
-- https://wiki.acme/finance/revenue-recognition
```
