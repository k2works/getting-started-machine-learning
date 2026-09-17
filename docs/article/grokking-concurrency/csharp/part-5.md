---
type: Article
title: "Part V - 同期と排他制御"
description: "Lock と Monitor を使った同期を学ぶ"
tags: [article, grokking-concurrency, csharp, concurrency, lock, synchronization]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、Lock と Monitor を使った同期を学びます。銀行口座の例を通じて、スレッドセーフな実装とデッドロック回避を理解します。

---

## 銀行口座の例

### lock を使った実装

```csharp
public class BankAccount
{
    private readonly object _lock = new();
    private int _balance;

    public void Deposit(int amount)
    {
        lock (_lock)
        {
            _balance += amount;
        }
    }

    public bool Withdraw(int amount)
    {
        lock (_lock)
        {
            if (_balance >= amount)
            {
                _balance -= amount;
                return true;
            }
            return false;
        }
    }
}
```

### デッドロック回避

```csharp
public static bool Transfer(BankAccount from, BankAccount to, int amount)
{
    // 常に同じ順序でロックを取得
    var first = RuntimeHelpers.GetHashCode(from) < RuntimeHelpers.GetHashCode(to) ? from : to;
    var second = first == from ? to : from;

    lock (first._lock)
    {
        lock (second._lock)
        {
            if (from._balance >= amount)
            {
                from._balance -= amount;
                to._balance += amount;
                return true;
            }
            return false;
        }
    }
}
```

---

## 同期プリミティブ

| プリミティブ | 用途 |
|-------------|------|
| `lock` | 排他制御 |
| `Monitor` | 条件変数 |
| `SemaphoreSlim` | リソース数制限 |
| `ReaderWriterLockSlim` | 読み書きロック |

---

## 次のステップ

Part VI では、ノンブロッキング I/O を学びます。

---

## 参考資料

- [lock ステートメント](https://docs.microsoft.com/ja-jp/dotnet/csharp/language-reference/statements/lock)
- [System.Threading.Monitor](https://docs.microsoft.com/ja-jp/dotnet/api/system.threading.monitor)
