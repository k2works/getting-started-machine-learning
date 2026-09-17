---
type: Article
title: "第12章: Visitor パターン — 6言語統合ガイド"
description: "Visitor パターンは、データ構造と操作を分離する GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第12章: Visitor パターン — 6言語統合ガイド

## 1. はじめに

Visitor パターンは、データ構造と操作を分離する GoF パターンです。OOP では「ダブルディスパッチ」を使いますが、関数型プログラミングでは**パターンマッチが Visitor を自然に置き換えます**。データ型の定義と操作関数の分離は、関数型言語の基本設計そのものです。

## 2. 共通の本質

### Visitor の本質

```
Visitor = データ型の各バリアントに対する操作の集合
```

OOP では accept/visit メソッドの二重ディスパッチが必要ですが、関数型では**パターンマッチで直接分岐**するだけです。

### 典型的な操作

- **面積計算**: 図形の種類に応じた面積算出
- **JSON 変換**: 図形をシリアライズ
- **描画**: 図形の種類に応じた出力
- **式の評価**: 数式 AST の評価・簡略化

## 3. 言語別実装比較

### 3.1 Visitor の実現方法

| 言語 | Visitor の表現 | パターンマッチ |
|------|-------------|-------------|
| Clojure | マルチメソッド | `::type` キーでディスパッチ |
| Scala | trait + extension / パターンマッチ | `match` 式 |
| Elixir | プロトコル + `defimpl` | 関数頭部のパターンマッチ |
| F# | 関数 + `match` 式 | 判別共用体の網羅的マッチ |
| Haskell | 関数 + `case` 式 | ADT の網羅的マッチ |
| Rust | 関数 + `match` 式 | enum の網羅的マッチ |

### 3.2 図形の面積計算

<details>
<summary>Clojure: マルチメソッド</summary>

```clojure
(defmulti calculate-area ::shape/type)

(defmethod calculate-area ::circle/circle [circle]
  (* Math/PI (:radius circle) (:radius circle)))

(defmethod calculate-area ::square/square [square]
  (* (:side square) (:side square)))

(defmethod calculate-area ::rectangle/rectangle [rect]
  (* (:width rect) (:height rect)))
```

新しい操作（Visitor）の追加は `defmulti` + `defmethod` を書くだけです。

</details>

<details>
<summary>Scala: パターンマッチ + 型クラス</summary>

```scala
// パターンマッチ版
def area(shape: Shape): Double = shape match
  case Circle(_, radius)      => math.Pi * radius * radius
  case Square(_, side)        => side * side
  case Rectangle(_, w, h)     => w * h

// 型クラス版（拡張メソッド）
trait HasArea[A]:
  extension (a: A) def area: Double

given HasArea[Circle] with
  extension (c: Circle) def area: Double = math.Pi * c.radius * c.radius
```

Scala は OOP スタイル（accept/visit）と関数型スタイル（パターンマッチ）の両方を提示しています。

</details>

<details>
<summary>F#: 関数 + パターンマッチ</summary>

```fsharp
let calculateArea (shape: Shape) : float =
    match shape with
    | Shape.Circle(_, r) -> System.Math.PI * r * r
    | Shape.Square(_, s) -> s * s
    | Shape.Rectangle(_, w, h) -> w * h
    | Shape.Composite(_, shapes) ->
        shapes |> List.sumBy calculateArea
```

各 Visitor は独立した関数として定義されます。Composite の再帰的な処理もパターンマッチで自然に表現できます。

</details>

<details>
<summary>Haskell: 関数 + case 式</summary>

```haskell
shapeArea :: Shape -> Double
shapeArea shape = case shape of
    Circle _ r       -> pi * r * r
    Square _ s       -> s * s
    Rectangle _ w h  -> w * h
    Composite _ shs  -> sum (map shapeArea shs)
```

Haskell では Visitor パターンは**単なる関数**です。型クラスを使えば、新しい型に対して操作を追加することもできます。

</details>

<details>
<summary>Rust: match 式</summary>

```rust
pub fn area(shape: &Shape) -> f64 {
    match shape {
        Shape::Circle { radius, .. } => std::f64::consts::PI * radius * radius,
        Shape::Square { side, .. } => side * side,
        Shape::Rectangle { width, height, .. } => width * height,
        Shape::Composite { shapes, .. } => shapes.iter().map(area).sum(),
    }
}
```

</details>

<details>
<summary>Elixir: プロトコル</summary>

```elixir
defprotocol ShapeVisitor do
  def visit(visitor, shape)
end

defmodule AreaVisitor do
  defstruct []
end

defimpl ShapeVisitor, for: AreaVisitor do
  def visit(_visitor, %Circle{radius: r}) do
    :math.pi() * r * r
  end

  def visit(_visitor, %Square{side: s}) do
    s * s
  end
end
```

</details>

### 3.3 JSON シリアライズ（複数 Visitor の例）

面積計算と JSON 変換は、同じデータ型に対する**異なる操作（Visitor）**です。

<details>
<summary>全言語での JSON Visitor 比較</summary>

```clojure
;; Clojure
(defmulti to-json ::shape/type)
(defmethod to-json ::circle/circle [{:keys [center radius]}]
  (format "{\"type\":\"circle\",\"radius\":%s}" radius))
```

