---
type: Article
title: "Part III - マルチタスキングとスケジューリング"
description: "ゲームループと協調マルチタスキング"
tags: [article, grokking-concurrency, rust, concurrency, multitasking]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、ゲームループを例にマルチタスキングを学びます。

---

## ゲームタスク

```rust
/// A game task that can be processed
pub struct GameTask {
    pub name: String,
    pub action: Box<dyn Fn() + Send + Sync>,
}

impl GameTask {
    pub fn new<F>(name: &str, action: F) -> Self
    where
        F: Fn() + Send + Sync + 'static,
    {
        GameTask {
            name: name.to_string(),
            action: Box::new(action),
        }
    }

    pub fn execute(&self) {
        (self.action)();
    }
}
```

---

## プロセッサフリーイベント

```rust
use std::sync::{Condvar, Mutex};

/// Event for signaling processor availability
pub struct ProcessorFreeEvent {
    state: Mutex<bool>,
    condvar: Condvar,
}

impl ProcessorFreeEvent {
    pub fn new() -> Self {
        ProcessorFreeEvent {
            state: Mutex::new(false),
            condvar: Condvar::new(),
        }
    }

    pub fn wait(&self) {
        let mut ready = self.state.lock().unwrap();
        while !*ready {
            ready = self.condvar.wait(ready).unwrap();
        }
        *ready = false;
    }

    pub fn signal(&self) {
        let mut ready = self.state.lock().unwrap();
        *ready = true;
        self.condvar.notify_one();
    }
}
```

---

## ゲームループ

```rust
/// Simple game loop that processes tasks
pub fn game_loop(tasks: Vec<GameTask>, iterations: usize) {
    for _ in 0..iterations {
        for task in &tasks {
            task.execute();
        }
    }
}

/// Cooperative game loop with event signaling
pub fn cooperative_game_loop(
    tasks: Vec<Arc<GameTask>>,
    event: Arc<ProcessorFreeEvent>,
    iterations: usize,
) {
    for _ in 0..iterations {
        for task in &tasks {
            task.execute();
            event.signal();
            thread::yield_now();
        }
    }
}
```

---

## Condvar の使い方

| メソッド | 説明 |
|----------|------|
| wait(guard) | シグナルを待機 |
| notify_one() | 1つのスレッドを起床 |
| notify_all() | 全スレッドを起床 |

---

## 次のステップ

[Part IV](part-4.md) では、Fork/Join と Pipeline パターンを学びます。
