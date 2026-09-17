---
type: Article
title: "Part II: プロセスとスレッド"
description: "Part I で逐次処理の限界を確認しました。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 2.1 はじめに

Part I で逐次処理の限界を確認しました。本章では、並行処理の基本単位である**プロセス**と**スレッド**を学び、パスワードクラッカーを並列化して CPU の全コアを活用します。

### プロセスとスレッドの違い

| 特性 | プロセス | スレッド |
|------|---------|---------|
| メモリ空間 | 独立（隔離） | 共有 |
| 生成コスト | 高い | 低い |
| 通信方法 | IPC（パイプ、ソケット等） | 共有メモリ |
| 障害の影響 | 他プロセスに影響しない | プロセス全体に影響 |

## 2.2 共通の本質

### スレッド並列化のパターン

パスワードクラッカーの並列化は、全言語で共通の戦略を取ります：

```
1. 探索空間を N 分割する（N = CPU コア数）
2. 各チャンクに対してスレッド/タスクを生成する
3. 各スレッドが担当範囲を逐次探索する
4. 最初に見つかった結果を収集し、他のスレッドを停止する
```

### チャンク分割の共通アルゴリズム

```
total = 探索空間のサイズ
chunkSize = ceil(total / numCores)

chunks = []
for i in 0..numCores:
    start = i * chunkSize
    end = min(start + chunkSize, total)
    chunks.append(ChunkRange(start, end))
```

この分割戦略により、理論上はコア数に比例した高速化（4 コアなら約 4 倍）が実現できます。

## 2.3 言語別実装比較

### スレッド生成

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
import Control.Concurrent.Async (async, wait, mapConcurrently)

-- 単一スレッド
createThread :: IO () -> IO ThreadId
createThread = forkIO

-- 複数スレッド（async ライブラリ）
runThreads :: Int -> (Int -> IO ()) -> IO ()
runThreads count action = do
    handles <- forM [0..count-1] $ \i -> async (action i)
    forM_ handles wait
```

**特徴**:

- `forkIO` は軽量スレッド（Green Thread）を生成
- `async` ライブラリが結果の型付き取得を提供
- `mapConcurrently` でリスト要素ごとに並列実行

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
;; Java Thread の直接利用
(defn run-in-thread [task]
  (let [thread (Thread. task)]
    (.start thread)
    thread))

;; future による非同期実行
(def result (future
  (Thread/sleep 1000)
  (+ 1 2)))

@result  ;; => 3（ブロッキング取得）
```

**特徴**:

- Java 相互運用で `Thread` クラスを直接利用
- `future` は暗黙のスレッドプールで非同期実行
- `@`（deref）でブロッキング取得

</details>

#### マルチパラダイム言語

<details>
<summary>Scala 実装</summary>

```scala
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.Duration

given ExecutionContext = ExecutionContext.global

// Future による非同期実行
val futures = (0 until count).map { i =>
    Future { task(i) }
}.toList

futures.foreach(f => Await.ready(f, Duration.Inf))
```

**特徴**:

- `Future` はモナド的に合成可能
- `ExecutionContext` が暗黙的にスレッドプールを提供
- Scala 3 の `given` 構文で暗黙の引数を宣言

</details>

<details>
<summary>Rust 実装</summary>

```rust
use std::thread;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

pub fn run_threads<F>(count: usize, f: F)
where
    F: Fn(usize) + Send + Sync + Clone + 'static,
{
    let handles: Vec<_> = (0..count)
        .map(|i| {
            let f = f.clone();
            thread::spawn(move || f(i))
        })
        .collect();

    for handle in handles {
        handle.join().unwrap();
    }
}
```

**特徴**:

- `Send + Sync + 'static` トレイト境界でコンパイル時に安全性を保証
- `move` クロージャで所有権を移動
- `JoinHandle<T>` で型付き結果を取得

</details>

<details>
<summary>F# 実装</summary>

```fsharp
// Async ワークフロー
let runAsync (count: int) (task: int -> unit) =
    [ for i in 0 .. count - 1 do
        async { task i } ]
    |> Async.Parallel
    |> Async.RunSynchronously
    |> ignore
```

**特徴**:

- `async { ... }` ワークフローでコンポーザブルな非同期処理
- `Async.Parallel` で複数ワークフローを並列実行
- パイプ演算子 `|>` でデータフローを表現

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装</summary>

```java
// ExecutorService によるスレッドプール
ExecutorService executor = Executors.newFixedThreadPool(numCores);

List<Future<String>> futures = new ArrayList<>();
for (ChunkRange chunk : chunks) {
    futures.add(executor.submit(() -> crackChunk(cryptoHash, length, chunk)));
}

executor.shutdown();
```

**特徴**:

- `ExecutorService` がスレッドのライフサイクルを管理
- `Future<T>` で非同期結果を型安全に取得
- Java 21+ の `Virtual Thread` で軽量スレッドも利用可能

</details>

<details>
<summary>C# 実装</summary>

