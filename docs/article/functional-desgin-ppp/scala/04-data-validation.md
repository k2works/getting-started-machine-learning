---
type: Article
title: "第4章: データバリデーション"
description: "Scala では、型システムとスマートコンストラクタを組み合わせることで、コンパイル時と実行時の両方でデータの整合性を保証できます。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第4章: データバリデーション

## はじめに

Scala では、型システムとスマートコンストラクタを組み合わせることで、コンパイル時と実行時の両方でデータの整合性を保証できます。本章では、Either、Validated パターン、Opaque Type を使ったデータバリデーションの実現方法を学びます。

## 1. 基本的なバリデーション（Either を使用）

### Either によるバリデーション

`Either[L, R]` は、成功時に `Right`、失敗時に `Left` を返す型です。バリデーションに最適です。

```scala
type ValidationResult[A] = Either[List[String], A]

def validateName(name: String): ValidationResult[String] =
  if name.isEmpty then Left(List("名前は空にできません"))
  else if name.length > 100 then Left(List("名前は100文字以内である必要があります"))
  else Right(name)

def validateAge(age: Int): ValidationResult[Int] =
  if age < 0 then Left(List("年齢は0以上である必要があります"))
  else if age > 150 then Left(List("年齢は150以下である必要があります"))
  else Right(age)

def validateEmail(email: String): ValidationResult[String] =
  val emailRegex = ".+@.+\\..+".r
  if emailRegex.matches(email) then Right(email)
  else Left(List("無効なメールアドレス形式です"))

// 使用例
validateName("田中太郎")  // => Right("田中太郎")
validateName("")          // => Left(List("名前は空にできません"))
validateAge(25)           // => Right(25)
validateAge(-1)           // => Left(List("年齢は0以上である必要があります"))
```

## 2. 列挙型とスマートコンストラクタ

### Enum による列挙型

```scala
enum Membership:
  case Bronze, Silver, Gold, Platinum

object Membership:
  def fromString(s: String): ValidationResult[Membership] =
    s.toLowerCase match
      case "bronze"   => Right(Bronze)
      case "silver"   => Right(Silver)
      case "gold"     => Right(Gold)
      case "platinum" => Right(Platinum)
      case _          => Left(List(s"無効な会員種別: $s"))

enum Status:
  case Active, Inactive, Suspended
```

## 3. Validated パターン（エラー蓄積）

Either は最初のエラーで停止しますが、すべてのエラーを収集したい場合は Validated パターンを使用します。

```scala
enum Validated[+E, +A]:
  case Valid(value: A)
  case Invalid(errors: List[E])

  def map[B](f: A => B): Validated[E, B] = this match
    case Valid(a)      => Valid(f(a))
    case Invalid(errs) => Invalid(errs)

  def flatMap[EE >: E, B](f: A => Validated[EE, B]): Validated[EE, B] = this match
    case Valid(a)      => f(a)
    case Invalid(errs) => Invalid(errs)

  def isValid: Boolean = this match
    case Valid(_)   => true
    case Invalid(_) => false

  def toEither: Either[List[E], A] = this match
    case Valid(a)      => Right(a)
    case Invalid(errs) => Left(errs)

object Validated:
  def valid[E, A](a: A): Validated[E, A] = Valid(a)
  def invalid[E, A](errors: List[E]): Validated[E, A] = Invalid(errors)

  // 2つの Validated を結合（エラー蓄積）
  def combine[E, A, B, C](va: Validated[E, A], vb: Validated[E, B])(f: (A, B) => C): Validated[E, C] =
    (va, vb) match
      case (Valid(a), Valid(b))       => Valid(f(a, b))
      case (Invalid(e1), Invalid(e2)) => Invalid(e1 ++ e2)  // エラーを蓄積！
      case (Invalid(e), _)            => Invalid(e)
      case (_, Invalid(e))            => Invalid(e)
```

### 使用例

```scala
val result = Validated.combine(
  Validated.invalid[String, Int](List("error1")),
  Validated.invalid[String, Int](List("error2"))
)(_ + _)
// => Invalid(List("error1", "error2"))  // 両方のエラーが収集される
```

## 4. Opaque Type によるドメインモデル

Opaque Type を使うと、実行時のオーバーヘッドなしに型安全なドメインプリミティブを作成できます。

