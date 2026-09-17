---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "前章までで、TDD の基本サイクルを通じて FizzBuzz プログラムを完成させました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

前章までで、TDD の基本サイクルを通じて FizzBuzz プログラムを完成させました。この章からは「動作するきれいなコード」を書き続けるために必要な **ソフトウェア開発の三種の神器** を整備していきます。

> 今日のソフトウェア開発の世界において絶対になければならない 3 つの技術的な柱があります。三本柱と言ったり、三種の神器と言ったりしていますが、それらは
>
> - バージョン管理
> - テスティング
> - 自動化
>
> の 3 つです。
>
> — 和田卓人

**バージョン管理** と **テスティング** に関しては第 1 部で触れました。本章ではバージョン管理をさらに深掘りし、**コミットメッセージの規約** について解説します。

## 4.2 コミットメッセージの重要性

これまでの作業では、区切りごとにリポジトリにコミットしてきました。しかし、コミットメッセージの書き方に一貫性がないと、後からプロジェクトの履歴を追うのが難しくなります。

チーム開発では特に、誰がいつ何のためにコードを変更したのかを明確にすることが重要です。そこで役立つのが **Conventional Commits** です。

## 4.3 Conventional Commits

本プロジェクトでは [Angular ルール](https://github.com/angular/angular.js/blob/master/DEVELOPERS.md#type) に基づいた **Conventional Commits** の書式を採用します。

### コミットメッセージのフォーマット

```
<タイプ>(<スコープ>): <タイトル>
<空行>
<ボディ>
<空行>
<フッタ>
```

### タイプの種類

| タイプ | 説明 | 例 |
|--------|------|-----|
| `feat` | 新機能の追加 | `feat: FizzBuzz のコアロジックを実装` |
| `fix` | バグの修正 | `fix: 15 の倍数の判定順序を修正` |
| `refactor` | リファクタリング | `refactor: match 式でパターンマッチに変更` |
| `test` | テストの追加・修正 | `test: 三角測量のテストケースを追加` |
| `docs` | ドキュメントの変更 | `docs: README にセットアップ手順を追加` |
| `chore` | ビルド・ツールの変更 | `chore: Cake ビルドスクリプトを追加` |
| `style` | コードスタイルの変更 | `style: Fantomas で自動フォーマット` |

### F# プロジェクトでの例

```bash
# テスト追加
$ git commit -m 'test: 数を文字列にして返す'

# 機能実装
$ git commit -m 'feat: generate 関数で match 式による FizzBuzz 判定を実装'

# リファクタリング
$ git commit -m 'refactor: パイプライン演算子でリスト生成を簡潔に記述'
```

## 4.4 Git の基本操作

### リポジトリの初期化

```bash
# プロジェクトディレクトリで Git リポジトリを初期化
$ git init

# .gitignore を作成
$ echo "bin/
obj/
.vs/
*.user
coverage/
test-results/" > .gitignore

# 初期コミット
$ git add .
$ git commit -m "feat: initial commit"
```

### 基本的なワークフロー

```bash
# 変更内容を確認
$ git status

# 特定のファイルをステージング
$ git add src/Library.fs tests/Tests.fs

# コミット
$ git commit -m 'feat: generate 関数で FizzBuzz 判定を実装'

# 履歴の確認
$ git log --oneline
```

### 意味のある単位でのコミット

TDD の各フェーズでコミットすることで、変更の意図が明確になります。

```bash
# Red フェーズ: テストの追加
$ git add tests/Tests.fs
$ git commit -m 'test: 三の倍数で Fizz を返すテストを追加'

# Green フェーズ: 実装
$ git add src/Library.fs
$ git commit -m 'feat: 三の倍数で Fizz を返す実装を追加'

# Refactor フェーズ: リファクタリング
$ git add src/Library.fs
$ git commit -m 'refactor: match 式でパターンマッチに変更'
```

## 4.5 Git フロー

### ブランチ戦略

| ブランチ | 用途 |
|---------|------|
| `main` | リリース可能な安定版 |
| `develop` | 開発中の最新コード |
| `feature/*` | 機能開発用ブランチ |

### 作業の流れ

1. `develop` ブランチから作業を開始
2. TDD サイクル（Red → Green → Refactor）を実行
3. Conventional Commits の書式でコミット
4. テストが通ることを確認してプッシュ

### Feature Branch ワークフロー

```bash
# 新機能開発用のブランチを作成
$ git checkout -b feature/add-fizzbuzz-type

# 開発作業（TDD サイクル）
# ... コーディング ...

# 変更をコミット
$ git add .
$ git commit -m "feat: FizzBuzzType 判別共用体を追加"

# develop ブランチに統合
$ git checkout develop
$ git merge feature/add-fizzbuzz-type
$ git branch -d feature/add-fizzbuzz-type
```

### F# プロジェクトでの .gitignore

.NET プロジェクトでは以下のファイルをバージョン管理から除外します。

```
bin/
obj/
.vs/
*.user
coverage/
test-results/
```

## 4.6 まとめ

この章では以下を学びました。

| 概念 | 説明 |
|------|------|
| Conventional Commits | コミットメッセージの標準的な書式 |
| タイプ | feat, fix, refactor, test, docs, chore, style |
| スコープ | 変更の影響範囲（任意） |
| Git フロー | main / develop / feature ブランチ戦略 |

次章では、NuGet によるパッケージ管理と Fantomas による静的解析を導入します。
