---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "Async によるノンブロッキング処理"
tags: [article, grokking-concurrency, fsharp, concurrency, nio, async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、F# の Async を使用したノンブロッキング I/O を学びます。

---

## ブロッキング vs ノンブロッキング

### ブロッキング I/O

```fsharp
// スレッドは I/O 完了まで待機
let content = File.ReadAllText("file.txt")
```

### ノンブロッキング I/O (Async)

```fsharp
let readFileAsync (path: string) : Async<string> =
    async {
        use reader = new StreamReader(path)
        let! content = reader.ReadToEndAsync() |> Async.AwaitTask
        return content
    }
```

---

## Async の基本

```fsharp
// 非同期計算を定義（実行されない）
let fetchData (url: string) : Async<string> =
    async {
        use client = new HttpClient()
        let! response = client.GetStringAsync(url) |> Async.AwaitTask
        return response
    }

// 同期的に実行
let result = Async.RunSynchronously (fetchData "https://example.com")

// 非同期的に開始
Async.Start (async {
    let! result = fetchData "https://example.com"
    printfn "%s" result
})
```

---

## 並列 Async

```fsharp
let downloadAll (urls: string list) : Async<string[]> =
    urls
    |> List.map fetchData
    |> Async.Parallel

// 使用例
let results =
    ["https://a.com"; "https://b.com"; "https://c.com"]
    |> downloadAll
    |> Async.RunSynchronously
```

---

## メリット

| 項目 | ブロッキング | Async |
|------|-------------|-------|
| スレッド消費 | 待機中も占有 | 解放 |
| スケーラビリティ | 低い | 高い |
| コード複雑さ | 低い | やや高い |

---

## 次のステップ

[Part VII](part-7.md) では、Async ワークフローの詳細を学びます。
