---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "この章では Clojure の 高階関数 と 関数合成 を使って、FizzBuzz プログラムをより関数型らしいスタイルに変換します。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

この章では Clojure の **高階関数** と **関数合成** を使って、FizzBuzz プログラムをより関数型らしいスタイルに変換します。

## 10.2 高階関数

高階関数とは、関数を引数として受け取るか、関数を返す関数のことです。Clojure では関数がファーストクラスの値であり、変数に束縛したり、引数として渡したりできます。

### map / filter / reduce

```clojure
;; map: 各要素に関数を適用
(map fizzbuzz (range 1 16))
;=> ("1" "2" "Fizz" "4" "Buzz" "Fizz" "7" "8" "Fizz" "Buzz"
;    "11" "Fizz" "13" "14" "FizzBuzz")

;; filter: 条件を満たす要素のみ抽出
(filter #(not= "" %) (map #(type/generate-string (type/->FizzBuzzType03)
                                                  (model/create-fizz-buzz-value %))
                          (range 1 16)))
;=> ("Fizz" "Buzz" "Fizz" "Fizz" "Buzz" "Fizz" "FizzBuzz")

;; reduce: 累積処理
(reduce str (map fizzbuzz (range 1 6)))
;=> "12FizzBuzz4"
```

### 部分適用（partial）

`partial` は関数の一部の引数を固定して新しい関数を作ります。

```clojure
(def fizzbuzz-type1
  (partial type/generate-string (type/->FizzBuzzType01)))

(fizzbuzz-type1 (model/create-fizz-buzz-value 15))
;=> "FizzBuzz"
```

### 無名関数

```clojure
;; fn による無名関数
(map (fn [n] (fizzbuzz n)) (range 1 6))

;; #() リーダーマクロによる短縮形
(map #(fizzbuzz %) (range 1 6))
```

## 10.3 関数合成

### comp による関数合成

`comp` は複数の関数を合成して新しい関数を作ります。右から左へ適用されます。

```clojure
(def fizzbuzz-with-prefix
  (comp (partial str "=> ") fizzbuzz))

(fizzbuzz-with-prefix 3)
;=> "=> Fizz"

(fizzbuzz-with-prefix 15)
;=> "=> FizzBuzz"
```

### スレッディングマクロ

`->` と `->>` はデータの変換パイプラインを記述します。

```clojure
;; ->> マクロ（最後の引数に挿入）
(->> (range 1 101)
     (map fizzbuzz)
     (filter #(re-matches #"[A-Za-z]+" %))
     (take 5))
;=> ("Fizz" "Buzz" "Fizz" "Fizz" "Buzz")

;; -> マクロ（最初の引数に挿入）
(-> 15
    fizzbuzz
    clojure.string/upper-case)
;=> "FIZZBUZZ"
```

## 10.4 関数ファクトリ

関数を返す関数を使って、カスタマイズ可能な処理を作れます。

```clojure
(defn create-fizzbuzz-filter
  "指定パターンにマッチする FizzBuzz 結果をフィルタリングする関数を返す"
  [pattern]
  (fn [results]
    (filter #(clojure.string/includes? % pattern) results)))

(def fizz-filter (create-fizzbuzz-filter "Fizz"))
(def buzz-filter (create-fizzbuzz-filter "Buzz"))

(fizz-filter ["1" "Fizz" "Buzz" "FizzBuzz"])
;=> ("Fizz" "FizzBuzz")
```

## 10.5 まとめ

この章では以下のことを学びました。

- **map / filter / reduce** による高階関数の活用
- **partial** による部分適用
- **comp** による関数合成
- **スレッディングマクロ**（`->`、`->>`）によるパイプライン処理
- **関数ファクトリ**（関数を返す関数）パターン
