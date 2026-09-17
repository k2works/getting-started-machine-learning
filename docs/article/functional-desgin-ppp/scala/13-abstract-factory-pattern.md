---
type: Article
title: "第13章: Abstract Factory パターン"
description: "Abstract Factory パターンは、関連するオブジェクトのファミリーを、その具体的なクラスを指定することなく生成するためのインターフェースを提供するパターンです。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第13章: Abstract Factory パターン

## はじめに

Abstract Factory パターンは、関連するオブジェクトのファミリーを、その具体的なクラスを指定することなく生成するためのインターフェースを提供するパターンです。

本章では、図形ファクトリ、UI ファクトリ、データベースファクトリの例を通じて、Abstract Factory パターンの実装を学びます。

## 1. パターンの構造

```plantuml
@startuml
title Abstract Factory パターン構造

interface "AbstractFactory" as Factory {
  +createProductA(): ProductA
  +createProductB(): ProductB
}

class "ConcreteFactory1" as Factory1 {
  +createProductA(): ProductA1
  +createProductB(): ProductB1
}

class "ConcreteFactory2" as Factory2 {
  +createProductA(): ProductA2
  +createProductB(): ProductB2
}

interface "ProductA" as ProductA
interface "ProductB" as ProductB

class "ProductA1" as A1
class "ProductA2" as A2
class "ProductB1" as B1
class "ProductB2" as B2

Factory <|.. Factory1
Factory <|.. Factory2
ProductA <|.. A1
ProductA <|.. A2
ProductB <|.. B1
ProductB <|.. B2

Factory1 ..> A1 : creates
Factory1 ..> B1 : creates
Factory2 ..> A2 : creates
Factory2 ..> B2 : creates
@enduml
```

## 2. 図形とスタイル - Product

```scala
case class Color(r: Int, g: Int, b: Int)

case class ShapeStyle(
  strokeColor: Color = Color.Black,
  strokeWidth: Double = 1.0,
  fillColor: Color = Color.Transparent
)

sealed trait Shape:
  def style: ShapeStyle
  def translate(dx: Double, dy: Double): Shape
  def scale(factor: Double): Shape
  def withStyle(newStyle: ShapeStyle): Shape

case class Circle(center: Point, radius: Double, style: ShapeStyle = ShapeStyle()) extends Shape
case class Square(topLeft: Point, side: Double, style: ShapeStyle = ShapeStyle()) extends Shape
case class Rectangle(topLeft: Point, width: Double, height: Double, style: ShapeStyle = ShapeStyle()) extends Shape
```

## 3. ShapeFactory - Abstract Factory

```scala
trait ShapeFactory:
  def createCircle(center: Point, radius: Double): Circle
  def createSquare(topLeft: Point, side: Double): Square
  def createRectangle(topLeft: Point, width: Double, height: Double): Rectangle
  def createTriangle(p1: Point, p2: Point, p3: Point): Triangle
```

## 4. Concrete Factories

### StandardShapeFactory

```scala
object StandardShapeFactory extends ShapeFactory:
  def createCircle(center: Point, radius: Double): Circle =
    Circle(center, radius)
  
  def createSquare(topLeft: Point, side: Double): Square =
    Square(topLeft, side)
  
  def createRectangle(topLeft: Point, width: Double, height: Double): Rectangle =
    Rectangle(topLeft, width, height)
  
  def createTriangle(p1: Point, p2: Point, p3: Point): Triangle =
    Triangle(p1, p2, p3)
```

### OutlinedShapeFactory

```scala
class OutlinedShapeFactory(
  strokeColor: Color,
  strokeWidth: Double
) extends ShapeFactory:
  private val style = ShapeStyle(strokeColor = strokeColor, strokeWidth = strokeWidth)
  
  def createCircle(center: Point, radius: Double): Circle =
    Circle(center, radius, style)
  
  def createSquare(topLeft: Point, side: Double): Square =
    Square(topLeft, side, style)
  // ...
```

### FilledShapeFactory

```scala
class FilledShapeFactory(
  fillColor: Color,
  strokeColor: Color = Color.Black,
  strokeWidth: Double = 1.0
) extends ShapeFactory:
  private val style = ShapeStyle(strokeColor, strokeWidth, fillColor)
  
  def createCircle(center: Point, radius: Double): Circle =
    Circle(center, radius, style)
  // ...
```

