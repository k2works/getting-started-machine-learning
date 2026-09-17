---
type: Article
title: "Part VIII: 分散並列処理"
description: "本章では、大規模データを複数のプロセッサで効率的に処理する MapReduce パターンを学びます。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 8.1 はじめに

本章では、大規模データを複数のプロセッサで効率的に処理する **MapReduce パターン**を学びます。ワードカウントを題材に、8 つの言語がそれぞれどのような並列コレクション・並列イテレータを提供しているかを比較します。

### MapReduce とは

MapReduce は 3 つのフェーズで構成されます：

```
入力データ → [Map] → (key, value) ペア → [Shuffle] → グループ化 → [Reduce] → 最終結果
```

1. **Map**: 各入力要素を (key, value) ペアに変換
2. **Shuffle**: ペアをキーごとにグループ化
3. **Reduce**: 各グループの値を集約

## 8.2 共通の本質

### ワードカウントのアルゴリズム

```
入力: ["hello world", "hello rust", "world of rust"]

Map:
  "hello world" → [("hello", 1), ("world", 1)]
  "hello rust"  → [("hello", 1), ("rust", 1)]
  "world of rust" → [("world", 1), ("of", 1), ("rust", 1)]

Shuffle:
  "hello" → [1, 1]
  "world" → [1, 1]
  "rust"  → [1, 1]
  "of"    → [1]

Reduce:
  "hello" → 2, "world" → 2, "rust" → 2, "of" → 1
```

### 並列化のポイント

Map フェーズは**恥ずかしいほど並列**（embarrassingly parallel）です。各テキストの処理は完全に独立しており、ロックや同期なしで並列化できます。

## 8.3 言語別実装比較

### Map フェーズ

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
mapPhase :: String -> [(String, Int)]
mapPhase text = [(map toLower word, 1) | word <- words text, not (null word)]
```

**特徴**: リスト内包表記でワンライナー。`words` は標準関数。

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
(defn map-phase [text]
  (->> (str/split (str/lower-case text) #"\s+")
       (filter (complement str/blank?))
       (map (fn [word] [word 1]))))
```

**特徴**: スレッディングマクロ `->>` でデータ変換パイプラインを表現。

</details>

#### マルチパラダイム言語

<details>
<summary>Rust 実装</summary>

```rust
pub fn map(text: &str) -> Vec<(String, usize)> {
    text.to_lowercase()
        .split_whitespace()
        .filter(|s| !s.is_empty())
        .map(|word| (word.to_string(), 1))
        .collect()
}
```

</details>

<details>
<summary>Scala 実装</summary>

```scala
def map(text: String): List[(String, Int)] =
  text.toLowerCase
    .split("\\s+")
    .filter(_.nonEmpty)
    .map(word => (word, 1))
    .toList
```

</details>

<details>
<summary>F# 実装</summary>

```fsharp
let map (text: string) : (string * int) list =
    text.ToLower().Split([|' '; '\t'; '\n'|], StringSplitOptions.RemoveEmptyEntries)
    |> Array.map (fun word -> (word, 1))
    |> Array.toList
```

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装</summary>

```java
public static List<Map.Entry<String, Integer>> map(String text) {
    return Arrays.stream(text.toLowerCase().split("\\s+"))
        .map(word -> Map.entry(word, 1))
        .toList();
}
```

</details>

<details>
<summary>C# 実装</summary>

```csharp
public static List<KeyValuePair<string, int>> Map(string text) {
    return text.ToLower()
        .Split(' ', StringSplitOptions.RemoveEmptyEntries)
        .Select(word => new KeyValuePair<string, int>(word, 1))
        .ToList();
}
```

</details>

<details>
<summary>Python 実装</summary>

```python
def map_function(text: str) -> List[Tuple[str, int]]:
    words = text.lower().split()
    return [(word, 1) for word in words]
```

</details>

### 並列 MapReduce（全体）

#### 並列化メカニズムの比較

| 言語 | 並列化 API | 特徴 |
|------|-----------|------|
| Python | `multiprocessing.Pool.map()` | プロセスベース（GIL 回避） |
| Java | `parallelStream()` | ForkJoinPool 自動管理 |
| C# | `AsParallel()` (PLINQ) | LINQ との統合 |
| Scala | `.par` コレクション | ワンメソッドで並列化 |
| F# | `Array.Parallel.collect` | Array 専用並列モジュール |
| Rust | `par_iter()` (Rayon) | ワークスティーリング |
| Haskell | `mapConcurrently` | 非同期並列マップ |
| Clojure | `pmap` | 並列マップ関数 |

<details>
<summary>Rust 実装（完全並列 fold/reduce）</summary>

```rust
pub fn count_words_full_parallel(texts: &[&str]) -> HashMap<String, usize> {
    texts
        .par_iter()
        .flat_map(|text| map(text))
        .fold(
            || HashMap::new(),
            |mut acc, (word, count)| {
                *acc.entry(word).or_insert(0) += count;
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

**特徴**: Map + Reduce を `par_iter().fold().reduce()` で一貫して並列実行。チャンク分割も自動。

</details>

<details>
<summary>Java 実装（parallelStream）</summary>

```java
public static Map<String, Integer> wordCount(List<String> texts) {
    List<Map.Entry<String, Integer>> mapped = texts.parallelStream()
        .flatMap(text -> map(text).stream())
        .toList();
    return reduce(mapped);
}