```scala
// 商品ID（バリデーション済み）
opaque type ProductId = String

object ProductId:
  private val pattern = "PROD-\\d{5}".r

  def apply(id: String): Validated[String, ProductId] =
    if pattern.matches(id) then Validated.valid(id)
    else Validated.invalid(List(s"無効な商品ID形式: $id (PROD-XXXXXの形式が必要)"))

  def unsafe(id: String): ProductId = id  // テスト用

  extension (id: ProductId)
    def value: String = id

// 価格（正の数）
opaque type Price = BigDecimal

object Price:
  def apply(amount: BigDecimal): Validated[String, Price] =
    if amount <= 0 then Validated.invalid(List("価格は正の数である必要があります"))
    else Validated.valid(amount)

  def unsafe(amount: BigDecimal): Price = amount

  extension (price: Price)
    def value: BigDecimal = price

// 数量（正の整数）
opaque type Quantity = Int

object Quantity:
  def apply(qty: Int): Validated[String, Quantity] =
    if qty <= 0 then Validated.invalid(List("数量は正の整数である必要があります"))
    else Validated.valid(qty)

  def unsafe(qty: Int): Quantity = qty

  extension (qty: Quantity)
    def value: Int = qty
```

### 商品モデル

```scala
case class Product(
  id: ProductId,
  name: ProductName,
  price: Price,
  description: Option[String] = None,
  category: Option[String] = None
)

object Product:
  def create(
    id: String,
    name: String,
    price: BigDecimal,
    description: Option[String] = None,
    category: Option[String] = None
  ): Validated[String, Product] =
    Validated.combine3(
      ProductId(id),
      ProductName(name),
      Price(price)
    )((pid, pname, pprice) => Product(pid, pname, pprice, description, category))

// 使用例
Product.create("PROD-00001", "テスト商品", BigDecimal(1000))
// => Valid(Product(...))

Product.create("INVALID", "", BigDecimal(-100))
// => Invalid(List("無効な商品ID形式...", "商品名は空にできません", "価格は正の数である必要があります"))
```

## 5. 注文ドメインモデル

```scala
opaque type OrderId = String

object OrderId:
  private val pattern = "ORD-\\d{8}".r

  def apply(id: String): Validated[String, OrderId] =
    if pattern.matches(id) then Validated.valid(id)
    else Validated.invalid(List(s"無効な注文ID形式: $id"))

opaque type CustomerId = String

object CustomerId:
  private val pattern = "CUST-\\d{6}".r

  def apply(id: String): Validated[String, CustomerId] =
    if pattern.matches(id) then Validated.valid(id)
    else Validated.invalid(List(s"無効な顧客ID形式: $id"))

case class OrderItem(
  productId: ProductId,
  quantity: Quantity,
  price: Price
):
  def total: BigDecimal = Price.value(price) * Quantity.value(quantity)

object OrderItem:
  def create(productId: String, quantity: Int, price: BigDecimal): Validated[String, OrderItem] =
    Validated.combine3(
      ProductId(productId),
      Quantity(quantity),
      Price(price)
    )(OrderItem.apply)

case class Order(
  orderId: OrderId,
  customerId: CustomerId,
  items: List[OrderItem],
  orderDate: java.time.LocalDate,
  total: Option[BigDecimal] = None,
  status: Option[Status] = None
):
  def calculateTotal: BigDecimal = items.map(_.total).sum
```

## 6. 条件付きバリデーション（ADT）

データの種類に応じて異なるバリデーションルールを適用する場合は、ADT（代数的データ型）を使用します。

