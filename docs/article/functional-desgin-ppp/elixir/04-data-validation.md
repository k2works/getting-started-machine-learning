---
type: Article
title: "Chapter 04: データバリデーション"
description: "Elixir におけるデータバリデーションと型安全性について学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 04: データバリデーション

Elixir におけるデータバリデーションと型安全性について学びます。

## 概要

このチャプターでは、関数型プログラミングにおけるデータバリデーションのパターンを紹介します。
Elixir の Result 型（`{:ok, value} | {:error, reason}`）と Validated パターンを使って、
安全で表現力豊かなバリデーションを実装します。

## 主なトピック

1. **Result 型によるエラーハンドリング**
2. **バリデータの合成**
3. **スマートコンストラクタ**
4. **エラーの蓄積**
5. **値オブジェクト**

## Result 型とは

Result 型は、成功と失敗を明示的に表現するデータ構造です。

```elixir
@type result(a) :: {:ok, a} | {:error, String.t()}
```

例外をスローする代わりに、失敗を通常の戻り値として扱います。

## 実装例

### 基本的なバリデータ

```elixir
defmodule Chapter04 do
  @doc """
  正の数であることを検証する。
  """
  @spec validate_positive(number()) :: {:ok, number()} | {:error, String.t()}
  def validate_positive(n) when n > 0, do: {:ok, n}
  def validate_positive(_), do: {:error, "正の数が必要です"}
  
  @doc """
  空でない文字列であることを検証する。
  """
  @spec validate_non_empty(String.t()) :: {:ok, String.t()} | {:error, String.t()}
  def validate_non_empty(""), do: {:error, "空文字列は許可されません"}
  def validate_non_empty(s) when is_binary(s), do: {:ok, s}
end
```

### バリデータの合成

複数のバリデータを組み合わせて、複雑な検証ロジックを構築します。

```elixir
# 最初のエラーで停止
def validate_all(value, validators) do
  Enum.reduce_while(validators, {:ok, value}, fn v, {:ok, val} ->
    case v.(val) do
      {:ok, _} = result -> {:cont, result}
      {:error, _} = error -> {:halt, error}
    end
  end)
end

# すべてのエラーを蓄積
def validate_all_accumulate(value, validators) do
  errors =
    validators
    |> Enum.map(fn v -> v.(value) end)
    |> Enum.flat_map(fn
      {:error, e} -> e
      {:ok, _} -> []
    end)

  if errors == [], do: {:valid, value}, else: {:invalid, errors}
end
```

### スマートコンストラクタ

不正な値を作成できないように、検証付きコンストラクタを提供します。

```elixir
defmodule Email do
  @enforce_keys [:value]
  defstruct [:value]

  @spec new(String.t()) :: {:ok, t()} | {:error, String.t()}
  def new(email) do
    cond do
      not String.contains?(email, "@") ->
        {:error, "メールアドレスには @ が必要です"}
      String.length(email) < 5 ->
        {:error, "メールアドレスが短すぎます"}
      true ->
        {:ok, %__MODULE__{value: email}}
    end
  end
end
```

## with 式によるバリデーション

Elixir の `with` 式を使うと、複数のバリデーションを順次実行できます。

```elixir
def validate_user(name, age, email) do
  with {:ok, valid_name} <- NonEmptyString.new(name),
       {:ok, valid_age} <- PositiveInteger.new(age),
       {:ok, valid_email} <- Email.new(email) do
    {:ok, %{name: valid_name, age: valid_age, email: valid_email}}
  end
end
```

`with` 式は最初の失敗で停止し、エラーを返します。

## Validated パターン

すべてのエラーを蓄積したい場合は、Validated パターンを使います。

```elixir
@type validated(a) :: {:valid, a} | {:invalid, [String.t()]}

def validate_user_accumulate(params) do
  results = [
    validate_field(params, :name, &NonEmptyString.new/1),
    validate_field(params, :age, &PositiveInteger.new/1),
    validate_field(params, :email, &Email.new/1)
  ]
  
  errors = Enum.flat_map(results, fn
    {:error, e} -> [e]
    {:ok, _} -> []
  end)
  
  if errors == [] do
    {:valid, build_user(results)}
  else
    {:invalid, errors}
  end
end
```

## テスト例

```elixir
defmodule Chapter04Test do
  use ExUnit.Case

  describe "Email.new/1" do
    test "有効なメールアドレスで作成できる" do
      assert {:ok, %Email{value: "test@example.com"}} = Email.new("test@example.com")
    end

    test "@ がないとエラー" do
      assert {:error, _} = Email.new("invalid")
    end
  end

  describe "validate_all/2" do
    test "すべてのバリデータが成功すると :ok" do
      validators = [
        fn x -> if x > 0, do: {:ok, x}, else: {:error, "正の数が必要"} end,
        fn x -> if x < 100, do: {:ok, x}, else: {:error, "100未満が必要"} end
      ]
      
      assert {:ok, 50} = Chapter04.validate_all(50, validators)
    end

    test "最初のエラーで停止する" do
      validators = [
        fn _ -> {:error, "エラー1"} end,
        fn _ -> {:error, "エラー2"} end
      ]
      
      assert {:error, ["エラー1"]} = Chapter04.validate_all(0, validators)
    end
  end
end
```

## まとめ

- **Result 型**で成功と失敗を明示的に表現
- **バリデータの合成**で複雑な検証ロジックを構築
- **スマートコンストラクタ**で不正な値の作成を防止
- **with 式**で逐次的なバリデーション
- **Validated パターン**ですべてのエラーを蓄積

## 関連リソース

- [Elixir ドキュメント: with](https://hexdocs.pm/elixir/Kernel.SpecialForms.html#with/1)
- [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/)
