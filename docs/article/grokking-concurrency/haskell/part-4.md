---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "Fork/Join と Pipeline パターン"
tags: [article, grokking-concurrency, haskell, concurrency, forkjoin, pipeline]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、Fork/Join と Pipeline パターンを学びます。

---

## Fork/Join パターン

### 投票カウンター

```haskell
import Control.Concurrent.Async (mapConcurrently)
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as Map

-- | Count votes sequentially
countVotes :: [String] -> Map String Int
countVotes = foldl' countOne Map.empty
  where
    countOne acc vote = Map.insertWith (+) vote 1 acc

-- | Count votes using Fork/Join pattern
countVotesParallel :: [String] -> IO (Map String Int)
countVotesParallel [] = return Map.empty
countVotesParallel votes = do
    let chunks = splitIntoChunks 4 votes
    results <- mapConcurrently (return . countVotes) chunks
    return $ foldl' (Map.unionWith (+)) Map.empty results
```

---

## Pipeline パターン

### Pipeline 構造

```haskell
-- | A pipeline of processing stages
newtype Pipeline a = Pipeline [a -> a]

-- | Create a new empty pipeline
newPipeline :: Pipeline a
newPipeline = Pipeline []

-- | Add a stage to the pipeline
addStage :: (a -> a) -> Pipeline a -> Pipeline a
addStage f (Pipeline stages) = Pipeline (stages ++ [f])

-- | Run the pipeline on a single input
runPipeline :: Pipeline a -> a -> a
runPipeline (Pipeline stages) input = foldl (flip ($)) input stages
```

---

### TQueue による並行パイプライン

```haskell
import Control.Concurrent.STM

-- | Concurrent pipeline using TQueues
concurrentPipeline :: [a -> a] -> [a] -> IO [a]
concurrentPipeline processors inputs = do
    -- Create queues between stages
    queues <- forM [1..numQueues] $ \_ -> newTQueueIO
    -- Feed input, create processor threads, collect results
    ...
```

---

## 使用例

```haskell
main :: IO ()
main = do
    -- Pipeline
    let pipeline = addStage (\x -> x - 3)
                 $ addStage (*2)
                 $ addStage (+1)
                 $ newPipeline
    print $ runPipeline pipeline (5 :: Int)  -- 9

    -- Fork/Join
    let votes = ["A", "B", "A", "A", "B", "C"]
    result <- countVotesParallel votes
    print result  -- fromList [("A",3),("B",2),("C",1)]
```

---

## 次のステップ

[Part V](part-5.md) では、STM による同期を学びます。
