---
type: Article
title: "Part IV: タスク分解と並列パターン"
description: "Part III でマルチタスキングの仕組みを学びました。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 4.1 はじめに

Part III でマルチタスキングの仕組みを学びました。本章では、複雑な問題を並列に処理するための 2 つの基本パターン — **Fork/Join** と **Pipeline** — を学びます。投票集計と洗濯パイプラインを題材に、8 つの言語での実装を比較します。

### データ並列 vs タスク並列

| パターン | 説明 | 例 |
|---------|------|-----|
| データ並列 | 同じ操作を複数のデータに適用 | 配列の各要素を 2 倍にする |
| タスク並列 | 異なる操作を同時に実行 | Input → Compute → Output |

## 4.2 共通の本質

### Fork/Join パターン

```
入力データ
    ↓
  [Fork] ── 分割 ──→ [Worker 1] [Worker 2] [Worker 3]
                         ↓          ↓          ↓
                      部分結果1   部分結果2   部分結果3
                         ↓          ↓          ↓
  [Join] ── 統合 ──→ 最終結果
```

**3 つのフェーズ**:

1. **Fork**: データをチャンクに分割し、各ワーカーに配布
2. **Process**: 各ワーカーが独立して部分結果を計算
3. **Join**: 部分結果を統合して最終結果を生成

### Pipeline パターン

```
[Stage 1: 洗濯] →→ [Stage 2: 乾燥] →→ [Stage 3: 畳み]
     ↓ Queue          ↓ Queue          ↓
   Load 1           Load 1           Load 1
   Load 2           Load 2           Load 2
   Load 3           Load 3           Load 3
```

各ステージが独立したスレッドで動作し、キューを介して次のステージにデータを渡します。逐次処理では 28 秒かかる 4 回の洗濯が、パイプラインでは約 19 秒に短縮されます。

## 4.3 言語別実装比較

### Fork/Join: 投票集計

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
countVotesParallel :: [String] -> IO (Map String Int)
countVotesParallel [] = return Map.empty
countVotesParallel votes = do
    let chunks = splitIntoChunks 4 votes
    results <- mapConcurrently (return . countVotes) chunks
    return $ foldl' (Map.unionWith (+)) Map.empty results
```

**特徴**:

- `mapConcurrently` でチャンクごとに並列集計
- `Map.unionWith (+)` で部分結果をマージ
- 純粋関数 `countVotes` は副作用なし

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
(defn count-votes-parallel [votes num-workers]
  (if (empty? votes)
    {}
    (let [chunks (partition-all (max 1 (quot (count votes) num-workers)) votes)
          futures (doall (map #(future (count-votes-sequential %)) chunks))
          results (map deref futures)]
      (merge-counts results))))
```

**特徴**:

- `partition-all` でデータを均等分割
- `future` + `deref` で非同期実行と結果取得
- `merge-with +` で部分結果をマージ

</details>

#### マルチパラダイム言語

<details>
<summary>Rust 実装（Rayon）</summary>

```rust
pub fn count_votes(votes: &[&str]) -> HashMap<String, usize> {
    votes
        .par_iter()
        .fold(
            || HashMap::new(),
            |mut acc, &vote| {
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

**特徴**:

- `par_iter()` + `fold` + `reduce` のワンパス並列処理
- 明示的なチャンク分割が不要（Rayon が自動分配）
- `entry` API でハッシュマップを安全に更新

</details>

<details>
<summary>Scala 実装</summary>

```scala
def countVotesParallel(votes: List[String]): Map[String, Int] =
  val numCores = Runtime.getRuntime.availableProcessors()
  val chunks = votes.grouped(math.max(1, votes.size / numCores)).toList

  val futures = chunks.map { chunk =>
    Future(countVotes(chunk))
  }

  val results = futures.map(f => Await.result(f, Duration.Inf))
  results.reduce(mergeResults)
```

**特徴**:

- `grouped` でコレクションを均等分割
- `Future` + `Await.result` で非同期実行
- `reduce(mergeResults)` で部分結果を統合

</details>

<details>
<summary>F# 実装</summary>

```fsharp
let countVotesParallel (votes: string list) : Map<string, int> =
    let numCores = Environment.ProcessorCount
    let chunks = votes |> List.chunkBySize (max 1 (List.length votes / numCores))

    let results =
        chunks
        |> List.map (fun chunk -> async { return countVotes chunk })
        |> Async.Parallel
        |> Async.RunSynchronously
        |> Array.toList

    results |> List.reduce mergeResults
