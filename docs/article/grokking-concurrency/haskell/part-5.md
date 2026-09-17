---
type: Article
title: "Part V - 同期と排他制御"
description: "STM によるデッドロックフリーな同期"
tags: [article, grokking-concurrency, haskell, concurrency, stm]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、STM を使ったデッドロックフリーな銀行口座を学びます。

---

## 銀行口座（STM）

```haskell
import Control.Concurrent.STM

-- | A thread-safe bank account using STM
data BankAccount = BankAccount
    { accountId      :: Int
    , accountBalance :: TVar Int
    }

-- | Create a new bank account
newBankAccount :: Int -> Int -> IO BankAccount
newBankAccount accId initialBalance = do
    balance <- newTVarIO initialBalance
    return $ BankAccount accId balance

-- | Get the current balance
getBalance :: BankAccount -> IO Int
getBalance account = readTVarIO (accountBalance account)

-- | Deposit money
deposit :: BankAccount -> Int -> IO ()
deposit account amount = atomically $
    modifyTVar' (accountBalance account) (+ amount)

-- | Withdraw money
withdraw :: BankAccount -> Int -> IO Bool
withdraw account amount = atomically $ do
    balance <- readTVar (accountBalance account)
    if balance >= amount
        then do
            writeTVar (accountBalance account) (balance - amount)
            return True
        else return False
```

---

## アトミック送金（デッドロックフリー）

```haskell
-- | Transfer money between accounts atomically
transfer :: BankAccount -> BankAccount -> Int -> IO Bool
transfer from to amount = atomically $ do
    fromBalance <- readTVar (accountBalance from)
    if fromBalance >= amount
        then do
            modifyTVar' (accountBalance from) (subtract amount)
            modifyTVar' (accountBalance to) (+ amount)
            return True
        else return False
```

---

## STM の利点

| 特徴 | 説明 |
|------|------|
| デッドロックフリー | 自動リトライによる解決 |
| 合成可能性 | 複数の操作を組み合わせ可能 |
| 例外安全 | ロールバックによる一貫性維持 |
| 簡潔さ | ロック順序を考慮不要 |

---

## STM vs MVar

| 機能 | STM | MVar |
|------|-----|------|
| 合成 | 可能 | 困難 |
| デッドロック | フリー | 可能性あり |
| パフォーマンス | やや低い | 高い |
| 複雑さ | 低い | 高い |

---

## 次のステップ

[Part VI](part-6.md) では、ノンブロッキング I/O を学びます。
