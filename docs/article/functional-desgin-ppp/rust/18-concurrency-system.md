---
type: Article
title: "第18章: 並行処理システム"
description: "本章では、Rust の並行処理機能を使って、状態機械、イベントバス、タスクキューなどを実装します。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第18章: 並行処理システム

## はじめに

本章では、Rust の並行処理機能を使って、状態機械、イベントバス、タスクキューなどを実装します。Rust の所有権システムと Arc/Mutex を活用した安全な並行プログラミングを学びます。

## 1. 状態機械（電話の例）

```rust
use std::sync::{Arc, Mutex};

/// 電話の状態
#[derive(Debug, Clone, PartialEq)]
pub enum PhoneState {
    Idle,
    Ringing,
    Connected,
    OnHold,
}

/// 電話イベント
#[derive(Debug, Clone)]
pub enum PhoneEvent {
    IncomingCall { caller: String },
    Answer,
    HangUp,
    Hold,
    Resume,
}

/// 電話
#[derive(Debug, Clone)]
pub struct Phone {
    pub state: PhoneState,
    pub caller: Option<String>,
}

impl Phone {
    pub fn new() -> Phone {
        Phone {
            state: PhoneState::Idle,
            caller: None,
        }
    }

    pub fn handle_event(&self, event: PhoneEvent) -> Result<Phone, String> {
        match (&self.state, event) {
            (PhoneState::Idle, PhoneEvent::IncomingCall { caller }) => {
                Ok(Phone {
                    state: PhoneState::Ringing,
                    caller: Some(caller),
                })
            }
            (PhoneState::Ringing, PhoneEvent::Answer) => {
                Ok(Phone {
                    state: PhoneState::Connected,
                    caller: self.caller.clone(),
                })
            }
            (PhoneState::Ringing, PhoneEvent::HangUp) => {
                Ok(Phone::new())
            }
            (PhoneState::Connected, PhoneEvent::Hold) => {
                Ok(Phone {
                    state: PhoneState::OnHold,
                    caller: self.caller.clone(),
                })
            }
            (PhoneState::Connected, PhoneEvent::HangUp) => {
                Ok(Phone::new())
            }
            (PhoneState::OnHold, PhoneEvent::Resume) => {
                Ok(Phone {
                    state: PhoneState::Connected,
                    caller: self.caller.clone(),
                })
            }
            (PhoneState::OnHold, PhoneEvent::HangUp) => {
                Ok(Phone::new())
            }
            (state, event) => {
                Err(format!("Invalid event {:?} in state {:?}", event, state))
            }
        }
    }
}
```

## 2. イベントバス

```rust
use std::collections::HashMap;

/// イベントハンドラー型
pub type EventHandler = Box<dyn Fn(&str) + Send + Sync>;

/// イベントバス
pub struct EventBus {
    handlers: Arc<Mutex<HashMap<String, Vec<EventHandler>>>>,
}

impl EventBus {
    pub fn new() -> EventBus {
        EventBus {
            handlers: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    pub fn subscribe(&self, event_type: &str, handler: EventHandler) {
        let mut handlers = self.handlers.lock().unwrap();
        handlers
            .entry(event_type.to_string())
            .or_insert_with(Vec::new)
            .push(handler);
    }

    pub fn publish(&self, event_type: &str, data: &str) {
        let handlers = self.handlers.lock().unwrap();
        if let Some(event_handlers) = handlers.get(event_type) {
            for handler in event_handlers {
                handler(data);
            }
        }
    }
}
```

## 3. 非同期タスクキュー