```csharp
// Task ベースの並列実行
var cts = new CancellationTokenSource();
var tasks = chunks.Select(chunk =>
    Task.Run(() => CrackChunk(cryptoHash, length, chunk), cts.Token)
).ToArray();

var completed = await Task.WhenAny(tasks);
if (completed.Result != null) cts.Cancel();
```

**特徴**:

- `Task.Run()` で非同期タスクを生成
- `CancellationToken` で協調的なキャンセルを実現
- `Task.WhenAny()` で最初に完了したタスクを取得

</details>

<details>
<summary>Python 実装</summary>

```python
from multiprocessing import Pool

# GIL のため CPU バウンドには multiprocessing を使用
def crack_password_parallel(crypto_hash, length, num_cores=4):
    chunks = get_chunks(length, num_cores)
    with Pool(num_cores) as pool:
        results = pool.starmap(crack_chunk, [
            (crypto_hash, length, start, end) for start, end in chunks
        ])
    return next((r for r in results if r is not None), None)
```

**特徴**:

- GIL の制約により `threading` ではなく `multiprocessing` を使用
- `Pool.starmap()` で引数付き関数を並列実行
- コンテキストマネージャでプールのライフサイクルを管理

</details>

### 並列パスワードクラッカー

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
crackPasswordParallel :: String -> String -> Int -> IO (Maybe String)
crackPasswordParallel _ _ 0 = return Nothing
crackPasswordParallel cryptoHash alphabet len = do
    results <- mapConcurrently
        (\c -> return $ crackRecursive cryptoHash alphabet [c] (len - 1))
        alphabet
    return $ listToMaybe (catMaybes results)
```

**戦略**: アルファベットの各先頭文字を `mapConcurrently` で並列化。純粋関数 `crackRecursive` は副作用がないため、安全に並列実行できます。

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
(defn crack-password-parallel [target-hash length num-threads]
  (let [total (long (Math/pow (count alphabet) length))
        chunks (chunk-range 0 total num-threads)
        futures (doall
                  (for [[start end] chunks]
                    (future
                      (crack-password-range target-hash start end length))))]
    (some identity (map deref futures))))
```

**戦略**: 探索空間をチャンクに分割し、各チャンクを `future` で並列実行。`some identity` で最初の非 nil 結果を返します。

</details>

#### マルチパラダイム言語

<details>
<summary>Scala 実装</summary>

```scala
def crackPasswordParallel(cryptoHash: String, length: Int): Option[String] =
  val combinations = getCombinations(length)
  val numCores = Runtime.getRuntime.availableProcessors
  val chunks = getChunks(numCores, combinations.length)
  val result = new AtomicReference[Option[String]](None)

  given ExecutionContext = ExecutionContext.global

  val futures = chunks.map { chunk =>
    Future {
      combinations.slice(chunk.start, chunk.end).find { password =>
        result.get().isEmpty && checkPassword(password, cryptoHash)
      } match
        case Some(found) => result.compareAndSet(None, Some(found))
        case None => ()
    }
  }

  futures.foreach(f => Await.ready(f, Duration.Inf))
  result.get()
```

**戦略**: `AtomicReference` で共有結果をロックフリーに管理。`compareAndSet` で最初の結果のみを設定します。

</details>

<details>
<summary>Rust 実装（Rayon）</summary>

```rust
pub fn crack_password_parallel(
    crypto_hash: &str,
    alphabet: &[char],
    length: usize,
) -> Option<String> {
    if length == 0 { return None; }

    alphabet.to_vec()
        .par_iter()
        .find_map_any(|&first| {
            crack_recursive(crypto_hash, alphabet, first.to_string(), length - 1)
        })
}
```

**戦略**: Rayon の `par_iter()` + `find_map_any()` で、ワークスティーリングスケジューラが自動的に負荷分散。最初に見つかった結果を返します。最もコード量が少ない並列実装です。

</details>

<details>
<summary>F# 実装</summary>

```fsharp
let crackPasswordParallel (cryptoHash: string) (length: int) : string option =
    let combinations = getCombinations length
    let numCores = Environment.ProcessorCount
    let chunks = getChunks numCores (List.length combinations)
    let result = ref None
    let arr = combinations |> List.toArray

    chunks
    |> List.map (fun chunk ->
        async {
            for password in arr.[chunk.Start .. chunk.End - 1] do
                if Option.isNone !result && checkPassword password cryptoHash then
                    result := Some password
        })
    |> Async.Parallel
    |> Async.RunSynchronously
    |> ignore

    !result
```

**戦略**: `ref` セルで共有結果を管理し、`Async.Parallel` で全チャンクを並列実行。

</details>

## 2.4 比較分析

### スレッド生成メカニズム

