---
type: Article
title: "第3章: 多態性とディスパッチ"
description: "多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第3章: 多態性とディスパッチ

## はじめに

多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。Scala では、sealed trait、トレイト、型クラス、パターンマッチングという複数のメカニズムで多態性を実現します。

本章では、これらのメカニズムを使い分けて、柔軟で拡張性の高いコードを書く方法を学びます。

## 1. Sealed Trait による多態性（代数的データ型）

Scala の `sealed trait` は、有限の型のバリエーションを定義するのに最適です。コンパイラがパターンマッチングの網羅性をチェックしてくれます。

### 基本的な使い方

```scala
// 図形を表す sealed trait
sealed trait Shape
case class Rectangle(width: Double, height: Double) extends Shape
case class Circle(radius: Double) extends Shape
case class Triangle(base: Double, height: Double) extends Shape

// 図形の面積を計算する
def calculateArea(shape: Shape): Double = shape match
  case Rectangle(w, h) => w * h
  case Circle(r)       => Math.PI * r * r
  case Triangle(b, h)  => b * h / 2

// 使用例
calculateArea(Rectangle(4, 5))  // => 20.0
calculateArea(Circle(3))        // => 28.27...
calculateArea(Triangle(6, 5))   // => 15.0
```

### Sealed Trait の利点

1. **網羅性チェック**: コンパイラがすべてのケースがカバーされているか確認
2. **型安全性**: 不正な型の値を渡せない
3. **パターンマッチング**: 分岐を簡潔に記述可能

## 2. 複合ディスパッチ

タプルを使ったパターンマッチングで、複数の値に基づくディスパッチを実現できます。

```scala
enum PaymentMethod:
  case CreditCard, BankTransfer, Cash

enum Currency:
  case JPY, USD, EUR

case class Payment(method: PaymentMethod, currency: Currency, amount: Int)
case class PaymentResult(status: String, message: String, amount: Int, converted: Option[Int] = None)

def processPayment(payment: Payment): PaymentResult =
  import PaymentMethod.*, Currency.*
  (payment.method, payment.currency) match
    case (CreditCard, JPY) =>
      PaymentResult("processed", "クレジットカード（円）で処理しました", payment.amount)
    case (CreditCard, USD) =>
      PaymentResult("processed", "Credit card (USD) processed", payment.amount, Some(payment.amount * 150))
    case (BankTransfer, JPY) =>
      PaymentResult("pending", "銀行振込を受け付けました", payment.amount)
    case _ =>
      PaymentResult("error", "サポートされていない支払い方法です", payment.amount)

// 使用例
processPayment(Payment(PaymentMethod.CreditCard, Currency.JPY, 1000))
// => PaymentResult("processed", "クレジットカード（円）で処理しました", 1000)
```

## 3. 階層的ディスパッチ（継承による）

Scala では継承を使って型の階層を定義し、各型に固有の振る舞いを持たせることができます。

```scala
sealed trait Account:
  def balance: Int
  def interestRate: Double

case class SavingsAccount(balance: Int) extends Account:
  val interestRate = 0.02

case class PremiumSavingsAccount(balance: Int) extends Account:
  val interestRate = 0.05

case class CheckingAccount(balance: Int) extends Account:
  val interestRate = 0.001

def calculateInterest(account: Account): Double =
  account.balance * account.interestRate

// 使用例
calculateInterest(SavingsAccount(10000))        // => 200.0
calculateInterest(PremiumSavingsAccount(10000)) // => 500.0
calculateInterest(CheckingAccount(10000))       // => 10.0
```

## 4. トレイト（Protocols に相当）

トレイトは、特定の操作セットを定義するインターフェースです。Java のインターフェースに似ていますが、デフォルト実装を持てます。

### トレイトの定義

```scala
trait Drawable:
  def draw: String
  def boundingBox: BoundingBox

case class BoundingBox(x: Double, y: Double, width: Double, height: Double)

trait Transformable[T]:
  def translate(dx: Double, dy: Double): T
  def scale(factor: Double): T
  def rotate(angle: Double): T
```

### トレイトの利点

1. **パフォーマンス**: パターンマッチングより高速な型ベースのディスパッチ
2. **明確なコントラクト**: 実装すべきメソッドが明示的
3. **ミックスイン**: 複数のトレイトを組み合わせ可能

