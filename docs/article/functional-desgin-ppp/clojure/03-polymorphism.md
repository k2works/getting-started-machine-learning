---
type: Article
title: "第3章: 多態性とディスパッチ"
description: "多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。"
tags: [article, functional-desgin-ppp, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第3章: 多態性とディスパッチ

## はじめに

多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。Clojure では、マルチメソッド、プロトコル、レコードという3つの主要なメカニズムで多態性を実現します。

本章では、これらのメカニズムを使い分けて、柔軟で拡張性の高いコードを書く方法を学びます。

## 1. マルチメソッド（Multimethods）

マルチメソッドは、任意のディスパッチ関数に基づいて異なる実装を選択できる、最も柔軟な多態性メカニズムです。

### 基本的な使い方

```clojure
;; ディスパッチ関数を定義
(defmulti calculate-area
  "図形の面積を計算するマルチメソッド"
  :shape)

;; 各図形タイプに対する実装
(defmethod calculate-area :rectangle
  [{:keys [width height]}]
  (* width height))

(defmethod calculate-area :circle
  [{:keys [radius]}]
  (* Math/PI radius radius))

(defmethod calculate-area :triangle
  [{:keys [base height]}]
  (/ (* base height) 2))

(defmethod calculate-area :default
  [shape]
  (throw (ex-info "未知の図形タイプ" {:shape shape})))

;; 使用例
(calculate-area {:shape :rectangle :width 4 :height 5})  ;; => 20
(calculate-area {:shape :circle :radius 3})              ;; => 28.27...
(calculate-area {:shape :triangle :base 6 :height 5})    ;; => 15
```

### マルチメソッドの利点

1. **オープンな拡張性**: 新しい型を追加しても既存コードを変更不要
2. **任意のディスパッチ**: 型だけでなく任意の値に基づいてディスパッチ可能
3. **データ駆動**: データの属性に基づいて振る舞いを決定

## 2. 複合ディスパッチ

マルチメソッドは複数の値に基づいてディスパッチできます。

```clojure
(defmulti process-payment
  "支払いを処理するマルチメソッド（支払い方法と通貨でディスパッチ）"
  (fn [payment] [(:method payment) (:currency payment)]))

(defmethod process-payment [:credit-card :jpy]
  [payment]
  {:status :processed
   :message "クレジットカード（円）で処理しました"
   :amount (:amount payment)})

(defmethod process-payment [:credit-card :usd]
  [payment]
  {:status :processed
   :message "Credit card (USD) processed"
   :amount (:amount payment)
   :converted (* (:amount payment) 150)})

(defmethod process-payment [:bank-transfer :jpy]
  [payment]
  {:status :pending
   :message "銀行振込を受け付けました"
   :amount (:amount payment)})

(defmethod process-payment :default
  [payment]
  {:status :error
   :message "サポートされていない支払い方法です"})

;; 使用例
(process-payment {:method :credit-card :currency :jpy :amount 1000})
;; => {:status :processed, :message "クレジットカード（円）で処理しました", :amount 1000}
```

## 3. 階層的ディスパッチ

Clojure では型の階層を定義して、継承的なディスパッチを実現できます。

```clojure
;; 型階層を定義
(derive ::savings ::account)
(derive ::checking ::account)
(derive ::premium-savings ::savings)

(defmulti calculate-interest
  "利息を計算するマルチメソッド（口座タイプでディスパッチ）"
  :account-type)

(defmethod calculate-interest ::savings
  [{:keys [balance]}]
  (* balance 0.02))

(defmethod calculate-interest ::premium-savings
  [{:keys [balance]}]
  (* balance 0.05))

(defmethod calculate-interest ::checking
  [{:keys [balance]}]
  (* balance 0.001))

(defmethod calculate-interest ::account
  [{:keys [balance]}]
  (* balance 0.01))

;; 使用例
(calculate-interest {:account-type ::savings :balance 10000})
;; => 200.0

(calculate-interest {:account-type ::premium-savings :balance 10000})
;; => 500.0（派生型の実装が優先される）
```

## 4. プロトコル（Protocols）

プロトコルは、特定の操作セットを定義するインターフェースです。Java のインターフェースに似ていますが、既存の型に後から実装を追加できる点が異なります。

### プロトコルの定義

```clojure
(defprotocol Drawable
  "描画可能なオブジェクトのプロトコル"
  (draw [this] "オブジェクトを描画する")
  (bounding-box [this] "バウンディングボックスを取得する"))

(defprotocol Transformable
  "変換可能なオブジェクトのプロトコル"
  (translate [this dx dy] "移動する")
  (scale [this factor] "拡大・縮小する")
  (rotate [this angle] "回転する"))
```

### プロトコルの利点

1. **パフォーマンス**: マルチメソッドより高速（型ベースのディスパッチに特化）
2. **明確なコントラクト**: 実装すべきメソッドが明示的
3. **既存型への拡張**: 後から任意の型にプロトコルを実装可能

## 5. レコード（Records）

レコードはマップに似たデータ構造で、プロトコルを実装できます。

```clojure
(defrecord Rectangle [x y width height]
  Drawable
  (draw [this]
    (str "Rectangle at (" x "," y ") with size " width "x" height))
  (bounding-box [this]
    {:x x :y y :width width :height height})

  Transformable
  (translate [this dx dy]
    (->Rectangle (+ x dx) (+ y dy) width height))
  (scale [this factor]
    (->Rectangle x y (* width factor) (* height factor)))
  (rotate [this angle]
    this))

(defrecord Circle [x y radius]
  Drawable
  (draw [this]
    (str "Circle at (" x "," y ") with radius " radius))
  (bounding-box [this]
    {:x (- x radius) :y (- y radius)
     :width (* 2 radius) :height (* 2 radius)})

  Transformable
  (translate [this dx dy]
    (->Circle (+ x dx) (+ y dy) radius))
  (scale [this factor]
    (->Circle x y (* radius factor)))
  (rotate [this angle]
    this))

;; 使用例
(def rect (->Rectangle 10 20 100 50))
(draw rect)           ;; => "Rectangle at (10,20) with size 100x50"
(translate rect 5 10) ;; => #Rectangle{:x 15, :y 30, :width 100, :height 50}

(def circle (->Circle 50 50 25))
(bounding-box circle) ;; => {:x 25, :y 25, :width 50, :height 50}
```

## 6. 既存の型にプロトコルを拡張

`extend-protocol` を使って、既存の型にプロトコルを実装できます。

```clojure
(defprotocol Stringable
  "文字列に変換可能なプロトコル"
  (to-string [this] "文字列表現を返す"))

(extend-protocol Stringable
  clojure.lang.IPersistentMap
  (to-string [this]
    (str "{" (clojure.string/join ", "
                                   (map (fn [[k v]] (str (name k) ": " v)) this)) "}"))

  clojure.lang.IPersistentVector
  (to-string [this]
    (str "[" (clojure.string/join ", " this) "]"))

  java.lang.String
  (to-string [this] this)

  java.lang.Number
  (to-string [this] (str this))

  nil
  (to-string [this] "nil"))

;; 使用例
(to-string {:name "田中" :age 30})  ;; => "{name: 田中, age: 30}"
(to-string [1 2 3])                  ;; => "[1, 2, 3]"
(to-string nil)                      ;; => "nil"
```

## 7. コンポーネントパターン

プロトコルを使って、コンポーネントのライフサイクル管理を実現します。

```clojure
(defprotocol Lifecycle
  "ライフサイクル管理プロトコル"
  (start [this] "コンポーネントを開始する")
  (stop [this] "コンポーネントを停止する"))

(defrecord DatabaseConnection [host port connected?]
  Lifecycle
  (start [this]
    (println "データベースに接続中:" host ":" port)
    (assoc this :connected? true))
  (stop [this]
    (println "データベース接続を切断中")
    (assoc this :connected? false)))

(defrecord WebServer [port db running?]
  Lifecycle
  (start [this]
    (println "Webサーバーを起動中 ポート:" port)
    (let [started-db (start db)]
      (assoc this :db started-db :running? true)))
  (stop [this]
    (println "Webサーバーを停止中")
    (let [stopped-db (stop db)]
      (assoc this :db stopped-db :running? false))))

;; 使用例
(def db (->DatabaseConnection "localhost" 5432 false))
(def server (->WebServer 8080 db false))

(def started-server (start server))
;; データベースに接続中: localhost : 5432
;; Webサーバーを起動中 ポート: 8080

(def stopped-server (stop started-server))
;; Webサーバーを停止中
;; データベース接続を切断中
```

## 8. 条件分岐の置き換え

多態性を使って、switch/case 文による型判定を排除できます。

### Before（条件分岐）

```clojure
;; 悪い例：型による条件分岐
(defn send-notification-bad [type message opts]
  (case type
    :email {:type :email :to (:to opts) :body message}
    :sms {:type :sms :to (:phone opts) :body (subs message 0 (min 160 (count message)))}
    :push {:type :push :device (:device opts) :body message}
    (throw (ex-info "未知の通知タイプ" {:type type}))))
```

### After（多態性）

```clojure
(defprotocol NotificationSender
  "通知送信プロトコル"
  (send-notification [this message] "通知を送信する")
  (get-delivery-time [this] "配信時間を取得する"))

(defrecord EmailNotification [to subject]
  NotificationSender
  (send-notification [this message]
    {:type :email
     :to to
     :subject subject
     :body message
     :status :sent})
  (get-delivery-time [this]
    "1-2分"))

(defrecord SMSNotification [phone-number]
  NotificationSender
  (send-notification [this message]
    {:type :sms
     :to phone-number
     :body (if (> (count message) 160)
             (subs message 0 157)
             message)
     :status :sent})
  (get-delivery-time [this]
    "数秒"))

(defrecord PushNotification [device-token]
  NotificationSender
  (send-notification [this message]
    {:type :push
     :device device-token
     :body message
     :status :sent})
  (get-delivery-time [this]
    "即時"))

;; ファクトリ関数
(defn create-notification
  "通知タイプに応じた通知オブジェクトを作成する"
  [type & {:as opts}]
  (case type
    :email (->EmailNotification (:to opts) (:subject opts "通知"))
    :sms (->SMSNotification (:phone opts))
    :push (->PushNotification (:device opts))
    (throw (ex-info "未知の通知タイプ" {:type type}))))

;; 使用例
(def email (create-notification :email :to "user@example.com"))
(send-notification email "重要なお知らせ")
;; => {:type :email, :to "user@example.com", :subject "通知", :body "重要なお知らせ", :status :sent}
```

## 9. マルチメソッドとプロトコルの使い分け

| 特徴 | マルチメソッド | プロトコル |
|------|--------------|-----------|
| ディスパッチ | 任意の関数 | 第一引数の型 |
| パフォーマンス | 遅い | 速い |
| 拡張性 | 非常に高い | 高い |
| 用途 | 複雑なディスパッチ | 型ベースの多態性 |

### 使い分けの指針

- **プロトコル**: 型に基づく単純な多態性で十分な場合
- **マルチメソッド**: 複数の値や複雑な条件でディスパッチが必要な場合

## まとめ

本章では、Clojure における多態性について学びました：

1. **マルチメソッド**: 任意のディスパッチ関数による柔軟な多態性
2. **複合ディスパッチ**: 複数の値に基づくディスパッチ
3. **階層的ディスパッチ**: 型階層を使った継承的な振る舞い
4. **プロトコル**: パフォーマンスの良い型ベースの多態性
5. **レコード**: プロトコルを実装するデータ構造
6. **既存型への拡張**: 後からプロトコルを実装
7. **コンポーネントパターン**: ライフサイクル管理
8. **条件分岐の置き換え**: switch 文を多態性で代替

これらのメカニズムを適切に使い分けることで、拡張性が高く保守しやすいコードを実現できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/clojure/part1/src/polymorphism.clj`
- テストコード: `apps/clojure/part1/spec/polymorphism_spec.clj`

## 次章予告

次章から第2部「仕様とテスト」に入ります。Clojure Spec を使ったデータの仕様定義とバリデーションについて学びます。
