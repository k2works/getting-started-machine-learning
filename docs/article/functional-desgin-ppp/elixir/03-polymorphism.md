---
type: Article
title: "第3章: 多態性の実現方法"
description: "多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第3章: 多態性の実現方法

## はじめに

多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。Elixir では、パターンマッチング、プロトコル、ビヘイビアという複数のメカニズムで多態性を実現します。

本章では、これらのメカニズムを使い分けて、柔軟で拡張性の高いコードを書く方法を学びます。

## 1. パターンマッチングによる多態性

Elixir ではタグ付きタプルと関数のパターンマッチングを使って、代数的データ型（ADT）に相当する設計を実現できます。

### 基本的な使い方

```elixir
# 図形をタグ付きタプルで表現
@type shape ::
  {:rectangle, width :: number(), height :: number()}
  | {:circle, radius :: number()}
  | {:triangle, base :: number(), height :: number()}

# 図形の面積を計算する
def calculate_area({:rectangle, width, height}), do: width * height
def calculate_area({:circle, radius}), do: :math.pi() * radius * radius
def calculate_area({:triangle, base, height}), do: base * height / 2

# 使用例
calculate_area({:rectangle, 4, 5})  # => 20
calculate_area({:circle, 3})        # => 28.27...
calculate_area({:triangle, 6, 5})   # => 15.0
```

### 構造体を使った多態性

```elixir
defmodule Rectangle do
  defstruct [:width, :height]
end

defmodule Circle do
  defstruct [:radius]
end

defmodule Triangle do
  defstruct [:base, :height]
end

def area(%Rectangle{width: w, height: h}), do: w * h
def area(%Circle{radius: r}), do: :math.pi() * r * r
def area(%Triangle{base: b, height: h}), do: b * h / 2

# 使用例
area(%Rectangle{width: 4, height: 5})  # => 20
area(%Circle{radius: 3})               # => 28.27...
```

## 2. 複合ディスパッチ

タプルを使ったパターンマッチングで、複数の値に基づくディスパッチを実現できます。

```elixir
defmodule Payment do
  defstruct [:method, :currency, :amount]
end

defmodule PaymentResult do
  defstruct [:status, :message, :amount, :converted]
end

def process_payment(%Payment{method: method, currency: currency, amount: amount}) do
  case {method, currency} do
    {:credit_card, :jpy} ->
      %PaymentResult{
        status: "processed",
        message: "クレジットカード（円）で処理しました",
        amount: amount
      }

    {:credit_card, :usd} ->
      %PaymentResult{
        status: "processed",
        message: "Credit card (USD) processed",
        amount: amount,
        converted: amount * 150
      }

    {:bank_transfer, :jpy} ->
      %PaymentResult{
        status: "pending",
        message: "銀行振込を受け付けました",
        amount: amount
      }

    _ ->
      %PaymentResult{
        status: "error",
        message: "サポートされていない支払い方法です",
        amount: amount
      }
  end
end

# 使用例
process_payment(%Payment{method: :credit_card, currency: :jpy, amount: 1000})
# => %PaymentResult{status: "processed", message: "クレジットカード（円）で処理しました", ...}
```

## 3. プロトコルによる多態性

プロトコルは、特定の操作セットを定義するインターフェースです。異なる型に対して同じ関数を呼び出せるようになります。

### プロトコルの定義と実装

```elixir
defprotocol Describable do
  @doc "オブジェクトの説明を返す"
  @spec describe(t) :: String.t()
  def describe(value)
end

defmodule Product do
  defstruct [:name, :price]
end

defmodule Service do
  defstruct [:name, :hourly_rate]
end

defimpl Describable, for: Product do
  def describe(%Product{name: name, price: price}) do
    "商品: #{name} (#{price}円)"
  end
end

defimpl Describable, for: Service do
  def describe(%Service{name: name, hourly_rate: rate}) do
    "サービス: #{name} (時給#{rate}円)"
  end
end

# 組み込み型にも実装できる
defimpl Describable, for: Map do
  def describe(map) do
    "マップ with #{map_size(map)} keys"
  end
end

defimpl Describable, for: List do
  def describe(list) do
    "リスト with #{length(list)} elements"
  end
end

# 使用例
Describable.describe(%Product{name: "りんご", price: 150})
# => "商品: りんご (150円)"

Describable.describe(%{a: 1, b: 2, c: 3})
# => "マップ with 3 keys"
```

### プロトコルの利点

1. **拡張性**: 既存の型に対して後から振る舞いを追加可能
2. **分離**: 型定義とプロトコル実装を別ファイルに分離可能
3. **多態性**: 異なる型に対して統一的なインターフェースを提供

## 4. ビヘイビアによる多態性

ビヘイビアは、モジュールが実装すべきコールバック関数を定義します。Java のインターフェースに似ています。

```elixir
defmodule Serializer do
  @callback serialize(data :: any()) :: String.t()
  @callback deserialize(string :: String.t()) :: any()
end

defmodule JsonSerializer do
  @behaviour Serializer

  @impl Serializer
  def serialize(data) do
    # JSON シリアライズ実装
    inspect(data)
  end

  @impl Serializer
  def deserialize(_string) do
    %{}
  end
end

defmodule CsvSerializer do
  @behaviour Serializer

  @impl Serializer
  def serialize(data) when is_list(data) do
    data
    |> Enum.map(&Enum.join(&1, ","))
    |> Enum.join("\n")
  end

  def serialize(_), do: ""

  @impl Serializer
  def deserialize(string) do
    string
    |> String.split("\n")
    |> Enum.map(&String.split(&1, ","))
  end
end

# 使用例
CsvSerializer.serialize([["a", "b", "c"], ["1", "2", "3"]])
# => "a,b,c\n1,2,3"
```

