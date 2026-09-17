---
type: Article
title: "Part IV - タスク分解と並列パターン"
description: "Fork/Join と Pipeline"
tags: [article, grokking-concurrency, clojure, concurrency, fork-join]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part IV: タスク分解と並列パターン

## 概要

本章では、Fork/Join パターンと Pipeline パターンを学びます。

---

## Fork/Join パターン

### VoteCounter 実装

```clojure
(defn count-votes-sequential
  "逐次で投票をカウント"
  [votes]
  (reduce (fn [acc vote]
            (update acc vote (fnil inc 0)))
          {}
          votes))

(defn merge-counts
  "複数のカウント結果をマージ"
  [counts]
  (reduce (fn [acc m]
            (merge-with + acc m))
          {}
          counts))
```

---

## 並列 Fork/Join

```clojure
(defn count-votes-parallel
  "並列で投票をカウント（Fork/Join パターン）"
  [votes num-workers]
  (if (empty? votes)
    {}
    (let [chunks (partition-all (max 1 (quot (count votes) num-workers)) votes)
          futures (doall (map #(future (count-votes-sequential %)) chunks))
          results (map deref futures)]
      (merge-counts results))))
```

---

## Pipeline パターン

### core.async を使用

```clojure
(require '[clojure.core.async :as async :refer [chan go >! <! >!! <!!]])

(defn create-pipeline
  "入力を2倍にするパイプラインを作成"
  []
  (let [input (chan 10)
        output (chan 10)]
    (go
      (loop []
        (when-let [v (<! input)]
          (>! output (* v 2))
          (recur))))
    {:input input :output output}))
```

---

## パイプライン操作

```clojure
(defn send-to-pipeline! [pipeline value]
  (>!! (:input pipeline) value))

(defn receive-from-pipeline! [pipeline]
  (<!! (:output pipeline)))

;; 使用例
(let [p (create-pipeline)]
  (send-to-pipeline! p 5)
  (receive-from-pipeline! p))  ;; => 10
```

---

## チェーンパイプライン

```clojure
(defn run-chain-pipeline
  "チェーンパイプラインを実行"
  [initial-value functions]
  (if (empty? functions)
    initial-value
    (let [channels (repeatedly (inc (count functions)) #(chan 1))]
      (doseq [[in-ch out-ch f] (map vector channels (rest channels) functions)]
        (go
          (when-let [v (<! in-ch)]
            (>! out-ch (f v)))))
      (>!! (first channels) initial-value)
      (<!! (last channels)))))

;; 使用例
(run-chain-pipeline 5 [#(* % 2) #(+ % 3) #(* % 10)])
;; => 130  ; ((5 * 2) + 3) * 10
```

---

## 次のステップ

[Part V](part-5.md) では、STM による同期と排他制御を学びます。
