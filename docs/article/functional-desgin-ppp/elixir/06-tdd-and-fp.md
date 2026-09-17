---
type: Article
title: "Chapter 06: TDD と関数型プログラミング"
description: "Elixir におけるテスト駆動開発（TDD）と関数型プログラミングの融合について学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 06: TDD と関数型プログラミング

Elixir におけるテスト駆動開発（TDD）と関数型プログラミングの融合について学びます。

## 概要

テスト駆動開発（TDD）は、テストを先に書いてから実装するソフトウェア開発手法です。
関数型プログラミングの純粋関数と不変データは、TDD と非常に相性が良いです。

## 主なトピック

1. **TDD の基本サイクル（Red-Green-Refactor）**
2. **テスタブルな設計**
3. **純粋関数とテスト容易性**
4. **モックとスタブの代替手法**
5. **Outside-In TDD**
6. **Parameterized Tests**

## TDD の基本サイクル

### Red-Green-Refactor

1. **Red**: 失敗するテストを書く
2. **Green**: テストを通す最小限の実装
3. **Refactor**: コードを整理（テストは通ったまま）

### FizzBuzz の例

```elixir
# Step 1: Red - 失敗するテストを書く
test "通常の数値は文字列として返す" do
  assert FizzBuzz.convert(1) == "1"
end

# Step 2: Green - 最小限の実装
def convert(n), do: Integer.to_string(n)

# Step 3: 次のテストを追加
test "3の倍数は 'Fizz' を返す" do
  assert FizzBuzz.convert(3) == "Fizz"
end

# 実装を拡張
def convert(n) when rem(n, 3) == 0, do: "Fizz"
def convert(n), do: Integer.to_string(n)

# 以下同様に 5の倍数、15の倍数を追加...
```

## テスタブルな設計

### 依存性の注入

外部依存を関数として注入することで、テスト時にスタブを使用できます。

```elixir
defmodule PricingService do
  @doc """
  割引率を取得する関数を注入することで、
  外部サービスをモック可能にする。
  """
  def calculate_price(product_id, base_price, discount_fetcher) do
    discount = discount_fetcher.(product_id)
    base_price * (1.0 - discount)
  end
end

# テストでの使用
test "割引率を適用して価格を計算する" do
  # スタブ：常に10%割引を返す
  discount_fetcher = fn _product_id -> 0.10 end
  
  assert PricingService.calculate_price("PROD001", 1000, discount_fetcher) == 900.0
end

# 本番での使用
def production_discount_fetcher(product_id) do
  DiscountAPI.fetch(product_id)
end

PricingService.calculate_price("PROD001", 1000, &production_discount_fetcher/1)
```

## 純粋関数によるビジネスロジック

ビジネスロジックを純粋関数として実装することで、テストが容易になります。

```elixir
defmodule OrderProcessor do
  @doc "注文の小計を計算する（純粋関数）"
  def calculate_subtotal(items) do
    items
    |> Enum.map(fn {_name, qty, price} -> qty * price end)
    |> Enum.sum()
  end

  @doc "顧客タイプに応じた割引率を返す（純粋関数）"
  def discount_rate(:regular), do: 0.0
  def discount_rate(:premium), do: 0.05
  def discount_rate(:vip), do: 0.10

  @doc "税額を計算する（純粋関数）"
  def calculate_tax(amount, tax_rate), do: amount * tax_rate

  @doc "注文を処理する（純粋関数の組み合わせ）"
  def process_order(%{items: items, customer_type: customer_type}, tax_rate) do
    subtotal = calculate_subtotal(items)
    discount_amount = subtotal * discount_rate(customer_type)
    discounted = subtotal - discount_amount
    tax_amount = calculate_tax(discounted, tax_rate)
    total = discounted + tax_amount

    %{
      subtotal: subtotal,
      discount: discount_amount,
      tax: tax_amount,
      total: total
    }
  end
end
```

テストは各純粋関数を独立してテストできます。