### ビヘイビアとプロトコルの使い分け

| 特性 | プロトコル | ビヘイビア |
|-----|-----------|-----------|
| ディスパッチ | データの型による | モジュールによる |
| 用途 | 異なる型に同じ操作 | 交換可能な実装 |
| 例 | `Enumerable`, `String.Chars` | `GenServer`, `Supervisor` |

## 5. 型クラス相当（プロトコル + 実装）

プロトコルを使って、関数型言語の型クラスに相当するパターンを実現できます。

```elixir
defprotocol Monoid do
  @doc "結合演算"
  @spec combine(t, t) :: t
  def combine(a, b)
end

defmodule Sum do
  defstruct [:value]
  def empty, do: %Sum{value: 0}
end

defmodule Multiply do
  defstruct [:value]
  def empty, do: %Multiply{value: 1}
end

defimpl Monoid, for: Sum do
  def combine(%Sum{value: a}, %Sum{value: b}), do: %Sum{value: a + b}
end

defimpl Monoid, for: Multiply do
  def combine(%Multiply{value: a}, %Multiply{value: b}), do: %Multiply{value: a * b}
end

defimpl Monoid, for: List do
  def combine(a, b), do: a ++ b
end

# モノイドのリストを畳み込む
def fold_monoid(list, empty) do
  Enum.reduce(list, empty, &Monoid.combine(&2, &1))
end

# 使用例
sums = [%Sum{value: 1}, %Sum{value: 2}, %Sum{value: 3}]
fold_monoid(sums, Sum.empty())
# => %Sum{value: 6}
```

## 6. 動的ディスパッチ

マップを使った関数のディスパッチで、実行時に柔軟な振る舞いの切り替えができます。

```elixir
def dispatch(handlers, operation, args) do
  case Map.get(handlers, operation) do
    nil -> raise ArgumentError, "Unknown operation: #{operation}"
    handler -> apply(handler, args)
  end
end

def create_calculator do
  handlers = %{
    add: fn a, b -> a + b end,
    subtract: fn a, b -> a - b end,
    multiply: fn a, b -> a * b end,
    divide: fn a, b -> a / b end
  }

  fn operation, a, b ->
    dispatch(handlers, operation, [a, b])
  end
end

# 使用例
calc = create_calculator()
calc.(:add, 5, 3)       # => 8
calc.(:divide, 10, 2)   # => 5.0
```

## 7. 式の評価（再帰的なパターンマッチング）

パターンマッチングを使った再帰的なデータ構造の処理は、Elixir の得意分野です。

```elixir
@type expr ::
  {:num, number()}
  | {:add, expr(), expr()}
  | {:sub, expr(), expr()}
  | {:mul, expr(), expr()}
  | {:div, expr(), expr()}

def evaluate({:num, n}), do: n
def evaluate({:add, left, right}), do: evaluate(left) + evaluate(right)
def evaluate({:sub, left, right}), do: evaluate(left) - evaluate(right)
def evaluate({:mul, left, right}), do: evaluate(left) * evaluate(right)
def evaluate({:div, left, right}), do: evaluate(left) / evaluate(right)

def expr_to_string({:num, n}), do: to_string(n)
def expr_to_string({:add, left, right}) do
  "(#{expr_to_string(left)} + #{expr_to_string(right)})"
end
# ... 他の演算子も同様

# 使用例
expr = {:add, {:num, 5}, {:mul, {:num, 2}, {:num, 3}}}
evaluate(expr)          # => 11
expr_to_string(expr)    # => "(5 + (2 * 3))"
```

## 8. ガード節による多態性

ガード節を使うと、型や値の条件に基づいて関数をディスパッチできます。

```elixir
def describe_value(n) when is_integer(n) and n > 0, do: "正の整数: #{n}"
def describe_value(n) when is_integer(n) and n < 0, do: "負の整数: #{n}"
def describe_value(0), do: "ゼロ"
def describe_value(n) when is_float(n), do: "浮動小数点数: #{n}"
def describe_value(s) when is_binary(s), do: "文字列: #{s}"
def describe_value(list) when is_list(list), do: "リスト（要素数: #{length(list)}）"
def describe_value(map) when is_map(map), do: "マップ（キー数: #{map_size(map)}）"
def describe_value(_), do: "その他"

# 使用例
describe_value(42)      # => "正の整数: 42"
describe_value(-5)      # => "負の整数: -5"
describe_value(3.14)    # => "浮動小数点数: 3.14"
describe_value("hello") # => "文字列: hello"
```

## まとめ

本章では、Elixir における多態性の実現方法について学びました：

1. **パターンマッチング**: タグ付きタプルと関数のマルチヘッドによる ADT
2. **構造体**: 明示的な型と構造体によるパターンマッチング
3. **複合ディスパッチ**: 複数の値に基づく分岐
4. **プロトコル**: 型に対する振る舞いの定義と実装
5. **ビヘイビア**: モジュールレベルのインターフェース定義
6. **動的ディスパッチ**: マップによる関数の実行時切り替え
7. **ガード節**: 型や値の条件による分岐

これらのメカニズムを適切に使い分けることで、柔軟で保守しやすいコードを書くことができます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/elixir/part1/lib/chapter03.ex`
- テストコード: `apps/elixir/part1/test/chapter03_test.exs`

## 次章予告

次章では、**データ検証**について学びます。Elixir での型チェックとバリデーションパターンを探ります。
