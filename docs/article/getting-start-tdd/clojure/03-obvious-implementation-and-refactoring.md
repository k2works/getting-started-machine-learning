---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章で三角測量による実装を学びました。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章で三角測量による実装を学びました。この章では **明白な実装** で残りの FizzBuzz ロジックを完成させ、**リファクタリング** によってコードを整理します。

## 3.2 3 と 5 両方の倍数

### RED

```clojure
  (testing "3 と 5 両方の倍数の場合には「FizzBuzz」と返す"
    (testing "15 を渡したら文字列 \"FizzBuzz\" を返す"
      (is (= "FizzBuzz" (fizzbuzz 15)))))
```

テスト実行 → RED。15 は 3 の倍数でもあるため "Fizz" が返されます。

### GREEN

3 と 5 両方の倍数の条件を最初に配置します。Clojure の `and` で複合条件を表現します。

```clojure
(defn fizzbuzz [n]
  (cond
    (and (zero? (mod n 3)) (zero? (mod n 5))) "FizzBuzz"
    (zero? (mod n 3)) "Fizz"
    (zero? (mod n 5)) "Buzz"
    :else (str n)))
```

テスト実行 → GREEN。

### REFACTOR

> 明白な実装
>
> すばやくグリーンにするにはどうすればよいだろうか——そのまま実装するのだ。
>
> — テスト駆動開発

現在のコードは明快で読みやすいですが、`(zero? (mod n 3))` という条件が重複しています。Clojure ではヘルパー関数を使って条件を抽出できます。

```clojure
(defn- divisible-by?
  "n が d で割り切れるかどうかを判定する"
  [n d]
  (zero? (mod n d)))

(defn fizzbuzz [n]
  (cond
    (and (divisible-by? n 3) (divisible-by? n 5)) "FizzBuzz"
    (divisible-by? n 3) "Fizz"
    (divisible-by? n 5) "Buzz"
    :else (str n)))
```

テスト実行 → GREEN。リファクタリング後もテストが通ることを確認しました。

## 3.3 1 から 100 までのリスト

1 から 100 までの FizzBuzz リストを生成する機能を追加します。

### RED

```clojure
(deftest fizzbuzz-list-test
  (testing "1 から 100 までの FizzBuzz リストを生成する"
    (let [result (fizzbuzz-list 1 100)]
      (is (= 100 (count result)))
      (is (= "1" (first result)))
      (is (= "Buzz" (nth result 99))))))
```

### GREEN

Clojure の `map` と `range` を使って実装します。

```clojure
(defn fizzbuzz-list [start end]
  (mapv fizzbuzz (range start (inc end))))
```

`mapv` はベクタ（永続配列）を返す `map` のバリアントです。テスト実行 → GREEN。

## 3.4 プリント機能

```clojure
(defn print-fizzbuzz [start end]
  (doseq [item (fizzbuzz-list start end)]
    (println item)))

(defn -main [& _args]
  (print-fizzbuzz 1 100))
```

`doseq` は副作用を伴うシーケンス処理に使います。`-main` 関数はエントリーポイントです。

## 3.5 スレッディングマクロ

Clojure には **スレッディングマクロ**（`->` と `->>`）があります。これにより、データの変換パイプラインを読みやすく記述できます。

```clojure
;; 通常の書き方
(println (first (mapv fizzbuzz (range 1 101))))

;; ->> マクロを使った書き方
(->> (range 1 101)
     (mapv fizzbuzz)
     first
     println)
```

`->>` マクロは、前の式の結果を次の式の **最後の引数** に挿入します。パイプラインのように左から右へデータが流れるイメージで読めます。

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装**: 自明な場合はそのまま実装する
- **リファクタリング**: `defn-` によるプライベート関数の抽出
- `mapv` と `range` によるリスト生成
- `doseq` による副作用を伴うシーケンス処理
- `->>` スレッディングマクロによるパイプライン処理

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

第 1 部の FizzBuzz 基本実装が完成しました。第 2 部では開発環境と自動化について学びます。
