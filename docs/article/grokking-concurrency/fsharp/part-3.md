---
type: Article
title: "Part III - マルチタスキングとスケジューリング"
description: "ゲームループで学ぶマルチタスキング"
tags: [article, grokking-concurrency, fsharp, concurrency, multitasking, gameloop]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、ゲームループを例にマルチタスキングとスケジューリングを学びます。

---

## ゲームタスク

```fsharp
type GameTask = { Name: string; Action: unit -> unit }

module GameLoop =
    /// Run a single task
    let runTask (task: GameTask) : unit =
        task.Action()
```

---

## イベント同期

```fsharp
type ProcessorFreeEvent = {
    Lock: obj
    mutable Signaled: bool
}

module GameLoop =
    /// Create a new processor free event
    let createProcessorFreeEvent () : ProcessorFreeEvent =
        { Lock = obj(); Signaled = false }

    /// Wait for the event to be signaled
    let waitForSignal (event: ProcessorFreeEvent) : unit =
        lock event.Lock (fun () ->
            while not event.Signaled do
                Monitor.Wait(event.Lock) |> ignore
        )

    /// Signal the event
    let signal (event: ProcessorFreeEvent) : unit =
        lock event.Lock (fun () ->
            event.Signaled <- true
            Monitor.PulseAll(event.Lock)
        )
```

---

## ゲームループ

```fsharp
/// Run all tasks in a single frame
let runOneFrame (tasks: GameTask list) : unit =
    tasks |> List.iter runTask

/// Run game loop for specified number of frames
let run (tasks: GameTask list) (frames: int) : unit =
    for frame in 1 .. frames do
        printfn $"Frame {frame}"
        runOneFrame tasks
```

---

## 使用例

```fsharp
let tasks = [
    { Name = "Input"; Action = fun () -> printfn "Processing input..." }
    { Name = "Update"; Action = fun () -> printfn "Updating game state..." }
    { Name = "Render"; Action = fun () -> printfn "Rendering frame..." }
]

GameLoop.run tasks 3
```

---

## 次のステップ

[Part IV](part-4.md) では、Fork/Join パターンとパイプラインを学びます。
