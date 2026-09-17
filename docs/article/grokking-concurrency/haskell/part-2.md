---
type: Article
title: "Part II - プロセスとスレッド"
description: "forkIO と async によるスレッド操作"
tags: [article, grokking-concurrency, haskell, concurrency, thread]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、Haskell の forkIO と async ライブラリを使ったスレッド操作を学びます。

---

## スレッドの基本

### forkIO によるスレッド作成

```haskell
import Control.Concurrent (forkIO, ThreadId)

-- | Create and start a new thread
createThread :: IO () -> IO ThreadId
createThread = forkIO
```

---

### async による複数スレッドの実行

```haskell
import Control.Concurrent.Async (async, wait)
import Control.Monad (forM, forM_)

-- | Run multiple threads and wait for completion
runThreads :: Int -> (Int -> IO ()) -> IO ()
runThreads count action = do
    handles <- forM [0..count-1] $ \i -> async (action i)
    forM_ handles wait
```

---

## 並列パスワードクラッカー

```haskell
import Control.Concurrent.Async (mapConcurrently)
import Data.Maybe (listToMaybe, catMaybes)

-- | Crack password using parallel execution
crackPasswordParallel :: String -> String -> Int -> IO (Maybe String)
crackPasswordParallel _ _ 0 = return Nothing
crackPasswordParallel cryptoHash alphabet len = do
    -- Parallelize on first character
    results <- mapConcurrently (tryFirstChar cryptoHash alphabet len) alphabet
    return $ listToMaybe (catMaybes results)
```

---

## async ライブラリ

| 関数 | 説明 |
|------|------|
| async | バックグラウンドで実行 |
| wait | 完了を待機 |
| cancel | キャンセル |
| mapConcurrently | 並列 map |

---

## 使用例

```haskell
import Control.Concurrent.STM

main :: IO ()
main = do
    counter <- newTVarIO (0 :: Int)

    runThreads 5 $ \_ ->
        atomically $ modifyTVar' counter (+1)

    result <- readTVarIO counter
    print result  -- 5
```

---

## 次のステップ

[Part III](part-3.md) では、マルチタスキングとゲームループを学びます。
