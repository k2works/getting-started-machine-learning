---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, clojure]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して **TODO リスト** を作るところから始めます。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

## 1.2 仕様の確認

今回取り組む FizzBuzz 問題の仕様は以下の通りです。

```
1 から 100 までの数をプリントするプログラムを書け。
ただし 3 の倍数のときは数の代わりに「Fizz」と、5 の倍数のときは「Buzz」とプリントし、
3 と 5 両方の倍数の場合には「FizzBuzz」とプリントすること。
```

この仕様をそのままプログラムに落とし込むには少しサイズが大きいですね。最初の作業は仕様を **TODO リスト** に分解する作業から着手しましょう。

## 1.3 TODO リストの作成

仕様を分解して TODO リストを作成します。

**TODO リスト**:

- [ ] 数を文字列にして返す
    - [ ] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まず「1 を渡したら文字列 "1" を返す」という、最も小さなタスクから取り掛かります。

## 1.4 テスティングフレームワークの導入

### テストファースト

最初にプログラムする対象を決めたので、早速プロダクトコードを実装……ではなく **テストファースト** で作業を進めましょう。

> テストファースト
>
> いつテストを書くべきだろうか——それはテスト対象のコードを書く前だ。
>
> — テスト駆動開発

今回 Clojure のテスティングフレームワークには標準ライブラリの **clojure.test** を利用します。`deftest`、`testing`、`is` マクロを使ってテストを記述します。

### 開発環境のセットアップ

Leiningen でプロジェクトをセットアップします。

```bash
# Nix 環境に入る
$ nix develop .#clojure

# プロジェクトディレクトリに移動
$ cd apps/clojure
```

Clojure のテストは `test/` ディレクトリ配下に、プロダクトコードと対応する名前空間で配置します。

### 環境確認テスト

環境が正しく設定されていることを確認するため、学習用テストを書きます。

```clojure
;; test/fizzbuzz/core_test.clj
(ns fizzbuzz.core-test
  (:require [clojure.test :refer :all]
            [fizzbuzz.core :refer :all]))

(deftest greeting-test
  (testing "挨拶機能"
    (is (= "hello world" (greeting)))))
```

プロダクトコードを作成します。

```clojure
;; src/fizzbuzz/core.clj
(ns fizzbuzz.core)

(defn greeting []
  "hello world")
```

テストを実行します。

```bash
$ lein test

lein test fizzbuzz.core-test

Ran 1 tests containing 1 assertions.
0 failures, 0 errors.
```

テスト環境が正しく動作することを確認できました。

## 1.5 最初のテスト

では、FizzBuzz の最初のテストを作成しましょう。「1 を渡したら文字列 "1" を返す」テストです。

```clojure
;; test/fizzbuzz/core_test.clj
(ns fizzbuzz.core-test
  (:require [clojure.test :refer :all]
            [fizzbuzz.core :refer :all]))

(deftest fizzbuzz-test
  (testing "数を文字列にして返す"
    (testing "1 を渡したら文字列 \"1\" を返す"
      (is (= "1" (fizzbuzz 1))))))
```

プロダクトコードに空の実装を追加します。

```clojure
;; src/fizzbuzz/core.clj
(ns fizzbuzz.core)

(defn fizzbuzz [n]
  nil)
```

テストを実行すると失敗します。

```bash
$ lein test

FAIL in (fizzbuzz-test) (core_test.clj:7)
数を文字列にして返す 1 を渡したら文字列 "1" を返す
expected: (= "1" (fizzbuzz 1))
  actual: (not (= "1" nil))

Ran 1 tests containing 1 assertions.
1 failures, 0 errors.
```

**RED** の状態です。テストが失敗していることを確認しました。

## 1.6 まとめ

この章では以下のことを学びました。

- **TODO リスト** を作成して仕様を小さなタスクに分解する方法
- **clojure.test** を使ったテストの書き方（`deftest`、`testing`、`is`）
- **テストファースト** のアプローチで最初に失敗するテストを書くこと
- Leiningen プロジェクトの基本構造

**TODO リスト**:

- [ ] 数を文字列にして返す
    - [x] 1 を渡したら文字列 "1" を返す ← RED
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする
