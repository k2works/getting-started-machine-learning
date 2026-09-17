---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "この章では FizzBuzz の仕様を cond で明白に実装し、1 から 100 までのリスト生成まで完成させます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

この章では FizzBuzz の仕様を `cond` で明白に実装し、1 から 100 までのリスト生成まで完成させます。最後に、読みやすさを高めるリファクタリングの観点を確認します。

## 3.2 cond による明白な実装

まずは必要なテストを追加します。

```elixir
# test/fizz_buzz_test.exs

defmodule FizzBuzzTest do
  use ExUnit.Case

  describe "generate/1" do
    test "5 の倍数を渡すと Buzz を返す" do
      assert FizzBuzz.generate(5) == "Buzz"
    end

    test "15 の倍数を渡すと FizzBuzz を返す" do
      assert FizzBuzz.generate(15) == "FizzBuzz"
    end
  end
end
```

このテストを通す明白な実装は次のとおりです。

```elixir
# lib/fizz_buzz.ex

defmodule FizzBuzz do
  def generate(n) do
    cond do
      rem(n, 15) == 0 -> "FizzBuzz"
      rem(n, 3) == 0 -> "Fizz"
      rem(n, 5) == 0 -> "Buzz"
      true -> Integer.to_string(n)
    end
  end
end
```

15 を先に判定することがポイントです。

## 3.3 1 から 100 までのリスト生成

続いて、結果一覧を作る関数のテストを書きます。

```elixir
# test/fizz_buzz_test.exs

defmodule FizzBuzzTest do
  use ExUnit.Case

  describe "generate_list/0" do
    test "1 から 100 までの FizzBuzz リストを返す" do
      result = FizzBuzz.generate_list()

      assert length(result) == 100
      assert Enum.at(result, 0) == "1"
      assert Enum.at(result, 2) == "Fizz"
      assert Enum.at(result, 4) == "Buzz"
      assert Enum.at(result, 14) == "FizzBuzz"
    end
  end
end
```

実装はパイプライン演算子 `|>` を使うと読みやすくなります。

```elixir
# lib/fizz_buzz.ex

defmodule FizzBuzz do
  def generate(n) do
    cond do
      rem(n, 15) == 0 -> "FizzBuzz"
      rem(n, 3) == 0 -> "Fizz"
      rem(n, 5) == 0 -> "Buzz"
      true -> Integer.to_string(n)
    end
  end

  def generate_list do
    1..100
    |> Enum.map(&generate/1)
  end
end
```

`|>` は「左の結果を右の関数に渡す」演算子です。データの流れを上から下に読めるため、処理の意図が明確になります。

## 3.4 リファクタリングのポイント

テストがすべて通る状態を保ちながら、次を確認します。

- 判定順が明確か（`15` → `3` → `5`）
- 重複した変換処理がないか
- 関数名が役割を正しく表しているか
- テストの責務が分離されているか（`describe` ごとに関心を分ける）

必要なら、テストデータを増やして安全に整理します。TDD では「動くこと」と「読みやすいこと」を両立させます。

## 3.5 まとめ

この章では、`cond` を使った明白な実装で `Buzz` と `FizzBuzz` を追加し、`1..100` のリスト生成を完成させました。さらに `|>` の基本と、テストを守りながら進めるリファクタリングの観点を確認しました。