```scala
// Scala
def toJson(shape: Shape): String = shape match
  case Circle(Point(x, y), r) =>
    s"""{"type":"circle","x":$x,"y":$y,"radius":$r}"""
  case Square(Point(x, y), s) =>
    s"""{"type":"square","x":$x,"y":$y,"side":$s}"""
```

```fsharp
// F#
let toJson (shape: Shape) : string =
    match shape with
    | Shape.Circle((x, y), r) ->
        sprintf """{"type":"circle","x":%f,"y":%f,"radius":%f}""" x y r
```

```haskell
-- Haskell
shapeToJson :: Shape -> String
shapeToJson shape = case shape of
    Circle (Point x y) r ->
        "{\"type\":\"circle\",\"radius\":" ++ show r ++ "}"
```

```rust
// Rust
pub fn to_json(shape: &Shape) -> String {
    match shape {
        Shape::Circle { center, radius } =>
            format!(r#"{{"type":"circle","radius":{}}}"#, radius),
        // ...
    }
}
```

</details>

### 3.4 式の評価（AST Visitor）

数式の AST（抽象構文木）に対する操作は、Visitor パターンの高度な活用例です。

<details>
<summary>数式 AST の評価比較</summary>

```haskell
-- Haskell
data Expr
    = Number Double
    | Add Expr Expr
    | Multiply Expr Expr
    | Variable String

evaluate :: Map String Double -> Expr -> Double
evaluate vars expr = case expr of
    Number n       -> n
    Add l r        -> evaluate vars l + evaluate vars r
    Multiply l r   -> evaluate vars l * evaluate vars r
    Variable name  -> vars ! name
```

```rust
// Rust
pub enum Expr {
    Number(f64),
    Add(Box<Expr>, Box<Expr>),
    Multiply(Box<Expr>, Box<Expr>),
}

pub fn evaluate(expr: &Expr, vars: &HashMap<String, f64>) -> f64 {
    match expr {
        Expr::Number(n) => *n,
        Expr::Add(l, r) => evaluate(l, vars) + evaluate(r, vars),
        Expr::Multiply(l, r) => evaluate(l, vars) * evaluate(r, vars),
    }
}
```

```fsharp
// F#
type Expr =
    | Number of float
    | Add of Expr * Expr
    | Multiply of Expr * Expr

let rec evaluate (vars: Map<string, float>) (expr: Expr) : float =
    match expr with
    | Number n -> n
    | Add(l, r) -> evaluate vars l + evaluate vars r
    | Multiply(l, r) -> evaluate vars l * evaluate vars r
```

</details>

## 4. 比較分析

### 4.1 Visitor パターンが不要になる理由

関数型言語では、OOP の Visitor パターンが提供する機能を**パターンマッチが自然に提供**します。

| OOP Visitor の要素 | 関数型の対応 |
|-------------------|-----------|
| accept メソッド | 不要（パターンマッチで直接分岐） |
| visit メソッド群 | 各ケースの処理関数 |
| ConcreteVisitor | 独立した関数 |
| ダブルディスパッチ | パターンマッチ |
| 新しい操作追加 | 新しい関数を定義するだけ |

### 4.2 Expression Problem

Visitor パターンは Expression Problem（新しいデータ型と新しい操作の両方を追加する困難さ）と深く関連しています。

| 追加対象 | 関数型の容易さ | OOP の容易さ |
|---------|-------------|-------------|
| 新しい操作 | 容易（関数を追加） | 困難（全クラスに visit 追加） |
| 新しいデータ型 | 困難（全関数に分岐追加） | 容易（クラスを追加） |

静的型付け言語（F#, Haskell, Rust, Scala）では、データ型を追加した際に**コンパイラが未処理のケースを検出**してくれます。動的型付け言語（Clojure, Elixir）ではテストで確認する必要があります。

### 4.3 fold ベースの汎用 Visitor

Haskell では `fold` を使った汎用的な Visitor パターンを定義できます：

```haskell
treeFold :: (a -> b) -> (b -> b -> b) -> Tree a -> b
treeFold leafFn nodeFn tree = case tree of
    Leaf x       -> leafFn x
    Node left right -> nodeFn (treeFold leafFn nodeFn left)
                              (treeFold leafFn nodeFn right)
```

これにより、任意の操作を fold の引数として渡すだけで新しい Visitor を定義できます。

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 動的な操作追加 | Clojure | マルチメソッドでオープンに拡張 |
| AST 処理 | Haskell, F# | ADT + パターンマッチが最適 |
| 既存型への操作追加 | Scala | 型クラス + extension メソッド |
| コンパイル時の網羅性 | F#, Haskell, Rust | パターンマッチの網羅性チェック |
| プロトコルベースの拡張 | Elixir | 既存型に後からプロトコル実装を追加可能 |

## 6. まとめ

Visitor パターンは、関数型プログラミングにおいて**最も自然に消滅する**GoF パターンです：

1. **パターンマッチが Visitor**: データ型の分岐は言語の基本機能
2. **操作の追加が容易**: 新しい関数を定義するだけで新しい Visitor
3. **Expression Problem**: 新しいデータ型追加時のコンパイラ支援が言語間で異なる

## 言語別個別記事

- [Clojure](../clojure/12-visitor-pattern.md) | [Scala](../scala/12-visitor-pattern.md) | [Elixir](../elixir/12-visitor-pattern.md) | [F#](../fsharp/12-visitor-pattern.md) | [Haskell](../haskell/12-visitor-pattern.md) | [Rust](../rust/12-visitor-pattern.md)
