---
type: Article
title: "第2章: 関数合成と高階関数"
description: "関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。"
tags: [article, functional-desgin-ppp, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第2章: 関数合成と高階関数

## はじめに

関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。本章では、Clojure における関数合成のテクニックと高階関数の活用方法を学びます。

## 1. 関数合成の基本 (comp)

### comp による関数の連結

`comp` は複数の関数を合成して、新しい関数を作成します。合成された関数は**右から左**へ順番に適用されます。

```clojure
(defn add-tax
  "税金を追加する"
  [rate amount]
  (* amount (+ 1 rate)))

(defn apply-discount-rate
  "割引を適用する"
  [rate amount]
  (* amount (- 1 rate)))

(defn round-to-yen
  "円単位に丸める"
  [amount]
  (Math/round (double amount)))

;; comp による関数合成
(def calculate-final-price
  "最終価格を計算する合成関数"
  (comp round-to-yen
        (partial add-tax 0.1)
        (partial apply-discount-rate 0.2)))

;; 使用例
(calculate-final-price 1000)
;; => 880
;; 処理順序: 1000 → 20%割引(800) → 10%税込(880) → 丸め(880)
```

### comp の利点

1. **宣言的な記述**: 処理の流れを関数のリストとして表現
2. **再利用性**: 合成した関数を別の場所で再利用可能
3. **テスト容易性**: 各関数を個別にテスト可能

## 2. 部分適用 (partial)

### partial による引数の固定

`partial` は関数の一部の引数を固定して、新しい関数を作成します。

```clojure
(defn greet
  "挨拶する"
  [greeting name]
  (str greeting ", " name "!"))

(def say-hello (partial greet "Hello"))
(def say-goodbye (partial greet "Goodbye"))

(say-hello "田中")    ;; => "Hello, 田中!"
(say-goodbye "鈴木")  ;; => "Goodbye, 鈴木!"
```

### 複数引数の部分適用

```clojure
(defn send-email
  "メールを送信する"
  [from to subject body]
  {:from from :to to :subject subject :body body})

(def send-from-system (partial send-email "system@example.com"))
(def send-notification (partial send-from-system "user@example.com" "通知"))

(send-notification "メッセージ本文")
;; => {:from "system@example.com"
;;     :to "user@example.com"
;;     :subject "通知"
;;     :body "メッセージ本文"}
```

## 3. juxt - 複数の関数を並列適用

`juxt` は複数の関数を受け取り、それらを同じ引数に適用してベクターで結果を返す関数を作成します。

```clojure
(defn get-stats
  "数値リストの統計情報を取得する"
  [numbers]
  ((juxt first last count #(apply min %) #(apply max %)) numbers))

(get-stats [3 1 4 1 5 9 2 6])
;; => [3 6 8 1 9]
;; [最初の値, 最後の値, 要素数, 最小値, 最大値]
```

### 実用例：データ分析

```clojure
(defn analyze-person
  "人物情報を分析する"
  [person]
  ((juxt :name :age #(if (>= (:age %) 18) :adult :minor)) person))

(analyze-person {:name "田中" :age 25})
;; => ["田中" 25 :adult]

(analyze-person {:name "鈴木" :age 15})
;; => ["鈴木" 15 :minor]
```

## 4. 高階関数によるデータ処理

高階関数とは、関数を引数として受け取るか、関数を返す関数のことです。

### ログ出力のラッパー

```clojure
(defn process-with-logging
  "処理をラップしてログを出力する高階関数"
  [f]
  (fn [& args]
    (println "入力:" args)
    (let [result (apply f args)]
      (println "出力:" result)
      result)))

(def add-with-log (process-with-logging +))
(add-with-log 2 3)
;; 入力: (2 3)
;; 出力: 5
;; => 5
```

### リトライ機能の追加

```clojure
(defn retry
  "失敗時にリトライする高階関数"
  [f max-retries]
  (fn [& args]
    (loop [attempts 0]
      (let [result (try
                     {:success true :value (apply f args)}
                     (catch Exception e
                       {:success false :error e}))]
        (if (:success result)
          (:value result)
          (if (< attempts max-retries)
            (recur (inc attempts))
            (throw (:error result))))))))

;; 不安定なAPI呼び出しをリトライ付きでラップ
(def fetch-with-retry (retry fetch-data 3))
```

### TTL 付きメモ化

```clojure
(defn memoize-with-ttl
  "TTL付きメモ化を行う高階関数"
  [f ttl-ms]
  (let [cache (atom {})]
    (fn [& args]
      (let [now (System/currentTimeMillis)
            cached (get @cache args)]
        (if (and cached (< (- now (:time cached)) ttl-ms))
          (:value cached)
          (let [result (apply f args)]
            (swap! cache assoc args {:value result :time now})
            result))))))
```

## 5. パイプライン処理

複数の関数を順次適用するパイプラインを構築します。

```clojure
(defn pipeline
  "関数のリストを順次適用するパイプラインを作成する"
  [& fns]
  (fn [input]
    (reduce (fn [acc f] (f acc)) input fns)))
```

### 注文処理パイプラインの実装

```clojure
(defn validate-order
  "注文を検証する"
  [order]
  (if (empty? (:items order))
    (throw (ex-info "注文にアイテムがありません" {:order order}))
    order))

(defn calculate-order-total
  "注文合計を計算する"
  [order]
  (let [total (->> (:items order)
                   (map #(* (:price %) (:quantity %)))
                   (reduce +))]
    (assoc order :total total)))

(defn apply-order-discount
  "注文割引を適用する"
  [order]
  (let [discount-rate (get {:gold 0.1 :silver 0.05 :bronze 0.02}
                           (get-in order [:customer :membership])
                           0)]
    (update order :total #(* % (- 1 discount-rate)))))

(defn add-shipping
  "送料を追加する"
  [order]
  (let [shipping (if (>= (:total order) 5000) 0 500)]
    (-> order
        (assoc :shipping shipping)
        (update :total + shipping))))

(def process-order-pipeline
  "注文処理パイプライン"
  (pipeline validate-order
            calculate-order-total
            apply-order-discount
            add-shipping))

;; 使用例
(process-order-pipeline
  {:items [{:price 1000 :quantity 2}
           {:price 500 :quantity 3}]
   :customer {:membership :gold}})
;; => {:items [...] :customer {...} :total 3650.0 :shipping 500}
```

## 6. 関数合成によるバリデーション

バリデーションロジックを関数合成で表現します。

```clojure
(defn validator
  "バリデータを作成する高階関数"
  [pred error-msg]
  (fn [value]
    (if (pred value)
      {:valid true :value value}
      {:valid false :error error-msg :value value})))

(defn combine-validators
  "複数のバリデータを合成する"
  [& validators]
  (fn [value]
    (reduce (fn [result v]
              (if (:valid result)
                (v (:value result))
                result))
            {:valid true :value value}
            validators)))

;; 個別のバリデータ
(def validate-positive (validator pos? "値は正の数である必要があります"))
(def validate-under-100 (validator #(< % 100) "値は100未満である必要があります"))
(def validate-integer (validator integer? "値は整数である必要があります"))

;; バリデータの合成
(def validate-quantity
  "数量バリデータ"
  (combine-validators validate-integer
                      validate-positive
                      validate-under-100))

;; 使用例
(validate-quantity 50)   ;; => {:valid true, :value 50}
(validate-quantity -1)   ;; => {:valid false, :error "値は正の数である必要があります", :value -1}
(validate-quantity 100)  ;; => {:valid false, :error "値は100未満である必要があります", :value 100}
```

## 7. 関数の変換

関数自体を変換するユーティリティ関数を作成します。

### 引数の順序を反転

```clojure
(defn flip
  "引数の順序を反転する"
  [f]
  (fn [a b]
    (f b a)))

((flip -) 3 5)  ;; => 2  (5 - 3)
```

### カリー化

```clojure
(defn curry
  "2引数関数をカリー化する"
  [f]
  (fn [a]
    (fn [b]
      (f a b))))

(defn uncurry
  "カリー化された関数を元に戻す"
  [f]
  (fn [a b]
    ((f a) b)))

(def curried-add (curry +))
(def add5 (curried-add 5))
(add5 3)  ;; => 8
```

## 8. 関数合成のパターン

### 述語の合成

```clojure
(defn compose-predicates
  "複数の述語を AND で合成する"
  [& preds]
  (fn [x]
    (every? #(% x) preds)))

(defn compose-predicates-or
  "複数の述語を OR で合成する"
  [& preds]
  (fn [x]
    (some #(% x) preds)))

;; 有効な年齢チェック
(def valid-age?
  (compose-predicates integer?
                      pos?
                      #(<= % 150)))

(valid-age? 25)   ;; => true
(valid-age? -1)   ;; => false
(valid-age? 200)  ;; => false

;; プレミアム顧客チェック
(def premium-customer?
  (compose-predicates-or
   #(= (:membership %) :gold)
   #(>= (:purchase-count %) 100)
   #(>= (:total-spent %) 100000)))

(premium-customer? {:membership :gold})           ;; => true
(premium-customer? {:purchase-count 150})         ;; => true
(premium-customer? {:membership :bronze
                    :purchase-count 10})          ;; => false
```

## まとめ

本章では、関数合成と高階関数について学びました：

1. **comp**: 複数の関数を合成して新しい関数を作成
2. **partial**: 引数を部分適用して特化した関数を作成
3. **juxt**: 複数の関数を並列適用してベクターで結果を取得
4. **高階関数**: ログ、リトライ、メモ化などの横断的関心事を抽象化
5. **パイプライン**: 処理の流れを関数のチェーンとして表現
6. **バリデーション**: 関数合成による柔軟な検証ロジック
7. **述語合成**: AND/OR で複数の条件を組み合わせ

これらのテクニックにより、小さく再利用可能な関数から複雑なビジネスロジックを構築できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/clojure/part1/src/composition.clj`
- テストコード: `apps/clojure/part1/spec/composition_spec.clj`

## 次章予告

次章では、**多態性とディスパッチ**について学びます。マルチメソッド、プロトコル、レコードを活用した柔軟な設計パターンを探ります。
