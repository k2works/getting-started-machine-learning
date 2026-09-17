---
type: Article
title: "Part V - 同期と排他制御"
description: "Mutex と Arc によるスレッドセーフな実装"
tags: [article, grokking-concurrency, rust, concurrency, synchronization, mutex]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、銀行口座を例に Mutex と Arc を使った同期を学びます。

---

## 銀行口座構造体

```rust
use std::sync::{Arc, Mutex};

/// A thread-safe bank account
#[derive(Debug)]
pub struct BankAccount {
    balance: Mutex<i64>,
    id: usize,
}

impl BankAccount {
    pub fn new(initial_balance: i64) -> Self {
        static COUNTER: std::sync::atomic::AtomicUsize =
            std::sync::atomic::AtomicUsize::new(0);
        BankAccount {
            balance: Mutex::new(initial_balance),
            id: COUNTER.fetch_add(1, std::sync::atomic::Ordering::SeqCst),
        }
    }

    pub fn get_balance(&self) -> i64 {
        *self.balance.lock().unwrap()
    }

    pub fn deposit(&self, amount: i64) {
        let mut balance = self.balance.lock().unwrap();
        *balance += amount;
    }

    pub fn withdraw(&self, amount: i64) -> bool {
        let mut balance = self.balance.lock().unwrap();
        if *balance >= amount {
            *balance -= amount;
            true
        } else {
            false
        }
    }
}
```

---

## デッドロック回避

```rust
/// Transfer money between accounts atomically, avoiding deadlock
pub fn transfer(
    from: &Arc<BankAccount>,
    to: &Arc<BankAccount>,
    amount: i64,
) -> bool {
    // Always lock in consistent order to avoid deadlock
    let (first, second, from_is_first) = if from.id < to.id {
        (from, to, true)
    } else {
        (to, from, false)
    };

    let mut first_guard = first.balance.lock().unwrap();
    let mut second_guard = second.balance.lock().unwrap();

    let (from_guard, to_guard) = if from_is_first {
        (&mut first_guard, &mut second_guard)
    } else {
        (&mut second_guard, &mut first_guard)
    };

    if **from_guard >= amount {
        **from_guard -= amount;
        **to_guard += amount;
        true
    } else {
        false
    }
}
```

---

## 同期プリミティブ

| 型 | 説明 |
|----|------|
| Mutex<T> | 排他ロック |
| RwLock<T> | 読み書きロック |
| Arc<T> | アトミック参照カウント |
| AtomicUsize | アトミック整数 |

---

## デッドロック回避戦略

1. **一貫したロック順序** - ID でソートしてロック
2. **try_lock** - ロック取得失敗時にリトライ
3. **タイムアウト** - 一定時間でロック解除

---

## 使用例

```rust
use std::thread;

fn main() {
    let account1 = Arc::new(BankAccount::new(1000));
    let account2 = Arc::new(BankAccount::new(1000));

    let mut handles = vec![];

    for i in 0..20 {
        let a1 = Arc::clone(&account1);
        let a2 = Arc::clone(&account2);

        handles.push(thread::spawn(move || {
            if i % 2 == 0 {
                transfer(&a1, &a2, 10);
            } else {
                transfer(&a2, &a1, 10);
            }
        }));
    }

    for handle in handles {
        handle.join().unwrap();
    }

    // Total remains 2000
    let total = account1.get_balance() + account2.get_balance();
    assert_eq!(total, 2000);
}
```

---

## 次のステップ

[Part VI](part-6.md) では、ノンブロッキング I/O を学びます。
