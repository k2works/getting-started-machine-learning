---
type: Article
title: "Part III: マルチタスキングとスケジューリング"
description: "Part II でスレッドによる並列処理を学びました。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 3.1 はじめに

Part II でスレッドによる並列処理を学びました。本章では、OS がどのように複数のタスクを切り替えて「同時に実行しているように見せる」かを学びます。ゲームループを題材に、**協調的マルチタスキング**の仕組みを 8 つの言語で実装します。

### マルチタスキングとは

1 つの CPU コアでも、タスクを高速に切り替えることで複数のタスクが同時に動いているように見せる技術です。

```
CPU 時間: [Task1] → [Task2] → [Task3] → [Task1] → [Task2] → [Task3] → ...
```

### 2 つのスケジューリング方式

| 方式 | 説明 | 利点 | 欠点 |
|------|------|------|------|
| プリエンプティブ | OS がタスクを強制的に中断 | 公平性が保証される | コンテキストスイッチのオーバーヘッド |
| 協調的 | タスクが自発的に制御を譲る | オーバーヘッドが小さい | 行儀の悪いタスクがシステムを占有 |

## 3.2 共通の本質

### ゲームループパターン

すべての言語で実装されるゲームループは、3 つのタスクを順次実行するパターンです：

```
while running:
    1. Input   — ユーザー入力の読み取り
    2. Compute — ゲーム状態の更新
    3. Render  — 画面の描画
```

### イベント同期パターン

複数のタスクを協調的に実行するため、**バイナリイベント**（フラグ）を使った同期パターンが全言語で共通しています：

```
Wait → フラグが True になるまでブロック
       → フラグを False にリセット（消費）
       → タスクを実行
       → フラグを True にセット（次のタスクに通知）
```

このパターンにより、ビジーウェイト（CPU を無駄に消費するループ）を避けつつ、タスク間の順序制御を実現します。

## 3.3 言語別実装比較

### 同期メカニズム

8 言語の同期メカニズムは、大きく 4 つのアプローチに分類できます：

| アプローチ | 言語 | メカニズム |
|-----------|------|----------|
| イベントオブジェクト | Python | `threading.Event` |
| 条件変数 + ロック | Java, C#, Scala, F# | `Condition` / `Monitor` |
| 条件変数 + Mutex | Rust | `Condvar` + `Mutex<bool>` |
| STM（トランザクショナルメモリ） | Haskell | `TVar` + `atomically` |
| Atom + ロック | Clojure | `atom` + `locking` |

### イベント同期の実装

#### 関数型ファースト言語

<details>
<summary>Haskell 実装（STM）</summary>

```haskell
import Control.Concurrent.STM

newtype ProcessorFreeEvent = ProcessorFreeEvent (TVar Bool)

newProcessorFreeEvent :: IO ProcessorFreeEvent
newProcessorFreeEvent = ProcessorFreeEvent <$> newTVarIO False

waitEvent :: ProcessorFreeEvent -> IO ()
waitEvent (ProcessorFreeEvent var) = atomically $ do
    ready <- readTVar var
    if ready
        then writeTVar var False
        else retry

signalEvent :: ProcessorFreeEvent -> IO ()
signalEvent (ProcessorFreeEvent var) = atomically $ writeTVar var True
```

**特徴**:

- **明示的なロックが不要** — `atomically` ブロックがトランザクションを保証
- **`retry`** — 条件が満たされるまで自動的にブロック・リトライ
- デッドロックが原理的に発生しない

</details>

<details>
<summary>Clojure 実装（atom + locking）</summary>

```clojure
(defn create-event []
  {:ready (atom false)
   :lock (Object.)})

(defn signal-event! [event]
  (locking (:lock event)
    (reset! (:ready event) true)
    (.notifyAll (:lock event))))

(defn wait-event! [event]
  (locking (:lock event)
    (while (not @(:ready event))
      (.wait (:lock event)))
    (reset! (:ready event) false)))
```

**特徴**:

- `atom` で単一値の状態管理、`locking` で Java の `synchronized` に相当
- `reset!` でアトミックな値の更新
- Java 相互運用で `wait`/`notifyAll` を直接利用

</details>

#### マルチパラダイム言語

<details>
<summary>Rust 実装（Condvar + Mutex）</summary>

```rust
use std::sync::{Condvar, Mutex};

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

**特徴**:

- `Mutex<bool>` と `Condvar` を分離した型安全な設計
- `condvar.wait(ready)` はロックガードを受け取り、自動的にアンロック→再ロック
- コンパイル時にロックの正しい使用を保証

</details>

<details>
<summary>Scala 実装（synchronized + @volatile）</summary>

```scala
class ProcessorFreeEvent:
  private val lock = new Object
  @volatile private var signaled = false

  def waitForSignal(): Unit = lock.synchronized {
    while !signaled do lock.wait()
  }

  def signal(): Unit = lock.synchronized {
    signaled = true
    lock.notifyAll()
  }

  def reset(): Unit = lock.synchronized {
    signaled = false
  }
