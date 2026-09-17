---
type: Article
title: "Part V - 同期と排他制御"
description: "銀行口座で学ぶロックとデッドロック回避"
tags: [article, grokking-concurrency, scala, concurrency, synchronization, lock]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、銀行口座を例に同期とデッドロック回避を学びます。

---

## 銀行口座クラス

```scala
class BankAccount(initialBalance: Int):
  private val lock = new Object
  private var balance: Int = initialBalance

  def getBalance: Int = lock.synchronized {
    balance
  }

  def deposit(amount: Int): Unit = lock.synchronized {
    balance += amount
  }

  def withdraw(amount: Int): Boolean = lock.synchronized {
    if balance >= amount then
      balance -= amount
      true
    else
      false
  }
```

---

## デッドロック回避

```scala
object BankAccount:
  /** Transfer money between accounts atomically, avoiding deadlock */
  def transfer(from: BankAccount, to: BankAccount, amount: Int): Boolean =
    // Always lock in consistent order to avoid deadlock
    val (first, second) = if System.identityHashCode(from) < System.identityHashCode(to)
      then (from, to)
      else (to, from)

    first.lock.synchronized {
      second.lock.synchronized {
        if from.balance >= amount then
          from.balance -= amount
          to.balance += amount
          true
        else
          false
      }
    }
```

---

## デッドロックとは

2 つのスレッドが互いのロックを待つ状態:

```
Thread A: lock(account1) -> waiting for lock(account2)
Thread B: lock(account2) -> waiting for lock(account1)
```

### 解決策: ロック順序の統一

`System.identityHashCode` を使用してロック順序を決定することで、すべてのスレッドが同じ順序でロックを取得します。

---

## 並行テスト

```scala
test("BankAccount should handle concurrent transfers safely"):
  val account1 = new BankAccount(1000)
  val account2 = new BankAccount(1000)

  val executor = Executors.newFixedThreadPool(10)
  val latch = new CountDownLatch(100)

  (1 to 50).foreach { _ =>
    executor.submit(new Runnable:
      def run(): Unit =
        BankAccount.transfer(account1, account2, 10)
        latch.countDown()
    )
    executor.submit(new Runnable:
      def run(): Unit =
        BankAccount.transfer(account2, account1, 10)
        latch.countDown()
    )
  }

  latch.await()
  executor.shutdown()

  // Total balance should be preserved
  (account1.getBalance + account2.getBalance) shouldBe 2000
```

---

## 次のステップ

[Part VI](part-6.md) では、ノンブロッキング I/O を学びます。
