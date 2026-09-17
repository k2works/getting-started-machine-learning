---
type: Article
title: "第 8 章: デザインパターンの適用"
description: "前章では struct と trait を使ってカプセル化とポリモーフィズムを実現しました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: デザインパターンの適用

## 8.1 はじめに

前章では struct と trait を使ってカプセル化とポリモーフィズムを実現しました。この章では **デザインパターン** を適用して、設計をさらに改善します。

## 8.2 Value Object パターン

### FizzBuzzValue の強化

前章で作成した `FizzBuzzValue` は既に Value Object パターンを実現しています。

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct FizzBuzzValue {
    number: i32,
    value: String,
}
```

`#[derive(PartialEq)]` により、2 つの `FizzBuzzValue` を値で比較できます。`Clone` により値のコピーが可能です。Rust の所有権システムにより、Value Object の不変性が自然に保証されます。

## 8.3 First-Class Collection パターン

### FizzBuzzList 構造体

FizzBuzzValue のコレクションを専用の型でラップします。

```rust
pub struct FizzBuzzList {
    list: Vec<FizzBuzzValue>,
}

impl FizzBuzzList {
    const MAX_COUNT: usize = 100;

    pub fn new(fizz_buzz_type: &dyn FizzBuzzType) -> Self {
        let list = (1..=Self::MAX_COUNT as i32)
            .map(|n| fizz_buzz_type.generate(n))
            .collect();
        Self { list }
    }

    pub fn value(&self) -> &[FizzBuzzValue] {
        &self.list
    }

    pub fn len(&self) -> usize {
        self.list.len()
    }

    pub fn is_empty(&self) -> bool {
        self.list.is_empty()
    }
}

impl std::fmt::Display for FizzBuzzList {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let values: Vec<String> = self.list.iter().map(|v| v.value().to_string()).collect();
        write!(f, "{}", values.join("\n"))
    }
}
```

First-Class Collection パターンにより、コレクション操作の責務を `FizzBuzzList` に集約できます。`MAX_COUNT` は定数として定義し、マジックナンバーを排除します。

## 8.4 Command パターン

### FizzBuzzCommand トレイト

FizzBuzz の操作をコマンドとして抽象化します。

```rust
pub trait FizzBuzzCommand {
    fn execute(&self) -> String;
}
```

### 単一値コマンド

```rust
pub struct FizzBuzzValueCommand {
    fizz_buzz_type: Box<dyn FizzBuzzType>,
    number: i32,
}

impl FizzBuzzValueCommand {
    pub fn new(type_number: i32, number: i32) -> Result<Self, String> {
        let fizz_buzz_type = create(type_number)?;
        Ok(Self { fizz_buzz_type, number })
    }
}

impl FizzBuzzCommand for FizzBuzzValueCommand {
    fn execute(&self) -> String {
        self.fizz_buzz_type.generate(self.number).to_string()
    }
}
```

### リストコマンド

```rust
pub struct FizzBuzzListCommand {
    fizz_buzz_type: Box<dyn FizzBuzzType>,
}

impl FizzBuzzListCommand {
    pub fn new(type_number: i32) -> Result<Self, String> {
        let fizz_buzz_type = create(type_number)?;
        Ok(Self { fizz_buzz_type })
    }
}

impl FizzBuzzCommand for FizzBuzzListCommand {
    fn execute(&self) -> String {
        let list = FizzBuzzList::new(self.fizz_buzz_type.as_ref());
        list.to_string()
    }
}
```

`?` 演算子は `Result` のエラーを自動的に伝播させる Rust のイディオムです。Java の例外伝播や Go の `if err != nil { return err }` パターンに相当します。

## 8.5 他言語との比較

| パターン | Java | Go | Rust |
|---------|------|------|------|
| Value Object | `equals`/`hashCode` 手動実装 | 構造体比較（手動） | `#[derive(PartialEq)]` |
| First-Class Collection | クラスで `List<T>` をラップ | 構造体でスライスをラップ | 構造体で `Vec<T>` をラップ |
| Command | `interface` + クラス | `interface` + 構造体 | `trait` + 構造体 |
| Factory | `static` メソッド | 関数 | 関数 + `Result<Box<dyn Trait>>` |

## 8.6 まとめ

この章では以下のデザインパターンを適用しました。

| パターン | 目的 | Rust の実現方法 |
|---------|------|---------------|
| Value Object | 値の等価性保証 | `#[derive(PartialEq, Clone)]` |
| First-Class Collection | コレクション操作の集約 | `struct` + `Vec<T>` |
| Command | 操作の抽象化と遅延実行 | `trait` + `Box<dyn Trait>` |
| Factory Method | 型番号による生成 | `match` + `Result<Box<dyn Trait>>` |

次章では SOLID 原則の観点から設計を評価し、モジュール分割を行います。
