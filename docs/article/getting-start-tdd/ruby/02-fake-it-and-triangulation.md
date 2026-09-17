---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, ruby]
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

```ruby
def test_2を渡したら文字列2を返す
  assert_equal '2', @fizzbuzz.generate(2)
end
```

テストを実行します。

```bash
$ bundle exec rake test
 FAIL["test_2を渡したら文字列2を返す", FizzBuzzTest]
        Expected: "2"
          Actual: "1"

2 tests, 2 assertions, 1 failures, 0 errors, 0 skips
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。Ruby では `to_s` メソッドでオブジェクトを文字列に変換できます。

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    number.to_s
  end
end
```

テストを実行します。

```bash
$ bundle exec rake test
Started with run options --seed 42479

  2/2: [==========] 100% Time: 00:00:00

Finished in 0.00098s
2 tests, 2 assertions, 0 failures, 0 errors, 0 skips
```

テストが通りました！2 つ目のテストによって `generate` メソッドの一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

**TODO リスト**:

- [x] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [x] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.3 3 の倍数 — Fizz

次は「3 の倍数のときは数の代わりに Fizz と返す」に取り掛かります。

### Red: 3 の倍数のテスト

```ruby
def test_3を渡したらFizzを返す
  assert_equal 'Fizz', @fizzbuzz.generate(3)
end
```

```bash
$ bundle exec rake test
 FAIL["test_3を渡したらFizzを返す", FizzBuzzTest]
        Expected: "Fizz"
          Actual: "3"
```

### Green: 明白な実装

3 の倍数のときは "Fizz" を返すように実装します。Ruby では `%` 演算子で剰余を求め、`.zero?` メソッドでゼロかどうかを判定できます。

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    return 'Fizz' if (number % 3).zero?

    number.to_s
  end
end
```

```bash
$ bundle exec rake test
3 tests, 3 assertions, 0 failures, 0 errors, 0 skips
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.4 5 の倍数 — Buzz

### Red: 5 の倍数のテスト

```ruby
def test_5を渡したらBuzzを返す
  assert_equal 'Buzz', @fizzbuzz.generate(5)
end
```

```bash
$ bundle exec rake test
 FAIL["test_5を渡したらBuzzを返す", FizzBuzzTest]
        Expected: "Buzz"
          Actual: "5"
```

### Green: Buzz の実装

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    return 'Fizz' if (number % 3).zero?
    return 'Buzz' if (number % 5).zero?

    number.to_s
  end
end
```

```bash
$ bundle exec rake test
4 tests, 4 assertions, 0 failures, 0 errors, 0 skips
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 15 の倍数 — FizzBuzz

### Red: 15 の倍数のテスト

```ruby
def test_15を渡したらFizzBuzzを返す
  assert_equal 'FizzBuzz', @fizzbuzz.generate(15)
end
```

```bash
$ bundle exec rake test
 FAIL["test_15を渡したらFizzBuzzを返す", FizzBuzzTest]
        Expected: "FizzBuzz"
          Actual: "Fizz"
```

15 は 3 の倍数でもあるため、"Fizz" が返されてしまいました。3 と 5 の両方の倍数の判定を先に行う必要があります。

### Green: FizzBuzz の実装

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    return 'FizzBuzz' if (number % 15).zero?
    return 'Fizz' if (number % 3).zero?
    return 'Buzz' if (number % 5).zero?

    number.to_s
  end
end
```

```bash
$ bundle exec rake test
5 tests, 5 assertions, 0 failures, 0 errors, 0 skips
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.6 まとめ

この章では以下のことを学びました。

- **三角測量** で 2 つ以上の例を使ってプログラムを一般化する手法
- Ruby の `to_s` メソッドによるオブジェクトの文字列変換
- Ruby の `%` 演算子と `.zero?` メソッドによる剰余判定
- `return ... if` 構文によるガード節（早期リターン）
- Red-Green サイクルを繰り返してコアロジックを段階的に構築する方法

次章では、残りの TODO（リスト生成とプリント）を実装し、リファクタリングで「動作するきれいなコード」を目指します。
