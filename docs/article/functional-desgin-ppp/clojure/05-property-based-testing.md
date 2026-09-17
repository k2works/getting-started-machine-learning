---
type: Article
title: "第5章: プロパティベーステスト"
description: "従来の単体テストでは、特定の入力に対する期待される出力を検証します。"
tags: [article, functional-desgin-ppp, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第5章: プロパティベーステスト

## はじめに

従来の単体テストでは、特定の入力に対する期待される出力を検証します。一方、**プロパティベーステスト**では、すべての入力に対して成り立つべき「性質（プロパティ）」を定義し、ランダムに生成された多数のテストケースで検証します。

本章では、Clojure の `test.check` ライブラリを使ったプロパティベーステストの手法を学びます。

## 1. プロパティベーステストとは

### 従来のテストとの違い

```clojure
;; 従来のテスト：特定の入力に対する出力を検証
(deftest test-reverse
  (is (= "olleh" (reverse-string "hello")))
  (is (= "" (reverse-string "")))
  (is (= "a" (reverse-string "a"))))

;; プロパティベーステスト：性質を検証
(def prop-reverse-involutory
  "文字列を2回反転すると元に戻る"
  (prop/for-all [s gen/string]
    (= s (reverse-string (reverse-string s)))))
```

### プロパティベーステストの利点

1. **網羅性**: 手動では思いつかないエッジケースを発見
2. **ドキュメント性**: コードの性質を明確に表現
3. **回帰防止**: リファクタリング時の安全網
4. **シュリンキング**: 失敗時に最小の反例を提示

## 2. 基本的なジェネレータ

### プリミティブジェネレータ

```clojure
(require '[clojure.test.check.generators :as gen])

;; 整数
gen/small-integer  ;; -200〜200程度の整数
gen/nat            ;; 自然数（0以上）
gen/pos-int        ;; 正の整数
gen/neg-int        ;; 負の整数

;; 文字列
gen/string                ;; 任意の文字列
gen/string-alphanumeric   ;; 英数字のみ

;; その他
gen/boolean        ;; true/false
gen/char           ;; 文字
gen/keyword        ;; キーワード

;; サンプル生成
(gen/sample gen/small-integer 5)
;; => (0 1 -2 1 4)
```

### 範囲と列挙

```clojure
;; 範囲指定
(def age-gen (gen/choose 0 150))

;; 浮動小数点数
(def percentage-gen (gen/double* {:min 0.0 :max 1.0}))

;; 列挙型
(def membership-gen (gen/elements [:bronze :silver :gold :platinum]))
```

## 3. コレクションジェネレータ

### ベクター・リスト

```clojure
;; ベクター
(gen/vector gen/small-integer)       ;; 任意長
(gen/vector gen/small-integer 1 10)  ;; 1〜10要素

;; リスト
(gen/list gen/small-integer)

;; 非空コレクション
(gen/not-empty (gen/vector gen/small-integer))
```

### マップとセット

```clojure
;; マップ
(gen/map gen/string-alphanumeric gen/small-integer)

;; セット
(gen/set gen/small-integer)

;; 構造化されたマップ
(gen/hash-map
  :name gen/string-alphanumeric
  :age (gen/choose 0 150)
  :active gen/boolean)
```

## 4. 複合ジェネレータ

### ドメインモデルのジェネレータ

```clojure
(def person-gen
  "人物データのジェネレータ"
  (gen/hash-map
   :name (gen/such-that #(not (empty? %)) gen/string-alphanumeric)
   :age (gen/choose 0 150)
   :membership (gen/elements [:bronze :silver :gold :platinum])))

(def product-gen
  "商品データのジェネレータ"
  (gen/hash-map
   :product-id (gen/fmap #(str "PROD-" (format "%05d" (mod % 100000))) gen/nat)
   :name (gen/such-that #(not (empty? %)) gen/string-alphanumeric)
   :price (gen/fmap #(+ 1 (mod % 10000)) gen/nat)
   :quantity (gen/fmap #(+ 1 (mod % 100)) gen/nat)))

(gen/sample product-gen 3)
;; => ({:product-id "PROD-00000", :name "a", :price 1, :quantity 1}
;;     {:product-id "PROD-00001", :name "Ab", :price 2, :quantity 2}
;;     {:product-id "PROD-00003", :name "x2Y", :price 4, :quantity 3})
```

## 5. ジェネレータの変換

### fmap: 値の変換

```clojure
;; 生成された値を変換
(def uppercase-string-gen
  (gen/fmap clojure.string/upper-case gen/string-alphanumeric))

;; フォーマットされたID
(def order-id-gen
  (gen/fmap #(str "ORD-" (format "%08d" %))
            (gen/choose 0 99999999)))
```

### bind: 依存関係のある生成

```clojure
;; 生成された値に基づいて別のジェネレータを選択
(def non-empty-subset-gen
  (gen/bind (gen/not-empty (gen/vector gen/small-integer))
            (fn [v]
              (gen/fmap #(take % v)
                        (gen/choose 1 (count v))))))
```

### such-that: フィルタリング

```clojure
;; 条件を満たす値のみを生成
(def positive-even-gen
  (gen/such-that #(and (pos? %) (even? %)) gen/small-integer 100))
```

## 6. プロパティの定義

### 基本的なプロパティ

```clojure
(require '[clojure.test.check.properties :as prop])

;; 文字列反転の性質
(def prop-reverse-involutory
  "文字列を2回反転すると元に戻る"
  (prop/for-all [s gen/string]
    (= s (reverse-string (reverse-string s)))))

(def prop-reverse-length-preserved
  "反転しても長さは変わらない"
  (prop/for-all [s gen/string]
    (= (count s) (count (reverse-string s)))))
```

### ソートの性質

```clojure
(def prop-sort-idempotent
  "ソートは冪等（2回ソートしても結果は同じ）"
  (prop/for-all [nums (gen/vector gen/small-integer)]
    (= (sort-numbers nums) (sort-numbers (sort-numbers nums)))))

(def prop-sort-preserves-elements
  "ソートは要素を保存する（追加も削除もしない）"
  (prop/for-all [nums (gen/vector gen/small-integer)]
    (= (frequencies nums) (frequencies (sort-numbers nums)))))

(def prop-sort-ordered
  "ソート結果は昇順に並ぶ"
  (prop/for-all [nums (gen/vector gen/small-integer)]
    (let [sorted (sort-numbers nums)]
      (or (empty? sorted)
          (apply <= sorted)))))
```

### ビジネスロジックの性質

```clojure
(def prop-discount-bounds
  "割引後の価格は0以上、元の価格以下"
  (prop/for-all [price (gen/fmap #(Math/abs %) gen/small-integer)
                 rate (gen/double* {:min 0.0 :max 1.0})]
    (let [discounted (calculate-discount price rate)]
      (and (>= discounted 0)
           (<= discounted price)))))
```

## 7. プロパティの実行

### quick-check

```clojure
(require '[clojure.test.check :as tc])

;; 100回テスト
(tc/quick-check 100 prop-reverse-involutory)
;; => {:result true, :pass? true, :num-tests 100, :time-elapsed-ms 15, ...}

;; 1000回テスト
(tc/quick-check 1000 prop-sort-ordered)
```

### 失敗時の出力

```clojure
;; バグのある関数
(defn buggy-abs [n]
  (if (neg? n) (- n) n))

(def prop-abs-positive
  (prop/for-all [n gen/small-integer]
    (>= (buggy-abs n) 0)))

(tc/quick-check 10000 prop-abs-positive)
;; Integer/MIN_VALUE で失敗する場合、シュリンキングで最小反例を提示
```

## 8. シュリンキング

テストが失敗すると、test.check は自動的に**シュリンキング**を行い、同じ失敗を引き起こす最小の入力を探します。

```clojure
;; 例：リストの長さが5未満というバグのあるプロパティ
(def prop-buggy
  (prop/for-all [v (gen/vector gen/small-integer)]
    (< (count v) 5)))

(tc/quick-check 100 prop-buggy)
;; => {:result false,
;;     :pass? false,
;;     :shrunk {:total-nodes-visited 7,
;;              :smallest [[0 0 0 0 0]]},  ;; 最小の反例
;;     ...}
```

シュリンキングにより、「長さ100のリストで失敗」ではなく「長さ5のリストで失敗」という最小の反例が得られます。

## 9. ラウンドトリッププロパティ

エンコード/デコードの可逆性は典型的なプロパティです。

```clojure
(defn encode-run-length [s]
  (->> s
       (partition-by identity)
       (map (fn [group] [(first group) (count group)]))
       (into [])))

(defn decode-run-length [encoded]
  (->> encoded
       (mapcat (fn [[ch n]] (repeat n ch)))
       (apply str)))

(def prop-run-length-roundtrip
  "ランレングス符号化は可逆"
  (prop/for-all [s (gen/fmap #(apply str %) (gen/vector gen/char-alpha))]
    (= s (decode-run-length (encode-run-length s)))))
```

## 10. 実践的なパターン

### 代数的性質

```clojure
;; 結合律
(def prop-addition-associative
  (prop/for-all [a gen/small-integer
                 b gen/small-integer
                 c gen/small-integer]
    (= (+ (+ a b) c) (+ a (+ b c)))))

;; 交換律
(def prop-addition-commutative
  (prop/for-all [a gen/small-integer
                 b gen/small-integer]
    (= (+ a b) (+ b a))))
```

### オラクルテスト

既知の正しい実装と比較します。

```clojure
(def prop-my-sort-matches-builtin
  "自作ソートは組み込みソートと同じ結果"
  (prop/for-all [nums (gen/vector gen/small-integer)]
    (= (my-sort nums) (sort nums))))
```

### 不変条件

```clojure
(def prop-order-total-positive
  "注文合計は常に正"
  (prop/for-all [order order-gen]
    (pos? (calculate-order-total order))))
```

## まとめ

本章では、プロパティベーステストについて学びました：

1. **ジェネレータ**: テストデータの自動生成
2. **プリミティブ**: 整数、文字列、ブール値など
3. **コレクション**: ベクター、マップ、セット
4. **変換**: fmap, bind, such-that による加工
5. **プロパティ**: すべての入力で成り立つべき性質
6. **シュリンキング**: 失敗時の最小反例探索
7. **パターン**: ラウンドトリップ、代数的性質、オラクルテスト

プロパティベーステストは、従来のテストを補完し、より堅牢なソフトウェアを実現します。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/clojure/part2/src/property_based_testing.clj`
- テストコード: `apps/clojure/part2/spec/property_based_testing_spec.clj`

## 次章予告

次章では、**テスト駆動開発と関数型プログラミング**について学びます。Red-Green-Refactor サイクルを関数型スタイルで実践する方法を探ります。
