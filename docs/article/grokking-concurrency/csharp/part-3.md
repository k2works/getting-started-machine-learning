---
type: Article
title: "Part III - マルチタスキングとスケジューリング"
description: "マルチタスキングの仕組みを学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, multitasking, scheduling]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、OS がどのようにタスクを切り替えるか、タイムシェアリングの仕組みを学びます。ゲームループの例を通じて、マルチタスキングを理解します。

---

## ゲームループの例

### タスクの定義

```csharp
public record GameTask(string Name, Action Action)
{
    public void Run() => Action();
}
```

### イベントによる調整

```csharp
public class ProcessorFreeEvent
{
    private readonly object _lock = new();
    private bool _signaled;

    public void WaitForSignal()
    {
        lock (_lock)
        {
            while (!_signaled)
            {
                Monitor.Wait(_lock);
            }
            _signaled = false;
        }
    }

    public void Signal()
    {
        lock (_lock)
        {
            _signaled = true;
            Monitor.Pulse(_lock);
        }
    }
}
```

### ゲームループ

```csharp
public class GameLoop(
    Action inputTask,
    Action computeTask,
    Action renderTask,
    Func<bool> continueCondition)
{
    public void Run()
    {
        while (continueCondition())
        {
            inputTask();
            computeTask();
            renderTask();
        }
    }
}
```

---

## スケジューリング方式

| 方式 | 説明 |
|------|------|
| 協調的 | タスクが自発的に制御を譲る |
| 先制的 | OS がタイムスライスで切り替え |

---

## 次のステップ

Part IV では、Fork/Join パターンとパイプラインパターンを学びます。

---

## 参考資料

- [System.Threading.Monitor](https://docs.microsoft.com/ja-jp/dotnet/api/system.threading.monitor)
