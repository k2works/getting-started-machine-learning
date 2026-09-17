---
type: Article
title: "第7章: Composite パターン"
description: "Composite パターンは、オブジェクトをツリー構造で構成し、個々のオブジェクトとオブジェクトの集合を同じように扱うことができるようにするパターンです。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第7章: Composite パターン

## はじめに

Composite パターンは、オブジェクトをツリー構造で構成し、個々のオブジェクトとオブジェクトの集合を同じように扱うことができるようにするパターンです。このパターンを使用すると、クライアントは個々のオブジェクトとその組み合わせを区別せずに操作できます。

本章では、図形（Shape）、スイッチ（Switchable）、ファイルシステム、メニュー、数式、組織構造など、様々な例を通じて Composite パターンの実装を学びます。

## 1. パターンの構造

Composite パターンは以下の要素で構成されます：

- **Component**: 全てのオブジェクトの共通インターフェース
- **Leaf**: 子要素を持たない末端オブジェクト
- **Composite**: 子要素を持つコンテナオブジェクト

```plantuml
@startuml
skinparam classAttributeIconSize 0

abstract class Component {
  +operation(): Result
}

class Leaf {
  +operation()
}

class Composite {
  -children: List<Component>
  +add(c: Component)
  +operation()
}

Component <|-- Leaf
Component <|-- Composite
Composite o-- Component : children
@enduml
```

## 2. Shape - 図形の Composite パターン

### 判別共用体による共通インターフェース

F# では判別共用体を使って Component を表現します：

```fsharp
/// 2D座標
type Point =
    { X: float
      Y: float }

/// 図形の共通インターフェース（判別共用体）
[<RequireQualifiedAccess>]
type Shape =
    | Circle of center: Point * radius: float
    | Square of topLeft: Point * side: float
    | Rectangle of topLeft: Point * width: float * height: float
    | Composite of shapes: Shape list
```

### 再帰的な操作

```fsharp
module Shape =
    /// 図形を移動
    let rec translate dx dy shape =
        match shape with
        | Shape.Circle(center, radius) ->
            Shape.Circle({ X = center.X + dx; Y = center.Y + dy }, radius)
        | Shape.Square(topLeft, side) ->
            Shape.Square({ X = topLeft.X + dx; Y = topLeft.Y + dy }, side)
        | Shape.Rectangle(topLeft, width, height) ->
            Shape.Rectangle({ X = topLeft.X + dx; Y = topLeft.Y + dy }, width, height)
        | Shape.Composite shapes ->
            Shape.Composite(shapes |> List.map (translate dx dy))

    /// 図形を拡大・縮小
    let rec scale factor shape =
        match shape with
        | Shape.Circle(center, radius) -> Shape.Circle(center, radius * factor)
        | Shape.Square(topLeft, side) -> Shape.Square(topLeft, side * factor)
        | Shape.Rectangle(topLeft, width, height) ->
            Shape.Rectangle(topLeft, width * factor, height * factor)
        | Shape.Composite shapes -> Shape.Composite(shapes |> List.map (scale factor))

    /// 図形の面積を計算
    let rec area shape =
        match shape with
        | Shape.Circle(_, radius) -> System.Math.PI * radius * radius
        | Shape.Square(_, side) -> side * side
        | Shape.Rectangle(_, width, height) -> width * height
        | Shape.Composite shapes -> shapes |> List.sumBy area
```

### 使用例

```fsharp
// 個々の図形を作成
let circle = Shape.Circle(Point.create 10.0 10.0, 5.0)
let square = Shape.Square(Point.create 0.0 0.0, 10.0)

// 複合図形を作成
let group =
    Shape.emptyComposite
    |> Shape.add circle
    |> Shape.add square

// 複合図形を移動（全ての子要素が移動する）
let moved = Shape.translate 5.0 5.0 group

// 複合図形を拡大（全ての子要素が拡大する）
let scaled = Shape.scale 2.0 group

// 面積は全ての子要素の合計
let totalArea = Shape.area group  // PI * 25 + 100
```

## 3. Switchable - スイッチの Composite パターン

```fsharp
/// スイッチの共通インターフェース
[<RequireQualifiedAccess>]
type Switchable =
    | Light of on: bool * name: string
    | DimmableLight of intensity: int * name: string
    | Fan of on: bool * speed: int * name: string
    | Composite of switchables: Switchable list * name: string

module Switchable =
    /// スイッチをオンにする
    let rec turnOn switchable =
        match switchable with
        | Switchable.Light(_, name) -> Switchable.Light(true, name)
        | Switchable.DimmableLight(_, name) -> Switchable.DimmableLight(100, name)
        | Switchable.Fan(_, speed, name) -> Switchable.Fan(true, speed, name)
        | Switchable.Composite(switchables, name) ->
            Switchable.Composite(switchables |> List.map turnOn, name)

    /// スイッチがオンかどうか
    let rec isOn switchable =
        match switchable with
        | Switchable.Light(on, _) -> on
        | Switchable.DimmableLight(intensity, _) -> intensity > 0
        | Switchable.Fan(on, _, _) -> on
        | Switchable.Composite(switchables, _) -> switchables |> List.exists isOn
```