```scala
enum NotificationType:
  case Email, SMS, Push

sealed trait Notification:
  def body: String

case class EmailNotification(to: String, subject: String, body: String) extends Notification
case class SMSNotification(phoneNumber: String, body: String) extends Notification
case class PushNotification(deviceToken: String, body: String) extends Notification

object Notification:
  private val emailPattern = ".+@.+\\..+".r
  private val phonePattern = "\\d{2,4}-\\d{2,4}-\\d{4}".r

  def createEmail(to: String, subject: String, body: String): Validated[String, EmailNotification] =
    if !emailPattern.matches(to) then Validated.invalid(List("無効なメールアドレス形式です"))
    else if subject.isEmpty then Validated.invalid(List("件名は空にできません"))
    else if body.isEmpty then Validated.invalid(List("本文は空にできません"))
    else Validated.valid(EmailNotification(to, subject, body))

  def createSMS(phoneNumber: String, body: String): Validated[String, SMSNotification] =
    if !phonePattern.matches(phoneNumber) then Validated.invalid(List("無効な電話番号形式です"))
    else if body.isEmpty then Validated.invalid(List("本文は空にできません"))
    else Validated.valid(SMSNotification(phoneNumber, body))

  def createPush(deviceToken: String, body: String): Validated[String, PushNotification] =
    if deviceToken.isEmpty then Validated.invalid(List("デバイストークンは空にできません"))
    else if body.isEmpty then Validated.invalid(List("本文は空にできません"))
    else Validated.valid(PushNotification(deviceToken, body))

// 使用例
Notification.createEmail("test@example.com", "テスト", "本文")
// => Valid(EmailNotification(...))

Notification.createSMS("090-1234-5678", "本文")
// => Valid(SMSNotification(...))
```

## 7. バリデーションユーティリティ

### バリデーション結果の構造化

```scala
case class ValidationResponse[A](valid: Boolean, data: Option[A], errors: List[String])

def validatePerson(name: String, age: Int): ValidationResponse[Person] =
  val nameV = if name.isEmpty then Validated.invalid[String, String](List("名前は空にできません"))
    else Validated.valid[String, String](name)
  val ageV = if age < 0 then Validated.invalid[String, Int](List("年齢は0以上である必要があります"))
    else Validated.valid[String, Int](age)

  val validated = Validated.combine(nameV, ageV)((n, a) => Person(n, a))

  validated match
    case Validated.Valid(p)   => ValidationResponse(true, Some(p), List.empty)
    case Validated.Invalid(e) => ValidationResponse(false, None, e)

// 使用例
validatePerson("田中", 30)
// => ValidationResponse(true, Some(Person("田中", 30)), List())

validatePerson("", -1)
// => ValidationResponse(false, None, List("名前は空にできません", "年齢は0以上である必要があります"))
```

### 例外をスローするバリデーション

```scala
def conformOrThrow[A](validated: Validated[String, A]): A =
  validated match
    case Validated.Valid(a)      => a
    case Validated.Invalid(errs) => throw new IllegalArgumentException(s"Validation failed: ${errs.mkString(", ")}")
```

## 8. 計算関数

```scala
def calculateItemTotal(item: OrderItem): BigDecimal =
  Price.value(item.price) * Quantity.value(item.quantity)

def calculateOrderTotal(order: Order): BigDecimal =
  order.items.map(calculateItemTotal).sum

def applyDiscount(total: BigDecimal, discountRate: Double): Either[String, BigDecimal] =
  if discountRate < 0 || discountRate > 1 then
    Left("割引率は0から1の間である必要があります")
  else
    Right(total * (1 - discountRate))

def sumPrices(prices: BigDecimal*): BigDecimal =
  prices.sum
```

## Clojure Spec との比較

| 特徴 | Clojure Spec | Scala |
|------|-------------|-------|
| 検証タイミング | 実行時 | コンパイル時 + 実行時 |
| エラー蓄積 | explain-data | Validated パターン |
| 型安全性 | 動的 | 静的（Opaque Type） |
| ジェネレータ | 組み込み | ScalaCheck（別ライブラリ） |
| 関数仕様 | fdef | 型シグネチャ + 事前条件 |

## まとめ

本章では、Scala におけるデータバリデーションについて学びました：

1. **Either**: 基本的な成功/失敗の表現
2. **Validated パターン**: エラーの蓄積
3. **Opaque Type**: 型安全なドメインプリミティブ
4. **スマートコンストラクタ**: バリデーション付きのインスタンス生成
5. **ADT**: 条件付きバリデーション
6. **ValidationResponse**: 構造化されたバリデーション結果

Scala の型システムを活用することで、コンパイル時に多くのエラーを検出し、実行時には Validated パターンで詳細なエラー情報を提供できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part2/src/main/scala/DataValidation.scala`
- テストコード: `apps/scala/part2/src/test/scala/DataValidationSpec.scala`

## 次章予告

次章では、**プロパティベーステスト**について学びます。ScalaCheck を使った生成的テストの手法を探ります。