```rust
use tokio::sync::mpsc;
use tokio::task::JoinHandle;

/// タスク
pub type Task = Box<dyn FnOnce() + Send + 'static>;

/// タスクキュー
pub struct TaskQueue {
    sender: mpsc::Sender<Task>,
    #[allow(dead_code)]
    handles: Vec<JoinHandle<()>>,
}

impl TaskQueue {
    pub fn new(num_workers: usize) -> TaskQueue {
        let (sender, receiver) = mpsc::channel::<Task>(100);
        let receiver = Arc::new(Mutex::new(receiver));
        let mut handles = Vec::new();

        for _ in 0..num_workers {
            let receiver = Arc::clone(&receiver);
            let handle = tokio::spawn(async move {
                loop {
                    let task = {
                        let mut rx = receiver.lock().unwrap();
                        rx.try_recv().ok()
                    };
                    
                    if let Some(task) = task {
                        task();
                    } else {
                        tokio::time::sleep(tokio::time::Duration::from_millis(10)).await;
                    }
                }
            });
            handles.push(handle);
        }

        TaskQueue { sender, handles }
    }

    pub async fn submit(&self, task: Task) -> Result<(), String> {
        self.sender
            .send(task)
            .await
            .map_err(|e| e.to_string())
    }
}
```

## 4. アトミックカウンター

```rust
use std::sync::atomic::{AtomicI64, Ordering};

/// スレッドセーフなカウンター
pub struct AtomicCounter {
    value: AtomicI64,
}

impl AtomicCounter {
    pub fn new(initial: i64) -> AtomicCounter {
        AtomicCounter {
            value: AtomicI64::new(initial),
        }
    }

    pub fn increment(&self) -> i64 {
        self.value.fetch_add(1, Ordering::SeqCst) + 1
    }

    pub fn decrement(&self) -> i64 {
        self.value.fetch_sub(1, Ordering::SeqCst) - 1
    }

    pub fn get(&self) -> i64 {
        self.value.load(Ordering::SeqCst)
    }

    pub fn add(&self, delta: i64) -> i64 {
        self.value.fetch_add(delta, Ordering::SeqCst) + delta
    }
}
```

## 5. アクション処理システム

```rust
/// アクションの種類
#[derive(Debug, Clone)]
pub enum Action {
    Start,
    Stop,
    Process { data: String },
    Shutdown,
}

/// アクションハンドラー
pub trait ActionHandler: Send + Sync {
    fn handle(&self, action: &Action) -> Result<String, String>;
}

/// ロギングハンドラー
pub struct LoggingHandler {
    logs: Arc<Mutex<Vec<String>>>,
}

impl ActionHandler for LoggingHandler {
    fn handle(&self, action: &Action) -> Result<String, String> {
        let log = format!("Handled action: {:?}", action);
        self.logs.lock().unwrap().push(log.clone());
        Ok(log)
    }
}

/// アクションディスパッチャー
pub struct ActionDispatcher {
    handlers: Vec<Arc<dyn ActionHandler>>,
}

impl ActionDispatcher {
    pub fn new() -> ActionDispatcher {
        ActionDispatcher {
            handlers: Vec::new(),
        }
    }

    pub fn register(&mut self, handler: Arc<dyn ActionHandler>) {
        self.handlers.push(handler);
    }

    pub fn dispatch(&self, action: &Action) -> Vec<Result<String, String>> {
        self.handlers
            .iter()
            .map(|h| h.handle(action))
            .collect()
    }
}
```

## 6. パターンの適用

1. **状態機械**: ADT による状態とイベントの表現
2. **イベントバス**: Observer パターンの並行版
3. **タスクキュー**: Command パターンの非同期版
4. **Arc/Mutex**: スレッドセーフな共有状態

## 7. Rust での特徴

### 所有権による安全性

```rust
// Arc で所有権を共有、Mutex で排他制御
let handlers: Arc<Mutex<HashMap<String, Vec<EventHandler>>>> = 
    Arc::new(Mutex::new(HashMap::new()));
```

### Send/Sync トレイト

```rust
// Send: 他のスレッドに送信可能
// Sync: 複数スレッドから参照可能
pub trait ActionHandler: Send + Sync {
    fn handle(&self, action: &Action) -> Result<String, String>;
}
```

## まとめ

本章では、並行処理システムを通じて：

1. 状態機械による並行状態管理
2. イベントバスによる非同期メッセージング
3. タスクキューによる並行処理
4. Arc/Mutex による安全な共有状態

を学びました。Rust の所有権システムにより、データ競合を防ぎながら並行プログラミングが可能です。

## 参考コード

- ソースコード: `apps/rust/part6/src/chapter18.rs`

## 次章予告

次章では、**Wa-Tor シミュレーション**を通じて、セルラーオートマトンを関数型で実装します。
