---
type: Article
title: "Part V - 同期と排他制御"
description: "STM と ref による銀行口座"
tags: [article, grokking-concurrency, clojure, concurrency, stm]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 概要

本章では、Clojure の STM（Software Transactional Memory）を学びます。

---

## Ref と dosync

```clojure
;; ref の作成
(def balance (ref 1000))

;; トランザクション内で更新
(dosync
  (alter balance + 100))

;; 値を取得
@balance  ;; => 1100
```

---

## BankAccount 実装

### 口座作成

```clojure
(defn create-account
  "銀行口座を作成"
  [id initial-balance]
  {:id id
   :balance (ref initial-balance)})

(defn get-id [account] (:id account))
(defn get-balance [account] @(:balance account))
```

---

## 入出金

```clojure
(defn deposit!
  "入金"
  [account amount]
  (dosync
    (alter (:balance account) + amount)))

(defn withdraw!
  "出金（残高チェック付き）"
  [account amount]
  (dosync
    (if (>= @(:balance account) amount)
      (do
        (alter (:balance account) - amount)
        true)
      false)))
```

---

## 振込（デッドロックフリー）

```clojure
(defn transfer!
  "振込（STM によるデッドロックフリー）"
  [from to amount]
  (dosync
    (if (>= @(:balance from) amount)
      (do
        (alter (:balance from) - amount)
        (alter (:balance to) + amount)
        true)
      false)))
```

---

## STM の特徴

| 特徴 | 説明 |
|------|------|
| 自動リトライ | 競合時に自動的にリトライ |
| デッドロックフリー | ロック順序の問題なし |
| 一貫性 | トランザクション内は一貫 |
| 分離性 | 他のトランザクションから分離 |

---

## 並行テスト

```clojure
(let [a1 (create-account 1 1000)
      a2 (create-account 2 1000)
      f1 (future (dotimes [_ 50] (transfer! a1 a2 1)))
      f2 (future (dotimes [_ 50] (transfer! a2 a1 1)))]
  @f1 @f2
  (+ (get-balance a1) (get-balance a2)))
;; => 2000（常に合計金額が保持される）
```

---

## 次のステップ

[Part VI](part-6.md) では、ノンブロッキング I/O を学びます。
