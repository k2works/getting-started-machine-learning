---
type: Article
title: "Part VII - 非同期プログラミング"
description: "Task と async/await を使った非同期プログラミングを学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, task, async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、C# の Task と async/await を使った非同期プログラミングを学びます。

---

## Task の基本

### Task の作成

```csharp
var task = Task.Run(() => {
    // 非同期処理
    return "Result";
});

string result = await task;
```

### チェーン処理

```csharp
var result = await Task.Run(() => FetchData())
    .ContinueWith(t => Process(t.Result))
    .ContinueWith(t => Save(t.Result));
```

### 並行実行

```csharp
var task1 = Task.Run(() => FetchData1());
var task2 = Task.Run(() => FetchData2());

await Task.WhenAll(task1, task2);
```

---

## ValueTask

```csharp
public ValueTask<int> GetCachedValueAsync()
{
    if (_cache.TryGetValue(key, out var value))
        return new ValueTask<int>(value);

    return new ValueTask<int>(FetchFromDatabaseAsync());
}
```

---

## 使い分けの指針

| シナリオ | 推奨 |
|----------|------|
| I/O バウンド | async/await |
| CPU バウンド | Task.Run / Parallel |
| 混合 | async/await + Task.Run |

---

## 次のステップ

Part VIII では、MapReduce パターンと PLINQ を学びます。

---

## 参考資料

- [Task](https://docs.microsoft.com/ja-jp/dotnet/api/system.threading.tasks.task)
- [ValueTask](https://docs.microsoft.com/ja-jp/dotnet/api/system.threading.tasks.valuetask-1)
