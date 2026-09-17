---
type: Article
title: "第20章: パターン間の相互作用"
description: "関数型設計パターンは単独で使用するよりも、組み合わせることでより強力になります。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第20章: パターン間の相互作用

## はじめに

関数型設計パターンは単独で使用するよりも、組み合わせることでより強力になります。この章では、複数のパターンを統合して複雑な問題を解決する方法を学びます。

## 1. Visitor + Iterator パターン

### 木構造の走査と処理

```elixir
defmodule TreeNode do
  defstruct [:value, :left, :right]

  def leaf(value), do: %__MODULE__{value: value}
  def node(value, left, right), do: %__MODULE__{value: value, left: left, right: right}
end

# イテレータ: 走査戦略
defmodule Iterator do
  def preorder(nil), do: []
  def preorder(%TreeNode{value: v, left: l, right: r}) do
    [v | preorder(l) ++ preorder(r)]
  end

  def inorder(nil), do: []
  def inorder(%TreeNode{value: v, left: l, right: r}) do
    inorder(l) ++ [v] ++ inorder(r)
  end
end

# ビジター: 要素への操作
defmodule Visitor do
  def sum(values), do: Enum.sum(values)
  def max(values), do: Enum.max(values)
  def map(values, func), do: Enum.map(values, func)
end
```

### 組み合わせて使用

```elixir
def traverse_and_visit(tree, iterator_fn, visitor_fn) do
  tree
  |> iterator_fn.()
  |> visitor_fn.()
end

# 使用例
tree
|> traverse_and_visit(&Iterator.inorder/1, &Visitor.sum/1)
```

## 2. Strategy + Factory パターン

### 動的な戦略生成

```elixir
defmodule DiscountFactory do
  def create(:regular), do: &regular_discount/1
  def create(:premium), do: &premium_discount/1
  def create(:vip), do: &vip_discount/1
  def create({:seasonal, rate}), do: fn price -> price * (1 - rate) end
  def create({:combined, strategies}) do
    fn price ->
      Enum.reduce(strategies, price, fn s, acc -> create(s).(acc) end)
    end
  end

  defp regular_discount(price), do: price
  defp premium_discount(price), do: price * 0.9
  defp vip_discount(price), do: price * 0.8
end

# 使用例
strategy = DiscountFactory.create({:combined, [:premium, {:seasonal, 0.1}]})
final_price = strategy.(100.0)  # 81.0
```

## 3. Observer + State パターン

### 状態変化の通知

```elixir
defmodule OrderStateMachine do
  use GenServer

  @transitions %{
    pending: [:confirmed, :cancelled],
    confirmed: [:shipped, :cancelled],
    shipped: [:delivered]
  }

  def transition(pid, new_state) do
    GenServer.call(pid, {:transition, new_state})
  end

  @impl true
  def handle_call({:transition, new_state}, _from, state) do
    allowed = Map.get(@transitions, state.current, [])

    if new_state in allowed do
      # オブザーバーに通知
      notify_observers(state.observers, %{
        old_state: state.current,
        new_state: new_state
      })
      {:reply, {:ok, new_state}, %{state | current: new_state}}
    else
      {:reply, {:error, :invalid_transition}, state}
    end
  end

  defp notify_observers(observers, event) do
    Enum.each(observers, fn {_id, handler} -> handler.(event) end)
  end
end
```

## 4. Decorator + Pipeline パターン

### 処理の動的拡張

```elixir
defmodule DecoratorPipeline do
  def with_logging(func, label) do
    fn input ->
      IO.puts("[#{label}] Input: #{inspect(input)}")
      result = func.(input)
      IO.puts("[#{label}] Output: #{inspect(result)}")
      result
    end
  end

  def with_timing(func) do
    fn input ->
      start = System.monotonic_time(:microsecond)
      result = func.(input)
      elapsed = System.monotonic_time(:microsecond) - start
      {result, elapsed}
    end
  end

  def with_error_handling(func, default \\ nil) do
    fn input ->
      try do
        {:ok, func.(input)}
      rescue
        e -> {:error, e, default}
      end
    end
  end
end

# パイプラインビルダー
defmodule PipelineBuilder do
  defstruct steps: []

  def new, do: %__MODULE__{}

  def add_step(builder, step) do
    %{builder | steps: builder.steps ++ [step]}
  end

  def build(builder, base_func) do
    Enum.reduce(builder.steps, base_func, fn decorator, func ->
      decorator.(func)
    end)
  end
end
```

## 5. Composite + Visitor パターン

### ファイルシステムの例

```elixir
defmodule FileSystem do
  defmodule File do
    defstruct [:name, :size]
  end

  defmodule Directory do
    defstruct [:name, children: []]
  end

  # ビジター: 合計サイズ
  def total_size(%File{size: size}), do: size
  def total_size(%Directory{children: children}) do
    children |> Enum.map(&total_size/1) |> Enum.sum()
  end

  # ビジター: ファイル数
  def file_count(%File{}), do: 1
  def file_count(%Directory{children: children}) do
    children |> Enum.map(&file_count/1) |> Enum.sum()
  end

  # ビジター: パターンマッチでファイル検索
  def find_files(%File{name: name} = file, pattern) do
    if String.match?(name, pattern), do: [file], else: []
  end
  def find_files(%Directory{children: children}, pattern) do
    children |> Enum.flat_map(&find_files(&1, pattern))
  end
end
```

### 式の評価

```elixir
defmodule Expression do
  defmodule Number do
    defstruct [:value]
  end

  defmodule BinaryOp do
    defstruct [:op, :left, :right]
  end

  # 評価ビジター
  def evaluate(%Number{value: v}), do: v
  def evaluate(%BinaryOp{op: :add, left: l, right: r}) do
    evaluate(l) + evaluate(r)
  end
  def evaluate(%BinaryOp{op: :mul, left: l, right: r}) do
    evaluate(l) * evaluate(r)
  end

  # 文字列化ビジター
  def stringify(%Number{value: v}), do: "#{v}"
  def stringify(%BinaryOp{op: op, left: l, right: r}) do
    "(#{stringify(l)} #{op_symbol(op)} #{stringify(r)})"
  end

  defp op_symbol(:add), do: "+"
  defp op_symbol(:mul), do: "*"
end
```

## 6. 統合例: 注文処理システム

```elixir
defmodule OrderProcessor do
  alias DiscountFactory
  alias ValidatorFactory

  defstruct [:customer_type, :items, :validators]

  def new(customer_type) do
    %__MODULE__{customer_type: customer_type, items: [], validators: []}
  end

  def add_item(processor, name, price) do
    %{processor | items: [{name, price} | processor.items]}
  end

  def add_validator(processor, validator_type) do
    %{processor | validators: [validator_type | processor.validators]}
  end

  def calculate_total(processor) do
    strategy = DiscountFactory.create(processor.customer_type)
    processor.items
    |> Enum.map(fn {_name, price} -> strategy.(price) end)
    |> Enum.sum()
  end

  def process(processor) do
    # パイプラインで処理
    PipelineBuilder.new()
    |> PipelineBuilder.add_error_handling(0.0)
    |> PipelineBuilder.execute(fn _ -> calculate_total(processor) end, processor)
  end
end
```

## まとめ

パターンの組み合わせにより、以下が実現できます：

1. **柔軟性**: 異なる走査戦略と処理を組み合わせ
2. **再利用性**: 共通のビルディングブロックを様々な文脈で使用
3. **拡張性**: デコレータで既存の処理を拡張
4. **型安全性**: パターンマッチによる堅牢な処理分岐

パターンを単独で理解した後は、組み合わせて使う練習が重要です。
