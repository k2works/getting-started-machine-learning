---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "Fork/Join パターンとパイプラインパターンを学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, fork-join, pipeline]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、Fork/Join パターンとパイプラインパターンを学びます。投票集計とパイプライン処理の例を通じて、並列パターンを理解します。

---

## Fork/Join パターン: 投票集計

### 逐次処理

```csharp
public static Dictionary<int, int> CountVotes(List<int> votes)
{
    var result = new Dictionary<int, int>();
    foreach (var vote in votes)
    {
        if (result.ContainsKey(vote))
            result[vote]++;
        else
            result[vote] = 1;
    }
    return result;
}
```

### 並列処理

```csharp
public static Dictionary<int, int> CountVotesParallel(List<int> votes, int numWorkers)
{
    var chunkSize = (int)Math.Ceiling((double)votes.Count / numWorkers);
    var chunks = new List<List<int>>();

    for (var i = 0; i < votes.Count; i += chunkSize)
    {
        chunks.Add(votes.GetRange(i, Math.Min(chunkSize, votes.Count - i)));
    }

    // Fork
    var tasks = chunks.Select(chunk =>
        Task.Run(() => CountVotes(chunk))
    ).ToArray();

    // Join
    Task.WaitAll(tasks);
    return tasks.Aggregate(new Dictionary<int, int>(),
        (acc, t) => MergeResults(acc, t.Result));
}
```

---

## パイプラインパターン

### ステージワーカー

```csharp
public class Pipeline<T>
{
    public void AddStage(string name, Func<object, object> processor) { ... }
    public void Start() { ... }
    public void Submit(T item) { ... }
    public void Stop() { ... }
}
```

### 使用例

```csharp
var pipeline = new Pipeline<string>();
pipeline.AddStage("wash", item => "washed:" + item);
pipeline.AddStage("dry", item => "dried:" + item);
pipeline.AddStage("fold", item => "folded:" + item);

pipeline.Start();
pipeline.Submit("load1");
pipeline.Stop();
```

---

## パターンの比較

| パターン | 用途 |
|----------|------|
| Fork/Join | データ並列処理 |
| Pipeline | ステージ並列処理 |

---

## 次のステップ

Part V では、同期と排他制御を学びます。

---

## 参考資料

- [Task Parallel Library](https://docs.microsoft.com/ja-jp/dotnet/standard/parallel-programming/)
- [BlockingCollection](https://docs.microsoft.com/ja-jp/dotnet/api/system.collections.concurrent.blockingcollection-1)
