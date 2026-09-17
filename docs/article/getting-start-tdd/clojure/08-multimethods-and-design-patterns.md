---
type: Article
title: "第 8 章: マルチメソッドとデザインパターン"
description: "前章ではプロトコルとレコードによるポリモーフィズムを学びました。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: マルチメソッドとデザインパターン

## 8.1 はじめに

前章ではプロトコルとレコードによるポリモーフィズムを学びました。この章では **マルチメソッド** による値ベースのディスパッチと、**デザインパターン** の Clojure 的な実現を学びます。

## 8.2 マルチメソッド

### defmulti / defmethod

マルチメソッドは **値に基づいたディスパッチ** を行います。プロトコルが型に基づくのに対し、マルチメソッドは任意の値でディスパッチできます。

```clojure
;; ディスパッチ関数の定義
(defmulti generate-by-type
  "タイプ番号に基づいて FizzBuzz 文字列を生成する"
  (fn [type-num _value] type-num))

;; タイプごとの実装
(defmethod generate-by-type 1 [_ value]
  (let [n (:number value)]
    (cond
      (model/fizz-buzz? n) "FizzBuzz"
      (model/fizz? n) "Fizz"
      (model/buzz? n) "Buzz"
      :else (str n))))

(defmethod generate-by-type 2 [_ value]
  (str (:number value)))

(defmethod generate-by-type 3 [_ value]
  (let [n (:number value)]
    (cond
      (model/fizz-buzz? n) "FizzBuzz"
      (model/fizz? n) "Fizz"
      (model/buzz? n) "Buzz"
      :else "")))

;; デフォルト（未定義タイプ）
(defmethod generate-by-type :default [type-num _]
  (throw (ex-info "FizzBuzz タイプが未定義です" {:type type-num})))
```

### プロトコル vs マルチメソッド

| 特徴 | プロトコル | マルチメソッド |
|------|-----------|--------------|
| ディスパッチ基準 | 第 1 引数の型 | 任意の値 |
| パフォーマンス | 高速（JVM レベル） | やや遅い |
| 拡張性 | 型の追加で拡張 | メソッドの追加で拡張 |
| 用途 | インターフェース設計 | 柔軟なディスパッチ |

## 8.3 ファクトリ関数パターン

Clojure ではクラスのコンストラクタの代わりにファクトリ関数を使います。

```clojure
(defn create-type [type-num]
  (case type-num
    1 (->FizzBuzzType01)
    2 (->FizzBuzzType02)
    3 (->FizzBuzzType03)
    (->FizzBuzzTypeNotDefined)))
```

`case` は `switch` 文に相当し、定数値でのディスパッチに最適化されています。

## 8.4 Command パターン

### プロトコルによる Command パターン

```clojure
;; src/fizzbuzz/application/command.clj
(defprotocol FizzBuzzCommand
  "FizzBuzz コマンドプロトコル"
  (execute [this]))

;; 単一値コマンド
(defrecord FizzBuzzValueCommand [number type-num]
  FizzBuzzCommand
  (execute [_this]
    (let [fizz-buzz-type (type/create-type type-num)
          value (model/create-fizz-buzz-value number)]
      (type/generate-string fizz-buzz-type value))))

;; リストコマンド
(defrecord FizzBuzzListCommand [numbers type-num]
  FizzBuzzCommand
  (execute [_this]
    (let [fizz-buzz-type (type/create-type type-num)]
      (->> numbers
           (mapv model/create-fizz-buzz-value)
           (mapv #(type/generate-string fizz-buzz-type %))
           model/create-fizz-buzz-list))))
```

### テスト

```clojure
(deftest fizz-buzz-value-command-test
  (testing "FizzBuzzValueCommand: 単一値コマンド"
    (is (= "1" (cmd/execute (cmd/->FizzBuzzValueCommand 1 1))))
    (is (= "Fizz" (cmd/execute (cmd/->FizzBuzzValueCommand 3 1))))
    (is (= "Buzz" (cmd/execute (cmd/->FizzBuzzValueCommand 5 1))))
    (is (= "FizzBuzz" (cmd/execute (cmd/->FizzBuzzValueCommand 15 1))))))
```

## 8.5 Strategy パターン

Clojure では高階関数が Strategy パターンの自然な表現です。

```clojure
(defn process-with-strategy [strategy-fn numbers]
  (mapv strategy-fn numbers))

;; 使用例
(process-with-strategy fizzbuzz (range 1 101))
```

関数をファーストクラスの値として渡すことで、Strategy パターンが関数の引数渡しだけで実現できます。

## 8.6 まとめ

この章では以下のことを学びました。

- **defmulti/defmethod** によるマルチメソッド（値ベースのディスパッチ）
- プロトコルとマルチメソッドの使い分け
- **ファクトリ関数** パターンと `case` 式
- **Command パターン** のプロトコルによる実現
- **Strategy パターン** の高階関数による実現
