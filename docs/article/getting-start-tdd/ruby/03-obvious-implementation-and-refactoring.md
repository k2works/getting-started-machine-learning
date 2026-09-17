---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成とプリント）を実装し、学習用テストを活用しながら「動作するきれいなコード」を目指してリファクタリングします。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から 100 までの FizzBuzz の結果をリストとして返すメソッドをテストします。

```ruby
def test_1から100までの数の配列を返す
  result = @fizzbuzz.generate_list
  assert_equal 100, result.length
  assert_equal '1', result[0]
  assert_equal '2', result[1]
  assert_equal 'Fizz', result[2]
  assert_equal '4', result[3]
  assert_equal 'Buzz', result[4]
  assert_equal 'FizzBuzz', result[14]
end
```

```bash
$ bundle exec rake test
ERROR["test_1から100までの数の配列を返す", FizzBuzzTest]
 NoMethodError: undefined method 'generate_list' for FizzBuzz:Class
```

### Green: 明白な実装

Ruby の `Range` と `map` メソッドを使って、1 から 100 までの数を FizzBuzz に変換したリストを返します。

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    return 'FizzBuzz' if (number % 15).zero?
    return 'Fizz' if (number % 3).zero?
    return 'Buzz' if (number % 5).zero?

    number.to_s
  end

  def self.generate_list
    (1..100).map { |n| generate(n) }
  end
end
```

```bash
$ bundle exec rake test
6 tests, 12 assertions, 0 failures, 0 errors, 0 skips
```

`(1..100)` は Ruby の `Range` オブジェクトで、`map` メソッドは各要素にブロックを適用して新しい配列を返します。Java の `IntStream.rangeClosed(1, 100).mapToObj(...)` や Python のリスト内包表記 `[generate(n) for n in range(1, 101)]` に相当します。

> 明白な実装
>
> シンプルな操作を実現するにはどうすればいいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [ ] プリントする

## 3.3 プリント機能

### 学習用テスト

プリント機能は、生成したリストの各要素を標準出力に出力するものです。学習用テストとして、`$stdout` のリダイレクトを試します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

Ruby では `StringIO` を使って標準出力をキャプチャできます。

```ruby
require 'stringio'

def test_プリントする
  out = StringIO.new
  $stdout = out
  @fizzbuzz.generate_list.each { |item| puts item }
  $stdout = STDOUT
  output = out.string
  assert_includes output, '1'
  assert_includes output, 'Fizz'
  assert_includes output, 'Buzz'
  assert_includes output, 'FizzBuzz'
end
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

## 3.4 リファクタリング

テスト駆動開発の流れを確認しておきましょう。

> 1. レッド：動作しない、おそらく最初のうちはコンパイルも通らないテストを 1 つ書く。
> 2. グリーン：そのテストを迅速に動作させる。このステップでは罪を犯してもよい。
> 3. リファクタリング：テストを通すために発生した重複をすべて除去する。
>
> レッド・グリーン・リファクタリング。それが TDD のマントラだ。
>
> — テスト駆動開発

### テストコードのリファクタリング

テストコードに重複はないでしょうか？`setup` メソッドで `FizzBuzz` クラスの参照を共有しているので、すでに一定のリファクタリングができています。

現在のテストコード全体を確認します。

```ruby
# test/fizz_buzz_test.rb
require_relative 'test_helper'
require_relative '../lib/fizz_buzz'

class FizzBuzzTest < Minitest::Test
  def setup
    @fizzbuzz = FizzBuzz
  end

  def test_1を渡したら文字列1を返す
    assert_equal '1', @fizzbuzz.generate(1)
  end

  def test_2を渡したら文字列2を返す
    assert_equal '2', @fizzbuzz.generate(2)
  end

  def test_3を渡したらFizzを返す
    assert_equal 'Fizz', @fizzbuzz.generate(3)
  end

  def test_5を渡したらBuzzを返す
    assert_equal 'Buzz', @fizzbuzz.generate(5)
  end

  def test_15を渡したらFizzBuzzを返す
    assert_equal 'FizzBuzz', @fizzbuzz.generate(15)
  end

  def test_1から100までの数の配列を返す
    result = @fizzbuzz.generate_list
    assert_equal 100, result.length
    assert_equal '1', result[0]
    assert_equal '2', result[1]
    assert_equal 'Fizz', result[2]
    assert_equal '4', result[3]
    assert_equal 'Buzz', result[4]
    assert_equal 'FizzBuzz', result[14]
  end
end
```

各テストメソッドは独立しており、1 テスト 1 アサーション（リスト生成テストを除く）の原則に従っています。テストの本質は「こういう状況と入力から、こういう振る舞いと出力を期待する」のレベルまで要約できます。

### プロダクションコードの確認

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    return 'FizzBuzz' if (number % 15).zero?
    return 'Fizz' if (number % 3).zero?
    return 'Buzz' if (number % 5).zero?

    number.to_s
  end

  def self.generate_list
    (1..100).map { |n| generate(n) }
  end
end
```

プロダクションコードは十分にシンプルで、リファクタリングの必要はありません。Ruby のガード節スタイル（`return ... if`）は、Java の `if-else if-else` チェーンよりも読みやすく簡潔です。

## 3.5 他言語との比較

| 概念 | Java | Python | TypeScript | Ruby |
|------|------|--------|-----------|------|
| テストフレームワーク | JUnit 5 | pytest | Vitest | Minitest |
| テスト実行 | `./gradlew test` | `pytest` | `npx vitest` | `bundle exec rake test` |
| 文字列変換 | `String.valueOf(n)` | `str(n)` | `n.toString()` | `n.to_s` |
| 剰余判定 | `n % 3 == 0` | `n % 3 == 0` | `n % 3 === 0` | `(n % 3).zero?` |
| リスト生成 | `IntStream.rangeClosed` | `[f(n) for n in range]` | `Array.from({length})` | `(1..100).map { }` |
| ガード節 | `if (...) return` | `if ...: return` | `if (...) return` | `return ... if` |

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装** でシンプルな操作をそのまま実装する手法
- Ruby の `Range` オブジェクトと `map` メソッドによるリスト生成
- `StringIO` を使った標準出力のキャプチャ（学習用テスト）
- **リファクタリング** でテストを通すために発生した重複を除去する考え方
- Red-Green-Refactor サイクルの完了

第 1 部の 3 章を通じて、TDD の基本サイクル（仮実装 → 三角測量 → 明白な実装 → リファクタリング）を一通り体験しました。次の第 2 部では、開発環境の自動化（バージョン管理、パッケージ管理、CI/CD）に進みます。
