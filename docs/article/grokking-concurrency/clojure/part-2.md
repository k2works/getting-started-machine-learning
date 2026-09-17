---
type: Article
title: "Part II - プロセスとスレッド"
description: "future と並列パスワードクラック"
tags: [article, grokking-concurrency, clojure, concurrency, threads]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part II: プロセスとスレッド

## 概要

本章では、Clojure のスレッド機能と future を学びます。

---

## スレッド基礎

### Java Thread の使用

```clojure
(defn run-in-thread
  "新しいスレッドでタスクを実行"
  [task]
  (let [thread (Thread. task)]
    (.start thread)
    thread))

;; 使用例
(run-in-thread #(println "Hello from thread!"))
```

---

## future

```clojure
;; 非同期計算
(def result (future
              (Thread/sleep 1000)
              (+ 1 2)))

;; 結果を取得（ブロッキング）
@result  ;; => 3
```

---

## 並列パスワードクラック

### チャンク分割

```clojure
(defn chunk-range
  "範囲をチャンクに分割"
  [start end num-chunks]
  (let [size (- end start)
        chunk-size (long (Math/ceil (/ size num-chunks)))]
    (loop [current start
           chunks []]
      (if (>= current end)
        chunks
        (let [next-end (min (+ current chunk-size) end)]
          (recur next-end (conj chunks [current next-end])))))))
```

---

## 並列クラック

```clojure
(defn crack-password-parallel
  "並列でパスワードをクラック"
  [target-hash length num-threads]
  (let [total-combinations (long (Math/pow 26 length))
        chunks (chunk-range 0 total-combinations num-threads)
        futures (doall
                  (for [[start end] chunks]
                    (future
                      (crack-password-range target-hash start end length))))]
    (some identity (map deref futures))))
```

---

## future の特徴

| 特徴 | 説明 |
|------|------|
| 非同期 | バックグラウンドで実行 |
| キャッシュ | 結果は一度だけ計算 |
| ブロッキング | deref で結果を待機 |
| 例外 | deref 時に再スロー |

---

## 次のステップ

[Part III](part-3.md) では、マルチタスキングを学びます。
