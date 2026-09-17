---
type: Article
title: "第13章: Abstract Factory パターン — 6言語統合ガイド"
description: "Abstract Factory パターンは、関連するオブジェクトのファミリーを一貫した方法で生成する GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第13章: Abstract Factory パターン — 6言語統合ガイド

## 1. はじめに

Abstract Factory パターンは、関連するオブジェクトのファミリーを一貫した方法で生成する GoF パターンです。OOP ではファクトリインターフェースと具象ファクトリクラスを用いますが、関数型プログラミングでは**ファクトリ関数のレコード / マップ / モジュール**として表現されます。スタイル付き図形、プラットフォーム別 UI、データベースアダプターなどの生成を統一的に管理します。

## 2. 共通の本質

### Abstract Factory の構造

```
Factory = { createA: params → A, createB: params → B }
```

- **製品ファミリー**: 一貫したスタイル/テーマの製品群を生成
- **ファクトリの差し替え**: 実行時にファクトリを切り替えて製品群を変更
- **一貫性の保証**: 同じファクトリから生成された製品は互いに整合

### 典型的なユースケース

- **図形ファクトリ**: Standard / Outlined / Filled スタイルの図形生成
- **UI ファクトリ**: Windows / Mac / Linux 向けのコンポーネント生成
- **データベースファクトリ**: MySQL / PostgreSQL / SQLite の接続生成

## 3. 言語別実装比較

### 3.1 ファクトリの表現方法

| 言語 | ファクトリ表現 | 製品の表現 |
|------|-------------|----------|
| Clojure | 関数のマップ / マルチメソッド | マップ |
| Scala | trait + object | case class |
| Elixir | プロトコル + 構造体 | 構造体 |
| F# | 判別共用体 / 関数レコード | レコード |
| Haskell | 型クラス / 関数レコード | ADT |
| Rust | trait + struct | struct |

### 3.2 図形ファクトリ

<details>
<summary>Scala: trait + object</summary>

```scala
trait ShapeFactory:
  def createCircle(center: Point, radius: Double): Shape
  def createSquare(topLeft: Point, side: Double): Shape
  def createRectangle(topLeft: Point, width: Double, height: Double): Shape

object StandardShapeFactory extends ShapeFactory:
  def createCircle(center: Point, radius: Double): Shape =
    Circle(center, radius)
  def createSquare(topLeft: Point, side: Double): Shape =
    Square(topLeft, side)
  def createRectangle(topLeft: Point, w: Double, h: Double): Shape =
    Rectangle(topLeft, w, h)

object OutlinedShapeFactory extends ShapeFactory:
  def createCircle(center: Point, radius: Double): Shape =
    StyledShape(Circle(center, radius), Style(outline = true, color = "black"))
  // ...
```

</details>

<details>
<summary>Haskell: 型クラス</summary>

```haskell
class ShapeFactory f where
    createCircle    :: f -> Point -> Double -> Shape
    createSquare    :: f -> Point -> Double -> Shape
    createRectangle :: f -> Point -> Double -> Double -> Shape

data StandardFactory = StandardFactory
data OutlinedFactory = OutlinedFactory String Float

instance ShapeFactory StandardFactory where
    createCircle _ center radius = Circle center radius
    createSquare _ topLeft side  = Square topLeft side
    createRectangle _ tl w h     = Rectangle tl w h

instance ShapeFactory OutlinedFactory where
    createCircle (OutlinedFactory color width) center radius =
        StyledShape (Circle center radius) (Style color width)
    -- ...
```

</details>

<details>
<summary>F#: 判別共用体によるファクトリ選択</summary>

```fsharp
type ShapeFactory =
    | Standard
    | Outlined of color: string * width: float
    | Filled of color: string

module ShapeFactory =
    let createCircle (factory: ShapeFactory) (center: Point) (radius: float) =
        let baseShape = Circle(center, radius)
        match factory with
        | Standard -> baseShape
        | Outlined(color, width) ->
            StyledShape(baseShape, { OutlineColor = color; OutlineWidth = width })
        | Filled(color) ->
            StyledShape(baseShape, { FillColor = color })

    let createSquare (factory: ShapeFactory) (topLeft: Point) (side: float) =
        let baseShape = Square(topLeft, side)
        match factory with
        | Standard -> baseShape
        | Outlined(color, width) ->
            StyledShape(baseShape, { OutlineColor = color; OutlineWidth = width })
        | Filled(color) ->
            StyledShape(baseShape, { FillColor = color })
```

</details>

<details>
<summary>Rust: trait + struct</summary>

```rust
pub trait ShapeFactory {
    fn create_circle(&self, center: Point, radius: f64) -> Shape;
    fn create_square(&self, top_left: Point, side: f64) -> Shape;
}

pub struct StandardFactory;

impl ShapeFactory for StandardFactory {
    fn create_circle(&self, center: Point, radius: f64) -> Shape {
        Shape::Circle { center, radius }
    }
    fn create_square(&self, top_left: Point, side: f64) -> Shape {
        Shape::Square { top_left, side }
    }
}

pub struct OutlinedFactory {
    pub color: String,
    pub width: f64,
}

impl ShapeFactory for OutlinedFactory {
    fn create_circle(&self, center: Point, radius: f64) -> Shape {
        Shape::Styled {
            shape: Box::new(Shape::Circle { center, radius }),
            style: Style { outline_color: self.color.clone(), outline_width: self.width },
        }
    }
    // ...
}
```

</details>

<details>
<summary>Clojure: 関数のマップ</summary>

