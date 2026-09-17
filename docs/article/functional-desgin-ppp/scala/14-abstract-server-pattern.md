---
type: Article
title: "第14章: Abstract Server パターン"
description: "Abstract Server パターンは、依存関係逆転の原則（DIP）を実現するパターンです。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第14章: Abstract Server パターン

## はじめに

Abstract Server パターンは、依存関係逆転の原則（DIP）を実現するパターンです。高レベルモジュールが低レベルモジュールの詳細に依存するのではなく、両者が抽象に依存することで疎結合を実現します。

本章では、スイッチとデバイス、リポジトリとサービスの例を通じて、Scala の trait と型クラスによる Abstract Server パターンの実装を学びます。

## 1. パターンの構造

Abstract Server パターンは以下の要素で構成されます：

- **Client**: サービスを利用するモジュール
- **Abstract Server**: サービスの抽象インターフェース
- **Concrete Server**: 具体的なサービスの実装

```plantuml
@startuml
title Abstract Server パターン構造

class "Client\n(Switch)" as Client {
  +engage(device)
  +disengage()
  +toggle()
}

interface "Abstract Server\n(Switchable)" as Server {
  +turnOn(device)
  +turnOff(device)
  +isOn(device)
}

class "Light" as Light
class "Fan" as Fan
class "Motor" as Motor

Client --> Server : depends on
Server <|.. Light
Server <|.. Fan
Server <|.. Motor
@enduml
```

## 2. Switchable パターン - 型クラスアプローチ

### Abstract Server の定義

```scala
/**
 * Abstract Server: スイッチ可能なデバイスのインターフェース
 */
trait Switchable[A]:
  def turnOn(device: A): A
  def turnOff(device: A): A
  def isOn(device: A): Boolean

/**
 * Switchable の拡張メソッド
 */
extension [A](device: A)(using s: Switchable[A])
  def on: A = s.turnOn(device)
  def off: A = s.turnOff(device)
  def isOn: Boolean = s.isOn(device)
  def toggle: A = if device.isOn then device.off else device.on
```

Scala では型クラスを使用して、異なる型に共通のインターフェースを提供します。`Switchable[A]` は任意の型 `A` に対してスイッチ操作を定義できます。

## 3. Concrete Server: Light

```scala
enum LightState:
  case On, Off

case class Light(state: LightState = LightState.Off)

given Switchable[Light] with
  def turnOn(light: Light): Light = light.copy(state = LightState.On)
  def turnOff(light: Light): Light = light.copy(state = LightState.Off)
  def isOn(light: Light): Boolean = light.state == LightState.On
```

### 使用例

```scala
val light = Light()
light.isOn        // false

val lightOn = light.on
lightOn.isOn      // true

val toggled = light.toggle
toggled.isOn      // true
```

## 4. Concrete Server: Fan

```scala
enum FanSpeed:
  case Low, Medium, High

case class Fan(
  state: LightState = LightState.Off,
  speed: Option[FanSpeed] = None
)

given Switchable[Fan] with
  def turnOn(fan: Fan): Fan =
    fan.copy(state = LightState.On, speed = Some(fan.speed.getOrElse(FanSpeed.Low)))
  def turnOff(fan: Fan): Fan =
    fan.copy(state = LightState.Off, speed = None)
  def isOn(fan: Fan): Boolean = fan.state == LightState.On

object Fan:
  def setSpeed(fan: Fan, speed: FanSpeed): Fan =
    if fan.isOn then fan.copy(speed = Some(speed)) else fan
```

Fan はオンにするとデフォルトで Low スピードになり、オフにするとスピードがリセットされます。

## 5. Concrete Server: Motor

```scala
enum MotorDirection:
  case Forward, Reverse

case class Motor(
  state: LightState = LightState.Off,
  direction: Option[MotorDirection] = None
)

given Switchable[Motor] with
  def turnOn(motor: Motor): Motor =
    motor.copy(
      state = LightState.On,
      direction = Some(motor.direction.getOrElse(MotorDirection.Forward))
    )
  def turnOff(motor: Motor): Motor =
    motor.copy(state = LightState.Off)
  def isOn(motor: Motor): Boolean = motor.state == LightState.On

object Motor:
  def reverseDirection(motor: Motor): Motor =
    if motor.isOn then
      val newDirection = motor.direction match
        case Some(MotorDirection.Forward) => MotorDirection.Reverse
        case Some(MotorDirection.Reverse) => MotorDirection.Forward
        case None => MotorDirection.Forward
      motor.copy(direction = Some(newDirection))
    else motor
```

## 6. Client: Switch

```scala
/**
 * Switch クライアント - Switchable プロトコルを通じてデバイスを操作
 */
object Switch:
  def engage[A: Switchable](device: A): A = device.on
  def disengage[A: Switchable](device: A): A = device.off
  def toggle[A: Switchable](device: A): A = device.toggle
  def status[A: Switchable](device: A): String = if device.isOn then "on" else "off"
```

