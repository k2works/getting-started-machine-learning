---
type: Article
title: "Part VII - 非同期プログラミング"
description: "Future と async/await の詳細"
tags: [article, grokking-concurrency, rust, concurrency, async, future]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、Rust の Future と async/await を詳しく学びます。

---

## Future トレイト

```rust
use std::future::Future;
use std::pin::Pin;
use std::task::{Context, Poll};

// Future は Poll を返す
pub trait Future {
    type Output;
    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output>;
}
```

---

## async/await の基本

```rust
// async fn は Future を返す
async fn compute() -> i32 {
    42
}

// await で結果を取得
async fn example() {
    let result = compute().await;
    println!("{}", result);
}
```

---

## 合成

```rust
use tokio;

// 順次実行
async fn sequential() -> i32 {
    let a = compute_a().await;
    let b = compute_b().await;
    a + b
}

// 並列実行
async fn parallel() -> (i32, i32, i32) {
    tokio::join!(
        compute_a(),
        compute_b(),
        compute_c(),
    )
}
```

---

## エラーハンドリング

```rust
async fn safe_computation() -> Result<i32, String> {
    let result = risky_computation().await?;
    Ok(result * 2)
}

async fn risky_computation() -> Result<i32, String> {
    // エラーの可能性がある処理
    Ok(42)
}
```

---

## キャンセル

```rust
use tokio::time::{timeout, Duration};

async fn with_timeout() {
    let result = timeout(
        Duration::from_secs(5),
        long_running_task()
    ).await;

    match result {
        Ok(value) => println!("Got: {:?}", value),
        Err(_) => println!("Timed out"),
    }
}
```

---

## select!

```rust
use tokio::select;

async fn race() {
    select! {
        result = task_a() => {
            println!("A finished first: {:?}", result);
        }
        result = task_b() => {
            println!("B finished first: {:?}", result);
        }
    }
}
```

---

## 次のステップ

[Part VIII](part-8.md) では、MapReduce パターンと並列配列を学びます。
