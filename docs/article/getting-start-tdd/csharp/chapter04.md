---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "前章までで、TDD の基本サイクルを通じて FizzBuzz プログラムを完成させました。"
tags: [article, getting-start-tdd, csharp]
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
| `refactor` | リファクタリング | `refactor: 条件判定をメソッドに抽出` |
| `test` | テストの追加・修正 | `test: 三角測量のテストケースを追加` |
| `docs` | ドキュメントの変更 | `docs: README にセットアップ手順を追加` |
| `chore` | ビルド・ツールの変更 | `chore: NuGet パッケージを更新` |
| `style` | コードスタイルの変更 | `style: dotnet format で自動フォーマット` |

### C# プロジェクトでの例

```bash
# テスト追加
$ git commit -m 'test: 数を文字列にして返す'

# 機能実装
$ git commit -m 'feat: Generate メソッドで FizzBuzz 判定を実装'

# リファクタリング
$ git commit -m 'refactor: 条件判定をメソッドに抽出'
```

## 4.4 Git フロー

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

## 4.5 まとめ

この章では以下を学びました。

| 概念 | 説明 |
|------|------|
| Conventional Commits | コミットメッセージの標準的な書式 |
| タイプ | feat, fix, refactor, test, docs, chore, style |
| スコープ | 変更の影響範囲（任意） |
| Git フロー | main / develop / feature ブランチ戦略 |

次章では、NuGet によるパッケージ管理と dotnet format による静的解析を導入します。
