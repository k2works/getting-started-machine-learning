---
type: Article
title: "Part II - プロセスとスレッド"
description: "OS レベルの並行処理の基本単位を学ぶ"
tags: [article, grokking-concurrency, java, concurrency, process, thread]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、OS レベルの並行処理の基本単位であるプロセスとスレッドを学びます。Java での実装方法と、スレッドプール、並列パスワードクラッキングについても解説します。

---

## 第4章: プロセスとスレッドの基礎

### プロセスとは

プロセスは、実行中のプログラムのインスタンスです。独自のメモリ空間、ファイルハンドル、その他のリソースを持ちます。

### スレッドとは

スレッドは、プロセス内の実行単位です。同じプロセス内のスレッドはメモリ空間を共有します。

### プロセス vs スレッド

| 特徴 | プロセス | スレッド |
|------|----------|----------|
| メモリ | 独立 | 共有 |
| 作成コスト | 高い | 低い |
| 通信 | IPC が必要 | 直接共有可能 |
| 安全性 | 高い | 競合の可能性 |

---

### スレッドの作成と実行

**ソースファイル**: `apps/java/src/main/java/concurrency/ch04/ThreadBasics.java`

```java
package concurrency.ch04;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class ThreadBasics {

    /**
     * ワーカースレッドを作成
     */
    public static Thread createWorkerThread(Runnable task) {
        return new Thread(task);
    }

    /**
     * 複数のワーカースレッドを作成
     */
    public static List<Thread> createMultipleWorkers(int count, IntConsumer task) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int index = i;
            Thread thread = new Thread(() -> task.accept(index), "Worker-" + i);
            threads.add(thread);
        }
        return threads;
    }

    /**
     * 名前付きスレッドを作成
     */
    public static Thread createNamedThread(String name, Runnable task) {
        return new Thread(task, name);
    }

    /**
     * アクティブスレッド数を取得
     */
    public static int getActiveThreadCount() {
        return Thread.activeCount();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting thread demo...");
        System.out.println("Current process: " + ProcessHandle.current().pid());
        System.out.println("Thread count: " + getActiveThreadCount());

        int numThreads = 5;
        List<Thread> threads = createMultipleWorkers(numThreads, i -> {
            String name = Thread.currentThread().getName();
            System.out.println(name + " doing " + i + " work");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        for (Thread thread : threads) {
            thread.start();
        }

        System.out.println("Thread count: " + getActiveThreadCount());

        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("All workers done!");
    }
}
```

#### 実行結果

```
Starting thread demo...
Current process: 12345
Thread count: 1
Worker-0 doing 0 work
Worker-1 doing 1 work
Worker-2 doing 2 work
Worker-3 doing 3 work
Worker-4 doing 4 work
Thread count: 6
All workers done!
```

---

## 第5章: スレッドプールと並列処理

### ExecutorService によるスレッドプール

Java の `java.util.concurrent` パッケージを使うと、スレッドプールを簡単に利用できます。

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 固定サイズのスレッドプール
ExecutorService executor = Executors.newFixedThreadPool(4);

// タスクを送信
executor.submit(() -> {
    System.out.println("Task running in " + Thread.currentThread().getName());
});

// シャットダウン
executor.shutdown();
```

### Virtual Threads（Java 21）

Java 21 以降では、Virtual Threads（仮想スレッド）が利用可能です。

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

---

### 並列パスワードクラッキング

Part I で学んだ逐次処理版を、マルチスレッドで並列化します。

**ソースファイル**: `apps/java/src/main/java/concurrency/ch05/PasswordCrackerParallel.java`

```java
package concurrency.ch05;

import concurrency.ch02.PasswordCracker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PasswordCrackerParallel {

    public record ChunkRange(int start, int end) {}

    /**
     * パスワード範囲をチャンクに分割
     */
    public static List<ChunkRange> getChunks(int numChunks, int length) {
        int maxNumber = (int) Math.pow(10, length) - 1;
        int chunkSize = (maxNumber + 1) / numChunks;

        List<ChunkRange> chunks = new ArrayList<>();
        for (int i = 0; i < numChunks; i++) {
            int start = i * chunkSize;
            int end = (i == numChunks - 1) ? maxNumber : start + chunkSize - 1;
            chunks.add(new ChunkRange(start, end));
        }
        return chunks;
    }

    /**
     * チャンク内でパスワードを探索
     */
    public static String crackChunk(String cryptoHash, int length,
                                     int chunkStart, int chunkEnd) {
        List<String> combinations =
            PasswordCracker.getCombinations(length, chunkStart, chunkEnd);

        for (String combination : combinations) {
            if (PasswordCracker.checkPassword(cryptoHash, combination)) {
                return combination;
            }
        }
        return null;
    }

    /**
     * 複数スレッドでパスワードを並列解読
     */
    public static String crackPasswordParallel(String cryptoHash, int length) {
        int numCores = Runtime.getRuntime().availableProcessors();
        List<ChunkRange> chunks = getChunks(numCores, length);

        try (ExecutorService executor = Executors.newFixedThreadPool(numCores)) {
            List<Future<String>> futures = new ArrayList<>();

            for (ChunkRange chunk : chunks) {
                Future<String> future = executor.submit(() ->
                    crackChunk(cryptoHash, length, chunk.start(), chunk.end())
                );
                futures.add(future);
            }

            for (Future<String> future : futures) {
                String result = future.get();
                if (result != null) {
                    executor.shutdownNow();
                    return result;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
```

### パフォーマンス比較

| 処理方式 | 処理時間 | CPU 使用率 |
|----------|----------|------------|
| 逐次処理 | ~60秒 | 25% (1コア) |
| 並列処理（4コア） | ~15秒 | 100% (全コア) |

---

## Java の並行処理 API

### 主要クラス

| クラス | 説明 |
|--------|------|
| `Thread` | 基本的なスレッドクラス |
| `Runnable` | 引数なし、戻り値なしのタスク |
| `Callable<V>` | 戻り値を持つタスク |
| `Future<V>` | 非同期計算の結果 |
| `ExecutorService` | スレッドプール |

### ExecutorService の種類

| ファクトリメソッド | 説明 |
|-------------------|------|
| `newFixedThreadPool(n)` | 固定サイズのスレッドプール |
| `newCachedThreadPool()` | 必要に応じてスレッドを作成 |
| `newSingleThreadExecutor()` | 単一スレッド |
| `newVirtualThreadPerTaskExecutor()` | Virtual Thread（Java 21+） |

---

## 使い分けの指針

| タスクタイプ | 推奨 |
|--------------|------|
| I/O バウンド | スレッド / Virtual Thread |
| CPU バウンド | スレッド（コア数分） |
| 大量の短いタスク | Virtual Thread |

---

## 実行方法

### テストの実行

```bash
cd apps/java
./gradlew test --tests "concurrency.ch04.*"
./gradlew test --tests "concurrency.ch05.*"
```

### サンプルコードの実行

```bash
cd apps/java
./gradlew run -PmainClass=concurrency.ch04.ThreadBasics
./gradlew run -PmainClass=concurrency.ch05.PasswordCrackerParallel
```

---

## 次のステップ

Part III では、マルチタスキングとスケジューリングの概念を学びます。OS がどのようにタスクを切り替えるか、タイムシェアリングの仕組みを理解します。

---

## 参考コード

- `apps/java/src/main/java/concurrency/ch04/ThreadBasics.java`
- `apps/java/src/main/java/concurrency/ch05/PasswordCrackerParallel.java`
