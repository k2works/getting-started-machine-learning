---
type: Article
title: "第 12 章: エラーハンドリングと with 構文"
description: "この章では、Elixir で例外に頼りすぎず、値としてエラーを扱う方法を学びます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと with 構文

## 12.1 はじめに

この章では、Elixir で例外に頼りすぎず、値としてエラーを扱う方法を学びます。`{:ok, value}` / `{:error, reason}` の Result パターン、`with` 構文による連鎖処理、`try/rescue` との使い分けを整理します。

## 12.2 タグ付きタプルによる Result パターン

関数の成功と失敗を同じ型で返すと、呼び出し側が分岐を明示できます。

```elixir
defmodule FizzBuzz.Safe do
  def safe_generate(number) when is_integer(number) and number > 0 do
    {:ok, generate(number)}
  end

  def safe_generate(number) when is_integer(number) do
    {:error, :non_positive}
  end

  def safe_generate(_), do: {:error, :not_integer}

  defp generate(number) when rem(number, 15) == 0, do: "FizzBuzz"
  defp generate(number) when rem(number, 3) == 0, do: "Fizz"
  defp generate(number) when rem(number, 5) == 0, do: "Buzz"
  defp generate(number), do: Integer.to_string(number)
end
```

## 12.3 safe_generate/1 で安全な生成を行う

`safe_generate/1` は例外を投げず、入力エラーを `{:error, reason}` で返します。これにより、関数合成時の失敗経路を追いやすくなります。

```elixir
defmodule FizzBuzz.SafeFacade do
  alias FizzBuzz.Safe

  def safe_generate_message(input) do
    case Safe.safe_generate(input) do
      {:ok, value} -> "result: #{value}"
      {:error, reason} -> "error: #{reason}"
    end
  end
end
```

## 12.4 with 構文でモナド的にエラーを連鎖する

`with` は「途中で `{:error, reason}` が出たら即終了」の流れを簡潔に書けます。`safe_generate_list/1` では、各要素の検証と変換を連鎖します。

```elixir
defmodule FizzBuzz.SafeList do
  alias FizzBuzz.Safe

  def safe_generate_list(inputs) when is_list(inputs) do
    inputs
    |> Enum.reduce_while({:ok, []}, fn input, {:ok, acc} ->
      with {:ok, value} <- Safe.safe_generate(input) do
        {:cont, {:ok, acc ++ [value]}}
      else
        {:error, reason} -> {:halt, {:error, {:invalid_item, input, reason}}}
      end
    end)
  end

  def safe_generate_list(_), do: {:error, :not_list}
end
```

## 12.5 try/rescue vs with の使い分け

`with` は、想定内エラーをタグ付きタプルで扱うときに使います。`try/rescue` は、外部ライブラリや危険な処理で例外が発生しうるときに限定します。

```elixir
defmodule FizzBuzz.ExceptionBoundary do
  alias FizzBuzz.Safe

  def parse_and_generate(text) do
    with {number, ""} <- Integer.parse(text),
         {:ok, value} <- Safe.safe_generate(number) do
      {:ok, value}
    else
      :error -> {:error, :parse_error}
      {:error, reason} -> {:error, reason}
    end
  end

  def parse_with_rescue(text) do
    try do
      number = String.to_integer(text)
      Safe.safe_generate(number)
    rescue
      ArgumentError -> {:error, :parse_error}
    end
  end
end
```

## 12.6 関数型エラーハンドリングの設計指針

- ドメイン上で起こりうる失敗は、例外ではなく `{:error, reason}` で表現します。
- `reason` は機械的に扱える atom や構造化データにします。
- `with` は 2 段以上の処理連鎖で使い、`case` のネストを減らします。
- 例外は境界層で捕捉し、内部では Result パターンへ正規化します。

## 12.7 ExUnit でエラーハンドリングを検証する

```elixir
defmodule FizzBuzz.ErrorHandlingTest do
  use ExUnit.Case, async: true

  alias FizzBuzz.{ExceptionBoundary, Safe, SafeList}

  test "safe_generate/1 は成功時に {:ok, value} を返す" do
    assert Safe.safe_generate(15) == {:ok, "FizzBuzz"}
  end

  test "safe_generate/1 は入力エラーを {:error, reason} で返す" do
    assert Safe.safe_generate(0) == {:error, :non_positive}
    assert Safe.safe_generate("3") == {:error, :not_integer}
  end

  test "safe_generate_list/1 は全要素が正しければ一覧を返す" do
    assert SafeList.safe_generate_list([1, 3, 5]) == {:ok, ["1", "Fizz", "Buzz"]}
  end

  test "safe_generate_list/1 は最初の不正要素で停止する" do
    assert SafeList.safe_generate_list([1, -1, 3]) == {:error, {:invalid_item, -1, :non_positive}}
  end

  test "with を使った parse_and_generate/1 は parse エラーを返せる" do
    assert ExceptionBoundary.parse_and_generate("abc") == {:error, :parse_error}
    assert ExceptionBoundary.parse_and_generate("15") == {:ok, "FizzBuzz"}
  end

  test "try/rescue ベースの実装も同じ契約に揃えられる" do
    assert ExceptionBoundary.parse_with_rescue("abc") == {:error, :parse_error}
    assert ExceptionBoundary.parse_with_rescue("5") == {:ok, "Buzz"}
  end
end
```

## 12.8 まとめ

この章では、`safe_generate/1` と `safe_generate_list/1` を通じて Result パターンと `with` 構文を使ったエラーハンドリングを確認しました。想定内エラーは値で返し、想定外例外は境界で `try/rescue` する設計にすると、関数型らしい見通しのよいコードになります。
