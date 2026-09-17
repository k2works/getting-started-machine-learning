---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章で最初の失敗するテストを書きました。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章で最初の失敗するテストを書きました。この章では **仮実装** と **三角測量** というテクニックを使って、テストを通すための最小限の実装を進めていきます。

## 2.2 仮実装（Fake It）

### GREEN にする

**仮実装** とは、テストを通すために最も簡単な実装を行うことです。

> ベタ書きの答えを書け
>
> すばやくグリーンにするにはどうすればよいだろうか——ベタ書きの答えを返すのだ。
>
> — テスト駆動開発

現在失敗しているテストをグリーンにするには、単に `"1"` を返せばよいですね。

```clojure
;; src/fizzbuzz/core.clj
(ns fizzbuzz.core)

(defn fizzbuzz [n]
  "1")
```

テストを実行します。

```bash
$ lein test

Ran 1 tests containing 1 assertions.
0 failures, 0 errors.
```

テストが通りました。**GREEN** です。

### REFACTOR

リファクタリングの必要があるかコードを確認しましょう。ベタ書きの実装ですが、次のテストで通用しなくなるでしょう。今の段階ではリファクタリングは不要です。

## 2.3 三角測量

次のテストを追加して、実装を一般化（抽象化）していきます。

> 三角測量
>
> どうやって抽象化を進めればよいだろうか——2 つ以上の例があるときのみ抽象化を行うのだ。
>
> — テスト駆動開発

```clojure
;; test/fizzbuzz/core_test.clj
(deftest fizzbuzz-test
  (testing "数を文字列にして返す"
    (testing "1 を渡したら文字列 \"1\" を返す"
      (is (= "1" (fizzbuzz 1))))
    (testing "2 を渡したら文字列 \"2\" を返す"
      (is (= "2" (fizzbuzz 2))))))
```

テストを実行すると失敗します。

```bash
$ lein test

FAIL in (fizzbuzz-test) (core_test.clj:9)
数を文字列にして返す 2 を渡したら文字列 "2" を返す
expected: (= "2" (fizzbuzz 2))
  actual: (not (= "2" "1"))
```

2 つ以上の例があるので抽象化しましょう。`str` 関数を使って数値を文字列に変換します。

```clojure
;; src/fizzbuzz/core.clj
(ns fizzbuzz.core)

(defn fizzbuzz [n]
  (str n))
```

テストを実行します。

```bash
$ lein test

Ran 1 tests containing 2 assertions.
0 failures, 0 errors.
```

テストが通りました。

## 2.4 3 の倍数のテスト

次は「3 の倍数のときは数の代わりに Fizz と返す」を実装しましょう。

```clojure
(deftest fizzbuzz-test
  (testing "数を文字列にして返す"
    (testing "1 を渡したら文字列 \"1\" を返す"
      (is (= "1" (fizzbuzz 1))))
    (testing "2 を渡したら文字列 \"2\" を返す"
      (is (= "2" (fizzbuzz 2)))))

  (testing "3 の倍数のときは数の代わりに「Fizz」と返す"
    (testing "3 を渡したら文字列 \"Fizz\" を返す"
      (is (= "Fizz" (fizzbuzz 3))))))
```

テスト実行 → RED。Clojure の `cond` を使って条件分岐を追加します。

```clojure
(defn fizzbuzz [n]
  (cond
    (zero? (mod n 3)) "Fizz"
    :else (str n)))
```

テスト実行 → GREEN。

## 2.5 5 の倍数のテスト

```clojure
  (testing "5 の倍数のときは「Buzz」と返す"
    (testing "5 を渡したら文字列 \"Buzz\" を返す"
      (is (= "Buzz" (fizzbuzz 5)))))
```

テスト実行 → RED。条件を追加します。

```clojure
(defn fizzbuzz [n]
  (cond
    (zero? (mod n 3)) "Fizz"
    (zero? (mod n 5)) "Buzz"
    :else (str n)))
```

テスト実行 → GREEN。

## 2.6 まとめ

この章では以下のことを学びました。

- **仮実装（Fake It）**: テストを通す最小限の実装（ベタ書き）
- **三角測量**: 2 つ以上のテストケースから一般化を導く
- Clojure の `cond` による条件分岐
- `zero?` と `mod` による倍数判定

**TODO リスト**:

- [x] 数を文字列にして返す
    - [x] 1 を渡したら文字列 "1" を返す
    - [x] 2 を渡したら文字列 "2" を返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする
