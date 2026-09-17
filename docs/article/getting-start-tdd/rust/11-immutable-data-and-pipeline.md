---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
description: "前章ではクロージャとイテレータを使った高階関数を学びました。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 はじめに

前章ではクロージャとイテレータを使った高階関数を学びました。この章では **イテレータチェーン** によるパイプライン処理と **不変データ** の考え方を深めます。

## 11.2 Rust の不変性

Rust では変数はデフォルトで不変（immutable）です。

```rust
let x = 5;       // 不変（デフォルト）
let mut y = 10;  // 可変（明示的）
```

`FizzBuzzValue` は `#[derive(Clone)]` で値のコピーが可能ですが、フィールドは非公開なため外部から変更できません。これにより不変性が自然に保証されます。

## 11.3 イテレータチェーン（パイプライン）

Rust のイテレータは **遅延評価** され、チェーンで連結できます。

```rust
// パイプライン: filter → map → collect
let fizz_numbers: Vec<i32> = list.value().iter()
    .filter(|v| v.value() == "Fizz")
    .map(|v| v.number())
    .collect();
```

### fold（集約）

```rust
// 全 FizzBuzz 値を改行区切りで連結
let joined = list.value().iter()
    .map(|v| v.value().to_string())
    .fold(String::new(), |acc, v| {
        if acc.is_empty() { v } else { format!("{}\n{}", acc, v) }
    });
```

### take（先頭 N 件）

```rust
let first_five: Vec<String> = list.value().iter()
    .take(5)
    .map(|v| v.value().to_string())
    .collect();
```

### group_by 相当の処理

Rust の標準ライブラリには `group_by` がないため、`HashMap` を使って実装します。

```rust
use std::collections::HashMap;

pub fn group_by_value(&self) -> HashMap<String, Vec<&FizzBuzzValue>> {
    let mut groups: HashMap<String, Vec<&FizzBuzzValue>> = HashMap::new();
    for v in &self.list {
        groups.entry(v.value().to_string()).or_default().push(v);
    }
    groups
}

pub fn count_by_value(&self) -> HashMap<String, usize> {
    self.group_by_value().into_iter()
        .map(|(k, v)| (k, v.len()))
        .collect()
}
```

## 11.4 FizzBuzzList のパイプライン操作

```rust
impl FizzBuzzList {
    pub fn take(&self, n: usize) -> Vec<&FizzBuzzValue> {
        self.list.iter().take(n).collect()
    }

    pub fn join(&self, separator: &str) -> String {
        self.list.iter()
            .map(|v| v.value().to_string())
            .collect::<Vec<_>>()
            .join(separator)
    }

    pub fn reduce<F>(&self, f: F) -> String
    where
        F: Fn(String, &FizzBuzzValue) -> String,
    {
        self.list.iter().fold(String::new(), |acc, v| f(acc, v))
    }
}
```

## 11.5 まとめ

| 概念 | Rust | PHP | Go |
|------|------|-----|-----|
| 不変デフォルト | `let x = 5` | — | — |
| パイプライン | `.iter().filter().map().collect()` | `array_filter` → `array_map` | `for` ループのチェーン |
| fold | `.fold(init, f)` | `array_reduce($arr, f, init)` | `for` + 累積変数 |
| 遅延評価 | イテレータは遅延 | — | — |

次章では、Result/Option 型を使ったエラーハンドリングと型安全性を学びます。