```clojure
(def standard-factory
  {:create-circle (fn [center radius]
                    {:type :circle :center center :radius radius})
   :create-square (fn [top-left side]
                    {:type :square :top-left top-left :side side})})

(def outlined-factory
  {:create-circle (fn [center radius]
                    {:type :styled-shape
                     :shape {:type :circle :center center :radius radius}
                     :style {:outline true :color "black"}})
   :create-square (fn [top-left side]
                    {:type :styled-shape
                     :shape {:type :square :top-left top-left :side side}
                     :style {:outline true :color "black"}})})

;; ファクトリの利用
(defn draw-scene [factory]
  (let [circle ((:create-circle factory) {:x 0 :y 0} 5)
        square ((:create-square factory) {:x 10 :y 10} 3)]
    [circle square]))
```

</details>

<details>
<summary>Elixir: プロトコル + 構造体</summary>

```elixir
defprotocol ShapeFactory do
  def create_circle(factory, center, radius)
  def create_square(factory, top_left, side)
end

defmodule StandardShapeFactory do
  defstruct []
end

defimpl ShapeFactory, for: StandardShapeFactory do
  def create_circle(_factory, center, radius) do
    %Circle{center: center, radius: radius}
  end

  def create_square(_factory, top_left, side) do
    %Square{top_left: top_left, side: side}
  end
end

defmodule OutlinedShapeFactory do
  defstruct [:outline_color, :outline_width]
end

defimpl ShapeFactory, for: OutlinedShapeFactory do
  def create_circle(factory, center, radius) do
    %StyledShape{
      shape: %Circle{center: center, radius: radius},
      style: %{color: factory.outline_color, width: factory.outline_width}
    }
  end
  # ...
end
```

</details>

### 3.3 UI ファクトリ（プラットフォーム別）

<details>
<summary>プラットフォーム別 UI 生成の比較</summary>

```rust
// Rust
pub trait GUIFactory {
    fn create_button(&self, label: &str) -> Box<dyn Button>;
    fn create_checkbox(&self, label: &str, checked: bool) -> Box<dyn Checkbox>;
}

pub struct WindowsFactory;
pub struct MacFactory;

impl GUIFactory for WindowsFactory {
    fn create_button(&self, label: &str) -> Box<dyn Button> {
        Box::new(WindowsButton { label: label.to_string() })
    }
    // ...
}
```

```scala
// Scala
trait GUIFactory:
  def createButton(label: String): Button
  def createCheckbox(label: String, checked: Boolean): Checkbox

object WindowsFactory extends GUIFactory:
  def createButton(label: String) = WindowsButton(label)
  def createCheckbox(label: String, checked: Boolean) = WindowsCheckbox(label, checked)

object MacFactory extends GUIFactory:
  def createButton(label: String) = MacButton(label)
  def createCheckbox(label: String, checked: Boolean) = MacCheckbox(label, checked)
```

</details>

## 4. 比較分析

### 4.1 ファクトリの抽象化レベル

| 言語 | 抽象化方法 | 切り替えの容易さ |
|------|----------|---------------|
| Clojure | マップの差し替え | 最も柔軟（動的） |
| Scala | trait の実装切り替え | 型安全に切り替え |
| Elixir | プロトコル実装の差し替え | プロトコルベースで柔軟 |
| F# | 判別共用体のパターンマッチ | コンパイル時に網羅性保証 |
| Haskell | 型クラスインスタンスの選択 | 型推論で自動選択 |
| Rust | trait object の差し替え | `Box<dyn Factory>` で動的 |

### 4.2 OOP Abstract Factory vs 関数型

| 観点 | OOP | 関数型 |
|------|-----|--------|
| ファクトリ | インターフェース + 実装クラス | 関数のレコード / 型クラス / trait |
| 製品 | 具象クラス | ADT / enum / マップ |
| 拡張 | 新クラス追加 | 新しいインスタンス / 判別共用体追加 |
| 依存性注入 | コンストラクタ | 関数引数 |

### 4.3 Elixir 版のボリューム

Elixir 版は 604 行と最も長い記事です。これは以下の理由によります：

- 3-4 種類のファクトリ（Standard, Outlined, Filled, Complex）の完全実装
- UI ファクトリ（Windows, Mac, Linux）の詳細例
- データベースファクトリの追加例
- 各ファクトリのテストコードと使用例

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| 動的なファクトリ切り替え | Clojure | マップベースで最も柔軟 |
| 型安全なファクトリ | Haskell | 型クラスで自動推論 |
| プラットフォーム別 UI | Rust, Scala | trait ベースの具象ファクトリ |
| コンパイル時の網羅性保証 | F# | 判別共用体でファクトリ種別を管理 |
| プロトコルベースの拡張 | Elixir | 既存型に後からファクトリ実装を追加 |

## 6. まとめ

Abstract Factory パターンは、関数型プログラミングで以下のように表現されます：

1. **ファクトリ = 関数の集合**: 生成関数をレコード / マップ / 型クラスでまとめる
2. **製品ファミリーの一貫性**: 同じファクトリから生成された製品は整合する
3. **差し替え可能性**: ファクトリを引数として渡すことで実行時に切り替え

## 言語別個別記事

- [Clojure](../clojure/13-abstract-factory-pattern.md) | [Scala](../scala/13-abstract-factory-pattern.md) | [Elixir](../elixir/13-abstract-factory-pattern.md) | [F#](../fsharp/13-abstract-factory-pattern.md) | [Haskell](../haskell/13-abstract-factory-pattern.md) | [Rust](../rust/13-abstract-factory-pattern.md)
