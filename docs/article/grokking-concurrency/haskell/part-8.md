---
type: Article
title: "Part VIII - 分散並列処理"
description: "MapReduce パターン"
tags: [article, grokking-concurrency, haskell, concurrency, mapreduce]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、MapReduce パターンと mapConcurrently を学びます。

---

## MapReduce パターン

### ワードカウント実装

```haskell
import Control.Concurrent.Async (mapConcurrently)
import Data.Char (toLower)
import Data.Map.Strict (Map)
import qualified Data.Map.Strict as Map

-- | Map: Convert text to (word, 1) pairs
mapPhase :: String -> [(String, Int)]
mapPhase text =
    [(map toLower word, 1) | word <- words text, not (null word)]

-- | Reduce: Aggregate word counts
reducePhase :: [(String, Int)] -> Map String Int
reducePhase = foldl' addPair Map.empty
  where
    addPair acc (word, count) = Map.insertWith (+) word count acc

-- | MapReduce: Count words in multiple texts
countWords :: [String] -> Map String Int
countWords texts =
    let mapped = concatMap mapPhase texts
    in reducePhase mapped
```

---

## 並列版

```haskell
-- | MapReduce with parallel execution
countWordsParallel :: [String] -> IO (Map String Int)
countWordsParallel [] = return Map.empty
countWordsParallel texts = do
    -- Map phase (parallel)
    mapped <- mapConcurrently (return . mapPhase) texts
    -- Reduce phase
    return $ reducePhase (concat mapped)
```

---

## 使用例

```haskell
main :: IO ()
main = do
    let texts = ["hello world", "hello haskell", "world of haskell"]

    result <- countWordsParallel texts
    -- fromList [("hello",2),("haskell",2),("of",1),("world",2)]
    print result
```

---

## mapConcurrently

```haskell
import Control.Concurrent.Async

-- 並列 map
results <- mapConcurrently processItem items

-- 並列 filter + map
results <- mapConcurrently (\x ->
    if condition x
        then return (Just (process x))
        else return Nothing
    ) items
```

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | Maybe、純粋関数 |
| II | スレッド | forkIO、async |
| III | マルチタスキング | STM、TVar |
| IV | 並列パターン | Fork/Join、Pipeline |
| V | 同期 | STM、デッドロックフリー |
| VI | ノンブロッキング | async |
| VII | 非同期 | race、concurrently |
| VIII | 分散処理 | MapReduce |

---

## 参考資料

- [Parallel and Concurrent Programming in Haskell](https://simonmar.github.io/pages/pcph.html)
- [async package](https://hackage.haskell.org/package/async)
- [stm package](https://hackage.haskell.org/package/stm)
- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