| 言語 | メカニズム | 戻り値 | 軽量スレッド |
|------|----------|--------|-------------|
| Python | `threading.Thread` / `multiprocessing.Process` | なし | なし（GIL 制約） |
| Java | `Thread` / `ExecutorService` | `Future<T>` | Virtual Thread (21+) |
| C# | `Task.Run()` | `Task<T>` | なし（Task はスレッドプール上） |
| Scala | `Future { ... }` | `Future[T]` | なし（ExecutionContext 上） |
| F# | `async { ... }` | `Async<'T>` | なし（スレッドプール上） |
| Rust | `thread::spawn()` / Rayon | `JoinHandle<T>` | なし（OS スレッド） |
| Haskell | `forkIO` / `async` | `Async a` | Green Thread (GHC RTS) |
| Clojure | `future` | `IDeref` | なし（JVM スレッドプール上） |

### 安全性保証のアプローチ

| アプローチ | 言語 | 保証レベル | 仕組み |
|-----------|------|----------|--------|
| コンパイル時（型システム） | Rust | 最高 | `Send`/`Sync` トレイト境界 |
| 不変性による回避 | Haskell, Clojure | 高い | 純粋関数と永続データ構造 |
| 型安全な並行プリミティブ | Scala, F# | 高い | `AtomicReference`, `Async` ワークフロー |
| ランタイムチェック | Java, C# | 中程度 | 同期プリミティブ + 例外 |
| GIL による保護 | Python | 低い（CPU バウンドでは無効） | スレッドでは真の並列化不可 |

### 並列パスワードクラッカーの設計パターン

| パターン | 言語 | 特徴 |
|---------|------|------|
| ワークスティーリング | Rust (Rayon) | 自動負荷分散、最短コード |
| アプリカティブ並列 | Haskell (`mapConcurrently`) | 要素ごとの並列化、関数型 |
| Future + 共有状態 | Scala, Clojure | チャンク分割 + 結果収集 |
| Async ワークフロー | F# | 宣言的な並列合成 |
| スレッドプール + Future | Java | ExecutorService による管理 |
| Task + キャンセル | C# | CancellationToken で協調停止 |
| プロセスプール | Python | GIL 回避のためプロセス並列 |

### 早期終了（最初の結果で停止）の実現方法

| 言語 | 方法 |
|------|------|
| Rust | `find_map_any()` が最初の `Some` を返した時点で停止 |
| C# | `Task.WhenAny()` + `CancellationToken` で残タスクをキャンセル |
| Java | `shutdownNow()` で全スレッドを中断 |
| Scala | `AtomicReference` + `isEmpty` チェックで残タスクをスキップ |
| F# | `ref` セルの `Option.isNone` チェックで残タスクをスキップ |
| Haskell | `catMaybes` + `listToMaybe` で最初の `Just` を取得 |
| Clojure | `some identity` で最初の非 nil を取得 |
| Python | `Pool.starmap()` の結果をフィルタリング |

## 2.5 実践的な選択指針

### CPU バウンド処理に適した言語

**最も適している**:

- **Rust** — ゼロコスト抽象化 + コンパイル時安全性。Rayon の `par_iter()` で最小限のコード変更で並列化
- **Haskell** — 純粋関数は安全に並列実行可能。`mapConcurrently` で宣言的に並列化

**バランスが良い**:

- **Scala / F#** — 型安全性と表現力の両立。既存の JVM/.NET エコシステムを活用
- **Java / C#** — 成熟した並行処理 API。エンタープライズ環境での実績

**注意が必要**:

- **Python** — GIL により `threading` では CPU バウンドの並列化ができない。`multiprocessing` が必要で、プロセス間通信のオーバーヘッドが発生
- **Clojure** — JVM 上で動作するため Java と同等の性能だが、動的型付けによるオーバーヘッドがある

## 2.6 まとめ

### 言語横断的な学び

1. **チャンク分割は普遍的** — どの言語でも探索空間を分割して並列化する戦略は同じ
2. **安全性のアプローチが言語の個性** — コンパイル時保証（Rust）、不変性（Haskell/Clojure）、ランタイムチェック（Java/C#）
3. **抽象度のトレードオフ** — 高レベル API（Rayon, `mapConcurrently`）ほど簡潔だが制御が限定される
4. **GIL は Python 固有の課題** — 他の言語にはない制約であり、`multiprocessing` への切り替えが必要

### 次のステップ

[Part III: マルチタスキングとスケジューリング](./part-3-ch06-multitasking.md) では、OS がどのようにスレッドを切り替えるか、協調的・プリエンプティブなスケジューリングの仕組みを学びます。

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part II - プロセスとスレッド](../python/part-2.md) |
| Java | [Part II - プロセスとスレッド](../java/part-2.md) |
| C# | [Part II - プロセスとスレッド](../csharp/part-2.md) |
| Scala | [Part II - プロセスとスレッド](../scala/part-2.md) |
| F# | [Part II - プロセスとスレッド](../fsharp/part-2.md) |
| Rust | [Part II - プロセスとスレッド](../rust/part-2.md) |
| Haskell | [Part II - プロセスとスレッド](../haskell/part-2.md) |
| Clojure | [Part II - プロセスとスレッド](../clojure/part-2.md) |
