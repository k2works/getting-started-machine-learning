---
type: Article
title: "第 7 章: カプセル化とポリモーフィズム"
description: "第 1 部では手続き型の FizzBuzz プログラムを TDD で構築しました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: カプセル化とポリモーフィズム

## 7.1 はじめに

第 1 部では手続き型の FizzBuzz プログラムを TDD で構築しました。この章からは **オブジェクト指向プログラミング** の要素を Rust で実現していきます。まず **カプセル化** と **ポリモーフィズム** を導入し、手続き型コードを構造化された設計に進化させます。

## 7.2 手続き型コードの課題

第 1 部で作成した `generate` 関数は手続き型プログラミングの典型例です。

```rust
pub fn generate(number: i32) -> String {
    match (number % 3, number % 5) {
        (0, 0) => "FizzBuzz".to_string(),
        (0, _) => "Fizz".to_string(),
        (_, 0) => "Buzz".to_string(),
        _ => number.to_string(),
    }
}
```

この設計の課題は、新しい FizzBuzz タイプ（数値のみ返す、FizzBuzz のみ返す等）を追加する場合に、既存の関数を直接修正する必要があることです。

## 7.3 カプセル化 — 値オブジェクトの導入

### FizzBuzzValue 構造体

数値と FizzBuzz の結果をまとめた値オブジェクトを作成します。

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct FizzBuzzValue {
    number: i32,
    value: String,
}

impl FizzBuzzValue {
    pub fn new(number: i32, value: String) -> Self {
        Self { number, value }
    }

    pub fn number(&self) -> i32 {
        self.number
    }

    pub fn value(&self) -> &str {
        &self.value
    }
}

impl std::fmt::Display for FizzBuzzValue {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.value)
    }
}
```

Rust ではフィールドをデフォルトで非公開（private）にし、メソッド経由でアクセスする設計が標準です。`#[derive(Debug, Clone, PartialEq)]` は Go の手動実装や Java の `equals`/`hashCode` に相当する機能を自動生成します。

## 7.4 ポリモーフィズム — トレイトの導入

### FizzBuzzType トレイト

FizzBuzz のタイプを抽象化するトレイトを定義します。

```rust
pub trait FizzBuzzType {
    fn generate(&self, number: i32) -> FizzBuzzValue;
}
```

### 3 つの実装

```rust
// タイプ 1: 通常の FizzBuzz
pub struct FizzBuzzType01;
impl FizzBuzzType for FizzBuzzType01 {
    fn generate(&self, number: i32) -> FizzBuzzValue {
        let value = match (number % 3, number % 5) {
            (0, 0) => "FizzBuzz".to_string(),
            (0, _) => "Fizz".to_string(),
            (_, 0) => "Buzz".to_string(),
            _ => number.to_string(),
        };
        FizzBuzzValue::new(number, value)
    }
}

// タイプ 2: 数値のみ
pub struct FizzBuzzType02;
impl FizzBuzzType for FizzBuzzType02 {
    fn generate(&self, number: i32) -> FizzBuzzValue {
        FizzBuzzValue::new(number, number.to_string())
    }
}

// タイプ 3: FizzBuzz のみ（Fizz/Buzz/FizzBuzz 以外は空文字）
pub struct FizzBuzzType03;
impl FizzBuzzType for FizzBuzzType03 {
    fn generate(&self, number: i32) -> FizzBuzzValue {
        let value = match (number % 3, number % 5) {
            (0, 0) => "FizzBuzz".to_string(),
            (0, _) => "Fizz".to_string(),
            (_, 0) => "Buzz".to_string(),
            _ => String::new(),
        };
        FizzBuzzValue::new(number, value)
    }
}
```

`trait` は Java の `interface` や Go の暗黙的インターフェースに相当しますが、Rust では `impl Trait for Struct` で明示的に実装します。

## 7.5 ファクトリ関数

タイプ番号から適切な実装を返すファクトリ関数を作成します。

```rust
pub fn create(type_number: i32) -> Result<Box<dyn FizzBuzzType>, String> {
    match type_number {
        1 => Ok(Box::new(FizzBuzzType01)),
        2 => Ok(Box::new(FizzBuzzType02)),
        3 => Ok(Box::new(FizzBuzzType03)),
        _ => Err(format!("タイプ{}は見つかりません", type_number)),
    }
}
```

`Box<dyn FizzBuzzType>` はトレイトオブジェクトで、Java の `FizzBuzzType` インターフェース型や Go の `interface{}` に相当します。`Result` 型で不正なタイプ番号のエラーハンドリングも行います。

## 7.6 まとめ

この章では以下を学びました。

| 概念 | Rust の実現方法 | 他言語の対応 |
|------|---------------|-------------|
| カプセル化 | `struct` + 非公開フィールド + メソッド | Java: private + getter |
| ポリモーフィズム | `trait` + `impl Trait for Struct` | Java: interface |
| 値オブジェクト | `#[derive(PartialEq)]` + 不変フィールド | Java: equals/hashCode |
| ファクトリ | `Result<Box<dyn Trait>>` | Java: Factory Method |

次章では、デザインパターン（Command、First-Class Collection）を適用していきます。