### 使用例

```fsharp
// 部屋のスイッチグループを作成
let bedroom =
    Switchable.emptyComposite "Bedroom"
    |> Switchable.add (Switchable.Light(false, "Ceiling"))
    |> Switchable.add (Switchable.DimmableLight(0, "Bedside"))

let livingRoom =
    Switchable.emptyComposite "LivingRoom"
    |> Switchable.add (Switchable.Light(false, "Main"))
    |> Switchable.add (Switchable.Fan(false, 0, "CeilingFan"))

// 家全体のグループ
let house =
    Switchable.emptyComposite "House"
    |> Switchable.add bedroom
    |> Switchable.add livingRoom

// 家中の全てのスイッチをオン
let allOn = Switchable.turnOn house
```

## 4. FileSystem - ファイルシステムの Composite パターン

```fsharp
/// ファイルシステムエントリ
[<RequireQualifiedAccess>]
type FileSystemEntry =
    | File of name: string * size: int64 * extension: string
    | Directory of name: string * children: FileSystemEntry list

module FileSystemEntry =
    /// サイズを取得
    let rec size entry =
        match entry with
        | FileSystemEntry.File(_, fileSize, _) -> fileSize
        | FileSystemEntry.Directory(_, children) -> children |> List.sumBy size

    /// ファイル数を取得
    let rec fileCount entry =
        match entry with
        | FileSystemEntry.File _ -> 1
        | FileSystemEntry.Directory(_, children) -> children |> List.sumBy fileCount

    /// 拡張子でファイルを検索
    let findByExtension ext entry =
        find
            (function
            | FileSystemEntry.File(_, _, e) -> e = ext
            | _ -> false)
            entry

    /// すべてのファイルを取得
    let rec allFiles entry =
        match entry with
        | FileSystemEntry.File _ as f -> [ f ]
        | FileSystemEntry.Directory(_, children) -> children |> List.collect allFiles
```

### 使用例

```fsharp
// ファイルシステム構造を構築
let readme = FileSystemEntry.File("readme.txt", 1024L, ".txt")
let config = FileSystemEntry.File("config.json", 512L, ".json")
let src = FileSystemEntry.Directory("src", [
    FileSystemEntry.File("main.fs", 2048L, ".fs")
    FileSystemEntry.File("util.fs", 1024L, ".fs")
])
let root = FileSystemEntry.Directory("project", [ readme; config; src ])

// 総サイズを取得
let totalSize = FileSystemEntry.size root  // 4608

// .fs ファイルを検索
let fsFiles = FileSystemEntry.findByExtension ".fs" root
```

## 5. MenuItem - メニューの Composite パターン

```fsharp
/// メニュー項目
[<RequireQualifiedAccess>]
type MenuItem =
    | Item of name: string * price: decimal * category: string
    | SetMenu of name: string * items: MenuItem list * discountRate: float

module MenuItem =
    /// 価格を計算
    let rec price menuItem =
        match menuItem with
        | MenuItem.Item(_, itemPrice, _) -> itemPrice
        | MenuItem.SetMenu(_, items, discountRate) ->
            let totalPrice = items |> List.sumBy price
            totalPrice * (1m - decimal discountRate)

    /// アイテム数を取得
    let rec itemCount menuItem =
        match menuItem with
        | MenuItem.Item _ -> 1
        | MenuItem.SetMenu(_, items, _) -> items |> List.sumBy itemCount
```

### 使用例

```fsharp
// 単品メニュー
let burger = MenuItem.Item("Hamburger", 500m, "Main")
let fries = MenuItem.Item("Fries", 200m, "Side")
let drink = MenuItem.Item("Cola", 150m, "Drink")

// セットメニュー（10%割引）
let lunchSet = MenuItem.SetMenu("Lunch Set", [ burger; fries; drink ], 0.1)

// セット価格 = (500 + 200 + 150) * 0.9 = 765
let setPrice = MenuItem.price lunchSet
```

## 6. Expression - 数式の Composite パターン

```fsharp
/// 数式
[<RequireQualifiedAccess>]
type Expression =
    | Number of float
    | Variable of name: string * value: float option
    | Add of left: Expression * right: Expression
    | Subtract of left: Expression * right: Expression
    | Multiply of left: Expression * right: Expression
    | Divide of left: Expression * right: Expression
    | Negate of Expression

module Expression =
    /// 数式を評価
    let rec evaluate expr =
        match expr with
        | Expression.Number n -> n
        | Expression.Variable(name, value) ->
            match value with
            | Some v -> v
            | None -> failwithf "Variable %s has no value" name
        | Expression.Add(left, right) -> evaluate left + evaluate right
        | Expression.Subtract(left, right) -> evaluate left - evaluate right
        | Expression.Multiply(left, right) -> evaluate left * evaluate right
        | Expression.Divide(left, right) -> evaluate left / evaluate right
        | Expression.Negate e -> -(evaluate e)

    /// 数式を簡略化
    let rec simplify expr =
        match expr with
        | Expression.Add(left, right) ->
            match simplify left, simplify right with
            | Expression.Number 0.0, r -> r
            | l, Expression.Number 0.0 -> l
            | Expression.Number a, Expression.Number b -> Expression.Number(a + b)
            | l, r -> Expression.Add(l, r)
        // ... 他の演算子も同様
```

