---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "async/await を使ったノンブロッキング I/O を学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, async, non-blocking]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、C# の async/await を使ったノンブロッキング I/O を学びます。

---

## async/await の基本

### 非同期メソッド

```csharp
public async Task<string> FetchDataAsync(string url)
{
    using var client = new HttpClient();
    return await client.GetStringAsync(url);
}
```

### 複数の非同期操作

```csharp
public async Task<string[]> FetchMultipleAsync(string[] urls)
{
    var tasks = urls.Select(url => FetchDataAsync(url));
    return await Task.WhenAll(tasks);
}
```

---

## 同期 vs 非同期

| 特性 | 同期 | 非同期 |
|------|------|--------|
| スレッド | ブロック | 解放 |
| スケーラビリティ | 低 | 高 |
| コード複雑度 | 低 | やや高 |

---

## 次のステップ

Part VII では、Task と非同期プログラミングを深く学びます。

---

## 参考資料

- [async/await](https://docs.microsoft.com/ja-jp/dotnet/csharp/programming-guide/concepts/async/)
