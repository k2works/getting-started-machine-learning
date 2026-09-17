---
type: Article
title: "第 9 章: モジュール設計とビヘイビア"
description: "この章では、モジュールの責務分割を保ちながら拡張しやすい構成を作るために、モジュールの入れ子、@behaviour / @callback、alias を使った設計を扱います。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: モジュール設計とビヘイビア

## 9.1 はじめに

この章では、モジュールの責務分割を保ちながら拡張しやすい構成を作るために、モジュールの入れ子、`@behaviour` / `@callback`、`alias` を使った設計を扱います。FizzBuzz のコマンド層を例に、共通インターフェースを実装する形へ整理します。

## 9.2 モジュールの入れ子で責務を表現する

Elixir はファイル階層とは独立して、名前空間で責務を整理できます。たとえば `FizzBuzz.Command.ValueCommand` は「単一値を扱うコマンド」を明確に示します。

```elixir
defmodule FizzBuzz.Command.ValueCommand do
  def run(number), do: Integer.to_string(number)
end
```

`FizzBuzz.Command` 配下に `ValueCommand` と `ListCommand` を置くと、同じ関心事のモジュール群を把握しやすくなります。

## 9.3 @behaviour と @callback でインターフェースを定義する

まずはコマンド共通の契約を `FizzBuzz.Command` ビヘイビアとして定義します。

```elixir
defmodule FizzBuzz.Command do
  @callback run(input :: term()) :: {:ok, term()} | {:error, atom()}
end
```

この `@callback` により、実装モジュールは `run/1` を提供する必要があります。未実装の場合、コンパイル時に警告が出るため、設計の抜け漏れを防げます。

## 9.4 ValueCommand と ListCommand でビヘイビアを実装する

`@behaviour FizzBuzz.Command` を指定し、`run/1` を実装します。`alias` を使ってモジュール参照を簡略化します。

```elixir
defmodule FizzBuzz.Command.ValueCommand do
  @behaviour FizzBuzz.Command

  alias FizzBuzz.FizzBuzzService

  @impl true
  def run(number) when is_integer(number) and number > 0 do
    {:ok, FizzBuzzService.generate(number)}
  end

  @impl true
  def run(_), do: {:error, :invalid_number}
end

defmodule FizzBuzz.Command.ListCommand do
  @behaviour FizzBuzz.Command

  alias FizzBuzz.FizzBuzzService

  @impl true
  def run(limit) when is_integer(limit) and limit > 0 do
    values =
      1..limit
      |> Enum.map(&FizzBuzzService.generate/1)

    {:ok, values}
  end

  @impl true
  def run(_), do: {:error, :invalid_limit}
end
```

`alias FizzBuzz.FizzBuzzService` を使うことで、`FizzBuzz.FizzBuzzService.generate/1` を `FizzBuzzService.generate/1` と短く書けます。

## 9.5 ExUnit でビヘイビア実装を検証する

各コマンドが同じ契約で動くことを ExUnit で確認します。

```elixir
defmodule FizzBuzz.CommandTest do
  use ExUnit.Case, async: true

  alias FizzBuzz.Command.{ListCommand, ValueCommand}

  test "ValueCommand は単一値を処理する" do
    assert ValueCommand.run(3) == {:ok, "Fizz"}
  end

  test "ValueCommand は不正値でエラーを返す" do
    assert ValueCommand.run(0) == {:error, :invalid_number}
    assert ValueCommand.run("3") == {:error, :invalid_number}
  end

  test "ListCommand は 1..limit の結果一覧を返す" do
    assert ListCommand.run(5) == {:ok, ["1", "2", "Fizz", "4", "Buzz"]}
  end

  test "ListCommand は不正値でエラーを返す" do
    assert ListCommand.run(-1) == {:error, :invalid_limit}
  end
end
```

`ValueCommand` と `ListCommand` は入出力対象が異なっても、同じ `run/1` 契約を守るため、呼び出し側の扱いを統一できます。

## 9.6 まとめ

この章では、`FizzBuzz.Command.ValueCommand` のようなモジュール入れ子で責務を整理し、`@behaviour` / `@callback` で契約を固定し、`alias` で記述を簡潔にする方法を確認しました。ビヘイビア中心の設計により、機能追加時も既存コードへの影響を局所化できます。
