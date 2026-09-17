---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "Fork/Join と Pipeline パターン"
tags: [article, grokking-concurrency, rust, concurrency, forkjoin, pipeline]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、Fork/Join と Pipeline パターンを学びます。

---

## Fork/Join パターン

### 投票カウンター

```rust
use rayon::prelude::*;
use std::collections::HashMap;

/// Vote Counter using Fork/Join pattern
pub fn count_votes(votes: &[&str]) -> HashMap<String, usize> {
    if votes.is_empty() {
        return HashMap::new();
    }

    // Use parallel fold and reduce
    votes
        .par_iter()
        .fold(
            || HashMap::new(),
            |mut acc: HashMap<String, usize>, &vote| {
                *acc.entry(vote.to_string()).or_insert(0) += 1;
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

## Pipeline パターン

### Pipeline 構造体

```rust
/// Pipeline that processes items through multiple stages
pub struct Pipeline {
    stages: Vec<Box<dyn Fn(i32) -> i32 + Send>>,
}

impl Pipeline {
    pub fn new() -> Self {
        Pipeline { stages: Vec::new() }
    }

    pub fn add_stage<F>(mut self, processor: F) -> Self
    where
        F: Fn(i32) -> i32 + Send + 'static,
    {
        self.stages.push(Box::new(processor));
        self
    }

    pub fn process(&self, input: i32) -> i32 {
        self.stages.iter().fold(input, |acc, stage| stage(acc))
    }
}
```

---

### チャネルによる並行パイプライン

```rust
use std::sync::mpsc::{channel, Receiver, Sender};
use std::thread;

/// Concurrent pipeline using channels
pub fn concurrent_pipeline<T>(
    input: Vec<T>,
    processors: Vec<Box<dyn Fn(T) -> T + Send + 'static>>,
) -> Vec<T>
where
    T: Send + 'static + Clone,
{
    if processors.is_empty() {
        return input;
    }

    let (first_tx, mut current_rx): (Sender<T>, Receiver<T>) = channel();

    // Send initial input
    let input_thread = thread::spawn(move || {
        for item in input {
            first_tx.send(item).unwrap();
        }
    });

    // Create processor threads
    let mut handles = vec![input_thread];

    for processor in processors {
        let (tx, rx): (Sender<T>, Receiver<T>) = channel();
        let prev_rx = current_rx;
        current_rx = rx;

        let handle = thread::spawn(move || {
            while let Ok(item) = prev_rx.recv() {
                let result = processor(item);
                if tx.send(result).is_err() {
                    break;
                }
            }
        });
        handles.push(handle);
    }

    // Collect results
    let mut results = Vec::new();
    while let Ok(item) = current_rx.recv() {
        results.push(item);
    }

    for handle in handles {
        handle.join().unwrap();
    }

    results
}
```

---

## 使用例

```rust
fn main() {
    // Pipeline
    let pipeline = Pipeline::new()
        .add_stage(|x| x + 1)
        .add_stage(|x| x * 2)
        .add_stage(|x| x - 3);

    let result = pipeline.process(5);
    // (5 + 1) * 2 - 3 = 9
    println!("Result: {}", result);

    // Fork/Join
    let votes = vec!["A", "B", "A", "A", "B", "C"];
    let counts = count_votes(&votes);
    // {"A": 3, "B": 2, "C": 1}
}
```

---

## 次のステップ

[Part V](part-5.md) では、同期と排他制御を学びます。
