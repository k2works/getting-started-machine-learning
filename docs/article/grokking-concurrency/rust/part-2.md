---
type: Article
title: "Part II - プロセスとスレッド"
description: "std::thread によるスレッド操作"
tags: [article, grokking-concurrency, rust, concurrency, thread]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、Rust の std::thread を使ったスレッド操作を学びます。

---

## スレッドの基本

### スレッドの作成

```rust
use std::thread::{self, JoinHandle};

/// Create and start a new thread
pub fn create_thread<F>(f: F) -> JoinHandle<()>
where
    F: FnOnce() + Send + 'static,
{
    thread::spawn(f)
}
```

---

### 複数スレッドの実行

```rust
/// Run multiple threads and wait for completion
pub fn run_threads<F>(count: usize, f: F)
where
    F: Fn(usize) + Send + Sync + Clone + 'static,
{
    let handles: Vec<_> = (0..count)
        .map(|i| {
            let f = f.clone();
            thread::spawn(move || f(i))
        })
        .collect();

    for handle in handles {
        handle.join().unwrap();
    }
}
```

---

## 並列パスワードクラッカー

```rust
use rayon::prelude::*;

/// Crack password using parallel execution with Rayon
pub fn crack_password_parallel(
    crypto_hash: &str,
    alphabet: &[char],
    length: usize,
) -> Option<String> {
    if length == 0 {
        return None;
    }

    let first_chars: Vec<char> = alphabet.to_vec();

    first_chars
        .par_iter()
        .find_map_any(|&first| {
            crack_recursive(crypto_hash, alphabet, first.to_string(), length - 1)
        })
}
```

---

## Send と Sync トレイト

| トレイト | 説明 | 例 |
|----------|------|-----|
| Send | 所有権を別スレッドに移動可能 | 多くの型 |
| Sync | 参照を複数スレッドで共有可能 | &T where T: Send |
| !Send | スレッド間移動不可 | Rc<T> |
| !Sync | 参照共有不可 | Cell<T> |

---

## 使用例

```rust
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

fn main() {
    let counter = Arc::new(AtomicUsize::new(0));

    let counter_clone = Arc::clone(&counter);
    run_threads(5, move |_| {
        counter_clone.fetch_add(1, Ordering::SeqCst);
    });

    println!("Count: {}", counter.load(Ordering::SeqCst)); // 5
}
```

---

## 次のステップ

[Part III](part-3.md) では、マルチタスキングとゲームループを学びます。