Switch クライアントは具体的なデバイス型を知りません。`Switchable` 型クラスを通じてのみデバイスと対話します。

### 使用例

```scala
// 同じ Switch コードで異なるデバイスを操作
val light = Switch.engage(Light())
Switch.status(light)  // "on"

val fan = Switch.engage(Fan())
Switch.status(fan)    // "on"

val motor = Switch.engage(Motor())
Switch.status(motor)  // "on"
```

## 7. Repository パターン

### Abstract Server: Repository

```scala
/**
 * Abstract Server: リポジトリインターフェース
 */
trait Repository[F[_], E, ID]:
  def findById(id: ID): F[Option[E]]
  def findAll: F[Seq[E]]
  def save(entity: E): F[E]
  def delete(id: ID): F[Option[E]]

/**
 * User エンティティ
 */
case class User(
  id: Option[Id] = None,
  name: String,
  email: String,
  createdAt: Long = System.currentTimeMillis()
)
```

`Repository` は高カインド型 `F[_]` を使用して、同期（`Identity`）や非同期（`Future`）など異なる効果を抽象化できます。

### Concrete Server: MemoryRepository

```scala
type Identity[A] = A

class MemoryRepository[E, ID](
  getId: E => Option[ID],
  setId: (E, ID) => E,
  generateId: () => ID
) extends Repository[Identity, E, ID]:

  private var data: Map[ID, E] = Map.empty

  def findById(id: ID): Identity[Option[E]] = data.get(id)
  def findAll: Identity[Seq[E]] = data.values.toSeq
  def save(entity: E): Identity[E] =
    val id = getId(entity).getOrElse(generateId())
    val entityWithId = setId(entity, id)
    data = data + (id -> entityWithId)
    entityWithId
  def delete(id: ID): Identity[Option[E]] =
    val entity = data.get(id)
    data = data - id
    entity
```

### Client: UserService

```scala
object UserService:
  def createUser[F[_]](
    repository: Repository[F, User, Id],
    name: String,
    email: String
  ): F[User] =
    repository.save(User(name = name, email = email))

  def getUser[F[_]](
    repository: Repository[F, User, Id],
    id: Id
  ): F[Option[User]] =
    repository.findById(id)

  def getAllUsers[F[_]](
    repository: Repository[F, User, Id]
  ): F[Seq[User]] =
    repository.findAll
```

UserService は `Repository` トレイトにのみ依存し、具体的な実装（メモリ、データベース）を知りません。

## 8. Logger パターン

### Abstract Server と実装

```scala
trait Logger:
  def debug(message: String): Unit
  def info(message: String): Unit
  def warn(message: String): Unit
  def error(message: String): Unit
  def error(message: String, throwable: Throwable): Unit

// テスト用ロガー
class TestLogger extends Logger:
  private var logs: List[(String, String)] = List.empty
  def debug(message: String): Unit = logs = logs :+ ("DEBUG", message)
  def info(message: String): Unit = logs = logs :+ ("INFO", message)
  def warn(message: String): Unit = logs = logs :+ ("WARN", message)
  def error(message: String): Unit = logs = logs :+ ("ERROR", message)
  def error(message: String, throwable: Throwable): Unit =
    logs = logs :+ ("ERROR", s"$message: ${throwable.getMessage}")
  def getLogs: List[(String, String)] = logs

// サイレントロガー
object SilentLogger extends Logger:
  def debug(message: String): Unit = ()
  def info(message: String): Unit = ()
  def warn(message: String): Unit = ()
  def error(message: String): Unit = ()
  def error(message: String, throwable: Throwable): Unit = ()
```

テスト時は `TestLogger` を使用してログを検証し、本番では `ConsoleLogger` を使用できます。

## 9. Notification パターン

### Abstract Server と実装

```scala
trait NotificationService:
  def send(recipient: String, subject: String, message: String): Boolean

// モック通知（テスト用）
class MockNotification extends NotificationService:
  private var sentNotifications: List[(String, String, String)] = List.empty
  def send(recipient: String, subject: String, message: String): Boolean =
    sentNotifications = sentNotifications :+ (recipient, subject, message)
    true
  def getSentNotifications: List[(String, String, String)] = sentNotifications

// 複合通知（複数のチャネルに送信）
class CompositeNotification(services: NotificationService*) extends NotificationService:
  def send(recipient: String, subject: String, message: String): Boolean =
    services.forall(_.send(recipient, subject, message))
```

## 10. 依存性注入

### Constructor Injection

```scala
class UserManagementService(
  repository: Repository[Identity, User, Id],
  notification: NotificationService,
  logger: Logger
):
  def registerUser(name: String, email: String): User =
    logger.info(s"Registering user: $name")
    val user = repository.save(User(name = name, email = email))
    notification.send(
      email,
      "Welcome!",
      s"Hello $name, your account has been created."
    )
    logger.info(s"User registered: ${user.id.getOrElse("unknown")}")
    user
```

すべての依存性がコンストラクタで注入され、テスト時にモックを簡単に差し替えられます。

