---
type: Article
title: "Part VII - 非同期プログラミング"
description: "CompletableFuture と Virtual Threads を使った非同期プログラミングを学ぶ"
tags: [article, grokking-concurrency, java, concurrency, async, completablefuture, virtual-threads]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、Java の CompletableFuture と Virtual Threads（Java 21）を使った非同期プログラミングを学びます。

---

## CompletableFuture

### 基本的な使い方

```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    // 非同期処理
    return "Result";
});

// 結果を取得
String result = future.get();
```

### チェーン処理

```java
CompletableFuture.supplyAsync(() -> fetchData())
    .thenApply(data -> process(data))
    .thenAccept(result -> save(result));
```

### 並行実行

```java
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> fetchData1());
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> fetchData2());

// すべての完了を待機
CompletableFuture.allOf(future1, future2).join();
```

---

## Virtual Threads (Java 21+)

### 基本的な使い方

```java
// Virtual Thread で実行
Thread.startVirtualThread(() -> {
    System.out.println("Running in virtual thread");
});

// Virtual Thread の ExecutorService
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> {
        System.out.println("Task in virtual thread");
    });
}
```

### プラットフォームスレッドとの違い

| 特性 | プラットフォームスレッド | Virtual Thread |
|------|------------------------|----------------|
| 作成コスト | 高い | 低い |
| メモリ | ~1MB/スレッド | ~KB/スレッド |
| 最大数 | 数千 | 数百万 |
| ブロッキング | 非効率 | 効率的 |

---

## 使い分けの指針

| シナリオ | 推奨 |
|----------|------|
| I/O バウンド | Virtual Thread / CompletableFuture |
| CPU バウンド | プラットフォームスレッド |
| 混合 | Virtual Thread + 並列ストリーム |

---

## 次のステップ

Part VIII では、MapReduce パターンと分散並列処理を学びます。

---

## 参考資料

- [Java CompletableFuture](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
