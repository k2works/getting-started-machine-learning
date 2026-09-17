---
type: Article
title: "第1章: 不変性とデータ変換"
description: "関数型プログラミングの最も重要な概念の一つが不変性（Immutability）です。"
tags: [article, functional-desgin-ppp, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# 第1章: 不変性とデータ変換

## はじめに

関数型プログラミングの最も重要な概念の一つが**不変性（Immutability）**です。不変データ構造を使用することで、プログラムの予測可能性が向上し、並行処理でのバグを防ぎ、コードの理解と保守が容易になります。

本章では、Clojure における不変データ構造の基本から、データ変換パイプライン、副作用の分離まで、実践的な例を通じて学びます。

## 1. 不変データ構造の基本

### なぜ不変性が重要なのか

従来の命令型プログラミングでは、変数やオブジェクトの状態を直接変更します：

```java
// Java（可変）
person.setAge(31);  // 元のオブジェクトが変更される
```

これに対し、Clojure のデータ構造は不変です。データを「変更」すると、新しいデータ構造が作成されます：

```clojure
(def original-person {:name "田中" :age 30})

(defn update-age [person new-age]
  (assoc person :age new-age))

;; 使用例
(def updated-person (update-age original-person 31))

original-person   ;; => {:name "田中" :age 30}  ← 元のデータは変わらない
updated-person    ;; => {:name "田中" :age 31}  ← 新しいデータ
```

### 不変性の利点

1. **予測可能性**: データが変更されないため、関数の動作を予測しやすい
2. **スレッドセーフ**: 複数のスレッドから安全にアクセスできる
3. **履歴の保持**: 変更前のデータを保持できる（Undo/Redo の実装が容易）
4. **デバッグの容易さ**: データの変更履歴を追跡しやすい

## 2. 構造共有（Structural Sharing）

「毎回新しいデータ構造を作成すると非効率では？」と思うかもしれません。Clojure は**構造共有**により、効率的にメモリを使用します。

```clojure
(def team {:name "開発チーム"
           :members [{:name "田中" :role :developer}
                     {:name "鈴木" :role :designer}
                     {:name "佐藤" :role :manager}]})

(defn add-member [team member]
  (update team :members conj member))

(def new-team (add-member team {:name "山田" :role :developer}))
```

`new-team` は新しいマップですが、`:name` の値や既存のメンバーデータは `team` と共有されています。変更されていない部分はコピーされず、参照が共有されます。

```
team:     {:name "開発チーム", :members [田中, 鈴木, 佐藤]}
                ↓                    ↓
new-team: {:name (共有), :members [田中, 鈴木, 佐藤, 山田]}
                                  ↑ 既存データは共有
```

## 3. データ変換パイプライン

関数型プログラミングでは、データを変換する一連の処理を**パイプライン**として表現します。Clojure では**スレッドマクロ**（`->` と `->>`）を使用して、読みやすいパイプラインを構築できます。

### 実践例：注文処理システム

```clojure
(def order {:items [{:name "商品A" :price 1000 :quantity 2}
                    {:name "商品B" :price 500 :quantity 3}
                    {:name "商品C" :price 2000 :quantity 1}]
            :customer {:name "山田" :membership :gold}})

;; 各アイテムの小計を計算
(defn calculate-subtotal [item]
  (* (:price item) (:quantity item)))

;; 会員種別に応じた割引率を取得
(defn membership-discount [membership]
  (case membership
    :gold 0.1
    :silver 0.05
    :bronze 0.02
    0))

;; 注文の合計金額を計算
(defn calculate-total [order]
  (->> (:items order)
       (map calculate-subtotal)
       (reduce +)))

;; 割引後の金額を計算
(defn apply-discount [order total]
  (let [discount-rate (-> order :customer :membership membership-discount)]
    (* total (- 1 discount-rate))))
```

### スレッドマクロの使い分け

- **`->`**（thread-first）: 結果を次の関数の**最初の引数**として渡す
- **`->>`**（thread-last）: 結果を次の関数の**最後の引数**として渡す

```clojure
;; -> の例（マップの操作に便利）
(-> order :customer :membership membership-discount)
;; 展開: (membership-discount (:membership (:customer order)))

;; ->> の例（コレクション操作に便利）
(->> (:items order)
     (map calculate-subtotal)
     (reduce +))
;; 展開: (reduce + (map calculate-subtotal (:items order)))
```

## 4. 副作用の分離

関数型プログラミングでは、**純粋関数**と**副作用を持つ関数**を明確に分離することが重要です。

### 純粋関数とは

- 同じ入力に対して常に同じ出力を返す
- 外部の状態を変更しない（副作用がない）

```clojure
;; 純粋関数
(defn pure-calculate-tax [amount tax-rate]
  (* amount tax-rate))

;; 何度呼んでも同じ結果
(pure-calculate-tax 1000 0.1)  ;; => 100.0
(pure-calculate-tax 1000 0.1)  ;; => 100.0
```

### 副作用の分離パターン

ビジネスロジック（純粋関数）と副作用（I/O）を分離します：

```clojure
;; ビジネスロジック（純粋関数）
(defn calculate-invoice [items tax-rate]
  (let [subtotal (reduce + (map calculate-subtotal items))
        tax (pure-calculate-tax subtotal tax-rate)
        total (+ subtotal tax)]
    {:subtotal subtotal
     :tax tax
     :total total}))

;; 副作用を含む処理（分離）
(defn save-invoice! [invoice]
  ;; データベースへの保存（副作用）
  (println "Saving invoice:" invoice)
  invoice)

(defn send-notification! [invoice customer-email]
  ;; メール送信（副作用）
  (println "Sending notification to:" customer-email)
  invoice)

;; 処理全体のオーケストレーション
(defn process-and-save-invoice! [items tax-rate customer-email]
  (-> (calculate-invoice items tax-rate)
      save-invoice!
      (send-notification! customer-email)))
```

副作用を持つ関数には `!` サフィックスを付ける慣例があります。

## 5. 永続的データ構造の活用：Undo/Redo の実装

不変データ構造を活用すると、履歴管理が非常に簡単に実装できます。

```clojure
;; 履歴を保持するデータ構造
(defn create-history []
  {:current nil
   :past []
   :future []})

(defn push-state [history new-state]
  (-> history
      (update :past conj (:current history))
      (assoc :current new-state)
      (assoc :future [])))

(defn undo [history]
  (if (empty? (:past history))
    history
    (let [previous (peek (:past history))]
      (-> history
          (update :future conj (:current history))
          (assoc :current previous)
          (update :past pop)))))

(defn redo [history]
  (if (empty? (:future history))
    history
    (let [next-state (peek (:future history))]
      (-> history
          (update :past conj (:current history))
          (assoc :current next-state)
          (update :future pop)))))
```

### 使用例

```clojure
(def history
  (-> (create-history)
      (push-state {:text "Hello"})
      (push-state {:text "Hello World"})
      (push-state {:text "Hello World!"})))

(:current history)  ;; => {:text "Hello World!"}

(def after-undo (undo history))
(:current after-undo)  ;; => {:text "Hello World"}

(def after-redo (redo after-undo))
(:current after-redo)  ;; => {:text "Hello World!"}
```

## 6. トランスデューサーによる効率的な変換

複数の変換を行う場合、通常は中間コレクションが作成されます。**トランスデューサー**を使用すると、複数の変換を1回のパスで処理できます。

```clojure
;; 複数の変換を合成
(def xf-process-items
  (comp
   (filter #(> (:quantity %) 0))           ;; 数量が0より大きいものをフィルタ
   (map #(assoc % :subtotal (calculate-subtotal %)))  ;; 小計を追加
   (filter #(> (:subtotal %) 100))))       ;; 小計が100より大きいものをフィルタ

;; トランスデューサーを使用した効率的な処理
(defn process-items-efficiently [items]
  (into [] xf-process-items items))
```

トランスデューサーの利点：


- 中間コレクションを作成しない
- 遅延評価との組み合わせが可能
- 様々なコンテキスト（ベクター、チャネルなど）で再利用可能

## まとめ

本章では、関数型プログラミングの基礎である不変性について学びました：

1. **不変データ構造**: データは変更されず、新しいデータが作成される
2. **構造共有**: 効率的なメモリ使用を実現
3. **データ変換パイプライン**: スレッドマクロによる読みやすい変換処理
4. **副作用の分離**: 純粋関数とI/O処理の明確な分離
5. **履歴管理**: 不変性を活用したUndo/Redoの簡潔な実装
6. **トランスデューサー**: 効率的な複合変換

これらの概念は、関数型プログラミングの他のすべてのパターンの基盤となります。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/clojure/part1/src/immutability.clj`
- テストコード: `apps/clojure/part1/spec/immutability_spec.clj`

## 次章予告

次章では、**関数合成と高階関数**について学びます。小さな関数を組み合わせて複雑な処理を構築する方法を探ります。
