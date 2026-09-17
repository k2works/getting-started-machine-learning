---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "ソフトウェア開発では、変更履歴を安全に管理し、チームで追跡可能にする仕組みが重要です。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

ソフトウェア開発では、変更履歴を安全に管理し、チームで追跡可能にする仕組みが重要です。
Git による版管理とコミットメッセージ規約を整えることで、TDD の小さな変更を確実に積み上げられます。

## 4.2 Git によるバージョン管理

Scala プロジェクト（`apps/scala`）では、次の基本フローで変更を記録します。

```bash
cd apps/scala
git init
git add .
git commit -m "chore(scala): initialize sbt project"
```

`git init` でリポジトリを初期化し、`git add` でステージング、`git commit` で履歴を確定します。
TDD では Red → Green → Refactor の小さな単位でコミットすると、変更意図を追いやすくなります。

### .gitignore の設定

`apps/scala/.gitignore` には、ビルド成果物と IDE 補助ファイルを含めます。

```bash
target/
.bsp/
.metals/
.bloop/
.idea/
*.class
*.log
*.jar
.DS_Store
project/metals.sbt
project/project/
```

最低限として、次の 4 つは必ず除外します。

- `target/`
- `.bsp/`
- `.metals/`
- `.bloop/`

## 4.3 Conventional Commits

Conventional Commits は、Angular Convention に由来するコミットメッセージ規約です。
基本フォーマットは次の通りです。

```text
<type>(<scope>): <subject>
```

- `type`: 変更種別
- `scope`: 変更対象（任意）
- `subject`: 変更内容の要約（命令形・簡潔）

### type の種類

- `feat`: 新機能の追加
- `fix`: バグ修正
- `docs`: ドキュメント変更
- `style`: 振る舞いに影響しない整形
- `refactor`: 振る舞いを変えない構造改善
- `test`: テスト追加・修正
- `chore`: ビルドや設定などの雑務

### scope と subject の書き方

- `scope` はモジュール単位で短く書きます（例: `scala`, `fizzbuzz`, `ci`）。
- `subject` は 50 文字前後を目安に、何をしたかを明確に書きます。
- 末尾の句点は付けません。

### コミットメッセージの例

```text
test(fizzbuzz): add failing test for multiples of 15
feat(fizzbuzz): implement generateList for 1 to n
refactor(fizzbuzz): simplify rule matching order
docs(scala): add chapter about sbt and scalafmt
chore(ci): add scala-ci workflow for pull requests
```

## 4.4 ブランチ戦略

### Git Flow の基本概念

Git Flow では、主に次のブランチを使います。

- `main`: リリース済みの安定コード
- `develop`: 開発統合ブランチ
- `feature/*`: 機能開発ブランチ
- `release/*`: リリース準備ブランチ
- `hotfix/*`: 緊急修正ブランチ

学習用プロジェクトでは、まず `main` と `feature/*` の運用から始めると実践しやすいです。

### feature ブランチの作成と運用

```bash
git switch main
git pull origin main
git switch -c feature/scala-chapter-04
```

運用ポイントは次の通りです。

- 1 ブランチ 1 テーマに絞ります。
- Red / Green / Refactor の区切りで小さくコミットします。
- 完了後に Pull Request を作成し、レビュー後に `main` へマージします。

## 4.5 まとめ

この章では、Scala の TDD 開発を支える版管理の基礎を整理しました。

- Git の基本操作で変更履歴を安全に管理する
- `.gitignore` で不要ファイルを除外する
- Conventional Commits で履歴を読みやすくする
- `feature` ブランチ運用で変更を分離する

次章では、`sbt` を中心にパッケージ管理と静的解析を整備します。
