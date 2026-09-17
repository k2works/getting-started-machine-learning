---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "async/await によるノンブロッキング処理"
tags: [article, grokking-concurrency, rust, concurrency, async, tokio]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、Rust の async/await を使用したノンブロッキング I/O を学びます。

---

## ブロッキング vs ノンブロッキング

### ブロッキング I/O

```rust
use std::fs;

// スレッドは I/O 完了まで待機
let content = fs::read_to_string("file.txt").unwrap();
```

### ノンブロッキング I/O (async)

```rust
use tokio::fs;

async fn read_file_async(path: &str) -> String {
    fs::read_to_string(path).await.unwrap()
}
```

---

## Tokio の基本

```rust
use tokio;

#[tokio::main]
async fn main() {
    let result = fetch_data("https://example.com").await;
    println!("{}", result);
}

async fn fetch_data(url: &str) -> String {
    // 非同期 HTTP リクエスト
    tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
    format!("Data from {}", url)
}
```

---

## 並行実行

```rust
use tokio;

#[tokio::main]
async fn main() {
    // 並行実行
    let (r1, r2, r3) = tokio::join!(
        fetch_data("url1"),
        fetch_data("url2"),
        fetch_data("url3"),
    );

    println!("{}, {}, {}", r1, r2, r3);
}
```

---

## メリット

| 項目 | ブロッキング | async |
|------|-------------|-------|
| スレッド消費 | 待機中も占有 | 解放 |
| スケーラビリティ | 低い | 高い |
| コード複雑さ | 低い | やや高い |

---

## 次のステップ

[Part VII](part-7.md) では、非同期プログラミングの詳細を学びます。
