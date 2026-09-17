---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では「1 を渡すと文字列 1 を返す」を仮実装で通しました。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では「1 を渡すと文字列 1 を返す」を仮実装で通しました。この章では、仮実装から三角測量へ進み、実装を一般化します。

## 2.2 仮実装の限界

前章の実装は次のようなハードコードでした。

```elixir
defmodule FizzBuzz do
  def generate(1), do: "1"
end
```

この実装は `1` 以外に対応できません。ここで新しいテストを追加して、一般化の必要性を明確にします。

## 2.3 三角測量: 2 を追加する

ExUnit の `describe` ブロックで関心ごとをまとめます。

```elixir
# test/fizz_buzz_test.exs

defmodule FizzBuzzTest do
  use ExUnit.Case

  describe "generate/1" do
    test "1 を渡すと文字列 1 を返す" do
      assert FizzBuzz.generate(1) == "1"
    end

    test "2 を渡すと文字列 2 を返す" do
      assert FizzBuzz.generate(2) == "2"
    end
  end
end
```

`mix test` を実行すると失敗します。Green にするため、仮実装から一段進めます。

```elixir
# lib/fizz_buzz.ex

defmodule FizzBuzz do
  def generate(n), do: Integer.to_string(n)
end
```

この変更で、`1` と `2` の両方が通ります。複数の具体例から実装を引き出すのが三角測量です。

## 2.4 3 の倍数で Fizz

次に、FizzBuzz の本題を追加します。

```elixir
# test/fizz_buzz_test.exs

defmodule FizzBuzzTest do
  use ExUnit.Case

  describe "generate/1" do
    test "1 を渡すと文字列 1 を返す" do
      assert FizzBuzz.generate(1) == "1"
    end

    test "2 を渡すと文字列 2 を返す" do
      assert FizzBuzz.generate(2) == "2"
    end

    test "3 の倍数を渡すと Fizz を返す" do
      assert FizzBuzz.generate(3) == "Fizz"
      assert FizzBuzz.generate(6) == "Fizz"
    end
  end
end
```

これを通す最小実装は次のように書けます。

```elixir
# lib/fizz_buzz.ex

defmodule FizzBuzz do
  def generate(n) when rem(n, 3) == 0, do: "Fizz"
  def generate(n), do: Integer.to_string(n)
end
```

`mix test` が成功すれば Green です。

## 2.5 まとめ

この章では、ハードコードの仮実装から、複数ケースによる三角測量で一般化する流れを学びました。また、`describe` でテストを整理し、「2 を渡すと文字列 2」「3 の倍数で Fizz」を ExUnit で表現しました。
