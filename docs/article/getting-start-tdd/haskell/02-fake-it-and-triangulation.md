---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。この章では、**三角測量** によってプログラムを一般化し、さらに FizzBuzz のコアロジックを実装していきます。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [ ] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.2 三角測量

1 を渡したら文字列 "1" を返すようにできました。では、2 を渡したらどうなるでしょうか？

### Red: 2 つ目のテストを書く

```haskell
    it "2 を渡すと '2' を返す" $
      generate 2 `shouldBe` "2"
```

テストを実行します。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す FAILED [1]

Failures:

  test/FizzBuzz/FizzBuzzSpec.hs:12:7:
  1) generate 2 を渡すと '2' を返す
       expected: "2"
        but got: "1"

2 examples, 1 failure
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。Haskell では `show` 関数で `Show` 型クラスのインスタンスを文字列に変換できます。

```haskell
generate :: Int -> String
generate n = show n
```

テストを実行します。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す

Finished in 0.0001 seconds
2 examples, 0 failures
```

テストが通りました。2 つ目のテストによって `generate` 関数の一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

Rust では `number.to_string()` と書くところを、Haskell では `show n` を使います。Haskell の `show` は `Show` 型クラスを実装した任意の型で利用でき、Rust の `Display` トレイトと同様の役割を果たします。

**TODO リスト**:

- [x] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [x] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.3 3 の倍数 -- Fizz

次は「3 の倍数のときは数の代わりに Fizz と返す」に取り掛かります。

### Red: 3 の倍数のテスト

```haskell
    it "3 の倍数を渡すと 'Fizz' を返す" $
      generate 3 `shouldBe` "Fizz"
```

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す FAILED [1]

Failures:

  test/FizzBuzz/FizzBuzzSpec.hs:15:7:
  1) generate 3 の倍数を渡すと 'Fizz' を返す
       expected: "Fizz"
        but got: "3"

3 examples, 1 failure
```

### Green: ガード式による条件分岐

3 の倍数のときは "Fizz" を返すように実装します。Haskell では **ガード式** を使って条件分岐を記述します。

```haskell
generate :: Int -> String
generate n
  | n `mod` 3 == 0 = "Fizz"
  | otherwise       = show n
```

ガード式は `|` の後に条件を書き、`=` の後に条件が真のときの返り値を記述します。`otherwise` は常に `True` を返す定数で、すべての条件に合致しなかった場合のデフォルトケースとして機能します。`mod` 関数はバッククォートで囲むことで中置演算子として使えます（`n `mod` 3` は `mod n 3` と同じ意味です）。

Rust の `if` 式や `match` 式に比べ、Haskell のガード式は数学的な関数定義に近い書き方ができます。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す

Finished in 0.0001 seconds
3 examples, 0 failures
```

テストが通りました。三角測量として 6 のテストも追加して確認します。

```haskell
    it "6 を渡すと 'Fizz' を返す" $
      generate 6 `shouldBe` "Fizz"
```

```bash
$ stack test
4 examples, 0 failures
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.4 5 の倍数 -- Buzz

### Red: 5 の倍数のテスト

```haskell
    it "5 の倍数を渡すと 'Buzz' を返す" $
      generate 5 `shouldBe` "Buzz"
```

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    5 の倍数を渡すと 'Buzz' を返す FAILED [1]

Failures:

  test/FizzBuzz/FizzBuzzSpec.hs:21:7:
  1) generate 5 の倍数を渡すと 'Buzz' を返す
       expected: "Buzz"
        but got: "5"

5 examples, 1 failure
```

### Green: Buzz の実装

ガード式に 5 の倍数の条件を追加します。

```haskell
generate :: Int -> String
generate n
  | n `mod` 3 == 0 = "Fizz"
  | n `mod` 5 == 0 = "Buzz"
  | otherwise       = show n
```

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    6 を渡すと 'Fizz' を返す

Finished in 0.0001 seconds
5 examples, 0 failures
```

三角測量として 10 のテストも追加します。

```haskell
    it "10 を渡すと 'Buzz' を返す" $
      generate 10 `shouldBe` "Buzz"
```

```bash
$ stack test
6 examples, 0 failures
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 15 の倍数 -- FizzBuzz

### Red: 15 の倍数のテスト

```haskell
    it "15 の倍数を渡すと 'FizzBuzz' を返す" $
      generate 15 `shouldBe` "FizzBuzz"
```

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    15 の倍数を渡すと 'FizzBuzz' を返す FAILED [1]

Failures:

  test/FizzBuzz/FizzBuzzSpec.hs:27:7:
  1) generate 15 の倍数を渡すと 'FizzBuzz' を返す
       expected: "FizzBuzz"
        but got: "Fizz"

7 examples, 1 failure
```

15 は 3 の倍数でもあるため、"Fizz" が返されてしまいました。ガード式は上から順番に評価されるので、3 の倍数の条件に先にマッチしてしまいます。3 と 5 の両方の倍数（つまり 15 の倍数）の判定を **先に** 行う必要があります。

### Green: ガード式の順序を修正

```haskell
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n
```

ガード式は上から順に評価されるため、最も限定的な条件（15 の倍数）を最初に配置します。これは Rust の `match` 式のパターンの並び順や、Go の `switch` 文の `case` の順序と同じ考え方です。

```bash
$ stack test
FizzBuzz.FizzBuzzSpec
  generate
    1 を渡すと '1' を返す
    2 を渡すと '2' を返す
    3 の倍数を渡すと 'Fizz' を返す
    5 の倍数を渡すと 'Buzz' を返す
    6 を渡すと 'Fizz' を返す
    10 を渡すと 'Buzz' を返す
    15 の倍数を渡すと 'FizzBuzz' を返す

Finished in 0.0001 seconds
7 examples, 0 failures
```

三角測量として 30 のテストも追加しておきます。

```haskell
    it "30 を渡すと 'FizzBuzz' を返す" $
      generate 30 `shouldBe` "FizzBuzz"
```

```bash
$ stack test
8 examples, 0 failures
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

ここまでの作業をコミットしておきましょう。

```bash
$ git add .
$ git commit -m 'feat: FizzBuzz のコアロジックを実装'
```

## 2.6 まとめ

この章では以下のことを学びました。

- **三角測量** で 2 つ以上の例を使ってプログラムを一般化する手法
- Haskell の `show` 関数による整数から文字列への変換
- Haskell の `mod` 関数と中置記法（`` n `mod` 3 ``）による剰余判定
- **ガード式** による条件分岐の記述（`|` と `otherwise`）
- ガード式の **評価順序** の重要性（限定的な条件を先に配置）
- Red-Green-Refactor サイクルを繰り返してコアロジックを段階的に構築する方法

次章では、残りの TODO（リスト生成）を実装し、リファクタリングで「動作するきれいなコード」を目指します。
