---
type: Article
title: "Part VII - 非同期プログラミング"
description: "Future と Promise による非同期処理"
tags: [article, grokking-concurrency, scala, concurrency, async, future]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、Scala の Future と Promise を使った非同期プログラミングを学びます。

---

## Future の基本

```scala
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global

val future: Future[Int] = Future {
  Thread.sleep(1000)
  42
}

// コールバック
future.onComplete {
  case Success(value) => println(s"Result: $value")
  case Failure(e) => println(s"Error: ${e.getMessage}")
}
```

---

## Future の合成

### map と flatMap

```scala
val f1: Future[Int] = Future(10)
val f2: Future[Int] = Future(20)

// map: 結果を変換
val doubled: Future[Int] = f1.map(_ * 2)

// flatMap: Future を連結
val sum: Future[Int] = f1.flatMap(a => f2.map(b => a + b))
```

### for 内包表記

```scala
val result: Future[Int] = for
  a <- Future(10)
  b <- Future(20)
  c <- Future(30)
yield a + b + c
```

---

## 並列実行

```scala
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

// 並列に実行される
val futures = List(
  Future { Thread.sleep(1000); "A" },
  Future { Thread.sleep(1000); "B" },
  Future { Thread.sleep(1000); "C" }
)

// 全てを待機
val results: Future[List[String]] = Future.sequence(futures)
val values = Await.result(results, 5.seconds)
// List("A", "B", "C") - 約 1 秒で完了
```

---

## Promise

```scala
import scala.concurrent.Promise

val promise = Promise[Int]()
val future = promise.future

// 別のスレッドで完了
Future {
  Thread.sleep(1000)
  promise.success(42)
}

future.onComplete {
  case Success(v) => println(s"Got: $v")
  case Failure(e) => println(s"Failed: $e")
}
```

---

## エラーハンドリング

```scala
val future = Future {
  throw new RuntimeException("Error!")
}

// recover: 失敗時のフォールバック
val recovered = future.recover {
  case _: RuntimeException => 0
}

// recoverWith: 失敗時に別の Future
val recoveredWith = future.recoverWith {
  case _: RuntimeException => Future.successful(0)
}
```

---

## 次のステップ

[Part VIII](part-8.md) では、MapReduce パターンと並列コレクションを学びます。
