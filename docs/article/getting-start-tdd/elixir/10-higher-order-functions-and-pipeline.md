---
type: Article
title: "第 10 章: 高階関数とパイプライン演算子"
description: "この章では、Elixir の高階関数を使って、データ変換を宣言的に記述する方法を学びます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数とパイプライン演算子

## 10.1 はじめに

この章では、Elixir の高階関数を使って、データ変換を宣言的に記述する方法を学びます。`Enum.map`、`Enum.filter`、`Enum.reduce`、無名関数、`|>` パイプライン演算子を組み合わせ、FizzBuzz の実装を拡張します。

## 10.2 高階関数の基本: Enum.map / Enum.filter / Enum.reduce

高階関数は、関数を引数として受け取る関数です。Elixir では `Enum` モジュールが代表例です。

```elixir
defmodule FizzBuzz.Functional do
  def generate(number) when rem(number, 15) == 0, do: "FizzBuzz"
  def generate(number) when rem(number, 3) == 0, do: "Fizz"
  def generate(number) when rem(number, 5) == 0, do: "Buzz"
  def generate(number), do: Integer.to_string(number)

  def generate_list(limit) do
    1..limit
    |> Enum.map(&generate/1)
  end

  def count_fizzbuzz(limit) do
    1..limit
    |> Enum.map(&generate/1)
    |> Enum.filter(&(&1 in ["Fizz", "Buzz", "FizzBuzz"]))
    |> Enum.count()
  end

  def summary(limit) do
    1..limit
    |> Enum.map(&generate/1)
    |> Enum.reduce(%{fizz: 0, buzz: 0, fizzbuzz: 0, number: 0}, fn value, acc ->
      case value do
        "Fizz" -> %{acc | fizz: acc.fizz + 1}
        "Buzz" -> %{acc | buzz: acc.buzz + 1}
        "FizzBuzz" -> %{acc | fizzbuzz: acc.fizzbuzz + 1}
        _ -> %{acc | number: acc.number + 1}
      end
    end)
  end
end
```

## 10.3 無名関数: fn と & キャプチャ演算子

Elixir には 2 つの無名関数の書き方があります。

```elixir
defmodule FizzBuzz.Anonymous do
  def multiply_all(numbers, factor) do
    Enum.map(numbers, fn n -> n * factor end)
  end

  def square_all(numbers) do
    Enum.map(numbers, &(&1 * &1))
  end

  def stringify(numbers) do
    Enum.map(numbers, &Integer.to_string/1)
  end
end
```

- `fn n -> ... end` は複雑なロジックに向いています。
- `&(&1 * &1)` のようなキャプチャ演算子は短く書けます。

## 10.4 パイプライン演算子 |> で関数チェーンを書く

`|>` は左側の値を右側関数の第 1 引数に渡します。処理の流れを上から下に読めるため、可読性が上がります。

```elixir
defmodule FizzBuzz.Pipeline do
  alias FizzBuzz.Functional

  def report(limit) do
    1..limit
    |> Enum.map(&Functional.generate/1)
    |> Enum.filter(&(&1 != "FizzBuzz"))
    |> Enum.join(",")
  end
end
```

## 10.5 generate_with/2 でカスタムルール関数を受け取る

高階関数を使うと、ルールを外部から注入できます。`generate_with/2` は「数値」と「ルール関数」を受け取ります。

```elixir
defmodule FizzBuzz.CustomRule do
  def generate_with(number, rule_fn) when is_function(rule_fn, 1) do
    case rule_fn.(number) do
      nil -> Integer.to_string(number)
      value -> value
    end
  end

  def generate_with(number, rule_fns) when is_list(rule_fns) do
    result =
      Enum.reduce_while(rule_fns, nil, fn rule_fn, _acc ->
        case rule_fn.(number) do
          nil -> {:cont, nil}
          value -> {:halt, value}
        end
      end)

    result || Integer.to_string(number)
  end
end
```

## 10.6 transform/2 と filter/2 で汎用化する

FizzBuzz 専用処理を汎用関数に切り出すと、再利用しやすくなります。

```elixir
defmodule FizzBuzz.Collection do
  def transform(values, mapper) when is_function(mapper, 1) do
    Enum.map(values, mapper)
  end

  def filter(values, predicate) when is_function(predicate, 1) do
    Enum.filter(values, predicate)
  end
end
```

## 10.7 ExUnit で高階関数を検証する

```elixir
defmodule FizzBuzz.HigherOrderFunctionsTest do
  use ExUnit.Case, async: true

  alias FizzBuzz.{Collection, CustomRule, Functional}

  test "Enum.map で FizzBuzz の一覧を生成できる" do
    assert Functional.generate_list(5) == ["1", "2", "Fizz", "4", "Buzz"]
  end

  test "Enum.filter と Enum.count で Fizz/Buzz 系だけ数えられる" do
    assert Functional.count_fizzbuzz(15) == 7
  end

  test "Enum.reduce で種別ごとの集計ができる" do
    assert Functional.summary(15) == %{fizz: 4, buzz: 2, fizzbuzz: 1, number: 8}
  end

  test "generate_with/2 は単一ルール関数を受け取れる" do
    even_rule = fn n -> if rem(n, 2) == 0, do: "Even", else: nil end

    assert CustomRule.generate_with(4, even_rule) == "Even"
    assert CustomRule.generate_with(3, even_rule) == "3"
  end

  test "generate_with/2 はルール関数リストを順に適用する" do
    rules = [
      fn n -> if rem(n, 7) == 0, do: "Pop", else: nil end,
      fn n -> if rem(n, 3) == 0, do: "Fizz", else: nil end
    ]

    assert CustomRule.generate_with(14, rules) == "Pop"
    assert CustomRule.generate_with(9, rules) == "Fizz"
    assert CustomRule.generate_with(2, rules) == "2"
  end

  test "transform/2 と filter/2 を組み合わせて再利用できる" do
    values = 1..6 |> Enum.to_list()

    assert values
           |> Collection.transform(&(&1 * 10))
           |> Collection.filter(&(&1 > 30)) == [40, 50, 60]
  end
end
```

## 10.8 まとめ

この章では、高階関数、無名関数、`|>` パイプライン演算子を使って処理を小さな関数へ分解し、合成する方法を確認しました。`generate_with/2`、`transform/2`、`filter/2` のように関数を受け取る設計により、仕様変更に強い実装を作れます。