```

**特徴**:

- `@volatile` でスレッド間のメモリ可視性を保証
- JVM の `synchronized` + `wait`/`notifyAll` を活用
- Scala 3 構文で簡潔に記述

</details>

<details>
<summary>F# 実装（Monitor + lock 式）</summary>

```fsharp
type ProcessorFreeEvent = {
    Lock: obj
    mutable Signaled: bool
}

module GameLoop =
    let createProcessorFreeEvent () : ProcessorFreeEvent =
        { Lock = obj(); Signaled = false }

    let waitForSignal (event: ProcessorFreeEvent) : unit =
        lock event.Lock (fun () ->
            while not event.Signaled do
                Monitor.Wait(event.Lock) |> ignore
        )

    let signal (event: ProcessorFreeEvent) : unit =
        lock event.Lock (fun () ->
            event.Signaled <- true
            Monitor.PulseAll(event.Lock)
        )
```

**特徴**:

- `lock` は式（文ではない）として値を返す
- `Monitor.PulseAll` で全待機スレッドを起床
- モジュール内関数として定義（OOP ではなく FP スタイル）

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装（ReentrantLock + Condition）</summary>

```java
public static class ProcessorFreeEvent {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean signaled = false;

    public void waitForSignal() {
        lock.lock();
        try {
            while (!signaled) {
                condition.await();
            }
            signaled = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    public void signal() {
        lock.lock();
        try {
            signaled = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
```

**特徴**:

- `ReentrantLock` + `Condition` で柔軟なロック制御
- `InterruptedException` の適切なハンドリング
- `try-finally` で確実にロックを解放

</details>

<details>
<summary>C# 実装（Monitor）</summary>

```csharp
public class ProcessorFreeEvent {
    private readonly object _lock = new();
    private bool _signaled;

    public void WaitForSignal() {
        lock (_lock) {
            while (!_signaled) {
                Monitor.Wait(_lock);
            }
            _signaled = false;
        }
    }

    public void Signal() {
        lock (_lock) {
            _signaled = true;
            Monitor.Pulse(_lock);
        }
    }
}
```

**特徴**:

- `lock` ステートメントが `Monitor.Enter`/`Monitor.Exit` を自動管理
- `Monitor.Pulse` で単一スレッドを起床
- Java より簡潔な構文

</details>

<details>
<summary>Python 実装（threading.Event）</summary>

```python
from threading import Event

processor_free = Event()
processor_free.set()

class Task(Thread):
    def __init__(self, func):
        super().__init__()
        self.func = func

    def run(self):
        while True:
            processor_free.wait()
            processor_free.clear()
            self.func()
            processor_free.set()
```

**特徴**:

- `Event` クラスが wait/set/clear を一体的に提供
- 最も高レベルな抽象化（ロックの詳細を隠蔽）
- デーモンスレッドでプロセス終了時に自動停止

</details>

### ゲームループの実装

#### タスク定義の比較

| 言語 | 定義方法 | 型 |
|------|---------|-----|
| Python | 関数 | `Callable` |
| Java | `record Task(String, Runnable)` | Record |
| C# | `record GameTask(string, Action)` | Record |
| Scala | `case class GameTask(String, () => Unit)` | Case Class |
| F# | `type GameTask = { Name: string; Action: unit -> unit }` | Record |
| Rust | `struct GameTask { name: String, action: Box<dyn Fn()> }` | Struct + Trait Object |
| Haskell | `data GameTask = GameTask { taskName :: String, taskAction :: IO () }` | Data Type |
| Clojure | クロージャ（暗黙的） | Map / Function |

#### ゲームループ本体

<details>
<summary>Rust 実装</summary>

```rust
pub fn game_loop(tasks: Vec<GameTask>, iterations: usize) {
    for _ in 0..iterations {
        for task in &tasks {
            task.execute();
        }
    }
}

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

</details>

<details>
<summary>Haskell 実装</summary>

```haskell
gameLoop :: [GameTask] -> Int -> IO ()
gameLoop tasks iterations =
    replicateM_ iterations $ forM_ tasks taskAction

cooperativeGameLoop :: [GameTask] -> ProcessorFreeEvent -> Int -> IO ()
cooperativeGameLoop tasks event iterations =
    replicateM_ iterations $ forM_ tasks $ \task -> do
        taskAction task
        signalEvent event
```

</details>

<details>
<summary>Java 実装</summary>

```java
public class GameLoop {
    private final Runnable inputTask;
    private final Runnable computeTask;
    private final Runnable renderTask;
    private final BooleanSupplier continueCondition;

    public void run() {
        while (continueCondition.getAsBoolean()) {
            inputTask.run();
            computeTask.run();
            renderTask.run();
        }
    }
}
```

</details>

## 3.4 比較分析

### 同期メカニズムの抽象度

```
高レベル  ┌─────────────────────────────────┐
          │ Haskell STM (retry/atomically)  │ ← ロック概念なし
          │ Python Event (set/wait/clear)   │ ← ロックを隠蔽
          ├─────────────────────────────────┤
中レベル  │ C# lock + Monitor              │ ← ロックを構文で簡略化
          │ Scala synchronized             │
          │ F# lock 式 + Monitor           │
          │ Clojure atom + locking         │
          ├─────────────────────────────────┤
低レベル  │ Java ReentrantLock + Condition  │ ← 明示的なロック管理
          │ Rust Condvar + Mutex            │ ← 型システムで安全性保証
          └─────────────────────────────────┘
```

### デッドロックリスクの比較

| 言語 | リスク | 理由 |
|------|-------|------|
| Haskell | なし | STM はトランザクショナル。競合時は自動リトライ |
| Clojure | 低い | `atom` は単一値の更新でロック競合が少ない |
| Rust | 低い | 型システムがロックの誤用を防止 |
| Python | 低い | `Event` は内部でロックを管理 |
| Scala / F# | 中程度 | `synchronized` の入れ子で発生可能 |
| Java / C# | 中程度 | 明示的ロックの順序管理が必要 |

### コンテキストスイッチのコスト

コンテキストスイッチの発生時には以下のコストが発生します（全言語共通）：

| コスト | 説明 |
|-------|------|
| レジスタ退避・復元 | マイクロ秒単位 |
| キャッシュ無効化 | 可変コスト（L1/L2/L3） |
| TLB フラッシュ | アドレス変換キャッシュのリセット |
| スケジューラオーバーヘッド | 次に実行するタスクの選択 |

ただし、Haskell の Green Thread は GHC ランタイムが管理するため、OS レベルのコンテキストスイッチよりも軽量です。

## 3.5 実践的な選択指針

### ゲームループ・リアルタイム処理に適した言語

**最も適している**:

- **Rust** — 予測可能なパフォーマンス、GC なし、`Condvar` による低レベル制御
- **C#** — ゲーム開発での実績（Unity）、`Monitor` の簡潔な構文

**理論的に優れている**:

- **Haskell** — STM によるデッドロックフリーな設計。ただしリアルタイム性は GC に依存

**プロトタイピング**:

- **Python** — `threading.Event` の高レベル API で素早く実装可能

### 協調的 vs プリエンプティブの使い分け

| ユースケース | 推奨方式 | 言語の対応 |
|-------------|---------|-----------|
| ゲームループ | 協調的 | 全言語で実装可能 |
| サーバー処理 | プリエンプティブ | OS スレッド（全言語） |
| GUI アプリ | イベント駆動 | Python (tkinter), C# (WPF), Java (Swing) |
| I/O 多重化 | 非同期 | Part VI-VII で詳述 |

## 3.6 まとめ

### 言語横断的な学び

1. **イベント同期パターンは普遍的** — wait/signal の二項対立はすべての言語で共通
2. **抽象度のスペクトラム** — Haskell STM（ロックなし）から Rust Condvar（明示的ロック）まで
3. **デッドロック回避の戦略は言語の思想に依存** — トランザクション（Haskell）、不変性（Clojure）、型システム（Rust）
4. **ゲームループは並行処理の基礎パターン** — Input→Compute→Render の順次実行は全言語で同一構造

### 次のステップ

[Part IV: タスク分解と並列パターン](./part-4-ch07-parallel-patterns.md) では、Fork/Join パターンとパイプラインパターンを学び、より複雑な並列処理の設計を探ります。

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part III - マルチタスキング](../python/part-3.md) |
| Java | [Part III - マルチタスキング](../java/part-3.md) |
| C# | [Part III - マルチタスキング](../csharp/part-3.md) |
| Scala | [Part III - マルチタスキング](../scala/part-3.md) |
| F# | [Part III - マルチタスキング](../fsharp/part-3.md) |
| Rust | [Part III - マルチタスキング](../rust/part-3.md) |
| Haskell | [Part III - マルチタスキング](../haskell/part-3.md) |
| Clojure | [Part III - マルチタスキング](../clojure/part-3.md) |