public static Map<String, Integer> reduce(List<Map.Entry<String, Integer>> pairs) {
    return pairs.stream()
        .collect(Collectors.groupingBy(
            Map.Entry::getKey,
            Collectors.summingInt(Map.Entry::getValue)
        ));
}
```

**特徴**: `parallelStream()` を呼ぶだけで並列化。`Collectors` で宣言的な集約。

</details>

<details>
<summary>C# 実装（PLINQ）</summary>

```csharp
public static Dictionary<string, int> CountWords(List<string> texts) {
    var mapped = texts
        .AsParallel()
        .SelectMany(text => Map(text))
        .ToList();
    return Reduce(mapped);
}
```

**特徴**: `.AsParallel()` を挿入するだけで LINQ クエリが並列化。

</details>

<details>
<summary>Haskell 実装</summary>

```haskell
countWordsParallel :: [String] -> IO (Map String Int)
countWordsParallel [] = return Map.empty
countWordsParallel texts = do
    mapped <- mapConcurrently (return . mapPhase) texts
    return $ reducePhase (concat mapped)
```

</details>

<details>
<summary>Clojure 実装（pmap）</summary>

```clojure
(defn count-words-parallel [texts]
  (if (empty? texts)
    {}
    (->> texts
         (pmap map-phase)
         (apply concat)
         reduce-phase)))
```

**特徴**: `map` を `pmap` に置き換えるだけで並列化。

</details>

## 8.4 比較分析

### 並列化の手軽さ

```
最も簡単  ┌──────────────────────────────┐
          │ Scala: .par                  │ ← 1 メソッド追加
          │ C#: .AsParallel()            │
          │ Clojure: map → pmap          │
          ├──────────────────────────────┤
手軽      │ Java: stream → parallelStream │
          │ Rust: iter → par_iter        │
          │ F#: Array.map → Parallel.map │
          ├──────────────────────────────┤
やや複雑  │ Haskell: mapConcurrently     │ ← IO モナドが必要
          │ Python: Pool.map()           │ ← プロセス分離が必要
          └──────────────────────────────┘
```

### Reduce フェーズの実装

| 言語 | Reduce 方法 | 特徴 |
|------|-----------|------|
| Python | `defaultdict(int)` + ループ | 命令的 |
| Java | `Collectors.groupingBy` + `summingInt` | 宣言的コレクター |
| C# | `GroupBy` + `Sum` | LINQ 統合 |
| Scala | `groupBy` + `mapValues` + `sum` | 関数型コレクション |
| F# | `List.groupBy` + `List.sumBy` | パイプライン |
| Rust | `HashMap.entry().or_insert()` | Entry API |
| Haskell | `foldl'` + `Map.insertWith (+)` | 純粋関数型 |
| Clojure | `reduce` + `update` + `fnil` | 関数合成 |

### パフォーマンス特性

| 言語 | 並列化方式 | スレッドプール | 最適なケース |
|------|----------|-------------|------------|
| Python | プロセスプール | OS プロセス | CPU バウンド（GIL 回避） |
| Java | ForkJoinPool | ワークスティーリング | 汎用並列処理 |
| C# | ThreadPool | .NET ThreadPool | LINQ クエリ並列化 |
| Scala | Fork/Join | ワークスティーリング | コレクション操作 |
| F# | .NET ThreadPool | .NET ThreadPool | 配列操作 |
| Rust | Rayon | ワークスティーリング | ゼロコスト並列化 |
| Haskell | GHC RTS | Green Thread | I/O 並行処理 |
| Clojure | JVM ThreadPool | 固定プール | 関数適用 |

### アムダールの法則

```
高速化率 = 1 / (S + P/N)

S = 逐次処理の割合
P = 並列化可能な割合 (P = 1 - S)
N = プロセッサ数

例: 逐次 10%、並列 90%、4 コア
高速化率 = 1 / (0.1 + 0.9/4) = 3.08 倍
```

並列化可能な部分（Map フェーズ）の割合が大きいほど、コア数に比例した高速化が期待できます。

## 8.5 実践的な選択指針

### 大規模データ処理に適した言語

**最も適している**:

- **Rust (Rayon)** — `fold` + `reduce` で Map と Reduce を一貫して並列化。ゼロコスト抽象化
- **Java (parallelStream)** — エンタープライズ規模のデータ処理。Stream API の成熟度

**手軽さ重視**:

- **Scala (.par)** — ワンメソッドで並列化。ただし順序保証に注意
- **C# (PLINQ)** — `.AsParallel()` で既存 LINQ クエリを即座に並列化
- **Clojure (pmap)** — `map` を `pmap` に置き換えるだけ

**関数型アプローチ**:

- **Haskell** — 純粋関数は安全に並列化。`mapConcurrently` が宣言的
- **F#** — `Array.Parallel` モジュールで配列操作を並列化

## 8.6 まとめ

### 言語横断的な学び

1. **MapReduce は関数型の自然な拡張** — `map` + `reduce` を並列化するだけで大規模データ処理が可能
2. **並列化の手軽さは言語の設計思想** — `.par` / `.AsParallel()` / `pmap` のワンメソッド並列化
3. **Reduce の表現力** — 宣言的 (Java Collectors) vs 命令的 (Python ループ) vs 関数型 (Haskell foldl')
4. **アムダールの法則** — 逐次部分の割合が並列化の効果を決定

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part VIII - 分散並列処理](../python/part-8.md) |
| Java | [Part VIII - 分散並列処理](../java/part-8.md) |
| C# | [Part VIII - 分散並列処理](../csharp/part-8.md) |
| Scala | [Part VIII - 分散並列処理](../scala/part-8.md) |
| F# | [Part VIII - 分散並列処理](../fsharp/part-8.md) |
| Rust | [Part VIII - 分散並列処理](../rust/part-8.md) |
| Haskell | [Part VIII - 分散並列処理](../haskell/part-8.md) |
| Clojure | [Part VIII - 分散並列処理](../clojure/part-8.md) |
