---
name: developing-release
description: アプリケーションのリリースワークフローを一貫実行。品質ゲート→バージョンバンプ→CHANGELOG 生成→git commit + tag を自動化する。「リリースしたい」「バージョンを上げたい」「CHANGELOG を生成したい」「リリース前のチェックをしたい」といった場面で発動する。リリース手順を標準化することで、ヒューマンエラーを排除し、いつでも安全にリリースできる状態を維持する。
---

# リリースワークフロー

品質ゲート→バージョンバンプ→CHANGELOG 生成→git commit + tag を一貫して実行する。

リリースを自動化する価値は「いつでもリリースできる」状態を維持すること。手作業によるミスを排除し、品質ゲートで最低品質を保証する。

## 参照ドキュメント

- @docs/reference/リリースガイド.md — リリースワークフロー全体

## オプション

| オプション | 説明 |
|-----------|------|
| なし | ドライランを実行（デフォルト） |
| `--dry-run` | CHANGELOG プレビュー + バージョン計算 |
| `--patch` | パッチリリース（バグ修正） |
| `--minor` | マイナーリリース（新機能追加、後方互換あり） |
| `--major` | メジャーリリース（破壊的変更） |
| `--preflight` | 品質ゲートのみ実行 |
| `--deploy` | リリース + デプロイ（`--patch` / `--minor` / `--major` と併用） |

## リリースフロー

1. **ドライラン**: CHANGELOG プレビューとバージョン計算を確認する
2. **リリース種別選択**: patch / minor / major を選択する
3. **品質ゲート（preflight）**: 全チェックを通過する
4. **リリース実行**: バージョンバンプ→CHANGELOG 生成→commit + tag
5. **リモートプッシュ**: コミットとタグをプッシュする
6. **プルリクエスト作成**: リリースブランチからメインブランチへの PR を作成し、CHANGELOG の差分をレビューする
7. **デプロイ**: 必要に応じて本番デプロイを実行する

## 品質ゲート（preflight）

リリース前に以下のチェックを直列実行する。全チェック通過が必須。

| チェック | コマンド | 内容 |
| :--- | :--- | :--- |
| working tree クリーン | `release:preflight:clean` | 未コミット変更がないこと |
| 静的解析 | `release:preflight:lint` | lint エラーがないこと |
| ユニットテスト | `release:preflight:test` | 全テストパス |
| ビルド確認 | `release:preflight:build` | ビルド成功 |
| E2E テスト | `release:preflight:e2e` | E2E テスト全パス |

## バージョニング規則

[Semantic Versioning](https://semver.org/) に従う。

| 種類 | 変更例 | バージョン変化 |
| :--- | :--- | :--- |
| `patch` | バグ修正、軽微な改善 | `0.1.0` → `0.1.1` |
| `minor` | 新機能追加（後方互換あり） | `0.1.0` → `0.2.0` |
| `major` | 破壊的変更 | `0.1.0` → `1.0.0` |

モノレポ構成の場合、全パッケージのバージョンを同期管理する。

## CHANGELOG 生成ルール

[Conventional Commits](https://www.conventionalcommits.org/) に基づいて自動生成する。

| prefix | カテゴリ |
| :--- | :--- |
| `feat` | Features |
| `fix` | Bug Fixes |
| `docs` | Documentation |
| `refactor` | Refactoring |
| `test` | Tests |
| `chore` / `perf` / `ci` / `style` / `build` | その他 |

直近の git tag から HEAD までのコミットを対象とする。タグが存在しない場合は全コミット履歴を対象とする。

## リリース実行ステップ

| ステップ | 内容 |
| :--- | :--- |
| [1/4] バージョン更新 | セマンティックバージョニングに従って更新 |
| [2/4] CHANGELOG 生成 | コミットを分類し CHANGELOG.md を生成 |
| [3/4] git commit + tag | `release: vX.X.X` でコミット→`vX.X.X` タグ作成 |
| [4/4] サマリー表示 | バージョン変化、タグ名、次のステップを表示 |

## 途中から再開

リリース作業の途中から再開する場合は、現在の状態を確認する。

**Example:**

```
ユーザー: 「品質ゲートは通った。リリースを実行したい」
回答: --preflight の結果を確認し、全チェック通過を検証する。
      --patch / --minor / --major のいずれかでリリースを実行する。
```

## トラブルシューティング

- **working tree がクリーンでない**: 変更をコミットまたは `git stash` してからリリース再実行する
- **テスト失敗**: テストを修正してからリリースを再実行する
- **リリースを元に戻したい**: リモートプッシュ前なら `git tag -d vX.X.X && git reset --hard HEAD~1`

## コンテキスト管理

品質ゲート完了後、CHANGELOG 生成後、リリースコミット完了後に `/compact` を実施する。

## 注意事項

- ビルドツール・パッケージマネージャーがセットアップ済み、依存関係インストール済みであること（前提条件）
- working tree がクリーンでないとリリース不可
- リリース前に必ずドライランで内容を確認する
- Conventional Commits でコミットメッセージを記述し CHANGELOG の品質を確保する

## 関連スキル

- `git-commit` — Conventional Commits 準拠のコミット作成
- `managing-operations` — デプロイ・運用管理
- `planning-releases` — リリース・イテレーション計画
- `orchestrating-development` — 開発フェーズ全体のワークフロー
