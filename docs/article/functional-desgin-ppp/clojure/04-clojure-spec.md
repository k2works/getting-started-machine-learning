---
type: Article
title: "第4章: Clojure Spec による仕様定義"
description: "Clojure Spec は、データ構造と関数の仕様を定義するための強力なライブラリです。"
tags: [article, functional-desgin-ppp, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第4章: Clojure Spec による仕様定義

## はじめに

Clojure Spec は、データ構造と関数の仕様を定義するための強力なライブラリです。型システムとは異なり、Spec は実行時にデータを検証し、自動テストを生成し、ドキュメントとしても機能します。

本章では、Spec を使ってデータの仕様を定義し、関数の契約を表現し、自動テスト生成を活用する方法を学びます。

## 1. 基本的なスペック定義

### スペックとは

スペックは、データが満たすべき条件を宣言的に定義します。

```clojure
(require '[clojure.spec.alpha :as s])

;; シンプルなスペック
(s/def ::name (s/and string? #(< 0 (count %) 100)))
(s/def ::age (s/and int? #(<= 0 % 150)))
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))

;; 列挙型のスペック
(s/def ::membership #{:bronze :silver :gold :platinum})
(s/def ::status #{:active :inactive :suspended})
```

### スペックの検証

```clojure
;; valid? で検証
(s/valid? ::name "田中太郎")  ;; => true
(s/valid? ::name "")          ;; => false（空文字列）
(s/valid? ::age 25)           ;; => true
(s/valid? ::age -1)           ;; => false（負の数）

;; explain でエラー詳細を表示
(s/explain ::age -1)
;; val: -1 fails spec: :user/age predicate: (<= 0 % 150)
```

## 2. コレクションのスペック

### ベクターとリスト

```clojure
;; ベクターのスペック
(s/def ::tags (s/coll-of string? :kind vector? :min-count 0 :max-count 10))

(s/valid? ::tags ["tag1" "tag2"])  ;; => true
(s/valid? ::tags '("tag1"))        ;; => false（リストは不可）
```

### マップのスペック

```clojure
;; 必須キーとオプションキー
(s/def ::person
  (s/keys :req-un [::name ::age]
          :opt-un [::email ::membership]))

(s/valid? ::person {:name "田中" :age 30})  ;; => true
(s/valid? ::person {:name "田中" :age 30 :email "tanaka@example.com"})  ;; => true
(s/valid? ::person {:name "田中"})  ;; => false（age が欠落）
```

### 入れ子のマップ

```clojure
(s/def ::street string?)
(s/def ::city string?)
(s/def ::postal-code (s/and string? #(re-matches #"\d{3}-\d{4}" %)))

(s/def ::address
  (s/keys :req-un [::street ::city ::postal-code]))

(s/def ::person-with-address
  (s/keys :req-un [::name ::age ::address]))

(s/valid? ::person-with-address
          {:name "田中"
           :age 30
           :address {:street "東京都渋谷区1-1-1"
                     :city "渋谷区"
                     :postal-code "150-0001"}})
;; => true
```

## 3. ドメインモデルの定義

### 商品と注文

```clojure
;; 商品
(s/def ::product-id (s/and string? #(re-matches #"PROD-\d{5}" %)))
(s/def ::product-name (s/and string? #(< 0 (count %) 200)))
(s/def ::price (s/and number? pos?))
(s/def ::quantity (s/and int? pos?))

(s/def ::product
  (s/keys :req-un [::product-id ::product-name ::price]
          :opt-un [::description ::category]))

;; 注文アイテム
(s/def ::order-item
  (s/keys :req-un [::product-id ::quantity ::price]))

;; 注文
(s/def ::order-id (s/and string? #(re-matches #"ORD-\d{8}" %)))
(s/def ::customer-id (s/and string? #(re-matches #"CUST-\d{6}" %)))
(s/def ::items (s/coll-of ::order-item :min-count 1))

(s/def ::order
  (s/keys :req-un [::order-id ::customer-id ::items ::order-date]
          :opt-un [::total ::status]))
```

## 4. 関数仕様の定義（fdef）

### 基本的な fdef

`s/fdef` を使って関数の引数、戻り値、および引数と戻り値の関係を定義します。

```clojure
(defn calculate-item-total
  "注文アイテムの合計を計算する"
  [{:keys [price quantity]}]
  (* price quantity))

(s/fdef calculate-item-total
  :args (s/cat :item ::order-item)
  :ret number?
  :fn (s/and #(pos? (:ret %))
             #(= (:ret %)
                 (* (get-in % [:args :item :price])
                    (get-in % [:args :item :quantity])))))
```

### fdef の構成要素

- **:args**: 引数のスペック（`s/cat` で名前付き引数を定義）
- **:ret**: 戻り値のスペック
- **:fn**: 引数と戻り値の関係を定義する述語

```clojure
(defn apply-discount
  "割引を適用する"
  [total discount-rate]
  (* total (- 1 discount-rate)))

(s/fdef apply-discount
  :args (s/cat :total ::total :discount-rate (s/and number? #(<= 0 % 1)))
  :ret number?
  :fn #(<= (:ret %) (get-in % [:args :total])))  ;; 戻り値は元の値以下
```

## 5. 多引数と可変長引数

### オーバーロードされた関数

```clojure
(defn create-person
  "人物を作成する"
  ([name age]
   {:name name :age age})
  ([name age email]
   {:name name :age age :email email}))

(s/fdef create-person
  :args (s/alt :two-args (s/cat :name ::name :age ::age)
               :three-args (s/cat :name ::name :age ::age :email ::email))
  :ret ::person)
```

### 可変長引数

```clojure
(defn sum-prices
  "複数の価格を合計する"
  [& prices]
  (reduce + 0 prices))

(s/fdef sum-prices
  :args (s/* ::price)
  :ret number?)
```

## 6. 条件付きスペック（マルチスペック）

データの内容に応じて異なるスペックを適用する場合は、マルチスペックを使用します。

```clojure
(s/def ::notification-type #{:email :sms :push})

(defmulti notification-spec :type)

(defmethod notification-spec :email [_]
  (s/keys :req-un [::type ::to ::subject ::body]))

(defmethod notification-spec :sms [_]
  (s/keys :req-un [::type ::phone-number ::body]))

(defmethod notification-spec :push [_]
  (s/keys :req-un [::type ::device-token ::body]))

(s/def ::notification (s/multi-spec notification-spec :type))

;; 検証
(s/valid? ::notification
          {:type :email
           :to "test@example.com"
           :subject "テスト"
           :body "本文"})
;; => true

(s/valid? ::notification
          {:type :sms
           :phone-number "090-1234-5678"
           :body "本文"})
;; => true
```

## 7. カスタムジェネレータ

スペックに対応するテストデータを自動生成するジェネレータを定義できます。

```clojure
(require '[clojure.spec.gen.alpha :as gen])

(defn product-id-gen
  "商品IDのジェネレータ"
  []
  (gen/fmap #(str "PROD-" (format "%05d" %))
            (gen/choose 0 99999)))

(s/def ::product-id-with-gen
  (s/with-gen
    (s/and string? #(re-matches #"PROD-\d{5}" %))
    product-id-gen))

;; サンプル生成
(gen/sample (s/gen ::product-id-with-gen) 5)
;; => ("PROD-00042" "PROD-00001" "PROD-12345" "PROD-00000" "PROD-99999")
```

### メールアドレスのジェネレータ

```clojure
(defn email-gen
  "メールアドレスのジェネレータ"
  []
  (gen/fmap (fn [[user domain]]
              (str user "@" domain ".com"))
            (gen/tuple (gen/such-that #(not (empty? %))
                                       (gen/string-alphanumeric))
                       (gen/such-that #(not (empty? %))
                                       (gen/string-alphanumeric)))))
```

## 8. バリデーションとエラーハンドリング

### バリデーション関数

```clojure
(defn validate-person
  "人物データを検証し、結果を返す"
  [person]
  (if (s/valid? ::person person)
    {:valid true :data person}
    {:valid false
     :errors (s/explain-data ::person person)}))

(validate-person {:name "田中" :age 30})
;; => {:valid true, :data {:name "田中", :age 30}}

(validate-person {:name "" :age 30})
;; => {:valid false, :errors {...}}
```

### conform による値の変換

```clojure
(defn conform-or-throw
  "スペックに適合しない場合は例外をスロー"
  [spec data]
  (let [conformed (s/conform spec data)]
    (if (= conformed ::s/invalid)
      (throw (ex-info "Validation failed"
                      {:spec spec
                       :data data
                       :problems (s/explain-data spec data)}))
      conformed)))
```

## 9. インストルメンテーション

開発時に関数の引数を自動検証できます。

```clojure
(require '[clojure.spec.test.alpha :as stest])

;; インストルメンテーションを有効化
(stest/instrument)

;; これ以降、fdef が定義された関数は引数が自動検証される
(calculate-item-total {:product-id "INVALID" :quantity -1 :price 0})
;; => ExceptionInfo: Call to #'user/calculate-item-total did not conform to spec

;; 無効化
(stest/unstrument)
```

## 10. テスト生成

`check` 関数で自動的にテストを生成・実行できます。

```clojure
(stest/check `calculate-item-total)
;; 100個のランダムな入力でテストを実行

(stest/check `apply-discount {:clojure.spec.test.check/opts {:num-tests 1000}})
;; 1000個のテストケースで実行
```

## まとめ

本章では、Clojure Spec について学びました：

1. **基本的なスペック**: 述語と組み合わせたデータ検証
2. **コレクションスペック**: ベクター、マップの構造定義
3. **ドメインモデル**: ビジネスデータの仕様定義
4. **fdef**: 関数の契約（引数、戻り値、関係）
5. **マルチスペック**: 条件付きの仕様定義
6. **カスタムジェネレータ**: テストデータの自動生成
7. **バリデーション**: 実行時検証とエラーハンドリング
8. **インストルメンテーション**: 開発時の自動検証
9. **テスト生成**: 自動プロパティベーステスト

Spec を活用することで、動的型付け言語の柔軟性を保ちながら、堅牢なデータ検証とドキュメント化を実現できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/clojure/part2/src/clojure_spec.clj`
- テストコード: `apps/clojure/part2/spec/clojure_spec_spec.clj`

## 次章予告

次章では、**プロパティベーステスト**について学びます。test.check を使った生成的テストの手法を探ります。
