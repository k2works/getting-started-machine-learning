---
type: Article
title: "Part II - プロセスとスレッド"
description: "プロセスとスレッドの基本を学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, thread, process]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、OS レベルの並行処理の基本単位であるプロセスとスレッドを学びます。C# の `Thread` クラスと `Task` を使った並列処理を実装します。

---

## スレッドの基本操作

### スレッドの作成

```csharp
public static Thread CreateWorkerThread(Action task)
{
    return new Thread(() => task());
}
```

### 複数スレッドの作成

```csharp
public static List<Thread> CreateMultipleWorkers(int count, Action<int> task)
{
    var threads = new List<Thread>();
    for (var i = 0; i < count; i++)
    {
        var index = i;
        var thread = new Thread(() => task(index))
        {
            Name = $"Worker-{index}"
        };
        threads.Add(thread);
    }
    return threads;
}
```

---

## 並列パスワードクラッカー

### チャンク分割

```csharp
public record ChunkRange(int Start, int End);

public static List<ChunkRange> GetChunks(int numChunks, int length)
{
    var maxNumber = (int)Math.Pow(10, length) - 1;
    var chunkSize = (maxNumber + 1) / numChunks;

    var chunks = new List<ChunkRange>();
    for (var i = 0; i < numChunks; i++)
    {
        var start = i * chunkSize;
        var end = i == numChunks - 1 ? maxNumber : start + chunkSize - 1;
        chunks.Add(new ChunkRange(start, end));
    }
    return chunks;
}
```

### 並列処理

```csharp
public static string? CrackPasswordParallel(string cryptoHash, int length)
{
    var numCores = Environment.ProcessorCount;
    var chunks = GetChunks(numCores, length);

    using var cts = new CancellationTokenSource();
    var tasks = chunks.Select(chunk =>
        Task.Run(() => CrackChunk(cryptoHash, length, chunk.Start, chunk.End), cts.Token)
    ).ToArray();

    // 最初に見つかった結果を返す
    while (tasks.Any(t => !t.IsCompleted))
    {
        var completedTask = Task.WhenAny(tasks).Result;
        if (completedTask.Result != null)
        {
            cts.Cancel();
            return completedTask.Result;
        }
    }
    return null;
}
```

---

## プロセスとスレッドの違い

| 特性 | プロセス | スレッド |
|------|----------|----------|
| メモリ空間 | 独立 | 共有 |
| 作成コスト | 高い | 低い |
| 通信 | IPC 必要 | 直接共有 |
| 分離性 | 高い | 低い |

---

## 次のステップ

Part III では、マルチタスキングとスケジューリングを学びます。

---

## 参考資料

- [System.Threading.Thread](https://docs.microsoft.com/ja-jp/dotnet/api/system.threading.thread)
- [Task Parallel Library](https://docs.microsoft.com/ja-jp/dotnet/standard/parallel-programming/task-parallel-library-tpl)
