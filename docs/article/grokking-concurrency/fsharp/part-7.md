---
type: Article
title: "Part VII - 非同期プログラミング"
description: "Async ワークフローによる非同期処理"
tags: [article, grokking-concurrency, fsharp, concurrency, async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、F# の Async ワークフローを詳しく学びます。

---

## Async ワークフローの基本

```fsharp
// async { } 内で非同期計算を記述
let computation : Async<int> =
    async {
        printfn "Starting..."
        do! Async.Sleep(1000)
        printfn "Done!"
        return 42
    }
```

---

## let! と do!

```fsharp
let example : Async<int> =
    async {
        // let! : 値を取り出す
        let! result = someAsyncComputation()

        // do! : 値を無視
        do! Async.Sleep(100)

        return result * 2
    }
```

---

## 合成

```fsharp
// 順次実行
let sequential : Async<int> =
    async {
        let! a = computeA()
        let! b = computeB()
        return a + b
    }

// 並列実行
let parallel : Async<int[]> =
    [computeA(); computeB(); computeC()]
    |> Async.Parallel
```

---

## エラーハンドリング

```fsharp
let safeComputation : Async<Result<int, string>> =
    async {
        try
            let! result = riskyComputation()
            return Ok result
        with
        | ex -> return Error ex.Message
    }
```

---

## キャンセル

```fsharp
let cancellableComputation (ct: CancellationToken) : Async<unit> =
    async {
        for i in 1 .. 100 do
            ct.ThrowIfCancellationRequested()
            do! Async.Sleep(100)
            printfn $"Step {i}"
    }

// 使用例
let cts = new CancellationTokenSource()
Async.Start(cancellableComputation cts.Token)
// キャンセル
cts.Cancel()
```

---

## Task との相互運用

```fsharp
// Task -> Async
let asyncFromTask : Async<string> =
    someTask |> Async.AwaitTask

// Async -> Task
let taskFromAsync : Task<string> =
    someAsync |> Async.StartAsTask
```

---

## 次のステップ

[Part VIII](part-8.md) では、MapReduce パターンと並列配列を学びます。
