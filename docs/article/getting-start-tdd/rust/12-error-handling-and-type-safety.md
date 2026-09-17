---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章ではイテレータチェーンによるパイプライン処理を学びました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章ではイテレータチェーンによるパイプライン処理を学びました。この最終章では、Rust の **Result / Option 型** を使ったエラーハンドリングと **enum** による型安全性を学びます。

## 12.2 Result 型によるエラーハンドリング

Rust には例外（Exception）がありません。代わりに `Result<T, E>` 型を使います。

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

### ? 演算子によるエラー伝播

```rust
pub fn new(type_number: i32, number: i32) -> Result<Self, String> {
    let fizz_buzz_type = create(type_number)?;  // エラーなら早期リターン
    Ok(Self { fizz_buzz_type, number })
}
```

`?` 演算子は Go の `if err != nil { return err }` パターンを簡潔に表現するものです。

## 12.3 Option 型

値が存在しない可能性を表現する型です。Rust には `null` がありません。

```rust
pub fn find_first(&self, target: &str) -> Option<&FizzBuzzValue> {
    self.list.iter().find(|v| v.value() == target)
}

// 使用例
match list.find_first("Buzz") {
    Some(value) => println!("Found: {}", value),
    None => println!("Not found"),
}
```

## 12.4 enum による型安全な定数

### FizzBuzzTypeName enum

```rust
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum FizzBuzzTypeName {
    Standard = 1,
    NumberOnly = 2,
    FizzBuzzOnly = 3,
}

impl FizzBuzzTypeName {
    pub fn from_number(n: i32) -> Result<Self, String> {
        match n {
            1 => Ok(Self::Standard),
            2 => Ok(Self::NumberOnly),
            3 => Ok(Self::FizzBuzzOnly),
            _ => Err(format!("タイプ{}は見つかりません", n)),
        }
    }

    pub fn create_type(&self) -> Box<dyn FizzBuzzType> {
        match self {
            Self::Standard => Box::new(FizzBuzzType01),
            Self::NumberOnly => Box::new(FizzBuzzType02),
            Self::FizzBuzzOnly => Box::new(FizzBuzzType03),
        }
    }

    pub fn label(&self) -> &str {
        match self {
            Self::Standard => "通常",
            Self::NumberOnly => "数値のみ",
            Self::FizzBuzzOnly => "FizzBuzzのみ",
        }
    }
}
```

Rust の `enum` は PHP の `enum`（8.1+）や Java の `enum` よりも強力で、パターンマッチングの網羅性チェックをコンパイラが行います。

## 12.5 他言語との比較

| 概念 | Rust | PHP | Go | Java |
|------|------|-----|-----|------|
| エラーハンドリング | `Result<T, E>` | `try-catch` | `error` 戻り値 | `try-catch` |
| null 安全 | `Option<T>` | nullable types | ゼロ値 | `Optional<T>` |
| 列挙型 | `enum`（代数的データ型） | `enum`（PHP 8.1+） | `const` + `iota` | `enum` |
| エラー伝播 | `?` 演算子 | `throw` | `if err != nil` | `throws` |
| パターンマッチ | `match`（網羅性チェック） | `match`（PHP 8.0+） | `switch` | `switch` |

## 12.6 まとめ

この章では以下を学びました。

| 概念 | Rust の実現方法 |
|------|---------------|
| エラーハンドリング | `Result<T, E>` + `?` 演算子 |
| null 安全 | `Option<T>` + パターンマッチ |
| 列挙型 | `enum` + メソッド実装 |
| 型安全なファクトリ | `enum` → `Box<dyn Trait>` 変換 |

全 12 章を通じて、Rust の TDD 基本サイクル、開発環境の自動化、オブジェクト指向的な設計、関数型プログラミングの活用を一通り学びました。Rust の所有権システム、トレイト、enum、Result/Option は、安全で堅牢なソフトウェアを構築するための強力な基盤です。
