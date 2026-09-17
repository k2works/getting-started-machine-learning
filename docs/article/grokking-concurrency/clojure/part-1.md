---
type: Article
title: "Part I - 逐次処理"
description: "Clojure の基本と SHA-256 ハッシュ"
tags: [article, grokking-concurrency, clojure, concurrency, sequential]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part I: 逐次処理

## 概要

本章では、Clojure の基本と逐次処理を学びます。

---

## PasswordCracker 実装

### SHA-256 ハッシュ

```clojure
(ns grokking-concurrency.ch02.password-cracker
  (:require [buddy.core.hash :as hash]
            [buddy.core.codecs :as codecs]))

(defn sha256
  "SHA256 ハッシュを計算"
  [s]
  (-> (hash/sha256 s)
      (codecs/bytes->hex)))
```

---

## パスワード生成

```clojure
(def ^:private alphabet "abcdefghijklmnopqrstuvwxyz")

(defn generate-password
  "数値インデックスからパスワードを生成"
  [index length]
  (let [base (count alphabet)]
    (loop [idx index
           acc '()]
      (if (= (count acc) length)
        (apply str acc)
        (recur (quot idx base)
               (cons (nth alphabet (mod idx base)) acc))))))
```

---

## 範囲検索

```clojure
(defn crack-password-range
  "範囲内でパスワードを検索"
  [target-hash start end length]
  (loop [idx start]
    (when (< idx end)
      (let [password (generate-password idx length)
            hash (sha256 password)]
        (if (= hash target-hash)
          password
          (recur (inc idx)))))))
```

---

## 逐次クラック

```clojure
(defn crack-password
  "パスワードをクラック（逐次処理）"
  [target-hash length]
  (let [total-combinations (long (Math/pow (count alphabet) length))]
    (crack-password-range target-hash 0 total-combinations length)))
```

---

## 使用例

```clojure
(let [target-hash (sha256 "aabb")]
  (crack-password target-hash 4))
;; => "aabb"
```

---

## Clojure の特徴

| 特徴 | 説明 |
|------|------|
| 不変データ | デフォルトで不変 |
| 遅延評価 | lazy sequences |
| マクロ | コード生成 |
| REPL | 対話的開発 |

---

## 次のステップ

[Part II](part-2.md) では、スレッドと並列処理を学びます。
