---
type: Article
title: "Part V - 同期と排他制御"
description: "レースコンディション、デッドロックと Lock、Semaphore を学ぶ"
tags: [article, grokking-concurrency, java, concurrency, lock, semaphore, deadlock]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、並行処理における同期と排他制御を学びます。レースコンディション、デッドロックなどの問題と、Lock、Semaphore による解決策を理解します。

---

## 第8章: Lock と排他制御

### レースコンディション

複数のスレッドが共有リソースに同時アクセスすると、予期しない結果が発生することがあります。

### 銀行口座の例

**ソースファイル**: `apps/java/src/main/java/concurrency/ch08/BankAccount.java`

```java
public class BankAccount {
    private final Lock lock = new ReentrantLock();
    private int balance;

    public void deposit(int amount) {
        lock.lock();
        try {
            balance += amount;
        } finally {
            lock.unlock();
        }
    }

    public boolean withdraw(int amount) {
        lock.lock();
        try {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}
```

### デッドロック回避

複数のロックを取得する場合、常に同じ順序でロックを取得することでデッドロックを回避できます。

```java
public static boolean transfer(BankAccount from, BankAccount to, int amount) {
    // 常に同じ順序でロックを取得
    BankAccount first = System.identityHashCode(from) < System.identityHashCode(to) ? from : to;
    BankAccount second = first == from ? to : from;

    first.lock.lock();
    try {
        second.lock.lock();
        try {
            if (from.balance >= amount) {
                from.balance -= amount;
                to.balance += amount;
                return true;
            }
            return false;
        } finally {
            second.lock.unlock();
        }
    } finally {
        first.lock.unlock();
    }
}
```

---

## 同期プリミティブ

| API | 説明 |
|-----|------|
| `ReentrantLock` | 再入可能なロック |
| `ReadWriteLock` | 読み取り/書き込みロック |
| `Semaphore` | カウンティングセマフォ |
| `CountDownLatch` | カウントダウンラッチ |
| `CyclicBarrier` | サイクリックバリア |

---

## 実行方法

```bash
cd apps/java
./gradlew test --tests "concurrency.ch08.*"
```

---

## 参考コード

- `apps/java/src/main/java/concurrency/ch08/BankAccount.java`
