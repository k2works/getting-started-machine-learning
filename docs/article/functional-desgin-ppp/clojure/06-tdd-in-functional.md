---
type: Article
title: "第6章: テスト駆動開発と関数型プログラミング"
description: "テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。"
tags: [article, functional-desgin-ppp, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第6章: テスト駆動開発と関数型プログラミング

## はじめに

テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。関数型プログラミングと TDD は相性が良く、純粋関数はテストが容易で、不変データ構造は予測可能な動作を保証します。

本章では、Red-Green-Refactor サイクルを関数型スタイルで実践する方法を学びます。

## 1. TDD の基本サイクル

### Red-Green-Refactor

```
┌─────────────────────────────────────────────────┐
│                                                 │
│    ┌───────┐     ┌───────┐     ┌───────────┐   │
│    │  Red  │ ──► │ Green │ ──► │ Refactor  │   │
│    └───────┘     └───────┘     └───────────┘   │
│        ▲                              │         │
│        └──────────────────────────────┘         │
│                                                 │
└─────────────────────────────────────────────────┘
```

1. **Red（赤）**: 失敗するテストを書く
2. **Green（緑）**: テストを通す最小限のコードを書く
3. **Refactor（リファクタリング）**: コードを改善する（テストは通ったまま）

## 2. FizzBuzz - TDD の典型例

### Step 1: Red（最初のテスト）

```clojure
;; テスト
(it "1は\"1\"を返す"
  (should= "1" (fizzbuzz 1)))
```

### Step 2: Green（最小限の実装）

```clojure
(defn fizzbuzz [n]
  "1")
```

### Step 3: 次のテストを追加

```clojure
(it "2は\"2\"を返す"
  (should= "2" (fizzbuzz 2)))
```

### 段階的に実装を発展

```clojure
;; 小さなヘルパー関数に分割
(defn fizz? [n] (zero? (mod n 3)))
(defn buzz? [n] (zero? (mod n 5)))
(defn fizzbuzz? [n] (and (fizz? n) (buzz? n)))

(defn fizzbuzz [n]
  (cond
    (fizzbuzz? n) "FizzBuzz"
    (fizz? n) "Fizz"
    (buzz? n) "Buzz"
    :else (str n)))
```

### 最終テストスイート

```clojure
(context "FizzBuzz"
  (it "1は\"1\"を返す" (should= "1" (fizzbuzz 1)))
  (it "2は\"2\"を返す" (should= "2" (fizzbuzz 2)))
  (it "3は\"Fizz\"を返す" (should= "Fizz" (fizzbuzz 3)))
  (it "5は\"Buzz\"を返す" (should= "Buzz" (fizzbuzz 5)))
  (it "15は\"FizzBuzz\"を返す" (should= "FizzBuzz" (fizzbuzz 15))))
```

## 3. ローマ数字変換

### テストから始める

```clojure
(context "to-roman"
  (it "1はIを返す" (should= "I" (to-roman 1)))
  (it "3はIIIを返す" (should= "III" (to-roman 3)))
  (it "4はIVを返す" (should= "IV" (to-roman 4)))
  (it "5はVを返す" (should= "V" (to-roman 5)))
  ;; ... 段階的に追加
  )
```

### データ駆動の実装

```clojure
(def roman-numerals
  "ローマ数字の対応表（大きい順）"
  [[1000 "M"] [900 "CM"] [500 "D"] [400 "CD"]
   [100 "C"] [90 "XC"] [50 "L"] [40 "XL"]
   [10 "X"] [9 "IX"] [5 "V"] [4 "IV"] [1 "I"]])

(defn to-roman [n]
  {:pre [(pos? n) (<= n 3999)]}
  (loop [n n result ""]
    (if (zero? n)
      result
      (let [[value numeral] (first (filter #(<= (first %) n) roman-numerals))]
        (recur (- n value) (str result numeral))))))
```

## 4. ボウリングスコア計算

### 複雑なビジネスロジックの TDD

```clojure
;; 段階的にテストを追加
(context "ボウリングスコア計算"
  (it "ガタースコアは0"
    (should= 0 (bowling-score (repeat 20 0))))

  (it "すべて1ピンは20点"
    (should= 20 (bowling-score (repeat 20 1))))

  (it "スペアの後の投球はボーナス"
    (should= 16 (bowling-score [5 5 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0])))

  (it "ストライクの後の2投はボーナス"
    (should= 24 (bowling-score [10 3 4 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0])))

  (it "パーフェクトゲームは300点"
    (should= 300 (bowling-score (repeat 12 10)))))
```

### 小さな関数に分割

```clojure
(defn strike? [frame] (= 10 (first frame)))
(defn spare? [frame] (and (not (strike? frame))
                           (= 10 (+ (first frame) (second frame)))))

(defn calculate-frame [frame remaining-rolls]
  (cond
    (strike? frame) (+ 10 (strike-bonus remaining-rolls))
    (spare? frame) (+ 10 (spare-bonus remaining-rolls))
    :else (frame-score frame)))
```

## 5. 素数 - シンプルな関数の TDD

### テストから設計を導く

```clojure
(context "素数"
  (it "prime? は素数を正しく判定する"
    (should-not (prime? 0))
    (should-not (prime? 1))
    (should (prime? 2))
    (should (prime? 3))
    (should-not (prime? 4))
    (should (prime? 5)))

  (it "primes-up-to は正しい素数リストを返す"
    (should= [2 3 5 7 11 13 17 19] (primes-up-to 20)))

  (it "prime-factors は素因数分解を返す"
    (should= [2 2 2 3] (prime-factors 24))))
```

### 実装

```clojure
(defn prime? [n]
  (cond
    (< n 2) false
    (= n 2) true
    (even? n) false
    :else (not-any? #(zero? (mod n %))
                    (range 3 (inc (Math/sqrt n)) 2))))

(defn primes-up-to [n]
  (filter prime? (range 2 (inc n))))

(defn prime-factors [n]
  (loop [n n, factor 2, factors []]
    (cond
      (= n 1) factors
      (zero? (mod n factor)) (recur (/ n factor) factor (conj factors factor))
      :else (recur n (inc factor) factors))))
```

## 6. スタック - データ構造の TDD

### 不変データ構造として実装

```clojure
(defn create-stack [] {:items []})

(defn stack-push [stack item]
  (update stack :items conj item))

(defn stack-pop [stack]
  {:pre [(not (empty? (:items stack)))]}
  {:value (peek (:items stack))
   :stack {:items (pop (:items stack))}})

(defn stack-peek [stack]
  (peek (:items stack)))
```

### テスト

```clojure
(it "LIFO順序で動作する"
  (let [stack (-> (create-stack)
                  (stack-push "a")
                  (stack-push "b")
                  (stack-push "c"))
        {v1 :value s1 :stack} (stack-pop stack)
        {v2 :value s2 :stack} (stack-pop s1)
        {v3 :value _s3 :stack} (stack-pop s2)]
    (should= "c" v1)
    (should= "b" v2)
    (should= "a" v3)))
```

## 7. 文字列電卓

### 段階的な要件追加

```clojure
(context "文字列電卓"
  (it "空文字列は0を返す"
    (should= 0 (string-calculator "")))

  (it "単一の数値はその値を返す"
    (should= 5 (string-calculator "5")))

  (it "カンマ区切りの数値を合計する"
    (should= 6 (string-calculator "1,2,3")))

  (it "改行区切りも処理する"
    (should= 6 (string-calculator "1\n2,3")))

  (it "カスタム区切り文字を使用できる"
    (should= 3 (string-calculator "//;\n1;2")))

  (it "負の数は例外をスローする"
    (should-throw clojure.lang.ExceptionInfo
                  (string-calculator "1,-2,3")))

  (it "1000より大きい数は無視する"
    (should= 2 (string-calculator "2,1001"))))
```

### 実装

```clojure
(defn string-calculator [s]
  (if (empty? s)
    0
    (->> (parse-numbers s)
         (validate-numbers)
         (filter #(<= % 1000))
         (reduce +))))
```

## 8. 純粋関数とテスト容易性

### 純粋関数の利点

```clojure
;; 純粋関数：テストが容易
(defn calculate-tax [amount rate]
  (* amount rate))

(defn calculate-total-with-tax [items tax-rate]
  (let [subtotal (reduce + (map :price items))
        tax (calculate-tax subtotal tax-rate)]
    {:subtotal subtotal
     :tax tax
     :total (+ subtotal tax)}))
```

### テスト

```clojure
(it "calculate-total-with-tax は税込み総額を計算する"
  (let [items [{:name "商品A" :price 1000}
               {:name "商品B" :price 2000}]
        result (calculate-total-with-tax items 0.1)]
    (should= 3000 (:subtotal result))
    (should= 300.0 (:tax result))
    (should= 3300.0 (:total result))))
```

## 9. リファクタリングパターン

### Before: 複雑な条件分岐

```clojure
(defn calculate-shipping-before [order]
  (let [total (:total order)
        weight (:weight order)
        region (:region order)]
    (cond
      (>= total 10000) 0
      (= region :local) (if (< weight 5) 300 500)
      (= region :domestic) (if (< weight 5) 500 800)
      (= region :international) (if (< weight 5) 2000 3000)
      :else 500)))
```

### After: データ駆動の実装

```clojure
(defn free-shipping? [total] (>= total 10000))

(def shipping-rates
  {:local {true 300 false 500}
   :domestic {true 500 false 800}
   :international {true 2000 false 3000}})

(defn calculate-shipping [{:keys [total weight region] :or {region :domestic}}]
  (if (free-shipping? total)
    0
    (get-in shipping-rates [region (< weight 5)] 500)))
```

## 10. TDD のベストプラクティス

### 1. 小さなステップで進む

- 一度に1つのテストだけを追加
- テストが通ったら次のテストへ

### 2. テスト名は仕様として読める

```clojure
(it "10000円以上は送料無料")
(it "負の数は例外をスローする")
(it "パーフェクトゲームは300点")
```

### 3. 純粋関数を優先

- 副作用を持つ関数は最小限に
- 副作用は境界に追い出す

### 4. エッジケースをテスト

```clojure
(it "空文字列は0を返す")
(it "空のリストは空のリストを返す")
(it "境界値で正しく動作する")
```

## まとめ

本章では、TDD と関数型プログラミングについて学びました：

1. **Red-Green-Refactor**: 基本サイクル
2. **FizzBuzz**: 典型的な TDD 例
3. **ローマ数字**: データ駆動の実装
4. **ボウリング**: 複雑なビジネスロジック
5. **素数**: シンプルな関数設計
6. **スタック**: 不変データ構造
7. **文字列電卓**: 段階的な要件追加
8. **純粋関数**: テスト容易性
9. **リファクタリング**: 条件分岐の整理

関数型プログラミングと TDD の組み合わせにより、信頼性の高いコードを効率的に開発できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/clojure/part2/src/tdd_in_functional.clj`
- テストコード: `apps/clojure/part2/spec/tdd_in_functional_spec.clj`

## 次章予告

次章から第3部「デザインパターン - 構造パターン」に入ります。Composite パターンを関数型スタイルで実装する方法を学びます。
