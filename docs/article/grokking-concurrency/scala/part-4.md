---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "Fork/Join パターンとパイプラインパターン"
tags: [article, grokking-concurrency, scala, concurrency, fork-join, pipeline]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、Fork/Join パターンとパイプラインパターンを学びます。

---

## Fork/Join パターン: 投票カウンター

### 逐次カウント

```scala
object VoteCounter:
  /** Count votes sequentially */
  def countVotes(votes: List[String]): Map[String, Int] =
    votes.groupBy(identity).view.mapValues(_.size).toMap

  /** Merge two vote count results */
  def mergeResults(a: Map[String, Int], b: Map[String, Int]): Map[String, Int] =
    (a.keys ++ b.keys).map { key =>
      key -> (a.getOrElse(key, 0) + b.getOrElse(key, 0))
    }.toMap
```

### 並列カウント

```scala
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.Duration

given ExecutionContext = ExecutionContext.global

/** Count votes using fork-join pattern */
def countVotesParallel(votes: List[String]): Map[String, Int] =
  if votes.isEmpty then return Map.empty

  val numCores = Runtime.getRuntime.availableProcessors()
  val chunkSize = math.max(1, votes.size / numCores)
  val chunks = votes.grouped(chunkSize).toList

  val futures = chunks.map { chunk =>
    Future(countVotes(chunk))
  }

  val results = futures.map(f => Await.result(f, Duration.Inf))
  results.reduce(mergeResults)
```

---

## パイプラインパターン

```scala
case class Stage(name: String, processor: Any => Any)

class Pipeline[T](stages: List[Stage] = List.empty):

  /** Add a stage to the pipeline */
  def addStage[A, B](name: String, processor: A => B): Pipeline[T] =
    new Pipeline[T](stages :+ Stage(name, x => processor(x.asInstanceOf[A])))

  /** Process data through all stages */
  def process(data: List[T]): List[Any] =
    if stages.isEmpty then return data

    data.map { item =>
      stages.foldLeft[Any](item) { (current, stage) =>
        stage.processor(current)
      }
    }

object Pipeline:
  def apply[T](): Pipeline[T] = new Pipeline[T]()
```

### 使用例

```scala
val pipeline = Pipeline[Int]()
  .addStage("double", (x: Int) => x * 2)
  .addStage("addOne", (x: Int) => x + 1)

val results = pipeline.process(List(1, 2, 3))
// List(3, 5, 7)
```

---

## ポイント

- **groupBy + mapValues**: 関数型スタイルの集計
- **foldLeft**: ステージを順次適用
- **ビルダーパターン**: 流暢なインターフェース

---

## 次のステップ

[Part V](part-5.md) では、同期と排他制御を学びます。