### 使用例

```scala
def createShapes(factory: ShapeFactory): List[Shape] =
  List(
    factory.createCircle(Point(0, 0), 5),
    factory.createSquare(Point(10, 10), 10),
    factory.createRectangle(Point(20, 20), 10, 5)
  )

// ファクトリを切り替えるだけで異なるスタイルの図形を生成
val standardShapes = createShapes(StandardShapeFactory)
val outlinedShapes = createShapes(OutlinedShapeFactory(Color.Red, 3.0))
val filledShapes = createShapes(FilledShapeFactory(Color.Blue))
```

## 5. UIFactory - プラットフォーム別 UI

### UI コンポーネント

```scala
trait Button:
  def label: String
  def theme: Theme
  def render: String

trait TextField:
  def placeholder: String
  def value: String
  def setValue(newValue: String): TextField

trait Checkbox:
  def label: String
  def checked: Boolean
  def toggle: Checkbox
```

### UIFactory インターフェース

```scala
trait UIFactory:
  def theme: Theme
  def createButton(label: String): Button
  def createTextField(placeholder: String): TextField
  def createCheckbox(label: String, checked: Boolean = false): Checkbox
```

### プラットフォーム別実装

```scala
// Windows
class WindowsUIFactory(val theme: Theme = Theme.Light) extends UIFactory:
  def createButton(label: String): WindowsButton = WindowsButton(label, theme)
  def createTextField(placeholder: String): WindowsTextField = WindowsTextField(placeholder, theme)
  def createCheckbox(label: String, checked: Boolean = false): WindowsCheckbox = WindowsCheckbox(label, checked, theme)

// MacOS
class MacOSUIFactory(val theme: Theme = Theme.Light) extends UIFactory:
  def createButton(label: String): MacOSButton = MacOSButton(label, theme)
  // ...

// Web
class WebUIFactory(val theme: Theme = Theme.Light) extends UIFactory:
  def createButton(label: String): WebButton = WebButton(label, theme)
  // ...
```

### 使用例

```scala
def createForm(factory: UIFactory): Map[String, String] =
  Map(
    "button" -> factory.createButton("Submit").render,
    "field" -> factory.createTextField("Email").render,
    "checkbox" -> factory.createCheckbox("Accept").render
  )

val windowsForm = createForm(WindowsUIFactory())  // [░ Submit]
val macForm = createForm(MacOSUIFactory())        // (○ Submit)
val webForm = createForm(WebUIFactory())          // <button>Submit</button>
```

## 6. DatabaseFactory - データベース抽象化

### データベースコンポーネント

```scala
trait Connection:
  def database: String
  def isConnected: Boolean
  def connect(): Connection
  def disconnect(): Connection
  def execute(sql: String): QueryResult

trait QueryBuilder:
  def select(columns: String*): QueryBuilder
  def from(table: String): QueryBuilder
  def where(condition: String): QueryBuilder
  def orderBy(column: String, ascending: Boolean = true): QueryBuilder
  def limit(n: Int): QueryBuilder
  def build: String
```

### DatabaseFactory インターフェース

```scala
trait DatabaseFactory:
  def createConnection(database: String): Connection
  def createQueryBuilder(): QueryBuilder
  def databaseType: String
```

### データベース別実装

```scala
object PostgreSQLFactory extends DatabaseFactory:
  def createConnection(database: String): PostgreSQLConnection = PostgreSQLConnection(database)
  def createQueryBuilder(): PostgreSQLQueryBuilder = new PostgreSQLQueryBuilder
  def databaseType: String = "PostgreSQL"

object MySQLFactory extends DatabaseFactory:
  def createConnection(database: String): MySQLConnection = MySQLConnection(database)
  def createQueryBuilder(): MySQLQueryBuilder = new MySQLQueryBuilder
  def databaseType: String = "MySQL"

object SQLiteFactory extends DatabaseFactory:
  def createConnection(database: String): SQLiteConnection = SQLiteConnection(database)
  def createQueryBuilder(): SQLiteQueryBuilder = new SQLiteQueryBuilder
  def databaseType: String = "SQLite"
```

### 使用例

