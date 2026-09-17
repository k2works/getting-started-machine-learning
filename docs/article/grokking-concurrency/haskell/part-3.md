---
type: Article
title: "Part III - マルチタスキングとスケジューリング"
description: "STM を使ったゲームループ"
tags: [article, grokking-concurrency, haskell, concurrency, stm]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part III: マルチタスキングとスケジューリング

## 概要

本章では、STM を使ったゲームループを学びます。

---

## ゲームタスク

```haskell
-- | A game task that can be processed
data GameTask = GameTask
    { taskName   :: String
    , taskAction :: IO ()
    }
```

---

## プロセッサフリーイベント（STM）

```haskell
import Control.Concurrent.STM

-- | Event for signaling processor availability
newtype ProcessorFreeEvent = ProcessorFreeEvent (TVar Bool)

-- | Create a new processor free event
newProcessorFreeEvent :: IO ProcessorFreeEvent
newProcessorFreeEvent = ProcessorFreeEvent <$> newTVarIO False

-- | Wait for the event to be signaled
waitEvent :: ProcessorFreeEvent -> IO ()
waitEvent (ProcessorFreeEvent var) = atomically $ do
    ready <- readTVar var
    if ready
        then writeTVar var False
        else retry

-- | Signal the event
signalEvent :: ProcessorFreeEvent -> IO ()
signalEvent (ProcessorFreeEvent var) = atomically $ writeTVar var True
```

---

## ゲームループ

```haskell
import Control.Monad (forM_, replicateM_)

-- | Simple game loop that processes tasks
gameLoop :: [GameTask] -> Int -> IO ()
gameLoop tasks iterations =
    replicateM_ iterations $ forM_ tasks taskAction

-- | Cooperative game loop with event signaling
cooperativeGameLoop :: [GameTask] -> ProcessorFreeEvent -> Int -> IO ()
cooperativeGameLoop tasks event iterations =
    replicateM_ iterations $ forM_ tasks $ \task -> do
        taskAction task
        signalEvent event
```

---

## STM の特徴

| 関数 | 説明 |
|------|------|
| atomically | トランザクションを実行 |
| readTVar | TVar を読み取る |
| writeTVar | TVar に書き込む |
| retry | 条件が満たされるまで待機 |

---

## 次のステップ

[Part IV](part-4.md) では、Fork/Join と Pipeline パターンを学びます。