### 使用例

```fsharp
// (2 + 3) * 4 = 20
let expr =
    Expression.Multiply(
        Expression.Add(Expression.Number 2.0, Expression.Number 3.0),
        Expression.Number 4.0
    )
let result = Expression.evaluate expr  // 20.0

// 変数を含む式
let exprWithVars =
    Expression.Add(Expression.Variable("x", None), Expression.Variable("y", None))
let bound = Expression.bind (Map.ofList [ ("x", 10.0); ("y", 20.0) ]) exprWithVars
let value = Expression.evaluate bound  // 30.0

// 式の簡略化
let simplified = Expression.simplify (Expression.Add(Expression.Variable("x", None), Expression.Number 0.0))
// => Expression.Variable("x", None)
```

## 7. Organization - 組織構造の Composite パターン

```fsharp
/// 組織の構成員
[<RequireQualifiedAccess>]
type OrganizationMember =
    | Employee of name: string * salary: decimal * role: string
    | Department of name: string * members: OrganizationMember list * manager: string option

module OrganizationMember =
    /// 総給与を計算
    let rec totalSalary orgMember =
        match orgMember with
        | OrganizationMember.Employee(_, salary, _) -> salary
        | OrganizationMember.Department(_, members, _) -> members |> List.sumBy totalSalary

    /// 従業員数を取得
    let rec employeeCount orgMember =
        match orgMember with
        | OrganizationMember.Employee _ -> 1
        | OrganizationMember.Department(_, members, _) -> members |> List.sumBy employeeCount

    /// 役職で従業員を検索
    let rec findByRole role orgMember =
        match orgMember with
        | OrganizationMember.Employee(_, _, r) as emp ->
            if r = role then [ emp ] else []
        | OrganizationMember.Department(_, members, _) ->
            members |> List.collect (findByRole role)
```

## パターンの利点

1. **統一的な操作**: 個々のオブジェクトとグループを同じインターフェースで操作可能
2. **階層構造**: ネストした構造を自然に表現可能
3. **拡張性**: 新しい Leaf や Composite を追加しやすい
4. **再帰的な構造**: Composite は他の Composite を含むことも可能

## F# での特徴

### 判別共用体による型安全性

```fsharp
// 全てのケースを網羅するパターンマッチ
let rec area shape =
    match shape with
    | Shape.Circle(_, radius) -> System.Math.PI * radius * radius
    | Shape.Square(_, side) -> side * side
    | Shape.Rectangle(_, width, height) -> width * height
    | Shape.Composite shapes -> shapes |> List.sumBy area
    // 新しいケースを追加すると、コンパイラが警告を出す
```

### 不変性による安全な操作

```fsharp
// 元のオブジェクトは変更されない
let original = Shape.emptyComposite |> Shape.add (Shape.Circle(Point.origin, 5.0))
let moved = Shape.translate 10.0 10.0 original

// original は変更されていない
```

### 再帰関数とパイプライン

```fsharp
// 再帰的な処理もパイプラインで読みやすく
let result =
    Shape.emptyComposite
    |> Shape.add circle
    |> Shape.add square
    |> Shape.translate 5.0 5.0
    |> Shape.scale 2.0
    |> Shape.area
```

## Clojure/Scala との比較

| 概念 | Clojure | Scala | F# |
|------|---------|-------|-----|
| インターフェース | マルチメソッド | sealed trait | 判別共用体 |
| Leaf | defmethod | case class extends trait | Union case |
| Composite | defmethod + mapv | case class extends trait | Union case with list |
| データ構造 | マップ | case class | レコード/Union |
| 操作の委譲 | mapv | shapes.map(...) | List.map |
| 不変性 | デフォルト | case class + copy | デフォルト |
| 網羅性チェック | なし | sealed で警告 | 完全チェック |

## まとめ

本章では、Composite パターンについて学びました：

1. **Shape の例**: 図形の移動と拡大を統一的に操作
2. **Switchable の例**: 複数のスイッチをグループ化して操作
3. **FileSystem の例**: ファイルとディレクトリの階層構造
4. **Menu の例**: 単品とセットメニューの価格計算
5. **Expression の例**: 数式の評価と簡略化
6. **Organization の例**: 組織構造の給与計算

Composite パターンは、ツリー構造のデータを扱う際に非常に有効なパターンです。F# の判別共用体とパターンマッチを使うことで、型安全で不変なツリー構造を簡潔に実装できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part3/src/Library.fs`
- テストコード: `apps/fsharp/part3/tests/Tests.fs`

## 次章予告

次章では、**Decorator パターン**について学びます。既存の機能に新しい機能を動的に追加する方法を探ります。
