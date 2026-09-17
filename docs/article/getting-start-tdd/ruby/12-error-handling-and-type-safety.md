---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章までに高階関数、不変データ、パイプライン処理を学びました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章までに高階関数、不変データ、パイプライン処理を学びました。最終章では、Ruby 3.x の **パターンマッチング** を使った型安全なコード、**安全なファクトリメソッド**、そして **フィボナッチ数** を題材にした **ベンチマーク** を実装します。

## 12.2 パターンマッチング（Ruby 3.x）

### case/in 構文

Ruby 3.0 以降、`case/in` でパターンマッチングが使えます。従来の `case/when` が値の一致を見るのに対し、`case/in` は構造の一致を見ます。

```ruby
# 従来の case/when（値の一致）
case value
when 1 then 'one'
when 2 then 'two'
end

# Ruby 3.x の case/in（構造の一致）
case [1, 2, 3]
in [Integer => a, Integer => b, Integer => c]
  puts "#{a}, #{b}, #{c}"  # => "1, 2, 3"
end
```

### deconstruct_keys によるオブジェクトのパターンマッチング

`deconstruct_keys` を実装すると、自作クラスでもパターンマッチングが使えます。

```ruby
class FizzBuzzValue
  def deconstruct_keys(_keys)
    { value: @value, number: @number }
  end
end
```

```ruby
# 使用例
value = FizzBuzzValue.new('FizzBuzz', 15)
case value
in { value: 'FizzBuzz', number: Integer => n }
  puts "FizzBuzz at #{n}"  # => "FizzBuzz at 15"
in { value: 'Fizz', number: Integer => n }
  puts "Fizz at #{n}"
in { value: 'Buzz', number: Integer => n }
  puts "Buzz at #{n}"
else
  puts "Number"
end
```

### テスト

```ruby
class FizzBuzzValueTest < Minitest::Test
  def test_パターンマッチングで値を取り出せる
    value = FizzBuzzValue.new('Fizz', 3)
    case value
    in { value: 'Fizz', number: 3 }
      matched = true
    else
      matched = false
    end
    assert matched
  end
end
```

## 12.3 型安全なファクトリ

### 問題: マジックナンバー

現在のファクトリメソッドは数値定数でタイプを指定しています。

```ruby
# マジックナンバーが散在する可能性
type = FizzBuzzType.create(1)  # 1 が何を意味するか不明確
```

### 解決: FizzBuzzTypeName

名前付き定数でタイプを指定できるようにします。

```ruby
module FizzBuzzTypeName
  STANDARD = FizzBuzzType::TYPE_01
  NUMBER_ONLY = FizzBuzzType::TYPE_02
  FIZZ_BUZZ_ONLY = FizzBuzzType::TYPE_03
end
```

```ruby
# 名前で指定（意図が明確）
type = FizzBuzzType.create(FizzBuzzTypeName::STANDARD)
```

### try_create: 安全なファクトリ

未定義のタイプに対して例外ではなく `nil` を返すメソッドを追加します。

```ruby
class FizzBuzzType
  def self.try_create(type)
    create(type)
  rescue RuntimeError
    nil
  end
end
```

### テスト

```ruby
class FizzBuzzTypeTest < Minitest::Test
  def test_FizzBuzzTypeNameで型安全に生成できる
    type = FizzBuzzType.create(FizzBuzzTypeName::STANDARD)
    assert_instance_of FizzBuzzType01, type
  end

  def test_try_createで有効なタイプはインスタンスを返す
    type = FizzBuzzType.try_create(FizzBuzzType::TYPE_01)
    assert_instance_of FizzBuzzType01, type
  end

  def test_try_createで不明なタイプはnilを返す
    type = FizzBuzzType.try_create(999)
    assert_nil type
  end
end
```

## 12.4 検索メソッド

FizzBuzzList に検索メソッドを追加しました。

```ruby
class FizzBuzzList
  def find_value(&)
    @value.find(&)
  end

  def any_match?(&)
    @value.any?(&)
  end

  def all_match?(&)
    @value.all?(&)
  end
end
```

### テスト

```ruby
class FizzBuzzListTest < Minitest::Test
  def test_find_valueで条件に合う最初の要素を返す
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    result = list.find_value { |v| v.value == 'Buzz' }
    assert_equal 'Buzz', result.value
    assert_equal 5, result.number
  end

  def test_any_matchで条件に合う要素が存在するか
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    assert list.any_match? { |v| v.value == 'FizzBuzz' }
    refute list.any_match? { |v| v.value == 'Unknown' }
  end

  def test_all_matchで全要素が条件を満たすか
    type = FizzBuzzType.create(FizzBuzzType::TYPE_02)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    assert list.all_match? { |v| v.number.positive? }
    refute list.all_match? { |v| v.value == 'Fizz' }
  end
end
```

## 12.5 フィボナッチ数とベンチマーク

### フィボナッチ数の TDD

フィボナッチ数を 3 つのアルゴリズムで実装し、パフォーマンスを比較します。

#### Fibonacci::Command（Strategy パターン）

```ruby
module Fibonacci
  class Command
    def initialize(algorithm)
      @algorithm = algorithm
    end

    def exec(number)
      @algorithm.exec(number)
    end
  end
end
```

#### 再帰（メモ化あり）

```ruby
module Fibonacci
  class Recursive
    def exec(number, memo = {})
      return 0 if number.zero?
      return 1 if number == 1

      memo[number] ||= exec(number - 1, memo) + exec(number - 2, memo)
    end
  end
end
```

#### ループ

```ruby
module Fibonacci
  class Loop
    def exec(number)
      a = 0
      b = 1
      c = 0
      (0...number).each do |_i|
        a = b
        b = c
        c = a + b
      end
      c
    end
  end
end
```

