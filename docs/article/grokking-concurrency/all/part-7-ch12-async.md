---
type: Article
title: "Part VII: 非同期プログラミング"
description: "Part VI でノンブロッキング I/O を学びました。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 7.1 はじめに

Part VI でノンブロッキング I/O を学びました。本章では、非同期プログラミングの中核となる **Future/Promise**、**コルーチン**、**async/await** の概念を 8 つの言語で比較します。

### なぜ非同期か

スレッドベースの並行処理はメモリと生成コストが高く、数千の同時接続を扱うには非効率です。非同期プログラミングは、少数のスレッドで多数の I/O 操作を効率的に多重化します。

## 7.2 共通の本質

### 非同期処理の 3 つの構成要素

| 構成要素 | 説明 | 例 |
|---------|------|-----|
| **Future/Promise** | 将来の計算結果を表す型 | `Future<T>`, `Task<T>`, `Promise` |
| **コルーチン** | 中断・再開可能な関数 | `async def`, `async fn`, `go` ブロック |
| **イベントループ** | 完了した I/O を検出し、対応するコルーチンを再開する | `asyncio`, `tokio`, GHC RTS |

### async/await のセマンティクス

```
async function fetch():
    result = await networkCall()   ← ここで中断、スレッドを解放
    process(result)                ← ネットワーク完了後に再開
```

`await` は「ここで結果を待つが、スレッドをブロックしない」という宣言です。

## 7.3 言語別実装比較

### async/await のアプローチ

8 言語の非同期プログラミングは、大きく 4 つのモデルに分類できます：

| モデル | 言語 | 特徴 |
|-------|------|------|
| 言語組み込み async/await | Python, C#, Rust | コンパイラ/インタプリタがステートマシンを生成 |
| モナド的合成 | Scala, F#, Haskell | `for` 内包表記、計算式、`do` 記法 |
| Virtual Thread | Java | JVM レベルの軽量スレッド |
| CSP (チャネル) | Clojure | `go` ブロック + チャネル通信 |

### Future/Promise の型表現

#### 関数型ファースト言語

<details>
<summary>Haskell 実装（async ライブラリ）</summary>

```haskell
import Control.Concurrent.Async

-- 逐次実行
sequential :: IO (Int, Int)
sequential = do
    a <- computeA
    b <- computeB
    return (a, b)

-- 並列実行
parallel :: IO (Int, Int)
parallel = concurrently computeA computeB

-- レース（最初に完了した方を返す）
raceExample :: IO (Either String Int)
raceExample = race fetchFromServerA fetchFromServerB

-- リソース安全な非同期
withAsync longRunningTask $ \handle -> do
    result <- wait handle
    processResult result
```

**特徴**:

- `async` / `wait` / `cancel` で非同期タスクを管理
- `concurrently` で 2 つのタスクを同時実行
- `race` で最初に完了した結果を取得
- `withAsync` でリソースリーク防止（自動キャンセル）

| 関数 | 用途 |
|------|------|
| `async` | バックグラウンドタスク開始 |
| `wait` | 結果を待機 |
| `cancel` | キャンセル |
| `race` | 最初の完了 |
| `concurrently` | 両方を並列実行 |
| `mapConcurrently` | リスト並列マップ |

</details>

<details>
<summary>Clojure 実装（core.async）</summary>

```clojure
(require '[clojure.core.async :as async :refer [chan go >! <! >!! <!! alt! timeout]])

;; チャネル作成と送受信
(def ch (chan 10))
(go (>! ch "hello"))       ;; 非ブロッキング送信（go ブロック内）
(go (println (<! ch)))     ;; 非ブロッキング受信（go ブロック内）

;; タイムアウト
(go
  (alt!
    ch ([v] (println "受信:" v))
    (timeout 1000) (println "タイムアウト")))

;; パイプライン並列処理
(let [in (chan 10)
      out (chan 10)]
  (async/pipeline 4 out (map inc) in)
  (async/onto-chan! in [1 2 3 4 5])
  (<!! (async/into [] out)))
;; => [2 3 4 5 6]
```

**特徴**:

- **CSP モデル**: チャネルを介したプロセス間通信
- `go` ブロック: ステートマシンにコンパイルされる軽量スレッド
- `<!` / `>!`: go ブロック内の非ブロッキング操作
- `<!!` / `>!!`: 通常スレッドのブロッキング操作
- `alt!`: 複数チャネルからの選択的受信

</details>

#### マルチパラダイム言語

<details>
<summary>Rust 実装（async/await + tokio）</summary>

```rust
// async 関数定義
async fn compute() -> i32 {
    42
}

// 逐次実行
async fn sequential() -> i32 {
    let a = compute_a().await;
    let b = compute_b().await;
    a + b
}

// 並列実行
async fn parallel() -> (i32, i32, i32) {
    tokio::join!(compute_a(), compute_b(), compute_c())
}

// タイムアウト
async fn with_timeout() {
    match timeout(Duration::from_secs(5), long_task()).await {
        Ok(value) => println!("Got: {:?}", value),
        Err(_) => println!("Timed out"),
    }
}

// 選択的実行
tokio::select! {
    result = task_a() => println!("A: {:?}", result),
    result = task_b() => println!("B: {:?}", result),
}
```

