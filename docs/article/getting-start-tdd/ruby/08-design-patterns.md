---
type: Article
title: "第 8 章: デザインパターンの適用"
description: "前章ではカプセル化とポリモーフィズムを使って、手続き的な条件分岐をクラス階層に置き換えました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: デザインパターンの適用

## 8.1 はじめに

前章ではカプセル化とポリモーフィズムを使って、手続き的な条件分岐をクラス階層に置き換えました。この章では、さらに多くの **デザインパターン** を適用して、コードの表現力と安全性を向上させます。

## 8.2 値オブジェクト（Value Object）

### 問題: プリミティブ型の使用

現在 `generate()` は `String` を返しています。しかし、FizzBuzz の結果には「変換前の数値」と「変換後の文字列」の 2 つの情報が含まれます。プリミティブ型では、このドメイン知識が表現できません。

### 解決: FizzBuzzValue クラス

```ruby
# frozen_string_literal: true

class FizzBuzzValue
  attr_reader :value, :number

  def initialize(value, number)
    raise ArgumentError, '値は正の値のみ許可します' if number.negative?

    @value = value
    @number = number
  end

  def ==(other)
    other.is_a?(FizzBuzzValue) && value == other.value && number == other.number
  end

  def eql?(other)
    self == other
  end

  def hash
    [value, number].hash
  end

  def to_s
    value
  end
end
```

### 値オブジェクトの特徴

| 特徴 | 実現方法 |
|------|---------|
| **不変性** | `attr_reader` のみ（setter なし） |
| **等価性** | `==` メソッドで値による比較 |
| **自己記述性** | `to_s` で文字列表現 |
| **副作用なし** | setter を持たない |
| **ハッシュキー対応** | `eql?` と `hash` を実装 |

### テスト

```ruby
class FizzBuzzValueTest < Minitest::Test
  def test_値と数値を保持する
    value = FizzBuzzValue.new('Fizz', 3)
    assert_equal 'Fizz', value.value
    assert_equal 3, value.number
  end

  def test_toStringは値を返す
    value = FizzBuzzValue.new('Buzz', 5)
    assert_equal 'Buzz', value.to_s
  end

  def test_同じ値と数値の場合equalはtrue
    v1 = FizzBuzzValue.new('Fizz', 3)
    v2 = FizzBuzzValue.new('Fizz', 3)
    assert_equal v1, v2
  end

  def test_異なる値の場合equalはfalse
    v1 = FizzBuzzValue.new('Fizz', 3)
    v2 = FizzBuzzValue.new('Buzz', 5)
    refute_equal v1, v2
  end

  def test_負の値はエラー
    assert_raises ArgumentError do
      FizzBuzzValue.new('Fizz', -1)
    end
  end
end
```

### FizzBuzzType の更新

`generate` メソッドの戻り値を `String` から `FizzBuzzValue` に変更します。

```ruby
class FizzBuzzType01 < FizzBuzzType
  def generate(number)
    return FizzBuzzValue.new('FizzBuzz', number) if fizz?(number) && buzz?(number)
    return FizzBuzzValue.new('Fizz', number) if fizz?(number)
    return FizzBuzzValue.new('Buzz', number) if buzz?(number)

    FizzBuzzValue.new(number.to_s, number)
  end
end
```

## 8.3 ファーストクラスコレクション（First-Class Collection）

### 問題: 生の配列の使用

`generate_list()` が `Array` を返すと、コレクションに対する操作が外部に散らばります。

### 解決: FizzBuzzList クラス

```ruby
# frozen_string_literal: true

class FizzBuzzList
  MAX_COUNT = 100

  include Enumerable

  attr_reader :value

  def initialize(list = [])
    raise "上限は#{MAX_COUNT}件までです" if list.count > MAX_COUNT

    @value = list.dup.freeze
  end

  def add(other_list)
    FizzBuzzList.new(@value + other_list)
  end

  def size
    @value.size
  end

  def to_string_array
    @value.map(&:to_s)
  end

  def each(&)
    @value.each(&)
  end
end
```

### ファーストクラスコレクションの特徴

| 特徴 | 実現方法 |
|------|---------|
| **不変性** | `dup.freeze` で凍結、`add` は新インスタンスを返す |
| **カプセル化** | コレクション操作をクラス内に集約 |
| **イテレータ** | `Enumerable` の Mix-in で `each`, `map` 等を提供 |
| **上限管理** | `MAX_COUNT` で件数を制限 |

### テスト