#### 一般項（数学公式）

```ruby
module Fibonacci
  class GeneralTerm
    def exec(number)
      a = ((1 + Math.sqrt(5)) / 2)**number
      b = ((1 - Math.sqrt(5)) / 2)**number
      ((a - b) / Math.sqrt(5)).round
    end
  end
end
```

### テスト

```ruby
class FibonacciTest < Minitest::Test
  def setup
    @recursive = Fibonacci::Command.new(Fibonacci::Recursive.new)
    @loop = Fibonacci::Command.new(Fibonacci::Loop.new)
    @general_term = Fibonacci::Command.new(Fibonacci::GeneralTerm.new)
  end

  def test_fibonacci_基本ケース
    cases = [[0, 0], [1, 1], [2, 1], [3, 2], [4, 3], [5, 5]]
    cases.each do |input, expected|
      assert_equal expected, @recursive.exec(input)
      assert_equal expected, @loop.exec(input)
      assert_equal expected, @general_term.exec(input)
    end
  end

  def test_fibonacci_再帰_大きな数
    assert_equal 102_334_155, @recursive.exec(40)
  end

  def test_fibonacci_ループ_大きな数
    assert_equal 102_334_155, @loop.exec(40)
  end

  def test_fibonacci_一般項_大きな数
    assert_equal 102_334_155, @general_term.exec(40)
  end
end
```

### ベンチマーク

`Minitest::Benchmark` で各アルゴリズムのパフォーマンスを測定します。

```ruby
class FibonacciBenchmark < Minitest::Benchmark
  def setup
    @recursive = Fibonacci::Command.new(Fibonacci::Recursive.new)
    @loop = Fibonacci::Command.new(Fibonacci::Loop.new)
    @general_term = Fibonacci::Command.new(Fibonacci::GeneralTerm.new)
  end

  def bench_recursive
    assert_performance_constant do |_n|
      1000.times { |i| @recursive.exec(i) }
    end
  end

  def bench_loop
    assert_performance_constant do |_n|
      1000.times { |i| @loop.exec(i) }
    end
  end

  def bench_general_term
    assert_performance_constant do |_n|
      1000.times { |i| @general_term.exec(i) }
    end
  end
end
```

### ベンチマーク結果

```bash
$ bundle exec rake benchmark
bench_general_term	 0.001057
bench_loop	 0.162474
bench_recursive	 0.449281
```

一般項が最速で、再帰の約 400 倍高速です。

| アルゴリズム | 計算量 | 特徴 |
|------------|--------|------|
| 再帰（メモ化） | O(n) | 理解しやすいが、メモ化が必要 |
| ループ | O(n) | メモリ効率が良い |
| 一般項 | O(1) | 数学公式で最速、大きな数では誤差の可能性 |

## 12.6 ディレクトリ構成

### Fibonacci モジュール

```
lib/
├── fizz_buzz/        (既存)
└── fibonacci/
    ├── fibonacci.rb  (バレルファイル)
    ├── command.rb    (Strategy パターン)
    ├── recursive.rb  (再帰アルゴリズム)
    ├── loop.rb       (ループアルゴリズム)
    └── general_term.rb (一般項アルゴリズム)

test/
├── fizz_buzz/        (既存 + FP メソッドのテスト追加)
└── fibonacci/
    ├── fibonacci_test.rb       (ユニットテスト)
    └── fibonacci_benchmark.rb  (ベンチマーク)
```

### テスト実行結果

```bash
$ bundle exec rake test
39 tests, 72 assertions, 0 failures, 0 errors, 0 skips
Coverage report generated for Unit Tests.
Line Coverage: 95.95% (142 / 148)
Branch Coverage: 100.0% (20 / 20)
```

## 12.7 各言語のエラーハンドリング比較

| 概念 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| パターンマッチング | `case/in` + `deconstruct_keys` | `switch` (JDK 21+) | Type Guards | `match/case` (3.10+) |
| 安全なファクトリ | `try_create` → `nil` | `Optional` | `T \| undefined` | `None` |
| null 安全 | `nil` + `&.`（safe navigation） | `Optional` | Union Types | `Optional` typing |
| 列挙型 | Symbol / Module 定数 | `enum` | `enum` | `Enum` |
| ベンチマーク | `Minitest::Benchmark` | JMH | カスタム | `timeit` |

## 12.8 第 4 部のまとめ

第 4 部（章 10〜12）を通じて、OOP の FizzBuzz に関数型プログラミングの要素を追加しました。

| 章 | テーマ | 適用した技術 |
|---|--------|-------------|
| 10 | 高階関数と関数合成 | ブロック/Proc/Lambda、カリー化、`>>` 合成 |
| 11 | 不変データとパイプライン | `freeze`、`then` パイプライン、`tally`、Lazy |
| 12 | エラーハンドリングと型安全性 | パターンマッチング、`try_create`、ベンチマーク |

### 全 12 章の学習体系

| 部 | テーマ | 章 |
|---|--------|---|
| 第 1 部 | TDD の基本サイクル | 章 1〜3: TODO リスト、仮実装と三角測量、明白な実装 |
| 第 2 部 | 開発環境と自動化 | 章 4〜6: バージョン管理、パッケージ管理、タスクランナー |
| 第 3 部 | オブジェクト指向設計 | 章 7〜9: ポリモーフィズム、デザインパターン、SOLID |
| 第 4 部 | 関数型プログラミング | 章 10〜12: 高階関数、パイプライン、型安全性 + ベンチマーク |

Ruby の特徴であるブロック・Proc・Lambda、Enumerable Mix-in、パターンマッチング、そしてベンチマークにより、OOP と FP のハイブリッドなプログラミングスタイルを実現しました。
