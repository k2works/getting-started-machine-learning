---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "第 3 部ではオブジェクト指向設計により FizzBuzz を構造化しました。"
tags: [article, getting-start-tdd, ruby]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

第 3 部ではオブジェクト指向設計により FizzBuzz を構造化しました。この第 4 部では、Ruby の関数型プログラミング機能を使ってコードをさらに柔軟にします。

Ruby はオブジェクト指向言語ですが、**ブロック**・**Proc**・**Lambda** という強力な関数型プリミティブを備えています。本章ではこれらを使って FizzBuzz に高階関数と関数合成を導入します。

## 10.2 ブロック・Proc・Lambda

### ブロック

Ruby のブロックは `{}` または `do...end` で記述する無名のコードブロックです。

```ruby
# ブロックの基本
[1, 2, 3].each { |n| puts n }

# do...end 形式
[1, 2, 3].each do |n|
  puts n
end
```

### Proc

ブロックをオブジェクトとして扱うには `Proc` を使います。

```ruby
square = Proc.new { |n| n * n }
square.call(5)  # => 25

# Proc は引数の数が合わなくてもエラーにならない
square.call(5, 10)  # => 25（余分な引数を無視）
```

### Lambda

`Lambda` は厳密な Proc です。引数の数をチェックし、`return` の挙動も異なります。

```ruby
square = ->(n) { n * n }
square.call(5)  # => 25

# Lambda は引数の数が合わないとエラー
# square.call(5, 10)  # => ArgumentError
```

### 比較

| 特徴 | ブロック | Proc | Lambda |
|------|---------|------|--------|
| オブジェクト | No | Yes | Yes |
| 引数チェック | なし | 緩い | 厳密 |
| return の挙動 | 呼び出し元から返る | 呼び出し元から返る | Lambda 内から返る |
| 生成方法 | `{}` / `do...end` | `Proc.new {}` | `->() {}` |

## 10.3 FizzBuzzList への関数型メソッド追加

### 問題: Enumerable の返り値

`FizzBuzzList` は `Enumerable` を include しているため、`select` や `map` が使えますが、戻り値は `Array` になります。メソッドチェーンで `FizzBuzzList` を維持するには、専用メソッドが必要です。

### 解決: FizzBuzzList を返すメソッド

```ruby
class FizzBuzzList
  # 条件に合う要素だけの FizzBuzzList を返す
  def select_type(&)
    FizzBuzzList.new(@value.select(&))
  end

  # 条件に合わない要素だけの FizzBuzzList を返す
  def reject_type(&)
    FizzBuzzList.new(@value.reject(&))
  end

  # 変換結果の FizzBuzzList を返す
  def map_type(&)
    FizzBuzzList.new(@value.map(&))
  end
end
```

Ruby 3.x では `&` だけでブロック引数を転送できます（anonymous block forwarding）。

### テスト

```ruby
class FizzBuzzListTest < Minitest::Test
  def test_select_typeでFizzBuzzListを返す
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    result = list.select_type { |v| v.value == 'Fizz' }
    assert_instance_of FizzBuzzList, result
    assert_equal 4, result.size  # 3,6,9,12（15はFizzBuzz）
  end

  def test_reject_typeでFizzBuzzListを返す
    type = FizzBuzzType.create(FizzBuzzType::TYPE_01)
    command = FizzBuzzListCommand.new(type, 15)
    list = command.execute
    result = list.reject_type { |v| v.value == 'Fizz' }
    assert_instance_of FizzBuzzList, result
    assert_equal 11, result.size
  end
end
```

## 10.4 述語関数と関数合成

### 述語 Lambda

条件を Lambda として定義すると再利用可能になります。

```ruby
is_fizz = ->(v) { v.value == 'Fizz' }
is_buzz = ->(v) { v.value == 'Buzz' }
is_fizz_buzz = ->(v) { v.value == 'FizzBuzz' }

# 使用例
list.select_type(&is_fizz)
list.select_type(&is_buzz)
```

### 関数合成（`>>` と `<<`）

Ruby 2.6 以降、Proc/Lambda に `>>` と `<<` 演算子で関数合成ができます。

```ruby
# >> は左から右へ合成（pipe）
double = ->(n) { n * 2 }
add_one = ->(n) { n + 1 }

double_then_add = double >> add_one
double_then_add.call(5)  # => 11（5*2=10, 10+1=11）

# << は右から左へ合成（compose）
add_then_double = double << add_one
add_then_double.call(5)  # => 12（5+1=6, 6*2=12）
```

### カリー化

```ruby
# カリー化で部分適用
multiply = ->(a, b) { a * b }
double = multiply.curry.(2)  # a=2 で部分適用
double.call(5)  # => 10

triple = multiply.curry.(3)  # a=3 で部分適用
triple.call(5)  # => 15
```

## 10.5 各言語の高階関数比較

| 概念 | Ruby | Java | TypeScript | Python |
|------|------|------|-----------|--------|
| 無名関数 | `->() {}` / ブロック | Lambda 式 | アロー関数 | `lambda` |
| 高階関数 | `map` / `select` / `inject` | `Stream` API | `map` / `filter` / `reduce` | `map()` / `filter()` / `reduce()` |
| 関数合成 | `>>` / `<<` | `Function.compose()` / `andThen()` | カスタム `compose`/`pipe` | `functools.reduce` |
| カリー化 | `.curry` | 手動実装 | 手動実装 | `functools.partial` |
| 部分適用 | `.curry.(arg)` | 手動クロージャ | 手動クロージャ | `functools.partial` |

## 10.6 まとめ

この章で学んだこと:

1. **ブロック・Proc・Lambda**: Ruby の 3 つの関数型プリミティブの違いと使い分け
2. **FizzBuzzList の関数型メソッド**: `select_type`、`reject_type`、`map_type` で FizzBuzzList を返すメソッドチェーン
3. **述語 Lambda**: 条件を Lambda として定義し再利用
4. **関数合成**: `>>` / `<<` 演算子による合成とカリー化

次の章では、不変データとパイプライン処理を学びます。
