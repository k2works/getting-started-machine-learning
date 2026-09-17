---
type: Article
title: "第2章: 関数合成と高階関数"
description: "関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第2章: 関数合成と高階関数

## はじめに

関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。本章では、Scala における関数合成のテクニックと高階関数の活用方法を学びます。

## 1. 関数合成の基本 (andThen, compose)

### andThen と compose による関数の連結

Scala では `andThen` と `compose` メソッドを使用して関数を合成できます。

- **`andThen`**: 左から右へ順番に適用（直感的）
- **`compose`**: 右から左へ順番に適用（数学的な関数合成）

```scala
def addTax(rate: Double)(amount: Double): Double =
  amount * (1 + rate)

def applyDiscountRate(rate: Double)(amount: Double): Double =
  amount * (1 - rate)

def roundToYen(amount: Double): Long =
  Math.round(amount)

// andThen による関数合成（左から右）
val calculateFinalPrice: Double => Long =
  applyDiscountRate(0.2) andThen addTax(0.1) andThen roundToYen

// compose による関数合成（右から左）
val calculateFinalPriceCompose: Double => Long =
  (roundToYen _) compose addTax(0.1) compose applyDiscountRate(0.2)

// 使用例
calculateFinalPrice(1000)
// => 880
// 処理順序: 1000 → 20%割引(800) → 10%税込(880) → 丸め(880)
```

### 関数合成の利点

1. **宣言的な記述**: 処理の流れを関数のチェーンとして表現
2. **再利用性**: 合成した関数を別の場所で再利用可能
3. **テスト容易性**: 各関数を個別にテスト可能

## 2. カリー化と部分適用

### カリー化による引数の固定

Scala では関数をカリー化形式で定義することで、部分適用が自然に行えます。

```scala
// カリー化された関数
def greetCurried(greeting: String)(name: String): String =
  s"$greeting, $name!"

val sayHello: String => String = greetCurried("Hello")
val sayGoodbye: String => String = greetCurried("Goodbye")

sayHello("田中")    // => "Hello, 田中!"
sayGoodbye("鈴木")  // => "Goodbye, 鈴木!"
```

### 複数引数の部分適用

```scala
case class Email(from: String, to: String, subject: String, body: String)

def sendEmail(from: String)(to: String)(subject: String)(body: String): Email =
  Email(from, to, subject, body)

val sendFromSystem = sendEmail("system@example.com")
val sendNotification = sendFromSystem("user@example.com")("通知")

sendNotification("メッセージ本文")
// => Email(from = "system@example.com",
//          to = "user@example.com",
//          subject = "通知",
//          body = "メッセージ本文")
```

## 3. 複数の関数を並列適用

Clojure の `juxt` に相当する機能は、Scala ではタプルやケースクラスを使って表現します。

```scala
// 数値リストの統計情報を取得する
def getStats(numbers: List[Int]): (Int, Int, Int, Int, Int) =
  (numbers.head, numbers.last, numbers.length, numbers.min, numbers.max)

getStats(List(3, 1, 4, 1, 5, 9, 2, 6))
// => (3, 6, 8, 1, 9)
// (最初の値, 最後の値, 要素数, 最小値, 最大値)
```

### 実用例：データ分析

```scala
case class PersonAnalysis(name: String, age: Int, category: String)

def analyzePerson(person: Map[String, Any]): PersonAnalysis =
  val name = person("name").asInstanceOf[String]
  val age = person("age").asInstanceOf[Int]
  val category = if age >= 18 then "adult" else "minor"
  PersonAnalysis(name, age, category)

analyzePerson(Map("name" -> "田中", "age" -> 25))
// => PersonAnalysis("田中", 25, "adult")

analyzePerson(Map("name" -> "鈴木", "age" -> 15))
// => PersonAnalysis("鈴木", 15, "minor")
```

## 4. 高階関数によるデータ処理

高階関数とは、関数を引数として受け取るか、関数を返す関数のことです。

### ログ出力のラッパー

```scala
def processWithLogging[A, B](f: A => B): A => B =
  (input: A) =>
    println(s"入力: $input")
    val result = f(input)
    println(s"出力: $result")
    result

val doubleWithLog = processWithLogging[Int, Int](_ * 2)
doubleWithLog(5)
// 入力: 5
// 出力: 10
// => 10
```

### リトライ機能の追加

```scala
def retry[A, B](f: A => B, maxRetries: Int): A => B =
  (input: A) =>
    def attempt(attempts: Int): B =
      try
        f(input)
      catch
        case e: Exception =>
          if attempts < maxRetries then attempt(attempts + 1)
          else throw e
    attempt(0)

// 不安定なAPI呼び出しをリトライ付きでラップ
val fetchWithRetry = retry(fetchData, 3)
```

### TTL 付きメモ化

```scala
def memoizeWithTtl[A, B](f: A => B, ttlMs: Long): A => B =
  var cache: Map[A, (B, Long)] = Map.empty

  (input: A) =>
    val now = System.currentTimeMillis()
    cache.get(input) match
      case Some((value, time)) if now - time < ttlMs => value
      case _ =>
        val result = f(input)
        cache = cache + (input -> (result, now))
        result
```

## 5. パイプライン処理

複数の関数を順次適用するパイプラインを構築します。

```scala
def pipeline[A](fns: (A => A)*): A => A =
  (input: A) => fns.foldLeft(input)((acc, f) => f(acc))
```

### 注文処理パイプラインの実装

