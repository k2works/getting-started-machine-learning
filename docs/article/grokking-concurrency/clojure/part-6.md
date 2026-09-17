---
type: Article
title: "Part VI - ノンブロッキング I/O"
description: "future と promise"
tags: [article, grokking-concurrency, clojure, concurrency, async]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 概要

本章では、Clojure の future と promise による非同期処理を学びます。

---

## Future

```clojure
;; 非同期計算
(def result (future
              (Thread/sleep 1000)
              (+ 1 2)))

;; 結果を取得（ブロッキング）
@result  ;; => 3

;; タイムアウト付き
(deref result 500 :timeout)
```

---

## Promise

```clojure
;; Promise 作成
(def p (promise))

;; 別スレッドで値を設定
(future
  (Thread/sleep 100)
  (deliver p :done))

;; 結果を待機
@p  ;; => :done
```

---

## 複数の非同期タスク

```clojure
(defn fetch-all [urls]
  (let [futures (doall (map #(future (fetch-url %)) urls))]
    (map deref futures)))
```

---

## タイムアウト処理

```clojure
(defn with-timeout [timeout-ms f]
  (let [result (future (f))]
    (deref result timeout-ms :timeout)))

;; 使用例
(with-timeout 1000 #(Thread/sleep 500))  ;; => nil
(with-timeout 100 #(Thread/sleep 500))   ;; => :timeout
```

---

## future vs promise

| 特徴 | future | promise |
|------|--------|---------|
| 計算開始 | 即座に開始 | deliver まで待機 |
| 値の設定 | 内部で計算 | 外部から deliver |
| 用途 | 非同期計算 | スレッド間通信 |

---

## 次のステップ

[Part VII](part-7.md) では、core.async を学びます。
