---
type: Article
title: "Part VIII - 分散並列処理"
description: "MapReduce パターンと並列コレクション"
tags: [article, grokking-concurrency, scala, concurrency, mapreduce, parallel-collections]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、MapReduce パターンと Scala の並列コレクションを学びます。

---

## MapReduce パターン

### ワードカウント実装

```scala
import scala.collection.parallel.CollectionConverters.*

object WordCount:

  /** Map: Convert text to (word, 1) pairs */
  def map(text: String): List[(String, Int)] =
    text.toLowerCase
      .split("\\s+")
      .filter(_.nonEmpty)
      .map(word => (word, 1))
      .toList

  /** Reduce: Aggregate word counts */
  def reduce(pairs: List[(String, Int)]): Map[String, Int] =
    pairs.groupBy(_._1).view.mapValues(_.map(_._2).sum).toMap

  /** MapReduce: Count words in multiple texts using parallel collections */
  def countWords(texts: List[String]): Map[String, Int] =
    // Map phase (parallel)
    val mapped = texts.par.flatMap(map).toList

    // Reduce phase
    reduce(mapped)
```

---

## 使用例

```scala
val texts = List(
  "hello world",
  "hello scala",
  "world of scala"
)

val result = WordCount.countWords(texts)
// Map(hello -> 2, world -> 2, scala -> 2, of -> 1)
```

---

## 並列コレクション

### 基本的な使い方

```scala
import scala.collection.parallel.CollectionConverters.*

val numbers = (1 to 1000000).toList

// 逐次処理
val seqSum = numbers.map(_ * 2).sum

// 並列処理
val parSum = numbers.par.map(_ * 2).sum
```

### 注意点

```scala
// 順序が保証されない
val result = (1 to 10).par.map(x => s"$x: ${Thread.currentThread.getName}")
// 順序はランダム

// 副作用のある操作は危険
var counter = 0
(1 to 1000).par.foreach(_ => counter += 1) // 競合状態
// counter < 1000 になる可能性あり
```

---

## 並列コレクションの選択

| コレクション | 並列版 | 適した操作 |
|-------------|--------|-----------|
| List | ParVector | map, filter |
| Array | ParArray | インデックスアクセス |
| Range | ParRange | 連続処理 |
| Map | ParMap | キー操作 |

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | 基本概念、ブルートフォース |
| II | スレッド | Thread、Future |
| III | マルチタスキング | ゲームループ |
| IV | 並列パターン | Fork/Join、Pipeline |
| V | 同期 | synchronized、デッドロック回避 |
| VI | ノンブロッキング | NIO、Selector |
| VII | 非同期 | Future、Promise |
| VIII | 分散処理 | MapReduce、並列コレクション |

---

## 参考資料

- [Scala Parallel Collections](https://docs.scala-lang.org/overviews/parallel-collections/overview.html)
- [Scala Futures and Promises](https://docs.scala-lang.org/overviews/core/futures.html)
- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
