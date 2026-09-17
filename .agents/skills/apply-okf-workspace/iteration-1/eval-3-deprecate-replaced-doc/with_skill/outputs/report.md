# eval-3: deploy-old.md の廃止（with_skill）

## OKF 適用結果

- 対象: `docs/operation/deploy-old.md`（操作: deprecate）
  - `status: stable` → `status: deprecated`
  - `replaced_by: /operation/deploy.md` を追加
  - ファイルは削除しない（OKF 規約: 廃止文書は履歴として残す）
- generated: 変更なし（本文は変えていないため `human:kakimomokuri, 2026-06-01` を維持）
- index.md: `docs/operation/index.md` を更新。通常一覧から外し「廃止」セクションへ移動、後継へのリンクを明記
- log.md: 2026-08-25 に Deprecation エントリを追加（理由: deploy.md（CI/CD 自動デプロイ）に置き換えたため廃止）
- 検証: `okf_check.py docs --check` → ERROR 1 / WARN 0
  - ERROR は `adr/002-cache-strategy.md: フロントマターが無い`。今回の依頼とは無関係の未追跡ファイルのため未変更
  - 今回の対象（operation/ 配下）にエラー・警告なし
- 要確認:
  - `adr/002-cache-strategy.md` に `apply` を掛けるかどうか（誰が書いたか不明のため `--by` の判断が必要）
  - Git Bash 環境では `--replaced-by /operation/deploy.md` が Windows パスに変換される。`MSYS_NO_PATHCONV=1` を付けて実行する必要があった（初回実行で `C:/Program Files/Git/operation/deploy.md` に化けたため再実行して修正）

## 実行コマンドと出力

```
$ python .claude/skills/apply-okf/scripts/okf_apply.py deprecate docs docs/operation/deploy-old.md --replaced-by /operation/deploy.md --log "deploy.md（CI/CD 自動デプロイ）に置き換えたため廃止"
docs\operation\deploy-old.md: status: deprecated（→ C:/Program Files/Git/operation/deploy.md）   # Git Bash のパス変換で壊れた
次: okf_check.py --check（または gulp okf:check）で確認する

$ MSYS_NO_PATHCONV=1 python .claude/skills/apply-okf/scripts/okf_apply.py deprecate docs docs/operation/deploy-old.md --replaced-by /operation/deploy.md --no-log
docs\operation\deploy-old.md: status: deprecated（→ /operation/deploy.md）
次: okf_check.py --check（または gulp okf:check）で確認する

$ python .claude/skills/migrating-okf/scripts/okf_check.py docs --check
ERROR adr/002-cache-strategy.md: フロントマターが無い

ERROR 1 / WARN 0

$ python .claude/skills/apply-okf/scripts/okf_apply.py status docs docs/operation/*.md
docs\operation\deploy-old.md: type=Playbook, title="デプロイ手順（旧）", status=deprecated, generated={ by: human:kakimomokuri, at: 2026-06-01T00:00:00Z }
docs\operation\deploy.md: type=Playbook, title="デプロイ手順", status=stable, generated={ by: human:kakimomokuri, at: 2026-08-15T00:00:00Z }
```

## git diff

```diff
diff --git a/docs/log.md b/docs/log.md
index 69894b0..64e827d 100644
--- a/docs/log.md
+++ b/docs/log.md
@@ -1,4 +1,7 @@
 # Docs Update Log
 
+## 2026-08-25
+* **Deprecation**: deploy.md（CI/CD 自動デプロイ）に置き換えたため廃止
+
 ## 2026-08-20
 * **Creation**: バンドルを初期化
diff --git a/docs/operation/deploy-old.md b/docs/operation/deploy-old.md
index 9c0d9d6..88834bb 100644
--- a/docs/operation/deploy-old.md
+++ b/docs/operation/deploy-old.md
@@ -3,8 +3,9 @@ type: Playbook
 title: "デプロイ手順（旧）"
 description: "ECS への手動デプロイ手順。"
 tags: [operation]
-status: stable
+status: deprecated
 generated: { by: human:kakimomokuri, at: 2026-06-01T00:00:00Z }
+replaced_by: /operation/deploy.md
 ---
 
 # デプロイ手順（旧）
diff --git a/docs/operation/index.md b/docs/operation/index.md
index af4033e..978027d 100644
--- a/docs/operation/index.md
+++ b/docs/operation/index.md
@@ -1,4 +1,7 @@
 # 運用
 
-* [デプロイ手順（旧）](./deploy-old.md) - ECS への手動デプロイ
 * [デプロイ手順](./deploy.md) - CI/CD からの自動デプロイ
+
+## 廃止
+
+* [デプロイ手順（旧）](./deploy-old.md) - ECS への手動デプロイ（deprecated。後継: [デプロイ手順](./deploy.md)）
```
