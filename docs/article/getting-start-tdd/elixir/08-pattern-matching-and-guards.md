---
type: Article
title: "第 8 章: パターンマッチとガード節"
description: "この章では、Elixir の分岐を読みやすく保つために、関数頭部のパターンマッチ、when ガード節、case と cond の使い分けを整理します。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: パターンマッチとガード節

## 8.1 はじめに

この章では、Elixir の分岐を読みやすく保つために、関数頭部のパターンマッチ、`when` ガード節、`case` と `cond` の使い分けを整理します。あわせて、タグ付きタプル `{:ok, value}` / `{:error, reason}` による結果表現も確認します。

## 8.2 関数頭部でのパターンマッチ

Elixir では値ごとの分岐を関数定義に直接書けます。

```elixir
defmodule FizzBuzz.Factory do
  alias FizzBuzz.Model.{Type01, Type02, Type03, Value}

  def create(1), do: %Type01{value: %Value{number: 1}}
  def create(2), do: %Type01{value: %Value{number: 2}}
  def create(3), do: %Type02{value: %Value{number: 3}}
  def create(5), do: %Type03{value: %Value{number: 5}}
  def create(_), do: :unsupported
end
```

`def create(1)`、`def create(2)`、`def create(_)` のように書くと、`if` より宣言的で見通しがよくなります。

## 8.3 ガード節で入力条件を制約する

パターンだけで不足する条件は `when` を使います。

```elixir
defmodule FizzBuzz.Validator do
  def validate(number) when is_integer(number) and number > 0, do: {:ok, number}
  def validate(number) when is_integer(number), do: {:error, :non_positive}
  def validate(_), do: {:error, :not_integer}
end
```

`when is_integer(number) and number > 0` により、正常系の条件を関数シグネチャ近くで明示できます。

## 8.4 case 式と cond 式の使い分け

`case` は「値の形」を分岐するときに向いており、`cond` は「複数の真偽条件」を評価するときに向いています。

```elixir
defmodule FizzBuzz.Renderer do
  def from_result(result) do
    case result do
      {:ok, value} -> "ok: #{value}"
      {:error, reason} -> "error: #{reason}"
    end
  end

  def classify(number) do
    cond do
      rem(number, 15) == 0 -> :fizz_buzz
      rem(number, 3) == 0 -> :fizz
      rem(number, 5) == 0 -> :buzz
      true -> :number
    end
  end
end
```

- `case`: `{:ok, value}` のようなタグ付きデータを分解する。
- `cond`: `rem/2` の結果など条件式を上から順に判定する。

## 8.5 ExUnit で分岐をテストする

分岐ロジックは仕様が崩れやすいため、タグ付きタプルまで含めてテストします。

```elixir
defmodule FizzBuzz.PatternMatchingTest do
  use ExUnit.Case, async: true

  alias FizzBuzz.Factory
  alias FizzBuzz.Renderer
  alias FizzBuzz.Validator
  alias FizzBuzz.Model.Type02

  test "create/1 は 3 を Type02 として生成する" do
    assert %Type02{} = Factory.create(3)
  end

  test "validate/1 は正の整数を {:ok, value} で返す" do
    assert Validator.validate(10) == {:ok, 10}
  end

  test "validate/1 は不正な入力を {:error, reason} で返す" do
    assert Validator.validate(0) == {:error, :non_positive}
    assert Validator.validate("10") == {:error, :not_integer}
  end

  test "from_result/1 はタグ付きタプルを文字列化する" do
    assert Renderer.from_result({:ok, "Fizz"}) == "ok: Fizz"
    assert Renderer.from_result({:error, :not_integer}) == "error: not_integer"
  end

  test "classify/1 は cond で優先順位どおりに判定する" do
    assert Renderer.classify(15) == :fizz_buzz
    assert Renderer.classify(6) == :fizz
    assert Renderer.classify(10) == :buzz
    assert Renderer.classify(7) == :number
  end
end
```

## 8.6 まとめ

この章では、関数頭部のパターンマッチ、`when` ガード節、`case` と `cond` の使い分け、タグ付きタプルによる結果表現を確認しました。これらを組み合わせると、分岐が増えても読みやすさと安全性を維持できます。
