---
type: Article
title: "Part VII - 非同期プログラミング"
description: "async ライブラリの詳細"
tags: [article, grokking-concurrency, haskell, concurrency, async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VII: 非同期プログラミング

## 概要

本章では、Haskell の async ライブラリを詳しく学びます。

---

## Async 型

```haskell
-- Async a は非同期計算を表す
data Async a

-- 非同期計算を開始
async :: IO a -> IO (Async a)

-- 結果を待機
wait :: Async a -> IO a

-- キャンセル
cancel :: Async a -> IO ()
```

---

## 並行実行

```haskell
import Control.Concurrent.Async

-- 順次実行
sequential :: IO (Int, Int)
sequential = do
    a <- computeA
    b <- computeB
    return (a, b)

-- 並行実行
parallel :: IO (Int, Int)
parallel = concurrently computeA computeB
```

---

## race

```haskell
import Control.Concurrent.Async

-- 先に終わった方を返す
raceExample :: IO (Either String Int)
raceExample = race fetchFromServerA fetchFromServerB
```

---

## エラーハンドリング

```haskell
import Control.Concurrent.Async
import Control.Exception (try, SomeException)

safeComputation :: IO (Either SomeException Int)
safeComputation = do
    result <- async riskyComputation
    try (wait result)
```

---

## withAsync（リソース安全）

```haskell
import Control.Concurrent.Async

-- 完了後に自動的にキャンセル
withAsync action $ \handle -> do
    result <- wait handle
    processResult result
```

---

## async 関数一覧

| 関数 | 説明 |
|------|------|
| async | バックグラウンド実行 |
| wait | 完了待機 |
| cancel | キャンセル |
| race | 競争 |
| concurrently | 両方並行実行 |
| mapConcurrently | 並列 map |
| withAsync | リソース安全な実行 |

---

## 次のステップ

[Part VIII](part-8.md) では、MapReduce パターンを学びます。
