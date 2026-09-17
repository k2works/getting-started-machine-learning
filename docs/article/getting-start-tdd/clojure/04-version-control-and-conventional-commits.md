---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "第 1 部では TDD の基本サイクル（RED → GREEN → REFACTOR）を体験しました。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

第 1 部では TDD の基本サイクル（RED → GREEN → REFACTOR）を体験しました。第 2 部では **ソフトウェア開発の三種の神器** を整備します。

> 今日のソフトウェア開発の世界において絶対になければならない 3 つの技術的な柱があります。
>
> - バージョン管理
> - テスティング
> - 自動化
>
> — https://t-wada.hatenablog.jp/entry/clean-code-that-works

## 4.2 バージョン管理

### Git によるバージョン管理

プロジェクトでは Git を使ってソースコードを管理します。作業の区切りごとにコミットを行い、変更履歴を追跡できるようにします。

```bash
$ git init
$ git add .
$ git commit -m "feat: FizzBuzz の基本実装を完了"
```

### Conventional Commits

コミットメッセージには **Conventional Commits** の規約を使います。

```
<タイプ>(<スコープ>): <タイトル>
```

主要なタイプ:

| タイプ | 説明 |
|--------|------|
| `feat` | 新機能の追加 |
| `fix` | バグ修正 |
| `docs` | ドキュメントの変更のみ |
| `style` | コードの意味に影響しない変更 |
| `refactor` | バグ修正でも機能追加でもないコード変更 |
| `test` | テストの追加や既存テストの修正 |
| `chore` | ビルドプロセスや補助ツールの変更 |

### コミットの粒度

TDD のリズムに合わせてコミットします。

```bash
# RED → GREEN → REFACTOR の 1 サイクルごとにコミット
$ git add -A
$ git commit -m "feat(clojure): 数を文字列にして返す fizzbuzz 関数を追加"

# リファクタリングは別コミット
$ git commit -m "refactor(clojure): divisible-by? ヘルパー関数を抽出"
```

## 4.3 .gitignore の設定

Clojure プロジェクトでは以下のファイルをバージョン管理から除外します。

```gitignore
# Leiningen
target/
.lein-*
.nrepl-port
.lein-repl-history

# Java
*.jar
*.class
pom.xml
pom.xml.asc

# OS
.DS_Store
```

## 4.4 まとめ

この章では以下のことを学びました。

- Git によるバージョン管理の基本
- **Conventional Commits** 規約に従ったコミットメッセージの書き方
- `.gitignore` による不要ファイルの除外設定
- TDD サイクルに合わせたコミットの粒度