```scala
def executeQuery(factory: DatabaseFactory, db: String): QueryResult =
  val conn = factory.createConnection(db).connect()
  val query = factory.createQueryBuilder()
    .select("id", "name")
    .from("users")
    .where("active = true")
    .limit(10)
    .build
  conn.execute(query)

// ファクトリを切り替えるだけで異なるデータベースに対応
val pgResult = executeQuery(PostgreSQLFactory, "pg_db")
val mysqlResult = executeQuery(MySQLFactory, "mysql_db")
val sqliteResult = executeQuery(SQLiteFactory, "sqlite.db")
```

## 7. 関数型アプローチ

```scala
object FunctionalFactory:
  case class FactoryConfig(
    strokeColor: Color = Color.Black,
    strokeWidth: Double = 1.0,
    fillColor: Color = Color.Transparent
  ):
    def style: ShapeStyle = ShapeStyle(strokeColor, strokeWidth, fillColor)
  
  def circleCreator(config: FactoryConfig)(center: Point, radius: Double): Circle =
    Circle(center, radius, config.style)
  
  def squareCreator(config: FactoryConfig)(topLeft: Point, side: Double): Square =
    Square(topLeft, side, config.style)
  
  // ファクトリを合成
  def composeFactory(
    baseConfig: FactoryConfig,
    modifiers: (FactoryConfig => FactoryConfig)*
  ): FactoryConfig =
    modifiers.foldLeft(baseConfig)((config, modifier) => modifier(config))
  
  // 修飾子
  def withStroke(color: Color, width: Double): FactoryConfig => FactoryConfig =
    config => config.copy(strokeColor = color, strokeWidth = width)
  
  def withFill(color: Color): FactoryConfig => FactoryConfig =
    config => config.copy(fillColor = color)
```

### 使用例

```scala
import FunctionalFactory._

val config = composeFactory(
  FactoryConfig(),
  withStroke(Color.Red, 2.0),
  withFill(Color.Blue)
)

val createCircle = circleCreator(config) _
val circle = createCircle(Point(10, 20), 5)
// Circle with red stroke and blue fill
```

## 8. ファクトリレジストリ

```scala
object FactoryRegistry:
  private var shapeFactories: Map[String, ShapeFactory] = Map(
    "standard" -> StandardShapeFactory,
    "outlined" -> OutlinedShapeFactory(Color.Black, 2.0),
    "filled" -> FilledShapeFactory(Color.Blue)
  )
  
  def registerShapeFactory(name: String, factory: ShapeFactory): Unit =
    shapeFactories = shapeFactories + (name -> factory)
  
  def getShapeFactory(name: String): Option[ShapeFactory] =
    shapeFactories.get(name)
```

### 使用例

```scala
val factory = FactoryRegistry.getShapeFactory("filled")
factory.foreach { f =>
  val circle = f.createCircle(Point(0, 0), 10)
  // Blue filled circle
}
```

## 9. パターンの利点

1. **製品ファミリーの一貫性**: 関連する製品が一貫して生成される
2. **具体クラスの分離**: クライアントは具体的な製品クラスを知らない
3. **製品ファミリーの交換**: ファクトリを交換するだけで製品群全体を変更可能
4. **新しい製品ファミリーの追加が容易**: 新しい ConcreteFactory を追加するだけ

## Clojure との比較

| 概念 | Clojure | Scala |
|------|---------|-------|
| AbstractFactory | マルチメソッド | trait |
| ConcreteFactory | defmethod | object/class extends trait |
| Product | マップ | case class |
| ファクトリ設定 | マップのキー | コンストラクタ引数 |
| ファクトリ選択 | 型キーでディスパッチ | 多態性 or パターンマッチ |

## まとめ

本章では、Abstract Factory パターンについて学びました：

1. **ShapeFactory**: Standard, Outlined, Filled の各ファクトリ
2. **UIFactory**: Windows, MacOS, Web の各プラットフォーム
3. **DatabaseFactory**: PostgreSQL, MySQL, SQLite の各データベース
4. **関数型アプローチ**: 設定と修飾子による合成
5. **ファクトリレジストリ**: ファクトリの動的な登録と取得

Abstract Factory パターンは、関連するオブジェクトのファミリーを一貫して生成する必要がある場面で有効です。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part3/src/main/scala/AbstractFactoryPattern.scala`
- テストコード: `apps/scala/part3/src/test/scala/AbstractFactoryPatternSpec.scala`

## 次章予告

次章では、**Abstract Server パターン**について学びます。依存関係の逆転を実現し、モジュール間の疎結合を達成する方法を探ります。
