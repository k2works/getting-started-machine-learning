---
type: Article
title: "第 7 章: 構造体とプロトコルによるポリモーフィズム"
description: "この章では、defstruct と defprotocol を使って、Elixir で型ごとに振る舞いを切り替える方法を学びます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: 構造体とプロトコルによるポリモーフィズム

## 7.1 はじめに

この章では、`defstruct` と `defprotocol` を使って、Elixir で型ごとに振る舞いを切り替える方法を学びます。FizzBuzz の値オブジェクトを導入し、`Type01`、`Type02`、`Type03` が同じインターフェース `generate/1` を実装する形でポリモーフィズムを実現します。

## 7.2 defstruct で値オブジェクトを定義する

FizzBuzz の入力値を表す値オブジェクト `FizzBuzz.Model.Value` を定義します。`@enforce_keys` で必須フィールドを宣言すると、不完全な構造体生成を防げます。

```elixir
defmodule FizzBuzz.Model.Value do
  @enforce_keys [:number]
  defstruct [:number]

  @type t :: %__MODULE__{
          number: pos_integer()
        }
end
```

`@enforce_keys [:number]` により、`%FizzBuzz.Model.Value{}` のような生成は実行時エラーになり、`number` の設定漏れを早期に検出できます。

## 7.3 defprotocol で共通インターフェースを定義する

次に、生成処理の共通契約として `Generatable` プロトコルを定義します。

```elixir
defprotocol FizzBuzz.Generatable do
  @spec generate(t()) :: String.t()
  def generate(value)
end
```

プロトコルは「どの型であっても `generate/1` を呼べる」という抽象化を提供します。実際の処理は各構造体の `defimpl` で分岐します。

## 7.4 defimpl で Type01 / Type02 / Type03 を実装する

`Type01`、`Type02`、`Type03` は、それぞれ通常数値、3 の倍数、5 の倍数を表す構造体とします。

```elixir
defmodule FizzBuzz.Model.Type01 do
  @enforce_keys [:value]
  defstruct [:value]
end

defmodule FizzBuzz.Model.Type02 do
  @enforce_keys [:value]
  defstruct [:value]
end

defmodule FizzBuzz.Model.Type03 do
  @enforce_keys [:value]
  defstruct [:value]
end

defimpl FizzBuzz.Generatable, for: FizzBuzz.Model.Type01 do
  def generate(%FizzBuzz.Model.Type01{value: value}), do: Integer.to_string(value.number)
end

defimpl FizzBuzz.Generatable, for: FizzBuzz.Model.Type02 do
  def generate(%FizzBuzz.Model.Type02{}), do: "Fizz"
end

defimpl FizzBuzz.Generatable, for: FizzBuzz.Model.Type03 do
  def generate(%FizzBuzz.Model.Type03{}), do: "Buzz"
end
```

この設計では、呼び出し側は具体型を意識せず、`FizzBuzz.Generatable.generate/1` だけを呼べばよくなります。

## 7.5 ExUnit でプロトコル実装を検証する

プロトコルの振る舞いは ExUnit で確認できます。

```elixir
defmodule FizzBuzz.GeneratableTest do
  use ExUnit.Case, async: true

  alias FizzBuzz.Generatable
  alias FizzBuzz.Model.{Type01, Type02, Type03, Value}

  test "Type01 は数値文字列を返す" do
    value = %Value{number: 1}
    assert Generatable.generate(%Type01{value: value}) == "1"
  end

  test "Type02 は Fizz を返す" do
    value = %Value{number: 3}
    assert Generatable.generate(%Type02{value: value}) == "Fizz"
  end

  test "Type03 は Buzz を返す" do
    value = %Value{number: 5}
    assert Generatable.generate(%Type03{value: value}) == "Buzz"
  end

  test "Value は number が必須" do
    assert_raise ArgumentError, fn ->
      struct!(Value, %{})
    end
  end
end
```

`struct!/2` を使うと、`@enforce_keys` の違反をテストで明示的に確認できます。

## 7.6 まとめ

この章では、`defstruct` と `@enforce_keys` で値オブジェクトを定義し、`defprotocol` / `defimpl` で型ごとに `generate/1` を実装しました。Elixir では継承ではなく、プロトコルでポリモーフィズムを組み立てるのが実践的です。
