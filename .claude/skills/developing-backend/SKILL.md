---
name: developing-backend
description: バックエンド開発の TDD ワークフローを支援。Red-Green-Refactor サイクルとインサイドアウトアプローチで品質の高いバックエンドを実装する。「バックエンドを実装したい」「API を作りたい」「サーバーサイドの機能を追加したい」「バックエンドのテストを書きたい」といった場面で発動する。TDD で開発することで、リファクタリングの安全網を確保し、変更を楽に安全にできるコードを維持する。
---

# バックエンド開発

TDD サイクルに従いバックエンドを開発する。インサイドアウトアプローチ（データ層→ドメイン層→API 層）で、内側から外側へ段階的に構築する。

インサイドアウトの利点は、ドメインロジックのテストが外部依存なしで書けること。テストの実行速度が速く、フィードバックループを短く保てる。

## 参照ドキュメント

| 種類 | パス |
|------|------|
| ワークフロー | @docs/reference/コーディングとテストガイド.md |
| アーキテクチャ | @docs/design/architecture_backend.md |
| データモデル | @docs/design/data-model.md |
| ドメインモデル | @docs/design/domain-model.md |
| 技術スタック | @docs/design/tech_stack.md |
| テスト戦略 | @docs/design/test_strategy.md |

## TDD サイクル

10-15 分で 1 サイクルを完了させる。サイクルが長引くなら、タスクの粒度が大きすぎる。

1. **Red**: 失敗するテストを最初に書く
2. **Green**: テストを通す最小限のコードを実装する
3. **Refactor**: 重複を除去し設計を改善する

## アプローチ

- **インサイドアウト**（推奨）: データ層から開始し上位層へ展開する
- **アウトサイドイン**: API から開始しドメインロジックを段階的に実装する

## テストコマンド

```bash
# 全テスト実行
cd apps/backend && ./gradlew test

# 特定テストクラス実行
cd apps/backend && ./gradlew test --tests "UserServiceTest"

# テストカバレッジ確認
cd apps/backend && ./gradlew jacocoTestReport
```

## API ドキュメント

```bash
# バックエンドを起動
cd apps/backend && ./gradlew bootRun
# Swagger UI: http://localhost:8080/swagger-ui.html
# OpenAPI JSON: http://localhost:8080/v3/api-docs
```

## 品質チェックリスト

コミット前に必ず確認する。

- [ ] すべてのテストがパス
- [ ] ESLint/コンパイラの警告がゼロ
- [ ] テストカバレッジが目標を満たしている
- [ ] 単一の論理的作業単位を表現
- [ ] コミットメッセージが変更内容を明確に説明

## 途中から再開

開発セッションの途中から再開する場合は、まず現在のテスト状態を確認する。

**Example:**

```
ユーザー: 「ユーザー認証の Entity と Repository は実装済み。Service 層に進みたい」
回答: 既存テストを実行して Green 状態を確認する。
      Service 層の失敗するテスト（Red）を書いてから実装に進む。
      Repository の戻り値を使った Service のビジネスロジックをテストする。
```

## コンテキスト管理

タスクの区切りごとに `/compact` を実施して Context limit reached エラーを回避する。

- TDD サイクルを数回繰り返した後、ユーザーストーリー完了時、コミット完了後に実施する
- `/compact` 前に現在の作業状態と次のタスクをメモとして出力する

## 注意事項

- Java/Gradle のテスト環境が設定済みであること（前提条件）
- TDD の三原則を厳密に守る。テストなしでプロダクションコードを書かない
- コミット前に必ず品質チェックリストを実行する
- 作業完了後に対象の @docs/development/iteration_plan-N.md の進捗を更新する
- TODO 駆動開発でタスクを細かく分割し、Rule of Three で 3 回同じコードが現れたらリファクタリングする

## 関連スキル

- `developing-frontend` — フロントエンド TDD 開発
- `orchestrating-development` — 開発フェーズ全体のワークフロー
- `git-commit` — Conventional Commits 準拠のコミット
