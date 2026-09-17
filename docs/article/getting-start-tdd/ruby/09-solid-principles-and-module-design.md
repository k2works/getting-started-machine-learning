---
type: Article
title: "第 9 章: SOLID 原則とモジュール設計"
description: "前章までに多くのデザインパターンを適用しましたが、すべてのクラスが 1 つのファイルに詰め込まれた状態です。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: SOLID 原則とモジュール設計

## 9.1 はじめに

前章までに多くのデザインパターンを適用しましたが、すべてのクラスが 1 つのファイルに詰め込まれた状態です。この章では **SOLID 原則** の観点からコードを検証し、責務に基づいた **モジュール設計** を行います。

## 9.2 SOLID 原則の検証

### 単一責任原則（SRP: Single Responsibility Principle）

> クラスが変更される理由は一つでなければならない

| クラス | 責務 | 変更理由 |
|--------|------|---------|
| `FizzBuzzValue` | FizzBuzz の結果値を表現 | 値の表現方法が変わるとき |
| `FizzBuzzList` | FizzBuzz の結果コレクションを管理 | コレクション操作が変わるとき |
| `FizzBuzzType01` | タイプ 1 の変換ルール | タイプ 1 のルールが変わるとき |
| `FizzBuzzType02` | タイプ 2 の変換ルール | タイプ 2 のルールが変わるとき |
| `FizzBuzzType03` | タイプ 3 の変換ルール | タイプ 3 のルールが変わるとき |
| `FizzBuzzValueCommand` | 単一値の生成操作 | 値の生成方法が変わるとき |
| `FizzBuzzListCommand` | リストの生成操作 | リストの生成方法が変わるとき |

各クラスが 1 つの責務を持ち、変更理由は 1 つです。SRP を満たしています。

### 開放閉鎖原則（OCP: Open-Closed Principle）

> ソフトウェアエンティティは拡張に対して開いていて、修正に対して閉じている

新しいタイプ（例: タイプ 4）を追加する場合:

1. `FizzBuzzType04` クラスを **新規作成** する
2. `FizzBuzzType.create()` の case 文に 1 行 **追加** する

既存の `FizzBuzzType01`〜`03` は一切変更しません。OCP を満たしています。

### 依存関係逆転の原則（DIP: Dependency Inversion Principle）

> 上位レベルのモジュールは下位レベルのモジュールに依存してはならない。両方とも抽象に依存すべき

```
FizzBuzzValueCommand ──→ FizzBuzzType (基底クラス)
FizzBuzzListCommand  ──→ FizzBuzzType (基底クラス)
                              ↑
                    FizzBuzzType01, Type02, Type03
```

- コマンド（上位）は基底クラス `FizzBuzzType` に依存
- 具体的な Type01〜03 には直接依存しない
- DIP を満たしています

## 9.3 モジュール設計

### 設計方針

責務に基づいて 3 つのレイヤーに分割します。

| レイヤー | 配置先 | 責務 |
|---------|-------|------|
| **ドメインモデル** | `domain/model/` | 値オブジェクト、コレクション |
| **ドメインタイプ** | `domain/type/` | ビジネスルール（FizzBuzz 変換） |
| **アプリケーション** | `application/` | 操作の実行（コマンド） |

### ディレクトリ構成

```
lib/
└── fizz_buzz/
    ├── fizz_buzz.rb                      (バレルファイル: 全 require)
    ├── domain/
    │   ├── model/
    │   │   ├── fizz_buzz_value.rb        (FizzBuzzValue)
    │   │   └── fizz_buzz_list.rb         (FizzBuzzList)
    │   └── type/
    │       ├── fizz_buzz_type.rb         (FizzBuzzType 基底 + create)
    │       ├── fizz_buzz_type_01.rb      (FizzBuzzType01)
    │       ├── fizz_buzz_type_02.rb      (FizzBuzzType02)
    │       └── fizz_buzz_type_03.rb      (FizzBuzzType03)
    └── application/
        ├── fizz_buzz_command.rb          (FizzBuzzCommand モジュール)
        ├── fizz_buzz_value_command.rb    (FizzBuzzValueCommand)
        └── fizz_buzz_list_command.rb     (FizzBuzzListCommand)
```