```ruby
class FizzBuzzListTest < Minitest::Test
  def test_空リストを生成できる
    list = FizzBuzzList.new
    assert_equal 0, list.size
  end

  def test_addで新しいリストを返す
    list = FizzBuzzList.new
    values = [FizzBuzzValue.new('1', 1)]
    new_list = list.add(values)
    assert_equal 0, list.size
    assert_equal 1, new_list.size
  end

  def test_to_string_arrayで文字列配列を返す
    values = [
      FizzBuzzValue.new('1', 1),
      FizzBuzzValue.new('2', 2),
      FizzBuzzValue.new('Fizz', 3)
    ]
    list = FizzBuzzList.new(values)
    assert_equal %w[1 2 Fizz], list.to_string_array
  end

  def test_上限を超えるとエラー
    values = (1..101).map { |n| FizzBuzzValue.new(n.to_s, n) }
    assert_raises RuntimeError do
      FizzBuzzList.new(values)
    end
  end
end
```

## 8.4 コマンドパターン（Command Pattern）

### 問題: 操作の直接実行

`FizzBuzz` クラスが「値の生成」と「リストの生成」という複数の操作を直接持っていました。

### 解決: FizzBuzzCommand

操作をオブジェクトとして表現します。Ruby ではモジュールをインターフェースとして使います。

```ruby
# frozen_string_literal: true

module FizzBuzzCommand
  def execute
    raise NotImplementedError
  end
end
```

### FizzBuzzValueCommand

```ruby
# frozen_string_literal: true

class FizzBuzzValueCommand
  include FizzBuzzCommand

  def initialize(type, number)
    @type = type
    @number = number
  end

  def execute
    @type.generate(@number)
  end
end
```

### FizzBuzzListCommand

```ruby
# frozen_string_literal: true

class FizzBuzzListCommand
  include FizzBuzzCommand

  def initialize(type, count = 100)
    @type = type
    @count = count
  end

  def execute
    list = (1..@count).map { |number| @type.generate(number) }
    FizzBuzzList.new(list)
  end
end
```

### テスト

```ruby
class FizzBuzzCommandTest < Minitest::Test
  def test_FizzBuzzValueCommandは単一の値を生成する
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzValueCommand.new(type, 3)
    result = command.execute
    assert_equal 'Fizz', result.to_s
  end

  def test_FizzBuzzListCommandはリストを生成する
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 100)
    result = command.execute
    arr = result.to_string_array

    assert_equal 100, result.size
    assert_equal 'Fizz', arr[2]
    assert_equal 'Buzz', arr[4]
    assert_equal 'FizzBuzz', arr[14]
  end

  def test_デフォルトで100件のリストを生成する
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type)
    result = command.execute
    assert_equal 100, result.size
  end
end
```

### コマンドパターンの利点

- **操作の具象化**: 「何をするか」をオブジェクトで表現
- **パラメータの保持**: 実行に必要な情報をコマンド内に保持
- **実行の分離**: 操作の「定義」と「実行」を分離
- **拡張性**: 新しい操作は新しいコマンドクラスを追加するだけ

## 8.5 リファクタリング後のクラス構造

### 適用したデザインパターン一覧

| パターン | クラス | 役割 |
|---------|--------|------|
| **Value Object** | `FizzBuzzValue` | 不変の値を表現 |
| **First-Class Collection** | `FizzBuzzList` | コレクション操作のカプセル化 |
| **Strategy** | `FizzBuzzType` + サブクラス | アルゴリズムの交換 |
| **Factory Method** | `FizzBuzzType.create()` | インスタンス生成の集約 |
| **Command** | `FizzBuzzCommand` + 実装クラス | 操作のオブジェクト化 |

### テスト実行結果

```bash
$ bundle exec rake test
22 tests, 27 assertions, 0 failures, 0 errors, 0 skips
```

## 8.6 各言語のデザインパターン比較

| パターン | Ruby | Java | TypeScript | Python |
|---------|------|------|-----------|--------|
| Value Object | `attr_reader` + `==` | `final` + `equals()` / `hashCode()` | `readonly` + `equals()` | `__eq__()` / `__hash__()` |
| Collection | `Enumerable` + `freeze` | `Collections.unmodifiableList()` | `Object.freeze()` + `Symbol.iterator` | `tuple` / `frozenset` |
| Interface | `module`（Mix-in） | `interface` | `interface` | `Protocol` / `ABC` |
| Command | `include FizzBuzzCommand` | `implements FizzBuzzCommand` | `implements FizzBuzzCommand` | ABC + `@abstractmethod` |

## 8.7 まとめ

この章で学んだこと:

1. **値オブジェクト**: プリミティブ型をドメイン固有のオブジェクトで置き換え、型安全性と表現力を向上
2. **ファーストクラスコレクション**: 配列操作をカプセル化し、`Enumerable` Mix-in で Ruby らしいイテレーションを実現
3. **コマンドパターン**: 操作をオブジェクトとして表現し、実行の柔軟性を確保
4. **ファクトリメソッド**: インスタンス生成ロジックを集約

次の章では、SOLID 原則の観点からコードを検証し、モジュール構造に再編成します。
