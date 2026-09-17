---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "async による非同期処理"
tags: [article, grokking-concurrency, haskell, concurrency, async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、Haskell の async ライブラリを使用した非同期処理を学びます。

---

## ブロッキング vs ノンブロッキング

### ブロッキング I/O

```haskell
-- スレッドは I/O 完了まで待機
content <- readFile "file.txt"
```

### ノンブロッキング I/O (async)

```haskell
import Control.Concurrent.Async

-- バックグラウンドで実行
handle <- async $ readFile "file.txt"
-- 他の処理を実行
doSomethingElse
-- 結果を取得
content <- wait handle
```

---

## async の基本

```haskell
import Control.Concurrent.Async

fetchData :: String -> IO String
fetchData url = do
    threadDelay 1000000  -- 1秒待機
    return $ "Data from " ++ url

main :: IO ()
main = do
    -- 非同期実行
    handle <- async $ fetchData "https://example.com"

    -- 同期的に待機
    result <- wait handle
    putStrLn result
```

---

## 並行実行

```haskell
import Control.Concurrent.Async

downloadAll :: [String] -> IO [String]
downloadAll urls = mapConcurrently fetchData urls

-- 使用例
main :: IO ()
main = do
    results <- downloadAll
        ["https://a.com", "https://b.com", "https://c.com"]
    mapM_ putStrLn results
```

---

## メリット

| 項目 | ブロッキング | async |
|------|-------------|-------|
| スレッド消費 | 待機中も占有 | 効率的 |
| スケーラビリティ | 低い | 高い |
| コード複雑さ | 低い | やや高い |

---

## 次のステップ

[Part VII](part-7.md) では、async ライブラリの詳細を学びます。
