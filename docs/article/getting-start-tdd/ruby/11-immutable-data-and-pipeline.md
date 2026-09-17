---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
description: "前章では高階関数と関数合成を学びました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 はじめに

前章では高階関数と関数合成を学びました。この章では、**不変データ** の原則と **パイプライン処理** のテクニックを適用して、FizzBuzz のデータ処理をより宣言的にします。

## 11.2 不変データの原則

### freeze による不変性

Ruby の `freeze` メソッドはオブジェクトの変更を禁止します。FizzBuzzList では既にこの原則を適用しています。

```ruby
class FizzBuzzList
  def initialize(list = [])
    @value = list.dup.freeze  # 配列を複製して凍結
  end
end
```

`dup.freeze` のパターンにより:


- `dup` で元の配列を変更しない
- `freeze` で内部配列の変更を禁止

### frozen_string_literal

Ruby では `# frozen_string_literal: true` マジックコメントにより、ファイル内のすべての文字列リテラルが自動的に凍結されます。

```ruby
# frozen_string_literal: true

str = "hello"
str << " world"  # => FrozenError
```

### FizzBuzzValue の不変性

`FizzBuzzValue` は `attr_reader` のみ（setter なし）で不変性を実現しています。

```ruby
class FizzBuzzValue
  attr_reader :value, :number  # getter のみ

  def initialize(value, number)
    @value = value
    @number = number
  end
  # setter メソッドは定義しない
end
```

## 11.3 パイプライン処理メソッド

### group_by_value

値ごとにグループ化します。

```ruby
class FizzBuzzList
  def group_by_value
    @value.group_by(&:value)
  end
end
```

```ruby
# 使用例
type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
command = FizzBuzzListCommand.new(type, 15)
list = command.execute
result = list.group_by_value
# => { "1" => [FizzBuzzValue(1,1)],
#      "2" => [FizzBuzzValue(2,2)],
#      "Fizz" => [FizzBuzzValue(Fizz,3), ...],
#      ... }
```

### tally_by_value

値ごとの件数を集計します。Ruby の `tally` メソッドを活用します。

```ruby
class FizzBuzzList
  def tally_by_value
    @value.map(&:value).tally
  end
end
```

```ruby
# 使用例
result = list.tally_by_value
# => { "1" => 1, "2" => 1, "Fizz" => 4, "4" => 1, "Buzz" => 2, ... "FizzBuzz" => 1 }
```

### take_values / join_values

```ruby
class FizzBuzzList
  def take_values(count)
    FizzBuzzList.new(@value.take(count))
  end

  def join_values(separator = ', ')
    @value.join(separator)
  end
end
```

### テスト

```ruby
class FizzBuzzListTest < Minitest::Test
  def test_take_valuesで先頭N件を返す
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    result = list.take_values(3)
    assert_instance_of FizzBuzzList, result
    assert_equal 3, result.size
  end

  def test_group_by_valueで値ごとにグループ化
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    result = list.group_by_value
    assert_instance_of Hash, result
    assert result.key?('Fizz')
    assert result.key?('Buzz')
    assert result.key?('FizzBuzz')
  end

  def test_tally_by_valueで値ごとの件数を返す
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    result = list.tally_by_value
    assert_equal 1, result['FizzBuzz']
    assert_equal 4, result['Fizz']
    assert_equal 2, result['Buzz']
  end

  def test_join_valuesで文字列結合
    values = [
      FizzBuzzValue.new('1', 1),
      FizzBuzzValue.new('2', 2),
      FizzBuzzValue.new('Fizz', 3)
    ]
    list = FizzBuzzList.new(values)
    assert_equal '1, 2, Fizz', list.join_values
  end
end
```

## 11.4 then によるパイプライン

Ruby 2.6 以降、`then`（または `yield_self`）でパイプライン処理が書けます。

### FizzBuzzListCommand のリファクタリング

```ruby
class FizzBuzzListCommand
  def execute
    (1..@count)
      .map { |number| @type.generate(number) }
      .then { |list| FizzBuzzList.new(list) }
  end
end
```

`then` を使うことで、処理の流れが上から下へ自然に読めます:


1. `(1..@count)` — 1 から count の Range を作成
2. `.map { ... }` — 各数値を FizzBuzzValue に変換
3. `.then { ... }` — 結果の配列を FizzBuzzList でラップ

### メソッドチェーンの活用

```ruby
# パイプライン処理の例
type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
command = FizzBuzzListCommand.new(type, 100)
result = command.execute
  .select_type { |v| v.value == 'Fizz' }
  .take_values(5)
  .join_values(', ')
# => "Fizz, Fizz, Fizz, Fizz, Fizz"
```

## 11.5 Lazy Enumerator

Ruby の `lazy` メソッドで遅延評価が可能です。大量データの処理で効率的です。

```ruby
# 遅延評価によるフィルタリング
(1..Float::INFINITY)
  .lazy
  .select { |n| (n % 3).zero? }
  .first(5)
# => [3, 6, 9, 12, 15]

# FizzBuzz への適用例
type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
(1..Float::INFINITY)
  .lazy
  .map { |n| type.generate(n) }
  .select { |v| v.value == 'FizzBuzz' }
  .first(3)
  .map(&:number)
# => [15, 30, 45]
```

## 11.6 各言語のパイプライン比較

| 概念 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| パイプライン | `then` / メソッドチェーン | Stream API | メソッドチェーン | ジェネレータ |
| グルーピング | `group_by` | `Collectors.groupingBy` | カスタム `groupBy` | `itertools.groupby` |
| 集計 | `tally` | `Collectors.counting` | カスタム `countBy` | `Counter` |
| 結合 | `join` | `Collectors.joining` | `join` | `str.join` |
| 遅延評価 | `lazy` | `Stream`（デフォルト遅延） | ジェネレータ `function*` | ジェネレータ |
| 不変性 | `freeze` / `dup.freeze` | `final` / `unmodifiableList` | `Object.freeze` / `readonly` | `tuple` / `frozenset` |

## 11.7 まとめ

この章で学んだこと:

1. **不変データ**: `freeze`、`dup.freeze`、`frozen_string_literal` による不変性の確保
2. **パイプラインメソッド**: `group_by_value`、`tally_by_value`、`take_values`、`join_values`
3. **then パイプライン**: `then` を使った宣言的な処理の流れ
4. **Lazy Enumerator**: 遅延評価による無限シーケンスの効率的な処理

次の章では、パターンマッチングによるエラーハンドリングとベンチマークを学びます。
