---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "この章では、FizzBuzz 問題を小さな TODO リストに分解し、ExUnit で最初のテストを書きます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

この章では、FizzBuzz 問題を小さな TODO リストに分解し、ExUnit で最初のテストを書きます。TDD の最初の一歩は、いきなり実装することではなく、失敗するテストから始めることです。

## 1.2 FizzBuzz の仕様と TODO リスト

対象の仕様は次のとおりです。

```text
1 から 100 までの数を出力する。
3 の倍数は Fizz、5 の倍数は Buzz、3 と 5 の両方の倍数は FizzBuzz を出力する。
```

まずは仕様を TODO リストに分解します。

- [ ] 数を文字列として返す
- [ ] 3 の倍数で Fizz を返す
- [ ] 5 の倍数で Buzz を返す
- [ ] 15 の倍数で FizzBuzz を返す
- [ ] 1 から 100 までの結果をリストで作る

最初のタスクとして「1 を渡すと文字列 1 を返す」だけを扱います。

## 1.3 ExUnit の基本

Elixir では `mix new` で作成したプロジェクトに ExUnit が標準で含まれます。最小のテストは `test` マクロと `assert` マクロで書けます。

```elixir
# test/fizz_buzz_test.exs

defmodule FizzBuzzTest do
  use ExUnit.Case

  test "1 を渡すと文字列 1 を返す" do
    assert FizzBuzz.generate(1) == "1"
  end
end
```

- `test` はテストケースを定義します。
- `assert` は期待値を検証します。

## 1.4 mix test の使い方

テスト実行は `mix test` を使います。

```bash
$ mix test
```

この時点では `FizzBuzz` モジュールが未実装なので、テストは失敗します。これが Red です。

## 1.5 Red から Green へ

### Red: 最初の失敗するテスト

先ほどのテストを実行すると、例えば次のように失敗します。

```text
** (UndefinedFunctionError) function FizzBuzz.generate/1 is undefined
```

### Green: 仮実装で通す

まずは最小限の仮実装でテストを通します。

```elixir
# lib/fizz_buzz.ex

defmodule FizzBuzz do
  def generate(1), do: "1"
end
```

再度 `mix test` を実行します。

```bash
$ mix test
```

1 件のテストが成功すれば Green です。まだ汎用的ではありませんが、TDD ではこの小さな成功を積み重ねます。

## 1.6 まとめ

この章では、TODO リスト駆動で最小タスクを切り出し、ExUnit の `test` / `assert` と `mix test` の基本を確認しました。さらに、最初の失敗するテストを書いてから仮実装で成功させる Red → Green を体験しました。
