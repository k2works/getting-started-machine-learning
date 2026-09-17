---
type: Article
title: "Part I - 並行処理の基礎"
description: "逐次処理とパスワードクラッカーの実装"
tags: [article, grokking-concurrency, rust, concurrency, sequential]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 並行処理の基礎

## 概要

本章では、並行処理の基礎として逐次処理を学びます。

---

## パスワードクラッカーの実装

### SHA-256 ハッシュ計算

```rust
use sha2::{Sha256, Digest};

/// Compute SHA-256 hash of a password
pub fn get_crypto_hash(password: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(password.as_bytes());
    let result = hasher.finalize();
    format!("{:x}", result)
}
```

---

### ブルートフォース探索

```rust
/// Crack password by brute force (sequential)
pub fn crack_password(
    crypto_hash: &str,
    alphabet: &[char],
    length: usize,
) -> Option<String> {
    if length == 0 {
        return None;
    }
    crack_recursive(crypto_hash, alphabet, String::new(), length)
}

fn crack_recursive(
    crypto_hash: &str,
    alphabet: &[char],
    prefix: String,
    remaining: usize,
) -> Option<String> {
    if remaining == 0 {
        if get_crypto_hash(&prefix) == crypto_hash {
            return Some(prefix);
        }
        return None;
    }

    for &c in alphabet {
        let mut candidate = prefix.clone();
        candidate.push(c);
        if let Some(result) = crack_recursive(
            crypto_hash, alphabet, candidate, remaining - 1
        ) {
            return Some(result);
        }
    }
    None
}
```

---

## 使用例

```rust
fn main() {
    let alphabet: Vec<char> = "ab".chars().collect();
    let target_hash = get_crypto_hash("ab");

    match crack_password(&target_hash, &alphabet, 2) {
        Some(password) => println!("Found: {}", password),
        None => println!("Not found"),
    }
}
```

---

## Rust の特徴

| 概念 | 説明 |
|------|------|
| Option<T> | 値が存在しない可能性を表現 |
| Iterator | 遅延評価による効率的な処理 |
| 所有権 | メモリ安全性の保証 |

---

## 次のステップ

[Part II](part-2.md) では、スレッドを使った並行処理を学びます。
