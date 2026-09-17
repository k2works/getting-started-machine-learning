---
type: Article
title: "第 9 章: SOLID 原則とモジュール設計"
description: "前章ではデザインパターンを適用して設計を改善しました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: SOLID 原則とモジュール設計

## 9.1 はじめに

前章ではデザインパターンを適用して設計を改善しました。この章では **SOLID 原則** の観点から設計を評価し、**モジュール分割** によってコードの責務を明確にします。

## 9.2 SOLID 原則の適用

### 単一責任の原則（SRP）

| モジュール | 責務 |
|-----------|------|
| `domain::types` | FizzBuzz タイプの定義と生成ルール |
| `domain::model` | 値オブジェクトとコレクション |
| `application` | コマンドの実行と結果の出力 |

### 開放閉鎖の原則（OCP）

`FizzBuzzType` トレイトを実装する新しい構造体を追加するだけで、既存のコードを変更せずに新しいタイプを追加できます。

### リスコフの置換の原則（LSP）

`FizzBuzzType` トレイトを実装するすべての構造体は、`Box<dyn FizzBuzzType>` として交換可能です。

### インターフェース分離の原則（ISP）

`FizzBuzzType` トレイトは `generate` メソッドのみを定義しており、不必要なメソッドを強制しません。

### 依存性逆転の原則（DIP）

`FizzBuzzCommand` は具体的な `FizzBuzzType01` ではなく `Box<dyn FizzBuzzType>` に依存しています。

## 9.3 モジュール分割

### ディレクトリ構成

```
src/
├── lib.rs                    (ライブラリルート)
├── main.rs                   (エントリポイント)
├── fizz_buzz.rs              (公開 API + ファクトリ)
├── domain/
│   ├── mod.rs
│   ├── model/
│   │   ├── mod.rs
│   │   ├── fizz_buzz_value.rs
│   │   └── fizz_buzz_list.rs
│   └── types/
│       ├── mod.rs
│       ├── fizz_buzz_type.rs
│       ├── fizz_buzz_type01.rs
│       ├── fizz_buzz_type02.rs
│       └── fizz_buzz_type03.rs
└── application/
    ├── mod.rs
    ├── fizz_buzz_command.rs
    ├── fizz_buzz_value_command.rs
    └── fizz_buzz_list_command.rs
```

### Rust のモジュールシステム

Rust の `mod` キーワードはモジュールを定義し、`pub` で公開/非公開を制御します。

```rust
// src/lib.rs
pub mod domain;
pub mod application;
pub mod fizz_buzz;
```

```rust
// src/domain/mod.rs
pub mod model;
pub mod types;
```

Go のパッケージシステム（大文字/小文字による公開制御）に対して、Rust では `pub` キーワードで明示的にアイテムを公開します。Java の `public`/`private` に近い感覚です。

## 9.4 依存関係

```
application → domain::types
application → domain::model
domain::model ← domain::types
```

- `application` は `domain` に依存
- `domain::types` は `domain::model` に依存（`FizzBuzzValue` を生成）
- 逆方向の依存は存在しない（単方向依存）

## 9.5 まとめ

この章では以下を実現しました。

| 原則 | 適用内容 |
|------|---------|
| SRP | types / model / application の責務分離 |
| OCP | trait による拡張ポイント |
| LSP | Box<dyn FizzBuzzType> による交換可能性 |
| ISP | 最小限のトレイト定義 |
| DIP | トレイトオブジェクトによる依存の抽象化 |

| モジュール | 責務 | 含まれる型 |
|-----------|------|-----------|
| `domain::types` | FizzBuzz 生成ルール | FizzBuzzType, Type01-03 |
| `domain::model` | 値オブジェクト | FizzBuzzValue, FizzBuzzList |
| `application` | コマンド実行 | FizzBuzzCommand, ValueCommand, ListCommand |

第 3 部を通じて、Rust のトレイト、構造体、モジュールシステムを使ったオブジェクト指向的な設計を学びました。次の第 4 部では、Rust の関数型プログラミング機能（クロージャ、イテレータ、Result/Option）を活用します。
