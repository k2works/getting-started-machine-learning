---
type: Article
title: "第 7 章: プロトコルとレコードによるポリモーフィズム"
description: "この章からは FizzBuzz プログラムに追加仕様を実装しながら、Clojure におけるポリモーフィズムを学びます。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: プロトコルとレコードによるポリモーフィズム

## 7.1 はじめに

この章からは FizzBuzz プログラムに追加仕様を実装しながら、Clojure におけるポリモーフィズムを学びます。Clojure は関数型言語ですが、`defprotocol` と `defrecord` による型ベースのポリモーフィズムを提供しています。

### 追加仕様

```
1 から 100 までの数をプリントするプログラムを書け。
ただし 3 の倍数のときは数の代わりに「Fizz」と、5 の倍数のときは「Buzz」とプリントし、
3 と 5 両方の倍数の場合には「FizzBuzz」とプリントすること。
タイプごとに出力を切り替えることができる。
タイプ 1 は通常、タイプ 2 は数字のみ、タイプ 3 は FizzBuzz の場合のみをプリントする。
```

## 7.2 TODO リスト

- [ ] タイプ 1 の場合（通常の FizzBuzz）
- [ ] タイプ 2 の場合（数字のみ）
- [ ] タイプ 3 の場合（FizzBuzz の場合のみ）
- [ ] それ以外のタイプの場合（例外を発生）

## 7.3 値オブジェクト — FizzBuzzValue

### defrecord による不変データ構造

Clojure の `defrecord` は不変のデータ構造を定義します。Java のクラスと異なり、フィールドは変更できません。

```clojure
;; src/fizzbuzz/domain/model.clj
(ns fizzbuzz.domain.model)

(defrecord FizzBuzzValue [number value])

(defn create-fizz-buzz-value [n]
  (->FizzBuzzValue n (str n)))
```

`->FizzBuzzValue` は自動生成されるコンストラクタ関数です。

### 述語関数

```clojure
(defn fizz? [n]
  (zero? (mod n 3)))

(defn buzz? [n]
  (zero? (mod n 5)))

(defn fizz-buzz? [n]
  (and (fizz? n) (buzz? n)))
```

Clojure の慣例として、述語関数（真偽値を返す関数）は名前の末尾に `?` を付けます。

## 7.4 プロトコル — FizzBuzzType

### defprotocol によるインターフェース定義

`defprotocol` は Java のインターフェースに相当します。実装すべき関数のシグネチャを定義します。

```clojure
;; src/fizzbuzz/domain/type.clj
(ns fizzbuzz.domain.type
  (:require [fizzbuzz.domain.model :as model]))

(defprotocol FizzBuzzType
  "FizzBuzz タイプごとの文字列生成プロトコル"
  (generate-string [this value]
    "FizzBuzzValue から文字列を生成する"))
```

### レコードによるプロトコル実装

各タイプをレコードとして定義し、プロトコルを実装します。

```clojure
;; タイプ 1: 通常の FizzBuzz
(defrecord FizzBuzzType01 []
  FizzBuzzType
  (generate-string [_this value]
    (let [n (:number value)]
      (cond
        (model/fizz-buzz? n) "FizzBuzz"
        (model/fizz? n) "Fizz"
        (model/buzz? n) "Buzz"
        :else (str n)))))

;; タイプ 2: 数字のみ
(defrecord FizzBuzzType02 []
  FizzBuzzType
  (generate-string [_this value]
    (str (:number value))))

;; タイプ 3: FizzBuzz の場合のみ
(defrecord FizzBuzzType03 []
  FizzBuzzType
  (generate-string [_this value]
    (let [n (:number value)]
      (cond
        (model/fizz-buzz? n) "FizzBuzz"
        (model/fizz? n) "Fizz"
        (model/buzz? n) "Buzz"
        :else ""))))
```

## 7.5 テストの実装

```clojure
;; test/fizzbuzz/domain/type_test.clj
(deftest fizz-buzz-type01-test
  (testing "タイプ 1: 通常の FizzBuzz"
    (let [t (type/->FizzBuzzType01)]
      (is (= "1" (type/generate-string t (model/create-fizz-buzz-value 1))))
      (is (= "Fizz" (type/generate-string t (model/create-fizz-buzz-value 3))))
      (is (= "Buzz" (type/generate-string t (model/create-fizz-buzz-value 5))))
      (is (= "FizzBuzz" (type/generate-string t (model/create-fizz-buzz-value 15)))))))
```

テスト実行 → GREEN。

## 7.6 まとめ

この章では以下のことを学びました。

- **defrecord** による不変データ構造（値オブジェクト）の定義
- **defprotocol** によるインターフェースの定義
- レコードによるプロトコルの実装（ポリモーフィズム）
- 述語関数の命名規則（`?` サフィックス）
- `let` によるローカルバインディング
