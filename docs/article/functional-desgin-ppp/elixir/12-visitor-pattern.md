---
type: Article
title: "第12章: Visitor パターン"
description: "Visitor パターンは、データ構造と操作を分離し、既存のデータ構造を変更することなく新しい操作を追加できるようにするパターンです。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第12章: Visitor パターン

## はじめに

Visitor パターンは、データ構造と操作を分離し、既存のデータ構造を変更することなく新しい操作を追加できるようにするパターンです。

Elixir では、プロトコルを使用して Visitor インターフェースを定義し、各図形タイプに対する実装を提供します。

## 1. Element: 図形の定義

```elixir
defmodule Circle do
  defstruct [:center, :radius]
  def new(center, radius), do: %__MODULE__{center: center, radius: radius}
end

defmodule Square do
  defstruct [:top_left, :side]
  def new(top_left, side), do: %__MODULE__{top_left: top_left, side: side}
end

defmodule Rectangle do
  defstruct [:top_left, :width, :height]
  def new(top_left, width, height) do
    %__MODULE__{top_left: top_left, width: width, height: height}
  end
end
```

## 2. Visitor プロトコル

```elixir
defprotocol ShapeVisitor do
  @doc "図形を訪問して処理を行う"
  def visit(visitor, shape)
end
```

## 3. JSON Visitor

```elixir
defmodule JsonVisitor do
  defstruct []
  def new, do: %__MODULE__{}

  def to_json(shape), do: ShapeVisitor.visit(new(), shape)
end

defimpl ShapeVisitor, for: JsonVisitor do
  def visit(_visitor, %Circle{center: {x, y}, radius: r}) do
    ~s({"type":"circle","center":[#{x},#{y}],"radius":#{r}})
  end

  def visit(_visitor, %Square{top_left: {x, y}, side: s}) do
    ~s({"type":"square","topLeft":[#{x},#{y}],"side":#{s}})
  end

  def visit(_visitor, %Rectangle{top_left: {x, y}, width: w, height: h}) do
    ~s({"type":"rectangle","topLeft":[#{x},#{y}],"width":#{w},"height":#{h}})
  end
end
```

## 4. Area Visitor

```elixir
defmodule AreaVisitor do
  defstruct []
  def new, do: %__MODULE__{}

  def calculate_area(shape), do: ShapeVisitor.visit(new(), shape)

  def total_area(shapes) do
    Enum.reduce(shapes, 0, &(&2 + calculate_area(&1)))
  end
end

defimpl ShapeVisitor, for: AreaVisitor do
  def visit(_visitor, %Circle{radius: r}) do
    :math.pi() * r * r
  end

  def visit(_visitor, %Square{side: s}) do
    s * s
  end

  def visit(_visitor, %Rectangle{width: w, height: h}) do
    w * h
  end
end
```

## 5. Perimeter Visitor

```elixir
defmodule PerimeterVisitor do
  defstruct []
  def new, do: %__MODULE__{}

  def calculate_perimeter(shape), do: ShapeVisitor.visit(new(), shape)
end

defimpl ShapeVisitor, for: PerimeterVisitor do
  def visit(_visitor, %Circle{radius: r}), do: 2 * :math.pi() * r
  def visit(_visitor, %Square{side: s}), do: 4 * s
  def visit(_visitor, %Rectangle{width: w, height: h}), do: 2 * (w + h)
end
```

## 6. SVG Visitor

```elixir
defmodule SvgVisitor do
  defstruct fill: "none", stroke: "black", stroke_width: 1

  def new(opts \\ []) do
    %__MODULE__{
      fill: Keyword.get(opts, :fill, "none"),
      stroke: Keyword.get(opts, :stroke, "black"),
      stroke_width: Keyword.get(opts, :stroke_width, 1)
    }
  end
end

defimpl ShapeVisitor, for: SvgVisitor do
  def visit(%{fill: f, stroke: s, stroke_width: sw}, %Circle{center: {cx, cy}, radius: r}) do
    ~s(<circle cx="#{cx}" cy="#{cy}" r="#{r}" fill="#{f}" stroke="#{s}" stroke-width="#{sw}"/>)
  end
  # ... 他の図形も同様
end
```

## 7. 複合 Visitor

```elixir
defmodule CompositeVisitor do
  def apply_all(shape, visitors) do
    Map.new(visitors, fn {key, visitor} ->
      {key, ShapeVisitor.visit(visitor, shape)}
    end)
  end
end

# 使用例
results = CompositeVisitor.apply_all(circle, [
  {:json, JsonVisitor.new()},
  {:area, AreaVisitor.new()}
])
```

## 8. DrawingContext

```elixir
defmodule DrawingContext do
  defstruct shapes: []

  def add_shape(ctx, shape), do: %{ctx | shapes: ctx.shapes ++ [shape]}

  def to_json(%{shapes: shapes}) do
    json_items = Enum.map(shapes, &JsonVisitor.to_json/1)
    "[" <> Enum.join(json_items, ",") <> "]"
  end

  def total_area(%{shapes: shapes}), do: AreaVisitor.total_area(shapes)
end
```

## まとめ

Elixir での Visitor パターンのポイント：

- **Protocol** で Visitor インターフェースを定義
- **defimpl** で各図形タイプに対する実装を提供
- 新しい操作を追加する際は新しい Visitor を作成
- 既存の図形コードを変更せずに拡張可能
- **CompositeVisitor** で複数の操作を一度に適用