## 5. トレイトを実装するケースクラス

```scala
case class DrawableRectangle(x: Double, y: Double, width: Double, height: Double)
    extends Drawable with Transformable[DrawableRectangle]:

  def draw: String = s"Rectangle at ($x,$y) with size ${width}x$height"

  def boundingBox: BoundingBox = BoundingBox(x, y, width, height)

  def translate(dx: Double, dy: Double): DrawableRectangle =
    copy(x = x + dx, y = y + dy)

  def scale(factor: Double): DrawableRectangle =
    copy(width = width * factor, height = height * factor)

  def rotate(angle: Double): DrawableRectangle = this

case class DrawableCircle(x: Double, y: Double, radius: Double)
    extends Drawable with Transformable[DrawableCircle]:

  def draw: String = s"Circle at ($x,$y) with radius $radius"

  def boundingBox: BoundingBox =
    BoundingBox(x - radius, y - radius, radius * 2, radius * 2)

  def translate(dx: Double, dy: Double): DrawableCircle =
    copy(x = x + dx, y = y + dy)

  def scale(factor: Double): DrawableCircle =
    copy(radius = radius * factor)

  def rotate(angle: Double): DrawableCircle = this

// 使用例
val rect = DrawableRectangle(10, 20, 100, 50)
rect.draw           // => "Rectangle at (10.0,20.0) with size 100.0x50.0"
rect.translate(5, 10) // => DrawableRectangle(15.0, 30.0, 100.0, 50.0)

val circle = DrawableCircle(50, 50, 25)
circle.boundingBox  // => BoundingBox(25.0, 25.0, 50.0, 50.0)
```

## 6. 型クラス（既存型への拡張）

型クラスは、既存の型に後から振る舞いを追加できる強力なパターンです。Clojure の `extend-protocol` に相当します。

```scala
// 型クラスの定義
trait Stringable[A]:
  def stringify(a: A): String

object Stringable:
  def apply[A](using s: Stringable[A]): Stringable[A] = s

  given Stringable[Map[String, Any]] with
    def stringify(m: Map[String, Any]): String =
      "{" + m.map((k, v) => s"$k: $v").mkString(", ") + "}"

  given Stringable[List[Any]] with
    def stringify(l: List[Any]): String =
      "[" + l.mkString(", ") + "]"

  given Stringable[String] with
    def stringify(s: String): String = s

  given Stringable[Int] with
    def stringify(i: Int): String = i.toString

  given optionStringable[A](using s: Stringable[A]): Stringable[Option[A]] with
    def stringify(opt: Option[A]): String = opt match
      case Some(a) => s.stringify(a)
      case None    => "nil"

// 拡張メソッド
extension [A](a: A)
  def toCustomString(using s: Stringable[A]): String = s.stringify(a)

// 使用例
val map: Map[String, Any] = Map("name" -> "田中", "age" -> 30)
map.toCustomString  // => "{name: 田中, age: 30}"

val list: List[Any] = List(1, 2, 3)
list.toCustomString // => "[1, 2, 3]"

(Some(42): Option[Int]).toCustomString // => "42"
(None: Option[Int]).toCustomString     // => "nil"
```

## 7. コンポーネントパターン

トレイトを使って、コンポーネントのライフサイクル管理を実現します。

```scala
trait Lifecycle[T]:
  def start: T
  def stop: T

case class DatabaseConnection(host: String, port: Int, connected: Boolean = false)
    extends Lifecycle[DatabaseConnection]:

  def start: DatabaseConnection =
    println(s"データベースに接続中: $host : $port")
    copy(connected = true)

  def stop: DatabaseConnection =
    println("データベース接続を切断中")
    copy(connected = false)

case class WebServer(port: Int, db: DatabaseConnection, running: Boolean = false)
    extends Lifecycle[WebServer]:

  def start: WebServer =
    println(s"Webサーバーを起動中 ポート: $port")
    val startedDb = db.start
    copy(db = startedDb, running = true)

  def stop: WebServer =
    println("Webサーバーを停止中")
    val stoppedDb = db.stop
    copy(db = stoppedDb, running = false)

// 使用例
val db = DatabaseConnection("localhost", 5432)
val server = WebServer(8080, db)

val startedServer = server.start
// データベースに接続中: localhost : 5432
// Webサーバーを起動中 ポート: 8080

val stoppedServer = startedServer.stop
// Webサーバーを停止中
// データベース接続を切断中
```