```

**特徴**:

- `List.chunkBySize` で均等分割
- `Async.Parallel` で並列実行
- パイプ演算子 `|>` で変換フローを明示

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装</summary>

```java
public static Map<Integer, Integer> countVotesParallel(
    List<Integer> votes, int numWorkers) {
    int chunkSize = (int) Math.ceil((double) votes.size() / numWorkers);
    List<List<Integer>> chunks = new ArrayList<>();
    for (int i = 0; i < votes.size(); i += chunkSize) {
        chunks.add(votes.subList(i, Math.min(i + chunkSize, votes.size())));
    }

    try (ExecutorService executor = Executors.newFixedThreadPool(numWorkers)) {
        List<Future<Map<Integer, Integer>>> futures = new ArrayList<>();
        for (List<Integer> chunk : chunks) {
            futures.add(executor.submit(() -> countVotes(chunk)));
        }

        Map<Integer, Integer> total = new HashMap<>();
        for (Future<Map<Integer, Integer>> future : futures) {
            total = mergeResults(total, future.get());
        }
        return total;
    }
}
```

**特徴**:

- `ExecutorService` でスレッドプールを管理
- `Future.get()` で結果をブロッキング取得
- `merge()` ユーティリティで結果を統合

</details>

<details>
<summary>C# 実装</summary>

```csharp
public static Dictionary<int, int> CountVotesParallel(
    List<int> votes, int numWorkers) {
    var chunkSize = (int)Math.Ceiling((double)votes.Count / numWorkers);
    var chunks = new List<List<int>>();
    for (var i = 0; i < votes.Count; i += chunkSize)
        chunks.Add(votes.GetRange(i, Math.Min(chunkSize, votes.Count - i)));

    var tasks = chunks.Select(chunk =>
        Task.Run(() => CountVotes(chunk))
    ).ToArray();

    Task.WaitAll(tasks);
    return tasks.Aggregate(new Dictionary<int, int>(),
        (acc, t) => MergeResults(acc, t.Result));
}
```

**特徴**:

- `Task.Run()` で並列タスクを生成
- LINQ `Aggregate` で結果をマージ
- `Task.WaitAll` で全タスクの完了を待機

</details>

### Pipeline: 洗濯パイプライン

#### チャネル/キューの比較

| 言語 | メカニズム | 送信 | 受信 |
|------|----------|------|------|
| Python | `queue.Queue` | `put()` | `get()` |
| Java | `BlockingQueue` | `offer()` / `put()` | `poll()` / `take()` |
| C# | `BlockingCollection<T>` | `TryAdd()` | `TryTake()` |
| Rust | `mpsc::channel` | `send()` | `recv()` |
| Haskell | `TQueue` (STM) | `writeTQueue` | `readTQueue` |
| Clojure | `core.async chan` | `>!` / `>!!` | `<!` / `<!!` |
| Scala | `LinkedBlockingQueue` | `offer()` | `poll()` |
| F# | `Async` ワークフロー | 関数合成 | 関数合成 |

<details>
<summary>Rust 実装（mpsc チャネル）</summary>

```rust
pub fn concurrent_pipeline<T>(
    input: Vec<T>,
    processors: Vec<Box<dyn Fn(T) -> T + Send + 'static>>,
) -> Vec<T>
where
    T: Send + 'static + Clone,
{
    let (first_tx, mut current_rx) = channel();

    let input_thread = thread::spawn(move || {
        for item in input {
            first_tx.send(item).unwrap();
        }
    });

    let mut handles = vec![input_thread];

    for processor in processors {
        let (tx, rx) = channel();
        let prev_rx = current_rx;
        current_rx = rx;

        let handle = thread::spawn(move || {
            while let Ok(item) = prev_rx.recv() {
                let result = processor(item);
                if tx.send(result).is_err() { break; }
            }
        });
        handles.push(handle);
    }

    let mut results = Vec::new();
    while let Ok(item) = current_rx.recv() {
        results.push(item);
    }

    for handle in handles { handle.join().unwrap(); }
    results
}
```

</details>

<details>
<summary>Clojure 実装（core.async）</summary>

```clojure
(defn run-chain-pipeline [initial-value functions]
  (if (empty? functions)
    initial-value
    (let [channels (repeatedly (inc (count functions)) #(chan 1))]
      (doseq [[in-ch out-ch f] (map vector channels (rest channels) functions)]
        (go
          (when-let [v (<! in-ch)]
            (>! out-ch (f v)))))
      (>!! (first channels) initial-value)
      (<!! (last channels)))))
```

</details>

<details>
<summary>Haskell 実装（TQueue）</summary>

```haskell
concurrentPipeline :: [a -> a] -> [a] -> IO [a]
concurrentPipeline processors inputs = do
    queues <- forM [1..length processors + 1] $ \_ -> newTQueueIO

    forkIO $ do
        mapM_ (atomically . writeTQueue (head queues) . Just) inputs
        atomically $ writeTQueue (head queues) Nothing

    let pairs = zip3 (init queues) (tail queues) processors
    mapM_ (\(inQ, outQ, proc) -> forkIO $ processStage inQ outQ proc) pairs

    collectResults (last queues)
```

</details>

## 4.4 比較分析

### Fork/Join の実装パターン

| パターン | 言語 | 特徴 |
|---------|------|------|
| 自動分配 | Rust (Rayon) | `par_iter()` がチャンク分割・負荷分散を自動化 |
| アプリカティブ並列 | Haskell | `mapConcurrently` で要素ごとに並列化 |
| Future 分散 | Java, Scala, Clojure | チャンク分割 → Future 生成 → 結果統合 |
| Async 並列 | F# | `Async.Parallel` で宣言的に並列実行 |
| Task 分散 | C# | `Task.Run()` + `Task.WaitAll` |
| プロセスプール | Python | `multiprocessing.Pool` で GIL を回避 |

### 結果マージの方法

| 言語 | マージ関数 | 特徴 |
|------|----------|------|
| Python | `dict` + ループ | 手動マージ |
| Java | `HashMap.merge()` | `Integer::sum` で関数的 |
| C# | LINQ `Aggregate` | 宣言的 |
| Scala | `reduce(mergeResults)` | 関数型 |
| F# | `List.reduce mergeResults` | パイプライン |
| Rust | `reduce` + `entry` API | ロックフリー |
| Haskell | `foldl' (Map.unionWith (+))` | 純粋関数型 |
| Clojure | `merge-with +` | 最も簡潔 |

### Pipeline の設計思想

| 言語 | キュー方式 | 通信モデル |
|------|----------|-----------|
| Python | `queue.Queue` | ブロッキング |
| Java | `BlockingQueue` | ブロッキング（タイムアウト付き） |
| C# | `BlockingCollection` | ブロッキング（キャンセル対応） |
| Rust | `mpsc::channel` | 所有権ベース |
| Haskell | `TQueue` (STM) | トランザクショナル |
| Clojure | `core.async chan` | CSP モデル（go ブロック） |

## 4.5 実践的な選択指針

### Fork/Join に適した言語

- **Rust (Rayon)** — `par_iter()` で最小限のコード変更で並列化。ワークスティーリングによる自動負荷分散
- **Haskell** — 純粋関数は安全に並列実行可能。`mapConcurrently` が宣言的
- **Clojure** — `merge-with +` が最も簡潔なマージ表現

### Pipeline に適した言語

- **Clojure (core.async)** — CSP モデルでチャネルベースのパイプラインを自然に表現
- **Rust** — `mpsc` チャネルで型安全なステージ間通信
- **Haskell** — STM ベースの `TQueue` でデッドロックフリー

## 4.6 まとめ

### 言語横断的な学び

1. **Fork/Join は map-reduce の基礎** — データ分割→並列処理→統合は全言語共通
2. **Pipeline はキュー設計が鍵** — ステージ間通信のメカニズムが言語の個性を決定
3. **抽象度の違い** — Rayon の自動分配 vs 手動チャンク分割の選択
4. **マージ操作の表現力** — `merge-with +` (Clojure) から `HashMap.merge()` (Java) まで

### 次のステップ

[Part VIII: 分散並列処理](./part-8-ch13-mapreduce.md) では、MapReduce パターンを使って大規模データの並列処理を学びます。

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part IV - タスク分解と並列パターン](../python/part-4.md) |
| Java | [Part IV - タスク分解と並列パターン](../java/part-4.md) |
| C# | [Part IV - タスク分解と並列パターン](../csharp/part-4.md) |
| Scala | [Part IV - タスク分解と並列パターン](../scala/part-4.md) |
| F# | [Part IV - タスク分解と並列パターン](../fsharp/part-4.md) |
| Rust | [Part IV - タスク分解と並列パターン](../rust/part-4.md) |
| Haskell | [Part IV - タスク分解と並列パターン](../haskell/part-4.md) |
| Clojure | [Part IV - タスク分解と並列パターン](../clojure/part-4.md) |