**特徴**:

- **ゼロコスト Future**: コンパイラがステートマシンを生成。ヒープ割り当てなし
- **Pin**: 自己参照構造体のメモリ安全性を保証
- `tokio::join!`: 複数 Future を並列実行
- `tokio::select!`: 最初に完了した Future を処理
- `?` 演算子: 非同期コンテキストでのエラー伝播

</details>

<details>
<summary>Scala 実装（Future + Promise）</summary>

```scala
import scala.concurrent.{Future, Promise, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// Future 合成
val f1: Future[Int] = Future(10)
val f2: Future[Int] = Future(20)

// for 内包表記（モナド的合成）
val sum: Future[Int] = for
  a <- f1
  b <- f2
yield a + b

// 並列実行
val results: Future[List[String]] = Future.sequence(futures)

// Promise（書き込み側）
val promise = Promise[Int]()
val future = promise.future
promise.success(42)

// エラーハンドリング
val recovered = future.recover {
  case _: RuntimeException => 0
}
```

**特徴**:

- `Future` は読み取り専用、`Promise` は書き込み側
- `for` 内包表記で `map` / `flatMap` を自然に合成
- `ExecutionContext` が暗黙的にスレッドプールを提供
- `recover` / `recoverWith` でエラーからの回復

</details>

<details>
<summary>F# 実装（Async ワークフロー）</summary>

```fsharp
// Async 計算式
let computation : Async<int> =
    async {
        printfn "Starting..."
        do! Async.Sleep(1000)
        printfn "Done!"
        return 42
    }

// 逐次実行
let sequential =
    async {
        let! a = computeA()
        let! b = computeB()
        return a + b
    }

// 並列実行
let parallel =
    [computeA(); computeB(); computeC()]
    |> Async.Parallel

// キャンセル
let cancellable (ct: CancellationToken) =
    async {
        for i in 1..100 do
            ct.ThrowIfCancellationRequested()
            do! Async.Sleep(100)
    }

// Task との相互運用
let asyncFromTask = someTask |> Async.AwaitTask
let taskFromAsync = someAsync |> Async.StartAsTask
```

**特徴**:

- `async { }` 計算式: `let!` でバインド、`do!` で実行
- `Async.Parallel` で並列合成
- `CancellationToken` で協調的キャンセル
- C# `Task` との相互運用

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装（CompletableFuture + Virtual Thread）</summary>

```java
// CompletableFuture チェーン
CompletableFuture.supplyAsync(() -> fetchData())
    .thenApply(data -> process(data))
    .thenAccept(result -> save(result));

// 並列実行
CompletableFuture.allOf(future1, future2).join();

// Virtual Thread（Java 21+）
Thread.startVirtualThread(() -> {
    // I/O バウンドの処理
});

var executor = Executors.newVirtualThreadPerTaskExecutor();
```

**特徴**:

- `CompletableFuture`: Promise と Future の二重の役割
- `thenApply` / `thenAccept` / `thenCompose` でチェーン
- Virtual Thread: JVM が管理する軽量スレッド（数百万生成可能）
- `allOf` / `anyOf` で複数 Future の合成

| 項目 | Platform Thread | Virtual Thread |
|------|-----------------|----------------|
| 生成コスト | 高い | 非常に低い |
| メモリ | ~1MB | ~KB |
| 最大数 | 数千 | 数百万 |
| ブロッキング | OS スレッド占有 | アンマウント |

</details>

<details>
<summary>C# 実装（async/await）</summary>

```csharp
// async/await（言語機能）
async Task<string> FetchDataAsync() {
    var data = await httpClient.GetStringAsync(url);
    return Process(data);
}

// 並列実行
await Task.WhenAll(task1, task2, task3);

// 最初の完了
var completed = await Task.WhenAny(task1, task2);

// ValueTask（最適化版）
public ValueTask<int> GetCachedValueAsync() {
    if (_cache.TryGetValue(key, out var value))
        return new ValueTask<int>(value);
    return new ValueTask<int>(FetchFromDatabaseAsync());
}
```

**特徴**:

- `async` / `await` は言語機能（C# 5.0+）
- コンパイラがステートマシンを自動生成
- `Task<T>` / `ValueTask<T>` の使い分け
- `ConfigureAwait(false)` でコンテキスト制御

</details>

<details>
<summary>Python 実装（asyncio）</summary>

```python
import asyncio

# コルーチン定義
async def fetch_data(url: str) -> str:
    await asyncio.sleep(1)  # ノンブロッキング遅延
    return f"Data from {url}"

# 並列実行
async def main():
    results = await asyncio.gather(
        fetch_data("url1"),
        fetch_data("url2"),
        fetch_data("url3"),
    )
    print(results)

asyncio.run(main())
```

**特徴**:

- `async def` でコルーチンを定義
- `await` で中断点を宣言
- `asyncio.gather` で複数コルーチンを並列実行
- `asyncio.run` でイベントループを開始

| API | 用途 |
|-----|------|
| `asyncio.run(coro)` | イベントループ起動 |
| `asyncio.create_task(coro)` | タスクスケジュール |
| `asyncio.gather(*coros)` | 並列実行 |
| `asyncio.wait(tasks)` | 待機 |
| `asyncio.sleep(delay)` | ノンブロッキング遅延 |

</details>

## 7.4 比較分析

### 非同期モデルの分類

| モデル | 言語 | 内部実装 | メモリ効率 |
|-------|------|---------|-----------|
| スタックレスコルーチン | Rust, Python, C# | コンパイラ生成ステートマシン | 最高 |
| スタックフルコルーチン | Java (Virtual Thread) | JVM 管理の仮想スタック | 高い |
| 計算式/モナド | Scala, F#, Haskell | 関数的合成 | 高い |
| CSP チャネル | Clojure | go ブロック + チャネル | 中程度 |

### 並列合成の構文

| 操作 | Python | Java | C# | Scala | F# | Rust | Haskell | Clojure |
|------|--------|------|-----|-------|-----|------|---------|---------|
| 全並列 | `gather()` | `allOf()` | `WhenAll()` | `sequence()` | `Parallel` | `join!` | `concurrently` | `alt!` |
| 最初の完了 | `wait(FIRST_COMPLETED)` | `anyOf()` | `WhenAny()` | `firstCompletedOf` | — | `select!` | `race` | `alt!` |
| タイムアウト | `wait_for()` | `orTimeout()` | タスク+タイマー | `Await.result(_, duration)` | `Async.Sleep` | `timeout()` | `timeout` | `(timeout ms)` |
| エラー回復 | `try/except` | `exceptionally()` | `try/catch` | `recover` | `try/with` | `?` 演算子 | `try` | `try/catch` |

### コルーチンの実装方式

```
コンパイラ生成     ┌──────────────────────────────┐
ステートマシン    │ Rust async fn               │ ← ゼロコスト
                  │ C# async Task               │
                  │ Python async def             │
                  ├──────────────────────────────┤
JVM 管理          │ Java Virtual Thread          │ ← スタックフル
                  ├──────────────────────────────┤
関数的合成        │ Scala Future + for           │ ← モナド的
                  │ F# async { let! }            │
                  │ Haskell IO + async           │
                  ├──────────────────────────────┤
CSP               │ Clojure go ブロック           │ ← チャネルベース
                  └──────────────────────────────┘
```

## 7.5 実践的な選択指針

### I/O バウンド処理に適した言語

**最も適している**:

- **Rust (tokio)** — ゼロコスト async/await。メモリ効率が最高
- **C#** — 言語レベルの async/await。.NET エコシステムとの統合
- **Java (Virtual Thread)** — 既存のブロッキング API をそのまま軽量化

**表現力が高い**:

- **Scala** — `for` 内包表記で Future を自然に合成
- **F#** — `async { }` 計算式で宣言的に記述
- **Haskell** — `async` ライブラリで安全な並行処理

**プロトタイピング**:

- **Python** — `asyncio` で素早く非同期サーバーを構築
- **Clojure** — CSP モデルでチャネルベースの設計

### CPU バウンド vs I/O バウンドの使い分け

| 処理タイプ | 推奨アプローチ | 言語例 |
|-----------|-------------|--------|
| I/O バウンド | async/await, コルーチン | Python asyncio, Rust tokio, C# Task |
| CPU バウンド | スレッドプール, 並列イテレータ | Rayon, parallelStream, multiprocessing |
| 混合 | async + スレッドプール | tokio::spawn_blocking, Task.Run |

## 7.6 まとめ

### 言語横断的な学び

1. **async/await は普遍的なパターン** — 構文は異なるが、セマンティクス（中断・再開）は同一
2. **ゼロコスト vs 便利さのトレードオフ** — Rust のゼロコスト Future vs Python の手軽さ
3. **モナド的合成は FP 言語の強み** — `for` / `async { }` / `do` 記法で自然に合成
4. **CSP は独自のモデル** — Clojure の `go` ブロック + チャネルは他と根本的に異なる
5. **Virtual Thread は革命的** — Java 21 で従来のブロッキング API が軽量化

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part VII - 非同期プログラミング](../python/part-7.md) |
| Java | [Part VII - 非同期プログラミング](../java/part-7.md) |
| C# | [Part VII - 非同期プログラミング](../csharp/part-7.md) |
| Scala | [Part VII - 非同期プログラミング](../scala/part-7.md) |
| F# | [Part VII - 非同期プログラミング](../fsharp/part-7.md) |
| Rust | [Part VII - 非同期プログラミング](../rust/part-7.md) |
| Haskell | [Part VII - 非同期プログラミング](../haskell/part-7.md) |
| Clojure | [Part VII - 非同期プログラミング](../clojure/part-7.md) |
