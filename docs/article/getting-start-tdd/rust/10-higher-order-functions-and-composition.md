---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "Rust は関数型プログラミングの機能を豊富にサポートしています。"
tags: [article, getting-start-tdd, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

Rust は関数型プログラミングの機能を豊富にサポートしています。この章では **クロージャ** と **イテレータ** を中心に、高階関数と関数合成の考え方を学びます。

## 10.2 クロージャ

Rust のクロージャは環境をキャプチャする無名関数です。`Fn`、`FnMut`、`FnOnce` の 3 つのトレイトに分類されます。

```rust
// クロージャの基本
let add = |a: i32, b: i32| a + b;
assert_eq!(5, add(2, 3));

// 環境のキャプチャ
let factor = 3;
let is_multiple = |n: i32| n % factor == 0;
assert!(is_multiple(9));
```

PHP のアロー関数 `fn($x) => $x * 2` や Go のクロージャに相当しますが、Rust のクロージャは所有権の観点から 3 種類に分類される点が独特です。

## 10.3 イテレータと高階関数

### map — 要素の変換

```rust
let values: Vec<String> = list.value().iter()
    .map(|v| v.value().to_string())
    .collect();
```

### filter — 要素の選別

```rust
let fizz_values: Vec<&FizzBuzzValue> = list.value().iter()
    .filter(|v| v.value() == "Fizz")
    .collect();
```

### find — 最初の一致要素

```rust
let first_buzz = list.value().iter()
    .find(|v| v.value() == "Buzz");
```

### any / all — 条件判定

```rust
let has_fizzbuzz = list.value().iter()
    .any(|v| v.value() == "FizzBuzz");

let all_non_empty = list.value().iter()
    .all(|v| !v.value().is_empty());
```

## 10.4 FizzBuzzList への関数型メソッド追加

```rust
impl FizzBuzzList {
    pub fn filter_by_value(&self, target: &str) -> Vec<&FizzBuzzValue> {
        self.list.iter().filter(|v| v.value() == target).collect()
    }

    pub fn map_values(&self) -> Vec<String> {
        self.list.iter().map(|v| v.value().to_string()).collect()
    }

    pub fn find_first(&self, target: &str) -> Option<&FizzBuzzValue> {
        self.list.iter().find(|v| v.value() == target)
    }

    pub fn any_match(&self, target: &str) -> bool {
        self.list.iter().any(|v| v.value() == target)
    }

    pub fn all_match(&self, predicate: impl Fn(&FizzBuzzValue) -> bool) -> bool {
        self.list.iter().all(|v| predicate(v))
    }
}
```

## 10.5 まとめ

| 概念 | Rust | PHP | Go |
|------|------|-----|-----|
| クロージャ | `\|x\| x * 2` | `fn($x) => $x * 2` | `func(x int) int { return x * 2 }` |
| map | `.iter().map(f).collect()` | `array_map(f, $arr)` | `for` + `append` |
| filter | `.iter().filter(f).collect()` | `array_filter($arr, f)` | `for` + `if` + `append` |
| find | `.iter().find(f)` → `Option` | — | `for` + `if` + `return` |

次章では、イテレータチェーンによるパイプライン処理と不変データの考え方を学びます。
