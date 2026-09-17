---
type: Article
title: "第 11 章: 永続データ構造とパイプライン処理"
description: "この章では Clojure の 永続データ構造（Persistent Data Structures）と、それを活用した パイプライン処理 を学びます。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 永続データ構造とパイプライン処理

## 11.1 はじめに

この章では Clojure の **永続データ構造**（Persistent Data Structures）と、それを活用した **パイプライン処理** を学びます。

## 11.2 永続データ構造

Clojure のデータ構造は **不変**（Immutable）です。「変更」操作は常に新しいデータ構造を返し、元のデータは変更されません。

### 構造共有

永続データ構造は **構造共有**（Structural Sharing）により、効率的にコピーなしで「変更」を実現します。

```clojure
(def original [1 2 3 4 5])
(def modified (conj original 6))

original  ;=> [1 2 3 4 5]   ← 変更されない
modified  ;=> [1 2 3 4 5 6] ← 新しいベクタ
```

### 主要なデータ構造

| データ構造 | リテラル | 特徴 |
|-----------|----------|------|
| ベクタ | `[1 2 3]` | インデックスアクセス O(log32 N) |
| リスト | `'(1 2 3)` | 先頭追加 O(1) |
| マップ | `{:a 1 :b 2}` | キーによるアクセス O(log32 N) |
| セット | `#{1 2 3}` | 一意な値の集合 |

### FizzBuzz での活用

```clojure
;; ベクタとして FizzBuzz リストを生成
(def fizzbuzz-results
  (mapv fizzbuzz (range 1 101)))

;; マップで統計情報を集計
(def stats
  (->> fizzbuzz-results
       (group-by identity)
       (map (fn [[k v]] [k (count v)]))
       (into {})))

;; => {"1" 1, "2" 1, "Fizz" 27, "4" 1, "Buzz" 14, ...}
```

## 11.3 シーケンス抽象

Clojure のシーケンス抽象は、すべてのコレクションに統一的なインターフェースを提供します。

### 遅延シーケンス

```clojure
;; 無限の FizzBuzz シーケンス（遅延評価）
(def infinite-fizzbuzz
  (map fizzbuzz (iterate inc 1)))

;; 必要な分だけ取得
(take 15 infinite-fizzbuzz)
;=> ("1" "2" "Fizz" "4" "Buzz" "Fizz" "7" "8" "Fizz" "Buzz"
;    "11" "Fizz" "13" "14" "FizzBuzz")
```

`iterate` は無限シーケンスを生成しますが、遅延評価のため実際にはアクセスした要素のみが計算されます。

### トランスデューサー

トランスデューサーは変換処理をコレクションから分離し、再利用可能にします。

```clojure
;; トランスデューサーの定義
(def fizzbuzz-xf
  (comp
    (map fizzbuzz)
    (filter #(re-matches #"[A-Za-z]+" %))))

;; ベクタに適用
(into [] fizzbuzz-xf (range 1 16))
;=> ["Fizz" "Buzz" "Fizz" "Fizz" "Buzz" "Fizz" "FizzBuzz"]

;; 文字列に適用
(transduce fizzbuzz-xf str (range 1 16))
;=> "FizzBuzzFizzFizzBuzzFizzFizzBuzz"
```

## 11.4 パイプライン処理

### 実践的なパイプライン

```clojure
(defn fizzbuzz-report
  "FizzBuzz の統計レポートを生成する"
  [max-n]
  (->> (range 1 (inc max-n))
       (map fizzbuzz)
       (group-by (fn [s]
                   (cond
                     (= s "FizzBuzz") :fizzbuzz
                     (= s "Fizz") :fizz
                     (= s "Buzz") :buzz
                     :else :number)))
       (map (fn [[k v]] [k (count v)]))
       (into (sorted-map))))

(fizzbuzz-report 100)
;=> {:buzz 14, :fizz 27, :fizzbuzz 6, :number 53}
```

## 11.5 アトムによる状態管理

不変データ構造を使いつつ、必要な場合にはアトムで状態を管理します。

```clojure
;; 処理カウンターの例
(def process-count (atom 0))

(defn counted-fizzbuzz [n]
  (swap! process-count inc)
  (fizzbuzz n))

(map counted-fizzbuzz (range 1 6))
@process-count ;=> 5
```

`atom` は Clojure の参照型の 1 つで、`swap!` で安全に状態を更新できます。

## 11.6 まとめ

この章では以下のことを学びました。

- **永続データ構造** と **構造共有** の仕組み
- ベクタ、マップ、セットなど主要なデータ構造の使い分け
- **遅延シーケンス** による無限データの効率的な処理
- **トランスデューサー** による変換処理の合成と再利用
- **スレッディングマクロ** を使ったパイプライン処理
- **アトム** による安全な状態管理
