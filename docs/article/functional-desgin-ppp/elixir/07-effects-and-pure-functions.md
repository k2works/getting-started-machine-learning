---
type: Article
title: "Chapter 07: 副作用と純粋関数"
description: "Elixir における副作用の管理と純粋関数の分離について学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 07: 副作用と純粋関数

Elixir における副作用の管理と純粋関数の分離について学びます。

## 概要

関数型プログラミングでは、副作用（I/O、状態変更、時間、乱数）を純粋なビジネスロジックから分離することが重要です。このチャプターでは、その設計パターンを紹介します。

## 主なトピック

1. **純粋関数と副作用の分離**
2. **Functional Core, Imperative Shell**
3. **副作用の遅延実行**
4. **Reader パターン**
5. **依存性注入**

## 純粋関数 vs 副作用

### 純粋関数

- 同じ入力に対して常に同じ出力
- 外部状態を変更しない

```elixir
# 純粋関数の例
def add(a, b), do: a + b
def upcase(s), do: String.upcase(s)
```

### 副作用を持つ関数

- I/O（コンソール、ファイル、ネットワーク）
- 状態変更
- 現在時刻の取得
- 乱数生成

```elixir
# 副作用を持つ関数
def current_time, do: DateTime.utc_now()  # 非決定的
def random_number, do: :rand.uniform()    # 非決定的
def print(msg), do: IO.puts(msg)          # I/O
```

## Functional Core, Imperative Shell

ビジネスロジックを純粋関数として実装し、副作用は境界層で処理します。

```elixir
# Functional Core（純粋関数）
defmodule PricingCore do
  def apply_discount(price, %{type: :percentage, value: pct}) do
    price * (1 - pct / 100)
  end

  def calculate_total(products) do
    products |> Enum.map(& &1.price) |> Enum.sum()
  end
end

# Imperative Shell（副作用を扱う）
defmodule PricingShell do
  def calculate_order_price(products, discount_fetcher, discount_code, tax_rate) do
    # 副作用：外部から割引情報を取得
    discount = discount_fetcher.(discount_code)
    # 純粋関数でビジネスロジックを実行
    PricingCore.calculate_final_price(products, discount, tax_rate)
  end
end
```

## 副作用の遅延実行（Effect as Data）

副作用をデータとして表現し、実行を遅延させます。

```elixir
defmodule Effect do
  def console_log(message) do
    {:effect, :console_log, message, fn _ -> :ok end}
  end

  def get_current_time do
    {:effect, :get_time, nil, fn time -> time end}
  end

  # 本番用インタープリター
  def run({:effect, :console_log, message, cont}) do
    IO.puts(message)
    cont.(:ok)
  end

  # テスト用インタープリター
  def run_test({:effect, effect_type, _, cont}, mock_values) do
    cont.(Map.get(mock_values, effect_type))
  end
end
```

## Reader パターン

環境（設定、依存性）を引数として渡す関数をラップします。

```elixir
defmodule Reader do
  def pure(value), do: fn _env -> value end
  def ask, do: fn env -> env end
  def asks(selector), do: fn env -> selector.(env) end

  def flat_map(reader, f) do
    fn env -> f.(reader.(env)).(env) end
  end

  def run(reader, env), do: reader.(env)
end

# 使用例
def get_db_url do
  Reader.asks(fn config -> config.db_url end)
end

def build_service do
  Reader.flat_map(get_db_url(), fn db_url ->
    Reader.flat_map(get_api_key(), fn api_key ->
      Reader.pure(%{db: db_url, key: api_key, ready: true})
    end)
  end)
end
```

## 時間の抽象化

時間依存をテスト可能にします。

```elixir
defmodule TimeService do
  def add_timestamp(data, time_provider) do
    Map.put(data, :timestamp, time_provider.())
  end

  def is_expired?(expiry_date, time_provider) do
    DateTime.compare(time_provider.(), expiry_date) == :gt
  end
end

# テスト
test "is_expired? は期限切れを判定する" do
  now = ~U[2024-01-15 12:00:00Z]
  provider = fn -> now end

  assert TimeService.is_expired?(~U[2024-01-10 12:00:00Z], provider) == true
  assert TimeService.is_expired?(~U[2024-01-20 12:00:00Z], provider) == false
end
```

## まとめ

- **純粋関数**でビジネスロジックを実装
- **Functional Core, Imperative Shell**で副作用を分離
- **依存性注入**でテスト可能な設計
- **Reader パターン**で環境を引き回す

## 関連リソース

- [Boundaries by Gary Bernhardt](https://www.destroyallsoftware.com/talks/boundaries)
- [Functional Core, Imperative Shell](https://www.youtube.com/watch?v=yTkzNHF6rMs)
