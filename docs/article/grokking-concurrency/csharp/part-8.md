---
type: Article
title: "Part VIII - 分散並列処理"
description: "MapReduce パターンと PLINQ を学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, mapreduce, plinq]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、MapReduce パターンと PLINQ を使った分散並列処理を学びます。

---

## MapReduce パターン

### ワードカウントの例

```csharp
public static class WordCount
{
    // Map: テキストを (word, 1) のペアに変換
    public static List<KeyValuePair<string, int>> Map(string text)
    {
        return text.ToLower()
            .Split(' ', StringSplitOptions.RemoveEmptyEntries)
            .Select(word => new KeyValuePair<string, int>(word, 1))
            .ToList();
    }

    // Reduce: 同じ単語のカウントを集約
    public static Dictionary<string, int> Reduce(List<KeyValuePair<string, int>> pairs)
    {
        return pairs
            .GroupBy(p => p.Key)
            .ToDictionary(g => g.Key, g => g.Sum(p => p.Value));
    }

    // MapReduce
    public static Dictionary<string, int> CountWords(List<string> texts)
    {
        // Map フェーズ（並列）
        var mapped = texts
            .AsParallel()
            .SelectMany(text => Map(text))
            .ToList();

        // Reduce フェーズ
        return Reduce(mapped);
    }
}
```

---

## PLINQ

```csharp
var result = data
    .AsParallel()
    .Where(x => x > 0)
    .Select(x => x * 2)
    .ToList();
```

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | 基本概念 |
| II | スレッド | Thread, Task |
| III | マルチタスキング | ゲームループ |
| IV | 並列パターン | Fork/Join, Pipeline |
| V | 同期 | lock, Monitor |
| VI | ノンブロッキング | async/await |
| VII | 非同期 | Task |
| VIII | 分散処理 | MapReduce, PLINQ |

---

## 参考資料

- [PLINQ](https://docs.microsoft.com/ja-jp/dotnet/standard/parallel-programming/introduction-to-plinq)
- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
