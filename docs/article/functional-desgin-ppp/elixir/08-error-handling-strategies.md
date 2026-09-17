---
type: Article
title: "Chapter 08: エラーハンドリング戦略"
description: "Elixir における関数型エラーハンドリングパターンを学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 08: エラーハンドリング戦略

Elixir における関数型エラーハンドリングパターンを学びます。

## 概要

Elixir では、例外よりも戻り値による明示的なエラー処理が推奨されます。このチャプターでは、Result 型、Validated パターン、エラー復旧戦略を紹介します。

## 主なトピック

1. **Result 型**
2. **Either パターン**
3. **Validated パターン（エラー蓄積）**
4. **エラー復旧パターン**
5. **エラードメインモデリング**

## Result 型

成功と失敗を明示的に表現します。

```elixir
defmodule Result do
  @type t(a, e) :: {:ok, a} | {:error, e}

  def ok(value), do: {:ok, value}
  def error(reason), do: {:error, reason}

  def map({:ok, value}, f), do: {:ok, f.(value)}
  def map({:error, _} = err, _f), do: err

  def flat_map({:ok, value}, f), do: f.(value)
  def flat_map({:error, _} = err, _f), do: err

  def unwrap_or({:ok, value}, _default), do: value
  def unwrap_or({:error, _}, default), do: default
end
```

### 使用例

```elixir
def divide(a, b) when b != 0, do: {:ok, a / b}
def divide(_, 0), do: {:error, "Division by zero"}

# チェイン
{:ok, 10}
|> Result.flat_map(&divide(&1, 2))
|> Result.flat_map(&divide(&1, 0))
# => {:error, "Division by zero"}
```

## Validated パターン

すべてのエラーを蓄積します。

```elixir
defmodule Validated do
  @type t(a, e) :: {:valid, a} | {:invalid, [e]}

  def valid(value), do: {:valid, value}
  def invalid(error), do: {:invalid, [error]}

  def combine({:valid, a}, {:valid, b}, f), do: {:valid, f.(a, b)}
  def combine({:invalid, e1}, {:invalid, e2}, _f), do: {:invalid, e1 ++ e2}
  def combine({:valid, _}, {:invalid, errors}, _f), do: {:invalid, errors}
  def combine({:invalid, errors}, {:valid, _}, _f), do: {:invalid, errors}
end
```

### バリデーション例

```elixir
def validate_user(username, email, age) do
  username_result = validate_username(username)
  email_result = validate_email(email)
  age_result = validate_age(age)

  Validated.sequence([username_result, email_result, age_result])
  |> Validated.map(fn [u, e, a] -> %{username: u, email: e, age: a} end)
end

# 全てのエラーを収集
validate_user("", "invalid", 10)
# => {:invalid, [{:required_field, "username"}, {:invalid_format, "email", ...}, {:out_of_range, "age", 18, 120}]}
```

## エラードメインモデリング

ドメイン固有のエラー型を定義します。

```elixir
defmodule Errors do
  @type validation_error ::
    {:required_field, String.t()}
    | {:invalid_format, String.t(), String.t()}
    | {:out_of_range, String.t(), number(), number()}

  @type business_error ::
    {:insufficient_funds, number(), number()}
    | {:item_not_found, String.t()}
    | {:duplicate_entry, String.t()}

  def format({:required_field, field}) do
    "フィールド '#{field}' は必須です"
  end
  def format({:insufficient_funds, required, available}) do
    "残高不足: 必要額 #{required}、残高 #{available}"
  end
end
```

## エラー復旧パターン

### リトライ

```elixir
def retry(operation, max_attempts, should_retry) do
  result = operation.()
  cond do
    Result.ok?(result) -> result
    attempt >= max_attempts -> result
    should_retry.(result) -> retry(operation, max_attempts, should_retry, attempt + 1)
    true -> result
  end
end
```

### フォールバック

```elixir
def fallback_chain([]), do: {:error, "all fallbacks failed"}
def fallback_chain([operation | rest]) do
  case operation.() do
    {:ok, _} = ok -> ok
    {:error, _} -> fallback_chain(rest)
  end
end
```

## パイプライン with エラー

```elixir
defmodule Pipeline do
  def pipe(initial, functions) do
    Enum.reduce(functions, initial, fn f, acc ->
      Result.flat_map(acc, f)
    end)
  end
end

Pipeline.pipe({:ok, 10}, [
  &({:ok, &1 * 2}),
  &({:ok, &1 + 5})
])
# => {:ok, 25}
```

## まとめ

- **Result 型**で成功/失敗を明示的に表現
- **Validated パターン**で全てのエラーを蓄積
- **エラードメインモデリング**で型安全なエラー処理
- **リトライ/フォールバック**で復旧可能なエラーを処理

## 関連リソース

- [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/)
- [Elixir with Pattern](https://hexdocs.pm/elixir/Kernel.SpecialForms.html#with/1)