### 依存関係

```
application/ ──→ domain/type/ ──→ domain/model/
     ↓                ↓                 ↓
  コマンド          タイプ            値・コレクション
```

- `domain/model/` は他のモジュールに依存しない（最も安定）
- `domain/type/` は `domain/model/` のみに依存
- `application/` は `domain/type/` と `domain/model/` に依存

### バレルファイル（fizz_buzz.rb）

Ruby では `require_relative` を使ったバレルファイルで、モジュールの公開 API を集約します。

```ruby
# lib/fizz_buzz/fizz_buzz.rb
# frozen_string_literal: true

require_relative 'domain/model/fizz_buzz_value'
require_relative 'domain/model/fizz_buzz_list'
require_relative 'domain/type/fizz_buzz_type'
require_relative 'domain/type/fizz_buzz_type_01'
require_relative 'domain/type/fizz_buzz_type_02'
require_relative 'domain/type/fizz_buzz_type_03'
require_relative 'application/fizz_buzz_command'
require_relative 'application/fizz_buzz_value_command'
require_relative 'application/fizz_buzz_list_command'
```

利用側は 1 つの require パスですべてのクラスにアクセスできます:

```ruby
require_relative '../lib/fizz_buzz/fizz_buzz'
```

**require の順序が重要**: `FizzBuzzValue` → `FizzBuzzList` → `FizzBuzzType` → サブクラス → `FizzBuzzCommand` → コマンド実装の順に読み込む必要があります。これは Ruby が定数を参照する時点でクラスが定義済みである必要があるためです。

## 9.4 テストの再編成

テストもモジュール構造に合わせて分割します。

```
test/
├── test_helper.rb
└── fizz_buzz/
    ├── domain/
    │   ├── model/
    │   │   ├── fizz_buzz_value_test.rb
    │   │   └── fizz_buzz_list_test.rb
    │   └── type/
    │       └── fizz_buzz_type_test.rb
    └── application/
        └── fizz_buzz_command_test.rb
```

### テスト実行結果

```bash
$ bundle exec rake test
Started with run options --seed 1052

22 tests, 27 assertions, 0 failures, 0 errors, 0 skips
Coverage report generated for Unit Tests.
Line Coverage: 94.12% (80 / 85)
Branch Coverage: 100.0% (16 / 16)
```

## 9.5 各言語のモジュール設計比較

| 概念 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| モジュール単位 | ファイル（`require_relative`） | パッケージ | ファイル（ES Modules） | パッケージ |
| 公開制御 | `private` / `protected` / `public` | `public` / package-private | `export` / non-export | `__all__` / `_` 規約 |
| バレルファイル | `require_relative` 集約 | パッケージの public class | `index.ts` の re-export | `__init__.py` |
| 名前空間 | モジュール / クラス | パッケージ名 | ファイルパス | モジュールパス |
| Mix-in | `include` / `extend` | なし（interface） | なし（mixin パターン） | 多重継承 |

## 9.6 まとめ

第 3 部（章 7〜9）を通じて、手続き的な FizzBuzz を OOP 設計に進化させました。

| 章 | テーマ | 適用したパターン |
|---|--------|---------------|
| 7 | カプセル化とポリモーフィズム | Strategy パターン、ファクトリメソッド |
| 8 | デザインパターンの適用 | Value Object、First-Class Collection、Command パターン |
| 9 | SOLID 原則とモジュール設計 | SRP、OCP、DIP、モジュール分割 |

### Before / After

**Before**（第 2 部終了時）:

```
lib/fizz_buzz.rb (1 ファイル、1 クラス)
test/fizz_buzz_test.rb (1 ファイル)
```

**After**（第 3 部終了時）:

```
lib/fizz_buzz/
├── domain/model/    (値オブジェクト、コレクション)
├── domain/type/     (ビジネスルール)
└── application/     (コマンド)

test/fizz_buzz/
├── domain/model/    (値・コレクションのテスト)
├── domain/type/     (タイプのテスト)
└── application/     (コマンドのテスト)
```

次の第 4 部では、関数型プログラミングの観点から FizzBuzz を再構成し、高階関数、不変データ、パターンマッチング、ベンチマークを学びます。
