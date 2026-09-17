---
type: Article
title: "Part II - プロセスとスレッド"
description: "F# でのスレッド操作と並列パスワードクラッキング"
tags: [article, grokking-concurrency, fsharp, concurrency, threads]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、スレッドの基本操作と Async を使った並列パスワードクラッキングを実装します。

---

## スレッドの基本

```fsharp
open System.Threading

module ThreadBasics =
    /// Create a worker thread with the given name and task
    let createWorker (name: string) (task: unit -> unit) : Thread =
        let thread = Thread(ThreadStart(task))
        thread.Name <- name
        thread

    /// Create multiple worker threads
    let createMultipleWorkers (count: int) (task: int -> unit) : Thread list =
        [ for i in 0 .. count - 1 do
            let thread = Thread(ThreadStart(fun () -> task i))
            thread.Name <- $"Worker-{i}"
            yield thread ]

    /// Start all threads and wait for them to complete
    let startAndJoinAll (threads: Thread list) : unit =
        threads |> List.iter (fun t -> t.Start())
        threads |> List.iter (fun t -> t.Join())
```

---

## 並列パスワードクラッキング

### Async による並列実行

```fsharp
type ChunkRange = { Start: int; End: int }

module PasswordCrackerParallel =
    /// Divide a range into chunks
    let getChunks (numChunks: int) (totalSize: int) : ChunkRange list =
        let chunkSize = totalSize / numChunks
        let remainder = totalSize % numChunks

        [ for i in 0 .. numChunks - 1 do
            let start = i * chunkSize + min i remainder
            let extraOne = if i < remainder then 1 else 0
            yield { Start = start; End = start + chunkSize + extraOne } ]

    /// Crack password using parallel execution
    let crackPasswordParallel (cryptoHash: string) (length: int) : string option =
        let combinations = getCombinations length
        let numCores = Environment.ProcessorCount
        let chunks = getChunks numCores (List.length combinations)

        let result = ref None
        let combinationsArray = combinations |> List.toArray

        let tasks =
            chunks
            |> List.map (fun chunk ->
                async {
                    let subList = combinationsArray.[chunk.Start .. chunk.End - 1]
                    for password in subList do
                        if Option.isNone !result && checkPassword password cryptoHash then
                            result := Some password
                })

        tasks
        |> Async.Parallel
        |> Async.RunSynchronously
        |> ignore

        !result
```

---

## ポイント

- **Async ワークフロー**: `async { ... }` で非同期計算を定義
- **Async.Parallel**: 複数の非同期計算を並列実行
- **参照セル (`ref`)**: 可変な共有状態

---

## 次のステップ

[Part III](part-3.md) では、マルチタスキングとゲームループを学びます。
