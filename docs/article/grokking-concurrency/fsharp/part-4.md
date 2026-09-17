---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "Fork/Join パターンとパイプラインパターン"
tags: [article, grokking-concurrency, fsharp, concurrency, fork-join, pipeline]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、Fork/Join パターンとパイプラインパターンを学びます。

---

## Fork/Join パターン: 投票カウンター

### 逐次カウント

```fsharp
module VoteCounter =
    /// Count votes sequentially
    let countVotes (votes: string list) : Map<string, int> =
        votes
        |> List.groupBy id
        |> List.map (fun (key, group) -> (key, List.length group))
        |> Map.ofList

    /// Merge two vote count results
    let mergeResults (a: Map<string, int>) (b: Map<string, int>) : Map<string, int> =
        let allKeys = Set.union (Map.keys a |> Set.ofSeq) (Map.keys b |> Set.ofSeq)
        allKeys
        |> Set.toList
        |> List.map (fun key ->
            let countA = Map.tryFind key a |> Option.defaultValue 0
            let countB = Map.tryFind key b |> Option.defaultValue 0
            (key, countA + countB))
        |> Map.ofList
```

### 並列カウント

```fsharp
/// Count votes using fork-join pattern
let countVotesParallel (votes: string list) : Map<string, int> =
    if List.isEmpty votes then
        Map.empty
    else
        let numCores = Environment.ProcessorCount
        let chunkSize = max 1 (List.length votes / numCores)
        let chunks = votes |> List.chunkBySize chunkSize

        let results =
            chunks
            |> List.map (fun chunk -> async { return countVotes chunk })
            |> Async.Parallel
            |> Async.RunSynchronously
            |> Array.toList

        results |> List.reduce mergeResults
```

---

## パイプラインパターン

```fsharp
type Stage = { Name: string; Processor: obj -> obj }
type Pipeline<'T> = { Stages: Stage list }

module Pipeline =
    let createPipeline<'T> : Pipeline<'T> =
        { Stages = [] }

    let addStage (name: string) (processor: obj -> obj) (pipeline: Pipeline<'T>) : Pipeline<'T> =
        { pipeline with Stages = pipeline.Stages @ [{ Name = name; Processor = processor }] }

    let run (pipeline: Pipeline<'T>) (data: 'T list) : obj list =
        if List.isEmpty pipeline.Stages then
            data |> List.map (fun x -> x :> obj)
        else
            data
            |> List.map (fun item ->
                pipeline.Stages
                |> List.fold (fun current stage -> stage.Processor current) (item :> obj)
            )
```

---

## 使用例

```fsharp
let pipeline =
    Pipeline.createPipeline<int>
    |> Pipeline.addStage "double" (fun x -> (x :?> int) * 2 :> obj)
    |> Pipeline.addStage "addOne" (fun x -> (x :?> int) + 1 :> obj)

let results = Pipeline.run pipeline [1; 2; 3]
// [3; 5; 7]
```

---

## 次のステップ

[Part V](part-5.md) では、同期と排他制御を学びます。
