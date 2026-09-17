---
type: Article
title: "Part VIII - 分散並列処理"
description: "pmap と MapReduce パターン"
tags: [article, grokking-concurrency, clojure, concurrency, mapreduce]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VIII: 分散並列処理

## 概要

本章では、pmap と MapReduce パターンを学びます。

---

## pmap（並列 map）

```clojure
;; 通常の map
(map inc [1 2 3 4 5])

;; 並列 map
(pmap inc [1 2 3 4 5])

;; CPU バウンドな処理に有効
(pmap expensive-computation large-collection)
```

---

## MapReduce パターン

### Map フェーズ

```clojure
(require '[clojure.string :as str])

(defn map-phase
  "Map: テキストを (word, 1) ペアに変換"
  [text]
  (->> (str/split (str/lower-case text) #"\s+")
       (filter (complement str/blank?))
       (map (fn [word] [word 1]))))
```

---

## Reduce フェーズ

```clojure
(defn reduce-phase
  "Reduce: ペアを集約してワードカウント"
  [pairs]
  (reduce (fn [acc [word count]]
            (update acc word (fnil + 0) count))
          {}
          pairs))
```

---

## ワードカウント実装

```clojure
(defn count-words-sequential
  "逐次ワードカウント"
  [texts]
  (->> texts
       (mapcat map-phase)
       reduce-phase))

(defn count-words-parallel
  "並列ワードカウント (pmap による MapReduce)"
  [texts]
  (if (empty? texts)
    {}
    (->> texts
         (pmap map-phase)
         (apply concat)
         reduce-phase)))
```

---

## 使用例

```clojure
(count-words-parallel
  ["hello world" "hello clojure" "world of clojure"])
;; => {"hello" 2, "world" 2, "clojure" 2, "of" 1}
```

---

## まとめ

本シリーズで学んだ内容:

| Part | トピック | キーポイント |
|------|----------|-------------|
| I | 逐次処理 | 純粋関数、SHA-256 |
| II | スレッド | future、Thread |
| III | マルチタスキング | atom、イベント同期 |
| IV | 並列パターン | Fork/Join、Pipeline |
| V | 同期 | STM、ref、dosync |
| VI | ノンブロッキング | future、promise |
| VII | 非同期 | core.async |
| VIII | 分散処理 | pmap、MapReduce |

---

## 参考資料

- [Clojure Reference: Atoms](https://clojure.org/reference/atoms)
- [Clojure Reference: Refs and Transactions](https://clojure.org/reference/refs)
- [core.async Guide](https://clojure.org/guides/async_walkthrough)
- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
