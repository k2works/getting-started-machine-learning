---
type: Article
title: "Part II - プロセスとスレッド"
description: "Scala でのスレッド操作と並列パスワードクラッキング"
tags: [article, grokking-concurrency, scala, concurrency, threads]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、スレッドの基本操作と並列パスワードクラッキングを実装します。

---

## スレッドの基本

### スレッド作成

```scala
object ThreadBasics:

  /** Create a worker thread with the given name and task */
  def createWorker(name: String, task: () => Unit): Thread =
    val thread = new Thread(() => task())
    thread.setName(name)
    thread

  /** Create multiple worker threads */
  def createMultipleWorkers(count: Int, task: Int => Unit): List[Thread] =
    (0 until count).map { i =>
      val thread = new Thread(() => task(i))
      thread.setName(s"Worker-$i")
      thread
    }.toList

  /** Start all threads and wait for them to complete */
  def startAndJoinAll(threads: List[Thread]): Unit =
    threads.foreach(_.start())
    threads.foreach(_.join())
```

---

## 並列パスワードクラッキング

### チャンク分割

```scala
case class ChunkRange(start: Int, end: Int)

object PasswordCrackerParallel:
  /** Divide a range into chunks */
  def getChunks(numChunks: Int, totalSize: Int): List[ChunkRange] =
    val chunkSize = totalSize / numChunks
    val remainder = totalSize % numChunks

    (0 until numChunks).map { i =>
      val start = i * chunkSize + math.min(i, remainder)
      val end = start + chunkSize + (if i < remainder then 1 else 0)
      ChunkRange(start, end)
    }.toList
```

### Future による並列実行

```scala
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.Duration
import java.util.concurrent.atomic.AtomicReference

given ExecutionContext = ExecutionContext.global

/** Crack password using parallel execution */
def crackPasswordParallel(cryptoHash: String, length: Int): Option[String] =
  val combinations = PasswordCracker.getCombinations(length)
  val numCores = Runtime.getRuntime.availableProcessors()
  val chunks = getChunks(numCores, combinations.length)

  val result = new AtomicReference[Option[String]](None)

  val futures = chunks.map { chunk =>
    Future {
      val subList = combinations.slice(chunk.start, chunk.end)
      subList.find { password =>
        result.get().isEmpty && PasswordCracker.checkPassword(password, cryptoHash)
      } match
        case Some(found) => result.compareAndSet(None, Some(found))
        case None => ()
    }
  }

  futures.foreach(f => Await.ready(f, Duration.Inf))
  result.get()
```

---

## ポイント

- **ExecutionContext**: Scala の並行処理実行環境
- **Future**: 非同期計算を表現
- **AtomicReference**: スレッドセーフな結果共有

---

## 次のステップ

[Part III](part-3.md) では、マルチタスキングとゲームループを学びます。