## 8. 条件分岐の置き換え（Strategy パターン）

多態性を使って、switch/case 文による型判定を排除できます。

### Before（条件分岐）

```scala
// 悪い例：型による条件分岐
def sendNotificationBad(notificationType: String, message: String, opts: Map[String, String]) =
  notificationType match
    case "email" => Map("type" -> "email", "to" -> opts("to"), "body" -> message)
    case "sms"   => Map("type" -> "sms", "to" -> opts("phone"), "body" -> message.take(160))
    case "push"  => Map("type" -> "push", "device" -> opts("device"), "body" -> message)
    case _       => throw new IllegalArgumentException("未知の通知タイプ")
```

### After（多態性）

```scala
trait NotificationSender:
  def sendNotification(message: String): NotificationResult
  def deliveryTime: String

case class NotificationResult(
  notificationType: String,
  to: String,
  body: String,
  status: String,
  subject: Option[String] = None
)

case class EmailNotification(to: String, subject: String = "通知") extends NotificationSender:
  def sendNotification(message: String): NotificationResult =
    NotificationResult("email", to, message, "sent", Some(subject))
  def deliveryTime: String = "1-2分"

case class SMSNotification(phoneNumber: String) extends NotificationSender:
  def sendNotification(message: String): NotificationResult =
    val truncated = if message.length > 160 then message.take(157) else message
    NotificationResult("sms", phoneNumber, truncated, "sent")
  def deliveryTime: String = "数秒"

case class PushNotification(deviceToken: String) extends NotificationSender:
  def sendNotification(message: String): NotificationResult =
    NotificationResult("push", deviceToken, message, "sent")
  def deliveryTime: String = "即時"

// ファクトリ関数
def createNotification(notificationType: String, opts: Map[String, String]): NotificationSender =
  notificationType match
    case "email" => EmailNotification(opts.getOrElse("to", ""), opts.getOrElse("subject", "通知"))
    case "sms"   => SMSNotification(opts.getOrElse("phone", ""))
    case "push"  => PushNotification(opts.getOrElse("device", ""))
    case _       => throw new IllegalArgumentException(s"未知の通知タイプ: $notificationType")

// 使用例
val email = createNotification("email", Map("to" -> "user@example.com"))
email.sendNotification("重要なお知らせ")
// => NotificationResult("email", "user@example.com", "重要なお知らせ", "sent", Some("通知"))
```

## 9. パターンマッチングとトレイトの使い分け

| 特徴 | パターンマッチング | トレイト | 型クラス |
|------|------------------|---------|---------|
| ディスパッチ | 値に基づく | 型に基づく | 型に基づく |
| 拡張性 | クローズド | オープン | 非常にオープン |
| パフォーマンス | 良い | 最良 | 良い |
| 用途 | ADT、有限の型 | 共通のインターフェース | 既存型への拡張 |

### 使い分けの指針

- **Sealed Trait + パターンマッチング**: 有限で固定された型のバリエーション
- **トレイト**: 新しい型が追加される可能性がある場合
- **型クラス**: 既存の型（標準ライブラリなど）に振る舞いを追加したい場合

## まとめ

本章では、Scala における多態性について学びました：

1. **Sealed Trait**: 代数的データ型による有限の型バリエーション
2. **複合ディスパッチ**: タプルによる複数の値に基づくパターンマッチング
3. **階層的ディスパッチ**: 継承を使った型階層
4. **トレイト**: 共通インターフェースの定義とミックスイン
5. **型クラス**: 既存型への振る舞いの追加
6. **コンポーネントパターン**: ライフサイクル管理
7. **条件分岐の置き換え**: Strategy パターン

これらのメカニズムを適切に使い分けることで、拡張性が高く保守しやすいコードを実現できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part1/src/main/scala/Polymorphism.scala`
- テストコード: `apps/scala/part1/src/test/scala/PolymorphismSpec.scala`

## 次章予告

次章から第2部「仕様とテスト」に入ります。Scala のデータバリデーションと仕様定義について学びます。
