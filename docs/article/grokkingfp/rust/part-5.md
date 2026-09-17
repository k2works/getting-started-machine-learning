---
type: Article
title: "Rust で学ぶ関数型プログラミング Part V: 並行処理"
description: "Part IV では async/await とストリーム処理を学びました。"
tags: [article, grokkingfp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Rust で学ぶ関数型プログラミング Part V: 並行処理

## はじめに

Part IV では async/await とストリーム処理を学びました。Part V では、関数型プログラミングにおける**並行処理**を扱います。

Scala では Ref（アトミック参照）と Fiber（軽量スレッド）を使いますが、Rust では `Arc`、`Mutex`/`RwLock`、tokio タスク、そしてチャネルを使って同様の概念を実現します。

## 第10章: 並行・並列処理

### 10.1 共有状態の基本 - Arc と Mutex

Rust の所有権システムでは、複数のスレッドからデータを共有するために `Arc`（Atomic Reference Counting）と `Mutex` を組み合わせます。

```rust
use std::sync::Arc;
use tokio::sync::Mutex;

/// アトミックカウンター
#[derive(Clone)]
pub struct AtomicCounter {
    value: Arc<Mutex<i32>>,
}

impl AtomicCounter {
    pub fn new(initial: i32) -> Self {
        Self {
            value: Arc::new(Mutex::new(initial)),
        }
    }

    pub async fn get(&self) -> i32 {
        *self.value.lock().await
    }

    pub async fn increment(&self) {
        let mut guard = self.value.lock().await;
        *guard += 1;
    }

    pub async fn update<F>(&self, f: F)
    where
        F: FnOnce(i32) -> i32,
    {
        let mut guard = self.value.lock().await;
        *guard = f(*guard);
    }
}
```

Scala の `Ref` と比較すると：

| Scala | Rust |
|-------|------|
| `Ref.of[IO, Int](0)` | `Arc::new(Mutex::new(0))` |
| `ref.get` | `arc.lock().await` |
| `ref.update(_ + 1)` | `*guard += 1` |

### 10.2 RwLock - 読み書きロック

複数の読み取りを同時に許可しつつ、書き込みは排他的に行う場合は `RwLock` を使います。

```rust
use tokio::sync::RwLock;

#[derive(Clone)]
pub struct SharedValue<T> {
    value: Arc<RwLock<T>>,
}

impl<T: Clone> SharedValue<T> {
    pub fn new(initial: T) -> Self {
        Self {
            value: Arc::new(RwLock::new(initial)),
        }
    }

    pub async fn read(&self) -> T {
        self.value.read().await.clone()
    }

    pub async fn write(&self, value: T) {
        *self.value.write().await = value;
    }
}
```

### 10.3 チェックインのリアルタイム集計

都市へのチェックインをリアルタイムで集計する例を実装します。

```rust
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct City {
    pub name: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CityStats {
    pub city: City,
    pub check_ins: i32,
}

/// トップN都市を計算（純粋関数）
pub fn top_cities(city_check_ins: &HashMap<City, i32>, n: usize) -> Vec<CityStats> {
    let mut stats: Vec<CityStats> = city_check_ins
        .iter()
        .map(|(city, &check_ins)| CityStats::new(city.clone(), check_ins))
        .collect();

    stats.sort_by(|a, b| b.check_ins.cmp(&a.check_ins));
    stats.truncate(n);
    stats
}
```

純粋関数として `top_cities` を実装することで、テストが容易になり、並行処理の複雑さから分離できます。

### 10.4 共有状態を使ったチェックイン処理

```rust
#[derive(Clone)]
pub struct CheckInStore {
    check_ins: Arc<RwLock<HashMap<City, i32>>>,
}

impl CheckInStore {
    pub fn new() -> Self {
        Self {
            check_ins: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    pub async fn store_check_in(&self, city: City) {
        let mut guard = self.check_ins.write().await;
        *guard.entry(city).or_insert(0) += 1;
    }

    pub async fn get_top(&self, n: usize) -> Vec<CityStats> {
        let check_ins = self.check_ins.read().await;
        top_cities(&check_ins, n)
    }
}
```

### 10.5 並列実行 - tokio::spawn と JoinHandle

複数のタスクを並列実行するには `tokio::spawn` を使います。

```rust
pub async fn run_parallel<T, U, F, Fut>(items: Vec<T>, f: F) -> Vec<U>
where
    T: Send + 'static,
    U: Send + 'static,
    F: Fn(T) -> Fut + Send + Sync + 'static,
    Fut: std::future::Future<Output = U> + Send,
{
    let f = Arc::new(f);
    let handles: Vec<JoinHandle<U>> = items
        .into_iter()
        .map(|item| {
            let f = Arc::clone(&f);
            tokio::spawn(async move { f(item).await })
        })
        .collect();

    let mut results = Vec::with_capacity(handles.len());
    for handle in handles {
        results.push(handle.await.unwrap());
    }
    results
}
```

Scala の `parSequence` に相当します：

| Scala | Rust |
|-------|------|
| `List(io1, io2, io3).parSequence` | `futures::future::join_all(...)` |
| `List.fill(3)(io).parSequence` | `tokio::spawn` + `join_all` |

### 10.6 タスクのキャンセル

バックグラウンドタスクをキャンセルするには `JoinHandle::abort()` を使います。

```rust
pub struct BackgroundTask<T> {
    handle: JoinHandle<T>,
}

impl<T> BackgroundTask<T> {
    pub fn cancel(self) {
        self.handle.abort();
    }

    pub async fn join(self) -> Result<T, tokio::task::JoinError> {
        self.handle.await
    }
}

/// バックグラウンドでカウントアップするタスクを開始
pub fn start_counter_task(
    interval: std::time::Duration,
) -> (AtomicCounter, BackgroundTask<()>) {
    let counter = AtomicCounter::new(0);
    let counter_clone = counter.clone();

    let handle = tokio::spawn(async move {
        loop {
            tokio::time::sleep(interval).await;
            counter_clone.increment().await;
        }
    });

    (counter, BackgroundTask { handle })
}
```

### 10.7 チェックイン処理システム

複数のタスクを並行実行して、チェックインの処理とランキングの更新を行います。

```rust
pub struct ProcessingCheckIns {
    store: CheckInStore,
    ranking: SharedValue<Vec<CityStats>>,
    tasks: Vec<JoinHandle<()>>,
}

impl ProcessingCheckIns {
    pub async fn current_ranking(&self) -> Vec<CityStats> {
        self.ranking.read().await
    }

    pub fn stop(self) {
        for task in self.tasks {
            task.abort();
        }
    }
}

pub async fn process_check_ins(
    cities: Vec<City>,
    ranking_update_interval: std::time::Duration,
) -> ProcessingCheckIns {
    let store = CheckInStore::new();
    let ranking = SharedValue::new(Vec::new());

    // チェックイン処理タスク
    let store_clone = store.clone();
    let check_in_task = tokio::spawn(async move {
        for city in cities {
            store_clone.store_check_in(city).await;
        }
    });

    // ランキング更新タスク
    let store_clone = store.clone();
    let ranking_clone = ranking.clone();
    let ranking_task = tokio::spawn(async move {
        loop {
            tokio::time::sleep(ranking_update_interval).await;
            let top = store_clone.get_top(3).await;
            ranking_clone.write(top).await;
        }
    });

    ProcessingCheckIns {
        store,
        ranking,
        tasks: vec![check_in_task, ranking_task],
    }
}
```

## 第11章: チャネルと並行パターン

### 11.1 mpsc チャネル - 複数送信者・単一受信者

Go の goroutine と channel に似た概念で、並行処理間の通信を行います。

```rust
use tokio::sync::mpsc;

pub async fn basic_mpsc_example() -> Vec<i32> {
    let (tx, mut rx) = mpsc::channel(10);

    // 送信タスク
    tokio::spawn(async move {
        for i in 1..=3 {
            tx.send(i).await.unwrap();
        }
    });

    // 受信
    let mut results = Vec::new();
    while let Some(value) = rx.recv().await {
        results.push(value);
    }

    results
}
```

### 11.2 oneshot チャネル - 一回限りの通信

結果を一度だけ返す場合に使います。

```rust
use tokio::sync::oneshot;

pub async fn compute_with_oneshot(input: i32) -> i32 {
    let (tx, rx) = oneshot::channel();

    tokio::spawn(async move {
        let result = input * input;
        tx.send(result).unwrap();
    });

    rx.await.unwrap()
}

/// 複数の計算を並列実行して結果を収集
pub async fn parallel_compute(inputs: Vec<i32>) -> Vec<i32> {
    let mut receivers = Vec::with_capacity(inputs.len());

    for input in inputs {
        let (tx, rx) = oneshot::channel();
        tokio::spawn(async move {
            let result = input * input;
            let _ = tx.send(result);
        });
        receivers.push(rx);
    }

    let mut results = Vec::with_capacity(receivers.len());
    for rx in receivers {
        results.push(rx.await.unwrap());
    }
    results
}
```

### 11.3 broadcast チャネル - 複数受信者

複数の受信者に同じメッセージを配信します。

```rust
use tokio::sync::broadcast;

pub async fn broadcast_example(receiver_count: usize) -> Vec<Vec<String>> {
    let (tx, _) = broadcast::channel(16);

    // 受信タスクを起動
    let mut handles = Vec::new();
    for _ in 0..receiver_count {
        let mut rx = tx.subscribe();
        handles.push(tokio::spawn(async move {
            let mut received = Vec::new();
            while let Ok(msg) = rx.recv().await {
                received.push(msg);
            }
            received
        }));
    }

    // メッセージを送信
    tx.send("Hello".to_string()).unwrap();
    tx.send("World".to_string()).unwrap();
    drop(tx);

    // 結果を収集
    let mut results = Vec::new();
    for handle in handles {
        results.push(handle.await.unwrap());
    }
    results
}
```

### 11.4 ワーカープールパターン

固定数のワーカーで並列処理を行うパターンです。

```rust
pub async fn worker_pool<T, U, F>(items: Vec<T>, worker_count: usize, f: F) -> Vec<U>
where
    T: Send + 'static,
    U: Send + 'static,
    F: Fn(T) -> U + Send + Sync + Clone + 'static,
{
    let (work_tx, work_rx) = async_channel::bounded(items.len());
    let (result_tx, mut result_rx) = mpsc::channel(items.len());

    let work_rx = Arc::new(work_rx);

    // ワーカーを起動
    let mut handles = Vec::new();
    for _ in 0..worker_count {
        let work_rx = Arc::clone(&work_rx);
        let result_tx = result_tx.clone();
        let f = f.clone();

        handles.push(tokio::spawn(async move {
            while let Ok(item) = work_rx.recv().await {
                let result = f(item);
                let _ = result_tx.send(result).await;
            }
        }));
    }

    // 作業をキューに追加
    for item in items {
        work_tx.send(item).await.unwrap();
    }
    work_tx.close();
    drop(result_tx);

    // 結果を収集
    let mut results = Vec::new();
    while let Some(result) = result_rx.recv().await {
        results.push(result);
    }

    results
}
```

### 11.5 パイプラインパターン

ストリーム処理をパイプライン形式で構築します。

```rust
pub struct Pipeline<T> {
    receiver: mpsc::Receiver<T>,
}

impl<T: Send + 'static> Pipeline<T> {
    pub fn from_iter<I>(iter: I) -> Self
    where
        I: IntoIterator<Item = T> + Send + 'static,
        I::IntoIter: Send,
    {
        let (tx, rx) = mpsc::channel(100);
        tokio::spawn(async move {
            for item in iter {
                if tx.send(item).await.is_err() { break; }
            }
        });
        Pipeline { receiver: rx }
    }

    pub fn map<U, F>(self, f: F) -> Pipeline<U>
    where
        U: Send + 'static,
        F: Fn(T) -> U + Send + 'static,
    {
        let (tx, rx) = mpsc::channel(100);
        let mut receiver = self.receiver;
        tokio::spawn(async move {
            while let Some(item) = receiver.recv().await {
                if tx.send(f(item)).await.is_err() { break; }
            }
        });
        Pipeline { receiver: rx }
    }

    pub async fn collect(mut self) -> Vec<T> {
        let mut results = Vec::new();
        while let Some(item) = self.receiver.recv().await {
            results.push(item);
        }
        results
    }
}

// 使用例
let results = Pipeline::from_iter(1..=10)
    .map(|x| x * 2)
    .filter(|x| *x > 10)
    .collect()
    .await;
// [12, 14, 16, 18, 20]
```

### 11.6 アクターパターン

メッセージパッシングで状態を管理するアクターモデルを実装します。

```rust
pub enum CounterMessage {
    Increment,
    Decrement,
    Get(oneshot::Sender<i32>),
    Stop,
}

pub struct CounterActor {
    sender: mpsc::Sender<CounterMessage>,
}

impl CounterActor {
    pub fn new(initial: i32) -> Self {
        let (tx, mut rx) = mpsc::channel::<CounterMessage>(32);

        tokio::spawn(async move {
            let mut value = initial;

            while let Some(msg) = rx.recv().await {
                match msg {
                    CounterMessage::Increment => value += 1,
                    CounterMessage::Decrement => value -= 1,
                    CounterMessage::Get(reply) => {
                        let _ = reply.send(value);
                    }
                    CounterMessage::Stop => break,
                }
            }
        });

        CounterActor { sender: tx }
    }

    pub async fn increment(&self) {
        let _ = self.sender.send(CounterMessage::Increment).await;
    }

    pub async fn get(&self) -> i32 {
        let (tx, rx) = oneshot::channel();
        let _ = self.sender.send(CounterMessage::Get(tx)).await;
        rx.await.unwrap_or(0)
    }
}
```

### 11.7 銀行口座アクター

より実践的なアクターの例として、銀行口座を実装します。

```rust
pub enum BankMessage {
    Deposit(i32),
    Withdraw(i32, oneshot::Sender<Result<(), String>>),
    GetBalance(oneshot::Sender<i32>),
    Stop,
}

pub struct BankAccount {
    sender: mpsc::Sender<BankMessage>,
}

impl BankAccount {
    pub fn new(initial_balance: i32) -> Self {
        let (tx, mut rx) = mpsc::channel::<BankMessage>(32);

        tokio::spawn(async move {
            let mut balance = initial_balance;

            while let Some(msg) = rx.recv().await {
                match msg {
                    BankMessage::Deposit(amount) => balance += amount,
                    BankMessage::Withdraw(amount, reply) => {
                        if balance >= amount {
                            balance -= amount;
                            let _ = reply.send(Ok(()));
                        } else {
                            let _ = reply.send(Err("Insufficient funds".to_string()));
                        }
                    }
                    BankMessage::GetBalance(reply) => { let _ = reply.send(balance); }
                    BankMessage::Stop => break,
                }
            }
        });

        BankAccount { sender: tx }
    }

    pub async fn withdraw(&self, amount: i32) -> Result<(), String> {
        let (tx, rx) = oneshot::channel();
        let _ = self.sender.send(BankMessage::Withdraw(amount, tx)).await;
        rx.await.unwrap_or(Err("Actor not responding".to_string()))
    }
}
```

### 11.8 イベントバス

Pub/Sub パターンでイベントを配信します。

```rust
#[derive(Debug, Clone)]
pub enum Event {
    UserLoggedIn { user_id: String },
    UserLoggedOut { user_id: String },
    MessageSent { from: String, to: String, content: String },
}

pub struct EventBus {
    sender: broadcast::Sender<Event>,
}

impl EventBus {
    pub fn new() -> Self {
        let (sender, _) = broadcast::channel(100);
        EventBus { sender }
    }

    pub fn subscribe(&self) -> broadcast::Receiver<Event> {
        self.sender.subscribe()
    }

    pub fn publish(&self, event: Event) {
        let _ = self.sender.send(event);
    }
}
```

### 11.9 セマフォによる同時実行制限

同時実行数を制限する場合は `Semaphore` を使います。

```rust
use tokio::sync::Semaphore;

pub async fn rate_limited_process<T, U, F>(
    items: Vec<T>,
    max_concurrent: usize,
    f: F,
) -> Vec<U>
where
    T: Send + 'static,
    U: Send + Clone + 'static,
    F: Fn(T) -> U + Send + Sync + Clone + 'static,
{
    let semaphore = Arc::new(Semaphore::new(max_concurrent));
    let results = Arc::new(Mutex::new(Vec::with_capacity(items.len())));

    let mut handles = Vec::new();
    for item in items {
        let semaphore = Arc::clone(&semaphore);
        let results = Arc::clone(&results);
        let f = f.clone();

        handles.push(tokio::spawn(async move {
            let _permit = semaphore.acquire().await.unwrap();
            let result = f(item);
            results.lock().await.push(result);
        }));
    }

    for handle in handles {
        let _ = handle.await;
    }

    // ...
}
```

## Scala との対比

| 概念 | Scala (cats-effect) | Rust (tokio) |
|------|---------------------|--------------|
| アトミック参照 | `Ref[IO, A]` | `Arc<Mutex<A>>` / `Arc<RwLock<A>>` |
| 軽量スレッド | `Fiber[IO, E, A]` | `JoinHandle<A>` |
| 並列実行 | `parSequence` | `join_all` / `tokio::spawn` |
| 永続実行 | `foreverM` | `loop { ... }` |
| キャンセル | `fiber.cancel` | `handle.abort()` |
| チャネル | `Queue[IO, A]` | `mpsc::channel` |
| 同時実行制限 | `Semaphore` | `tokio::sync::Semaphore` |

## まとめ

Part V では以下を学びました：

### 第10章: 並行・並列処理

1. **Arc と Mutex**: 共有状態の安全な管理
2. **RwLock**: 読み書きロックによる効率的な共有
3. **tokio::spawn**: 並列タスクの起動
4. **JoinHandle**: タスクの完了待機とキャンセル
5. **純粋関数との分離**: ビジネスロジックと並行処理の分離

### 第11章: チャネルと並行パターン

1. **mpsc**: 複数送信者・単一受信者チャネル
2. **oneshot**: 一回限りの結果返却
3. **broadcast**: 複数受信者へのメッセージ配信
4. **ワーカープール**: 固定数ワーカーによる並列処理
5. **パイプライン**: ストリーム処理のパイプライン構築
6. **アクター**: メッセージパッシングによる状態管理
7. **セマフォ**: 同時実行数の制限

Rust の所有権システムと tokio の組み合わせは、コンパイル時にデータ競合を防ぎつつ、高性能な並行処理を実現します。

## 次のステップ

Part VI では、以下のトピックを扱う予定です：

- 実践的なアプリケーション構築
- トレイトによる抽象化
- テスト戦略
- 依存性注入

これらのパターンを学ぶことで、保守性の高い実際のアプリケーションを構築できるようになります。
