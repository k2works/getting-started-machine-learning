---
type: Article
title: "Part VIII - 分散並列処理"
description: "MapReduce パターンと並列配列"
tags: [article, grokking-concurrency, fsharp, concurrency, mapreduce, parallel]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、MapReduce パターンと F# の並列配列を学びます。

---

## MapReduce パターン

### ワードカウント実装

```fsharp
module WordCount =
    /// Map: Convert text to (word, 1) pairs
    let map (text: string) : (string * int) list =
        text.ToLower().Split([|' '; '\t'; '\n'; '\r'|], StringSplitOptions.RemoveEmptyEntries)
        |> Array.filter (fun s -> s.Length > 0)
        |> Array.map (fun word -> (word, 1))
        |> Array.toList

    /// Reduce: Aggregate word counts
    let reduce (pairs: (string * int) list) : Map<string, int> =
        pairs
        |> List.groupBy fst
        |> List.map (fun (key, group) -> (key, group |> List.sumBy snd))
        |> Map.ofList

    /// MapReduce: Count words in multiple texts using parallel execution
    let countWords (texts: string list) : Map<string, int> =
        // Map phase (parallel)
        let mapped =
            texts
            |> List.toArray
            |> Array.Parallel.collect (fun text -> map text |> List.toArray)
            |> Array.toList

        // Reduce phase
        reduce mapped
```

---

## 使用例

```fsharp
let texts = [
    "hello world"
    "hello fsharp"
    "world of fsharp"
]

let result = WordCount.countWords texts
// Map [("hello", 2); ("world", 2); ("fsharp", 2); ("of", 1)]
```

---

## Array.Parallel

```fsharp
// 並列 map
let doubled = [|1..1000000|] |> Array.Parallel.map (fun x -> x * 2)

// 並列 filter + map (collect)
let processed =
    data
    |> Array.Parallel.collect (fun x ->
        if x > 0 then [|x * 2|]
        else [||]
    )
```

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | 基本概念、パイプライン演算子 |
| II | スレッド | Thread、Async.Parallel |
| III | マルチタスキング | ゲームループ |
| IV | 並列パターン | Fork/Join、Pipeline |
| V | 同期 | lock、デッドロック回避 |
| VI | ノンブロッキング | Async |
| VII | 非同期 | Async ワークフロー |
| VIII | 分散処理 | MapReduce、Array.Parallel |

---

## 参考資料

- [F# Async Programming](https://docs.microsoft.com/ja-jp/dotnet/fsharp/tutorials/asynchronous-and-concurrent-programming/async)
- [Array.Parallel Module](https://fsharp.github.io/fsharp-core-docs/reference/fsharp-collections-arraymodule-parallel.html)
- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
