---
type: Article
title: "Part V - 同期と排他制御"
description: "銀行口座で学ぶロックとデッドロック回避"
tags: [article, grokking-concurrency, fsharp, concurrency, synchronization, lock]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、銀行口座を例に同期とデッドロック回避を学びます。

---

## 銀行口座レコード

```fsharp
type BankAccount = {
    Lock: obj
    mutable Balance: int
}

module BankAccount =
    /// Create a new bank account
    let createAccount (initialBalance: int) : BankAccount =
        { Lock = obj(); Balance = initialBalance }

    /// Get the current balance
    let getBalance (account: BankAccount) : int =
        lock account.Lock (fun () -> account.Balance)

    /// Deposit money into the account
    let deposit (account: BankAccount) (amount: int) : unit =
        lock account.Lock (fun () ->
            account.Balance <- account.Balance + amount
        )

    /// Withdraw money from the account
    let withdraw (account: BankAccount) (amount: int) : bool =
        lock account.Lock (fun () ->
            if account.Balance >= amount then
                account.Balance <- account.Balance - amount
                true
            else
                false
        )
```

---

## デッドロック回避

```fsharp
open System.Runtime.CompilerServices

/// Transfer money between accounts atomically, avoiding deadlock
let transfer (from: BankAccount) (toAccount: BankAccount) (amount: int) : bool =
    // Always lock in consistent order to avoid deadlock
    let fromHash = RuntimeHelpers.GetHashCode(from)
    let toHash = RuntimeHelpers.GetHashCode(toAccount)
    let (first, second) =
        if fromHash < toHash then (from, toAccount)
        else (toAccount, from)

    lock first.Lock (fun () ->
        lock second.Lock (fun () ->
            if from.Balance >= amount then
                from.Balance <- from.Balance - amount
                toAccount.Balance <- toAccount.Balance + amount
                true
            else
                false
        )
    )
```

---

## F# の lock 関数

```fsharp
// F# の lock 関数は以下のパターンを簡潔に書ける
lock lockObj (fun () ->
    // クリティカルセクション
    doSomething()
)

// これは以下と等価
Monitor.Enter(lockObj)
try
    doSomething()
finally
    Monitor.Exit(lockObj)
```

---

## 次のステップ

[Part VI](part-6.md) では、ノンブロッキング I/O を学びます。
