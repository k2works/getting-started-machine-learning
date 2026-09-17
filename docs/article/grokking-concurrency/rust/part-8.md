---
type: Article
title: "Part VIII - 分散並列処理"
description: "MapReduce パターンと Rayon"
tags: [article, grokking-concurrency, rust, concurrency, mapreduce, rayon]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、MapReduce パターンと Rayon の並列イテレータを学びます。

---

## MapReduce パターン

### ワードカウント実装

```rust
use rayon::prelude::*;
use std::collections::HashMap;

/// Map: Convert text to (word, 1) pairs
pub fn map(text: &str) -> Vec<(String, usize)> {
    text.to_lowercase()
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(|word| (word.to_string(), 1))
        .collect()
}

/// Reduce: Aggregate word counts
pub fn reduce(pairs: Vec<(String, usize)>) -> HashMap<String, usize> {
    let mut result = HashMap::new();
    for (word, count) in pairs {
        *result.entry(word).or_insert(0) += count;
    }
    result
}

/// MapReduce: Count words using parallel execution
pub fn count_words(texts: &[&str]) -> HashMap<String, usize> {
    // Map phase (parallel)
    let mapped: Vec<(String, usize)> = texts
        .par_iter()
        .flat_map(|text| map(text))
        .collect();

    // Reduce phase
    reduce(mapped)
}
```

---

## 完全並列版

```rust
/// Parallel reduce using Rayon
pub fn count_words_full_parallel(texts: &[&str]) -> HashMap<String, usize> {
    texts
        .par_iter()
        .flat_map(|text| map(text))
        .fold(
            || HashMap::new(),
            |mut acc, (word, count)| {
                *acc.entry(word).or_insert(0) += count;
                acc
            },
        )
        .reduce(
            || HashMap::new(),
            |mut a, b| {
                for (k, v) in b {
                    *a.entry(k).or_insert(0) += v;
                }
                a
            },
        )
}
```

---

## 使用例

```rust
fn main() {
    let texts = vec![
        "hello world",
        "hello rust",
        "world of rust",
    ];

    let result = count_words(&texts);
    // {"hello": 2, "world": 2, "rust": 2, "of": 1}
}
```

---

## Rayon の並列イテレータ

```rust
use rayon::prelude::*;

// 並列 map
let doubled: Vec<_> = (1..1000000)
    .into_par_iter()
    .map(|x| x * 2)
    .collect();

// 並列 filter + map
let processed: Vec<_> = data
    .par_iter()
    .filter(|&x| *x > 0)
    .map(|x| x * 2)
    .collect();

// 並列 fold + reduce
let sum: i32 = (1..1000000)
    .into_par_iter()
    .fold(|| 0, |acc, x| acc + x)
    .reduce(|| 0, |a, b| a + b);
```

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | Option、Iterator |
| II | スレッド | std::thread、Send/Sync |
| III | マルチタスキング | Condvar、ゲームループ |
| IV | 並列パターン | Fork/Join、Pipeline |
| V | 同期 | Mutex、Arc、デッドロック回避 |
| VI | ノンブロッキング | async/await |
| VII | 非同期 | Future、Tokio |
| VIII | 分散処理 | MapReduce、Rayon |

---

## 参考資料

- [The Rust Programming Language - Fearless Concurrency](https://doc.rust-lang.org/book/ch16-00-concurrency.html)
- [Rayon Documentation](https://docs.rs/rayon/)
- [Tokio Documentation](https://docs.rs/tokio/)
- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