```scala
case class OrderItem(price: Int, quantity: Int)
case class Customer(membership: String)
case class Order(items: List[OrderItem], customer: Customer, total: Double = 0, shipping: Int = 0)

def validateOrder(order: Order): Order =
  if order.items.isEmpty then
    throw new IllegalArgumentException("注文にアイテムがありません")
  else
    order

def calculateOrderTotal(order: Order): Order =
  val total = order.items.map(item => item.price * item.quantity).sum
  order.copy(total = total)

def applyOrderDiscount(order: Order): Order =
  val discountRates = Map("gold" -> 0.1, "silver" -> 0.05, "bronze" -> 0.02)
  val discountRate = discountRates.getOrElse(order.customer.membership, 0.0)
  order.copy(total = order.total * (1 - discountRate))

def addShipping(order: Order): Order =
  val shipping = if order.total >= 5000 then 0 else 500
  order.copy(shipping = shipping, total = order.total + shipping)

val processOrderPipeline: Order => Order =
  pipeline(validateOrder, calculateOrderTotal, applyOrderDiscount, addShipping)

// 使用例
processOrderPipeline(Order(
  items = List(OrderItem(1000, 2), OrderItem(500, 3)),
  customer = Customer("gold")
))
// => Order(items = [...], customer = Customer("gold"), total = 3650.0, shipping = 500)
```

## 6. 関数合成によるバリデーション

バリデーションロジックを関数合成で表現します。

```scala
case class ValidationResult[A](valid: Boolean, value: A, error: Option[String] = None)

def validator[A](pred: A => Boolean, errorMsg: String): A => ValidationResult[A] =
  (value: A) =>
    if pred(value) then ValidationResult(valid = true, value = value)
    else ValidationResult(valid = false, value = value, error = Some(errorMsg))

def combineValidators[A](validators: (A => ValidationResult[A])*): A => ValidationResult[A] =
  (value: A) =>
    validators.foldLeft(ValidationResult(valid = true, value = value)) { (result, v) =>
      if result.valid then v(result.value)
      else result
    }

// 個別のバリデータ
val validatePositive = validator[Int](_ > 0, "値は正の数である必要があります")
val validateUnder100 = validator[Int](_ < 100, "値は100未満である必要があります")

// バリデータの合成
def validateQuantity(value: Int): ValidationResult[Int] =
  combineValidators(validatePositive, validateUnder100)(value)

// 使用例
validateQuantity(50)   // => ValidationResult(true, 50, None)
validateQuantity(-1)   // => ValidationResult(false, -1, Some("値は正の数である必要があります"))
validateQuantity(100)  // => ValidationResult(false, 100, Some("値は100未満である必要があります"))
```

## 7. 関数の変換

関数自体を変換するユーティリティ関数を作成します。

### 引数の順序を反転

```scala
def flip[A, B, C](f: (A, B) => C): (B, A) => C =
  (b: B, a: A) => f(a, b)

val subtract = (a: Int, b: Int) => a - b
flip(subtract)(3, 5)  // => 2  (5 - 3)
```

### カリー化と非カリー化

```scala
def curry[A, B, C](f: (A, B) => C): A => B => C =
  (a: A) => (b: B) => f(a, b)

def uncurry[A, B, C](f: A => B => C): (A, B) => C =
  (a: A, b: B) => f(a)(b)

val add = (a: Int, b: Int) => a + b
val curriedAdd = curry(add)
val add5 = curriedAdd(5)
add5(3)  // => 8
```

### 補関数（complement）

```scala
def complementFn[A](pred: A => Boolean): A => Boolean =
  (a: A) => !pred(a)

val isEven = (x: Int) => x % 2 == 0
val isOdd = complementFn(isEven)
isOdd(3)  // => true
```

## 8. 関数合成のパターン

### 述語の合成

```scala
def composePredicates[A](preds: (A => Boolean)*): A => Boolean =
  (x: A) => preds.forall(_(x))

def composePredicatesOr[A](preds: (A => Boolean)*): A => Boolean =
  (x: A) => preds.exists(_(x))

// 有効な年齢チェック
val validAge: Int => Boolean =
  composePredicates[Int](
    _ > 0,
    _ <= 150
  )

validAge(25)   // => true
validAge(-1)   // => false
validAge(200)  // => false

// プレミアム顧客チェック
case class CustomerInfo(membership: String, purchaseCount: Int, totalSpent: Int)

val premiumCustomer: CustomerInfo => Boolean =
  composePredicatesOr[CustomerInfo](
    _.membership == "gold",
    _.purchaseCount >= 100,
    _.totalSpent >= 100000
  )

premiumCustomer(CustomerInfo("gold", 0, 0))           // => true
premiumCustomer(CustomerInfo("bronze", 100, 0))       // => true
premiumCustomer(CustomerInfo("bronze", 10, 1000))     // => false
```

## まとめ

本章では、関数合成と高階関数について学びました：

1. **andThen/compose**: 関数を合成して新しい関数を作成
2. **カリー化**: 引数を部分適用して特化した関数を作成
3. **タプル/ケースクラス**: 複数の関数を並列適用して結果を取得
4. **高階関数**: ログ、リトライ、メモ化などの横断的関心事を抽象化
5. **パイプライン**: 処理の流れを関数のチェーンとして表現
6. **バリデーション**: 関数合成による柔軟な検証ロジック
7. **述語合成**: AND/OR で複数の条件を組み合わせ

これらのテクニックにより、小さく再利用可能な関数から複雑なビジネスロジックを構築できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part1/src/main/scala/Composition.scala`
- テストコード: `apps/scala/part1/src/test/scala/CompositionSpec.scala`

## 次章予告

次章では、**多態性とディスパッチ**について学びます。トレイト、型クラス、パターンマッチングを活用した柔軟な設計パターンを探ります。
