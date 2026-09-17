---
type: Article
title: "第 11 章: Stream と遅延評価"
description: "この章では、Stream モジュールを使った遅延評価を学びます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: Stream と遅延評価

## 11.1 はじめに

この章では、`Stream` モジュールを使った遅延評価を学びます。`Enum` による先行評価との違いを理解し、FizzBuzz の無限ストリームを安全に扱う方法を確認します。

## 11.2 Stream.iterate / Stream.map / Stream.take の基本

`Stream` は遅延評価です。`Enum` のようにすぐ計算せず、必要になったときにだけ処理します。

```elixir
defmodule FizzBuzz.Streaming do
  def generate(number) when rem(number, 15) == 0, do: "FizzBuzz"
  def generate(number) when rem(number, 3) == 0, do: "Fizz"
  def generate(number) when rem(number, 5) == 0, do: "Buzz"
  def generate(number), do: Integer.to_string(number)

  def lazy_stream do
    Stream.iterate(1, &(&1 + 1))
    |> Stream.map(&generate/1)
  end
end
```

`lazy_stream/0` は無限列ですが、実際にはまだ計算されていません。

## 11.3 遅延評価と先行評価の違い: Enum vs Stream

`Enum` はコレクション全体を順に評価します。`Stream` は最後に `Enum.take` などの消費処理が呼ばれたときだけ評価します。

```elixir
defmodule FizzBuzz.EagerVsLazy do
  def eager(limit) do
    1..limit
    |> Enum.map(&expensive_generate/1)
  end

  def lazy(limit) do
    1..limit
    |> Stream.map(&expensive_generate/1)
    |> Enum.take(5)
  end

  defp expensive_generate(number) do
    Process.sleep(1)

    cond do
      rem(number, 15) == 0 -> "FizzBuzz"
      rem(number, 3) == 0 -> "Fizz"
      rem(number, 5) == 0 -> "Buzz"
      true -> Integer.to_string(number)
    end
  end
end
```

`eager/1` は `limit` 件すべてを計算しますが、`lazy/1` は先頭 5 件だけ計算します。

## 11.4 lazy_stream/0 から必要な分だけ取得する

無限ストリームは必ず `Enum.take/2` や `Enum.at/2` で境界を作って使います。

```elixir
defmodule FizzBuzz.StreamConsumer do
  alias FizzBuzz.Streaming

  def first_ten do
    Streaming.lazy_stream()
    |> Enum.take(10)
  end

  def hundredth_value do
    Streaming.lazy_stream()
    |> Enum.at(99)
  end
end
```

## 11.5 パフォーマンス比較の考え方

大量データで「一部だけ必要」なケースでは、`Stream` の方が有利です。評価コストの高い関数を使うほど差が出ます。

```elixir
defmodule FizzBuzz.BenchmarkLike do
  def compare(limit) do
    {eager_us, _} =
      :timer.tc(fn ->
        1..limit
        |> Enum.map(&heavy_work/1)
        |> Enum.take(10)
      end)

    {lazy_us, _} =
      :timer.tc(fn ->
        1..limit
        |> Stream.map(&heavy_work/1)
        |> Enum.take(10)
      end)

    %{eager_us: eager_us, lazy_us: lazy_us}
  end

  defp heavy_work(n) do
    if rem(n, 15) == 0, do: "FizzBuzz", else: Integer.to_string(n)
  end
end
```

## 11.6 ExUnit で Stream の挙動を検証する

```elixir
defmodule FizzBuzz.StreamTest do
  use ExUnit.Case, async: true

  alias FizzBuzz.{BenchmarkLike, StreamConsumer, Streaming}

  test "lazy_stream/0 から先頭 5 件を取り出せる" do
    assert Streaming.lazy_stream() |> Enum.take(5) == ["1", "2", "Fizz", "4", "Buzz"]
  end

  test "Enum.at/2 で任意位置を取得できる" do
    assert StreamConsumer.hundredth_value() == "Buzz"
  end

  test "無限ストリームでも take で安全に扱える" do
    result = Streaming.lazy_stream() |> Enum.take(15)
    assert Enum.at(result, 14) == "FizzBuzz"
  end

  test "遅延評価は先頭だけ必要な処理で有利になりやすい" do
    times = BenchmarkLike.compare(50_000)

    assert is_integer(times.eager_us)
    assert is_integer(times.lazy_us)
    assert times.lazy_us <= times.eager_us
  end
end
```

## 11.7 まとめ

この章では、`Stream.iterate`、`Stream.map`、`Stream.take` を使って遅延評価を実現し、`Enum` との違いを確認しました。`lazy_stream/0` のような無限データは、必要な分だけ取り出す設計にすると、安全性と性能を両立できます。