### Reader スタイル

```scala
case class AppEnv(
  repository: Repository[Identity, User, Id],
  notification: NotificationService,
  logger: Logger
)

type Reader[A] = AppEnv => A

def createUserR(name: String, email: String): Reader[User] = env =>
  env.logger.info(s"Creating user: $name")
  val user = env.repository.save(User(name = name, email = email))
  env.notification.send(email, "Welcome!", s"Hello $name!")
  user

def getUserR(id: Id): Reader[Option[User]] = env =>
  env.repository.findById(id)
```

Reader パターンを使用すると、環境を明示的に渡さずに依存性を利用できます。

## 11. Payment Gateway パターン

```scala
sealed trait PaymentResult
case class PaymentSuccess(transactionId: String, amount: Double) extends PaymentResult
case class PaymentFailure(reason: String) extends PaymentResult

trait PaymentGateway:
  def charge(amount: Double, cardToken: String): PaymentResult
  def refund(transactionId: String, amount: Double): PaymentResult

// Stripe Gateway
class StripeGateway extends PaymentGateway:
  def charge(amount: Double, cardToken: String): PaymentResult =
    if cardToken.startsWith("valid_") then
      PaymentSuccess(s"stripe_${System.currentTimeMillis()}", amount)
    else
      PaymentFailure("Invalid card token")
  // ...

// PayPal Gateway
class PayPalGateway extends PaymentGateway:
  def charge(amount: Double, cardToken: String): PaymentResult =
    if cardToken.startsWith("valid_") then
      PaymentSuccess(s"paypal_${System.currentTimeMillis()}", amount)
    else
      PaymentFailure("Invalid card token")
  // ...

// Client
class CheckoutService(paymentGateway: PaymentGateway):
  def processPayment(amount: Double, cardToken: String): Either[String, String] =
    paymentGateway.charge(amount, cardToken) match
      case PaymentSuccess(transactionId, _) => Right(transactionId)
      case PaymentFailure(reason) => Left(reason)
```

`CheckoutService` は `PaymentGateway` インターフェースにのみ依存し、Stripe と PayPal を簡単に切り替えられます。

## 12. 関数型アプローチ

```scala
object FunctionalAbstractServer:
  /**
   * 関数型 Switchable - 状態変換関数のレコード
   */
  case class SwitchableFn[A](
    turnOn: A => A,
    turnOff: A => A,
    isOn: A => Boolean
  ):
    def toggle(device: A): A =
      if isOn(device) then turnOff(device) else turnOn(device)

  case class SimpleDevice(on: Boolean = false)

  val simpleDeviceSwitchable: SwitchableFn[SimpleDevice] = SwitchableFn(
    turnOn = _.copy(on = true),
    turnOff = _.copy(on = false),
    isOn = _.on
  )

  trait FunctionalSwitch[A]:
    def engage(device: A): A
    def disengage(device: A): A
    def toggle(device: A): A
    def status(device: A): String

  def createSwitch[A](switchable: SwitchableFn[A]): FunctionalSwitch[A] =
    new FunctionalSwitch[A]:
      def engage(device: A): A = switchable.turnOn(device)
      def disengage(device: A): A = switchable.turnOff(device)
      def toggle(device: A): A = switchable.toggle(device)
      def status(device: A): String = if switchable.isOn(device) then "on" else "off"
```

関数のレコードとして抽象化を表現することで、より柔軟な合成が可能になります。

## 13. パターンの利点

1. **疎結合**: クライアントは具体的な実装を知らない
2. **テスト容易性**: モック/スタブを簡単に注入可能
3. **柔軟性**: 実装の交換が容易
4. **依存関係逆転**: 高レベルモジュールが低レベルモジュールに依存しない

## 14. Clojure との比較

| 概念 | Clojure | Scala |
|------|---------|-------|
| Abstract Server | `defprotocol` | `trait` / 型クラス |
| Concrete Server | `defrecord` + プロトコル実装 | `class` / `given` インスタンス |
| 状態管理 | イミュータブルレコード | イミュータブル `case class` |
| 依存性注入 | 関数の引数 | コンストラクタ / Reader パターン |
| 多態性 | プロトコルディスパッチ | 型クラス / サブタイピング |

Scala では型クラスを使用することで、Clojure のプロトコルと同様の多態性を実現しつつ、コンパイル時の型安全性を得られます。

## まとめ

本章では、Abstract Server パターンについて学びました：

1. **型クラスによる抽象化**: `Switchable`, `Repository`
2. **trait による実装**: `Light`, `Fan`, `Motor`, `MemoryRepository`
3. **クライアントの独立性**: `Switch`, `UserService`
4. **依存関係逆転**: 高レベルモジュールが抽象に依存

Abstract Server パターンは、モジュール間の疎結合を実現し、テスト容易性と柔軟性を向上させます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part3/src/main/scala/AbstractServerPattern.scala`
- テストコード: `apps/scala/part3/src/test/scala/AbstractServerPatternSpec.scala`