```elixir
describe "OrderProcessor" do
  test "calculate_subtotal" do
    items = [{"Item1", 2, 100.0}, {"Item2", 3, 50.0}]
    assert OrderProcessor.calculate_subtotal(items) == 350.0
  end

  test "discount_rate" do
    assert OrderProcessor.discount_rate(:regular) == 0.0
    assert OrderProcessor.discount_rate(:premium) == 0.05
    assert OrderProcessor.discount_rate(:vip) == 0.10
  end

  test "process_order for VIP customer" do
    order = %{items: [{"Item", 1, 1000.0}], customer_type: :vip}
    result = OrderProcessor.process_order(order, 0.10)
    
    assert result.subtotal == 1000.0
    assert result.discount == 100.0
    assert result.tax == 90.0
    assert result.total == 990.0
  end
end
```

## 状態遷移のテスト

状態遷移を純粋関数としてモデル化します。

```elixir
defmodule OrderState do
  def new(id, items) do
    %{id: id, state: :pending, items: items, history: [:pending]}
  end

  def confirm(%{state: :pending} = order) do
    {:ok, %{order | state: :confirmed, history: [:confirmed | order.history]}}
  end
  def confirm(_), do: {:error, "Cannot confirm"}

  def ship(%{state: :confirmed} = order) do
    {:ok, %{order | state: :shipped, history: [:shipped | order.history]}}
  end
  def ship(_), do: {:error, "Cannot ship"}
  
  # ... 他の遷移
end
```

```elixir
describe "OrderState 状態遷移" do
  test "正常な状態遷移" do
    order = OrderState.new("ORD001", [])
    
    {:ok, order} = OrderState.confirm(order)
    assert order.state == :confirmed
    
    {:ok, order} = OrderState.ship(order)
    assert order.state == :shipped
  end

  test "不正な状態遷移はエラー" do
    order = OrderState.new("ORD001", [])
    # pending から直接 ship はできない
    assert {:error, _} = OrderState.ship(order)
  end
end
```

## テストデータビルダーパターン

テストデータを柔軟に構築するパターンです。

```elixir
defmodule UserBuilder do
  def build do
    %{
      name: "Test User",
      email: "test@example.com",
      age: 25,
      role: :user,
      active: true
    }
  end

  def build(overrides) do
    build() |> Map.merge(Map.new(overrides))
  end

  def admin, do: build(role: :admin)
  def inactive, do: build(active: false)

  def build_list(count) do
    Enum.map(1..count, fn i ->
      build(name: "User #{i}", email: "user#{i}@example.com")
    end)
  end
end
```

```elixir
test "管理者のみがアクセス可能" do
  admin = UserBuilder.admin()
  user = UserBuilder.build()
  
  assert authorize(admin, :admin_panel) == :ok
  assert authorize(user, :admin_panel) == :error
end
```

## パラメタライズドテスト

同じテストロジックを複数の入力で実行します。

```elixir
describe "FizzBuzz パラメタライズドテスト" do
  for {input, expected} <- [
    {1, "1"},
    {2, "2"},
    {3, "Fizz"},
    {5, "Buzz"},
    {15, "FizzBuzz"}
  ] do
    test "FizzBuzz.convert(#{input}) == #{expected}" do
      assert FizzBuzz.convert(unquote(input)) == unquote(expected)
    end
  end
end
```

## Result 型を使ったエラーハンドリング

```elixir
defmodule Calculator do
  def divide(_, b) when b == 0 or b == 0.0 do
    {:error, "Division by zero"}
  end
  def divide(a, b), do: {:ok, a / b}

  def chain(n), do: {:ok, n}

  def then_divide({:ok, a}, b), do: divide(a, b)
  def then_divide(error, _), do: error
end
```

```elixir
test "エラーはチェインを通じて伝播する" do
  result =
    Calculator.chain(10)
    |> Calculator.then_divide(0)
    |> Calculator.then_add(5)

  assert {:error, "Division by zero"} = result
end
```

## まとめ

- **TDD サイクル**（Red-Green-Refactor）で段階的に実装
- **依存性注入**でテスタブルな設計
- **純粋関数**でビジネスロジックを表現
- **状態遷移**を純粋関数としてモデル化
- **テストデータビルダー**で柔軟なテストデータ作成
- **パラメタライズドテスト**で多くのケースをカバー

## 関連リソース

- [ExUnit ドキュメント](https://hexdocs.pm/ex_unit/)
- [Test-Driven Development by Example](https://www.amazon.com/Test-Driven-Development-Kent-Beck/dp/0321146530)
