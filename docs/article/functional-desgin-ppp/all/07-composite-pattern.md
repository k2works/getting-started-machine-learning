---
type: Article
title: "第7章: Composite パターン — 6言語統合ガイド"
description: "Composite パターンは、個別のオブジェクトと複合オブジェクトを同一のインターフェースで扱う GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第7章: Composite パターン — 6言語統合ガイド

## 1. はじめに

Composite パターンは、個別のオブジェクトと複合オブジェクトを同一のインターフェースで扱う GoF パターンです。関数型プログラミングでは、**再帰的なデータ型（ADT / 判別共用体 / enum）**がこのパターンを自然に表現します。ツリー構造のデータに対する操作が、パターンマッチと再帰で簡潔に記述できます。

> **Elixir の読者へ**: Elixir 版は本章で「副作用と純粋関数」を扱っています。Elixir 固有のアプローチについては[コラム](#elixir-コラム副作用と純粋関数)を参照してください。

## 2. 共通の本質

### Composite の構造

```
Shape = Circle radius
      | Square side
      | Composite [Shape]    ← 再帰的な定義
```

- **Leaf（葉）**: 個別の要素（Circle, Square）
- **Composite（複合）**: 子要素のリストを持つノード
- **統一インターフェース**: Leaf と Composite を同じ関数で処理

### 典型的な操作

- **面積計算**: 各図形の面積を再帰的に合計
- **移動（translate）**: すべての要素を一括移動
- **バウンディングボックス**: 複合図形の外接矩形を計算

## 3. 言語別実装比較

### 3.1 再帰的データ型の定義

| 言語 | 型定義 | 再帰の表現 |
|------|--------|----------|
| Clojure | マップ + `::type` キー | `:children` リスト |
| Scala | `sealed trait` + `case class` | `children: Vector[Shape]` |
| F# | 判別共用体 | `Composite of Shape list` |
| Haskell | ADT | `CompositeShape [Shape]` |
| Rust | `enum` | `Composite { shapes: Vec<Shape> }` |

<details>
<summary>Haskell: ADT による再帰的定義</summary>

```haskell
data Shape
    = Circle Point Double
    | Square Point Double
    | Rectangle Point Double Double
    | CompositeShape [Shape]

area :: Shape -> Double
area shape = case shape of
    Circle _ r        -> pi * r * r
    Square _ s        -> s * s
    Rectangle _ w h   -> w * h
    CompositeShape ss -> sum (map area ss)
```

`CompositeShape [Shape]` が再帰的な定義のポイントです。`area` 関数は `map area` で子要素に再帰適用します。

</details>

<details>
<summary>Scala: sealed trait + パターンマッチ</summary>

```scala
sealed trait Shape
case class Circle(center: Point, radius: Double) extends Shape
case class Square(topLeft: Point, side: Double) extends Shape
case class CompositeShape(children: Vector[Shape]) extends Shape

def area(shape: Shape): Double = shape match
  case Circle(_, r)          => math.Pi * r * r
  case Square(_, s)          => s * s
  case CompositeShape(children) => children.map(area).sum
```

</details>

<details>
<summary>F#: 判別共用体</summary>

```fsharp
type Shape =
    | Circle of center: Point * radius: float
    | Square of topLeft: Point * side: float
    | Composite of name: string * shapes: Shape list

let rec area (shape: Shape) : float =
    match shape with
    | Circle(_, r) -> System.Math.PI * r * r
    | Square(_, s) -> s * s
    | Composite(_, shapes) -> shapes |> List.sumBy area
```

`rec` キーワードで再帰関数を明示します。`List.sumBy` で簡潔に合計を計算できます。

</details>

<details>
<summary>Rust: enum + Box</summary>

```rust
pub enum Shape {
    Circle { center: Point, radius: f64 },
    Square { top_left: Point, side: f64 },
    Composite { shapes: Vec<Shape> },
}

impl Shape {
    pub fn area(&self) -> f64 {
        match self {
            Shape::Circle { radius, .. } =>
                std::f64::consts::PI * radius * radius,
            Shape::Square { side, .. } => side * side,
            Shape::Composite { shapes } =>
                shapes.iter().map(|s| s.area()).sum(),
        }
    }
}
```

Rust では `Vec<Shape>` で所有権を持つ子要素リストを表現します。

</details>

<details>
<summary>Clojure: マップ + マルチメソッド</summary>

```clojure
(defn make-circle [center radius]
  {::type ::circle ::center center ::radius radius})

(defn make-composite [& shapes]
  {::type ::composite ::children (vec shapes)})

(defmulti area ::type)
(defmethod area ::circle [{:keys [radius]}]
  (* Math/PI radius radius))
(defmethod area ::square [{:keys [side]}]
  (* side side))
(defmethod area ::composite [{:keys [children]}]
  (reduce + (map area children)))
```

</details>

### 3.2 移動（translate）操作

複合図形全体を移動する操作は、Composite パターンの典型的な活用例です。

<details>
<summary>translate の実装比較</summary>

```haskell
-- Haskell
translate :: Double -> Double -> Shape -> Shape
translate dx dy shape = case shape of
    Circle (Point x y) r -> Circle (Point (x+dx) (y+dy)) r
    Square (Point x y) s -> Square (Point (x+dx) (y+dy)) s
    CompositeShape ss    -> CompositeShape (map (translate dx dy) ss)
```

```scala
// Scala
def translate(dx: Double, dy: Double)(shape: Shape): Shape = shape match
  case Circle(Point(x, y), r) => Circle(Point(x+dx, y+dy), r)
  case Square(Point(x, y), s) => Square(Point(x+dx, y+dy), s)
  case CompositeShape(cs)     => CompositeShape(cs.map(translate(dx, dy)))
```

```rust
// Rust
pub fn translate(&self, dx: f64, dy: f64) -> Shape {
    match self {
        Shape::Circle { center, radius } =>
            Shape::Circle {
                center: Point { x: center.x + dx, y: center.y + dy },
                radius: *radius,
            },
        Shape::Composite { shapes } =>
            Shape::Composite {
                shapes: shapes.iter().map(|s| s.translate(dx, dy)).collect(),
            },
        // ...
    }
}
```

</details>

すべての言語で、Composite ケースは**子要素に対して同じ関数を再帰適用**する同じパターンです。

### 3.3 flatten（ネスト構造の平坦化）

<details>
<summary>ネスト構造の平坦化</summary>

```scala
// Scala
def flatten(shape: Shape): Vector[Shape] = shape match
  case CompositeShape(children) => children.flatMap(flatten)
  case leaf                     => Vector(leaf)
```

```haskell
-- Haskell
flatten :: Shape -> [Shape]
flatten (CompositeShape ss) = concatMap flatten ss
flatten leaf                = [leaf]
```

```fsharp
// F#
let rec flatten (shape: Shape) : Shape list =
    match shape with
    | Composite(_, shapes) -> shapes |> List.collect flatten
    | leaf -> [leaf]
```

</details>

## 4. 比較分析

### 4.1 再帰的データ型の表現力

| 言語 | 型安全性 | ネストの深さ制限 | メモリ管理 |
|------|---------|----------------|----------|
| Clojure | なし（動的） | なし | GC（JVM） |
| Scala | sealed で網羅性保証 | なし | GC（JVM） |
| F# | 判別共用体で網羅性保証 | なし | GC（.NET） |
| Haskell | ADT で網羅性保証 | なし（遅延評価） | GC（GHC） |
| Rust | enum で網羅性保証 | `Vec` で所有権管理 | 所有権システム |

### 4.2 OOP Composite vs 関数型 Composite

| 観点 | OOP | 関数型 |
|------|-----|--------|
| 型定義 | インターフェース + クラス階層 | ADT / 判別共用体 / enum |
| 操作追加 | 各クラスにメソッド追加 | 新しい関数を定義 |
| データ構造 | ミュータブルな子リスト | 不変の再帰構造 |
| 操作の統一性 | `accept` メソッド | パターンマッチ |

## 5. Elixir コラム：副作用と純粋関数

Elixir の第 7 章は Composite パターンではなく、**副作用と純粋関数の分離**を扱っています。

### Functional Core / Imperative Shell

```elixir
# 純粋関数（テスト容易）
defmodule PriceCalculator do
  def calculate(items, tax_rate) do
    subtotal = Enum.sum(Enum.map(items, & &1.price))
    subtotal * (1 + tax_rate)
  end
end

# 副作用を含むシェル
defmodule OrderService do
  def process_order(order_id) do
    items = Repo.get_items(order_id)        # I/O
    total = PriceCalculator.calculate(items, 0.1)  # 純粋
    Repo.save_total(order_id, total)        # I/O
  end
end
```

### Effect as Data

副作用をデータとして表現し、実行を遅延させるパターンです：

```elixir
defmodule Effects do
  def log(message), do: {:log, message}
  def save(data), do: {:save, data}
  def fetch(id), do: {:fetch, id}
end

# 効果を解釈する
def interpret({:log, msg}), do: IO.puts(msg)
def interpret({:save, data}), do: Repo.save(data)
def interpret({:fetch, id}), do: Repo.get(id)
```

このアプローチは Haskell の IO モナドと同じ発想であり、「何をするか」と「どう実行するか」を分離します。

## 6. まとめ

Composite パターンは、関数型プログラミングの**再帰的データ型**で最も自然に表現されます：

1. **再帰的 ADT**: 個別要素と複合要素を同じ型で定義
2. **パターンマッチ + 再帰**: 統一的な操作を簡潔に記述
3. **不変性**: 構造の変更は新しいツリーを生成

## 言語別個別記事

- [Clojure](../clojure/07-composite-pattern.md) | [Scala](../scala/07-composite-pattern.md) | [Elixir](../elixir/07-effects-and-pure-functions.md) | [F#](../fsharp/07-composite-pattern.md) | [Haskell](../haskell/07-composite-pattern.md) | [Rust](../rust/07-composite-pattern.md)
