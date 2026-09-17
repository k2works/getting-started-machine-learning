---
type: Article
title: "第17章: レンタルビデオシステム"
description: "本章では、Martin Fowler の「リファクタリング」で有名なレンタルビデオシステムを題材に、関数型プログラミングによる料金計算ロジックの設計を学びます。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第17章: レンタルビデオシステム

## はじめに

本章では、Martin Fowler の「リファクタリング」で有名なレンタルビデオシステムを題材に、関数型プログラミングによる料金計算ロジックの設計を学びます。

この問題を通じて以下の概念を学びます：

- パターンマッチによる料金計算
- 明細書フォーマッターの実装
- データと処理の分離
- DSL による流暢な API

## 1. ドメインモデル

### 映画カテゴリ

レンタルビデオシステムでは、以下の3種類の映画カテゴリをサポートします：

- **通常（Regular）**: 2日まで2.0、以降1日ごとに1.5追加
- **新作（New Release）**: 1日ごとに3.0
- **子供向け（Children's）**: 3日まで1.5、以降1日ごとに1.5追加

```scala
enum MovieCategory:
  case Regular     // 通常
  case NewRelease  // 新作
  case Childrens   // 子供向け
```

### 映画

```scala
case class Movie(title: String, category: MovieCategory)

object Movie:
  def regular(title: String): Movie = Movie(title, MovieCategory.Regular)
  def newRelease(title: String): Movie = Movie(title, MovieCategory.NewRelease)
  def childrens(title: String): Movie = Movie(title, MovieCategory.Childrens)
```

### レンタルと顧客

```scala
case class Rental(movie: Movie, days: Days)

case class Customer(name: String, rentals: List[Rental] = Nil):
  def addRental(rental: Rental): Customer = copy(rentals = rentals :+ rental)
```

## 2. 料金計算

### 料金計算器

```scala
trait PriceCalculator:
  def calculateAmount(days: Days): Money
  def calculatePoints(days: Days): Points

object RegularPricing extends PriceCalculator:
  def calculateAmount(days: Days): Money =
    if days > 2 then BigDecimal(2.0) + BigDecimal((days - 2) * 1.5)
    else BigDecimal(2.0)
  def calculatePoints(days: Days): Points = 1

object NewReleasePricing extends PriceCalculator:
  def calculateAmount(days: Days): Money = BigDecimal(days * 3.0)
  def calculatePoints(days: Days): Points = if days > 1 then 2 else 1

object ChildrensPricing extends PriceCalculator:
  def calculateAmount(days: Days): Money =
    if days > 3 then BigDecimal(1.5) + BigDecimal((days - 3) * 1.5)
    else BigDecimal(1.5)
  def calculatePoints(days: Days): Points = 1
```

### 計算例

| カテゴリ | 日数 | 料金計算 | 結果 |
|---------|------|---------|------|
| 通常 | 2日 | 2.0 | 2.0 |
| 通常 | 5日 | 2.0 + 3 * 1.5 | 6.5 |
| 新作 | 3日 | 3 * 3.0 | 9.0 |
| 子供向け | 3日 | 1.5 | 1.5 |
| 子供向け | 6日 | 1.5 + 3 * 1.5 | 6.0 |

## 3. 明細データ

```scala
case class RentalLine(
  title: String,
  days: Days,
  amount: Money,
  points: Points
)

case class StatementData(
  customerName: String,
  rentalLines: List[RentalLine],
  totalAmount: Money,
  totalPoints: Points
)

def generateStatementData(customer: Customer): StatementData =
  val lines = customer.rentals.map { rental =>
    RentalLine(
      title = rental.movie.title,
      days = rental.days,
      amount = calculateRentalAmount(rental),
      points = calculateRentalPoints(rental)
    )
  }
  StatementData(
    customerName = customer.name,
    rentalLines = lines,
    totalAmount = totalAmount(customer.rentals),
    totalPoints = totalPoints(customer.rentals)
  )
```

## 4. 明細書フォーマッター

### フォーマット形式

```scala
enum StatementFormat:
  case Text
  case Html
  case Json
```

### テキスト形式

```scala
object TextFormatter extends StatementFormatter:
  def format(data: StatementData): String =
    val header = s"Rental Record for ${data.customerName}\n"
    val lines = data.rentalLines.map { line =>
      s"\t${line.title}\t${line.amount}\n"
    }.mkString
    val footer = s"Amount owed is ${data.totalAmount}\n" +
      s"You earned ${data.totalPoints} frequent renter points"
    header + lines + footer
```

### HTML形式

```scala
object HtmlFormatter extends StatementFormatter:
  def format(data: StatementData): String =
    val header = s"<h1>Rental Record for <em>${data.customerName}</em></h1>\n<ul>\n"
    val lines = data.rentalLines.map { line =>
      s"  <li>${line.title} - ${line.amount}</li>\n"
    }.mkString
    val footer = s"</ul>\n" +
      s"<p>Amount owed is <strong>${data.totalAmount}</strong></p>\n" +
      s"<p>You earned <strong>${data.totalPoints}</strong> frequent renter points</p>"
    header + lines + footer
```

### 出力例

**テキスト形式:**
```
Rental Record for John
  Inception  3.5
  Frozen     3.0
  New Movie  6.0
Amount owed is 12.5
You earned 4 frequent renter points
```

**HTML形式:**
```html
<h1>Rental Record for <em>John</em></h1>
<ul>
  <li>Inception - 3.5</li>
  <li>Frozen - 3.0</li>
  <li>New Movie - 6.0</li>
</ul>
<p>Amount owed is <strong>12.5</strong></p>
<p>You earned <strong>4</strong> frequent renter points</p>
```

## 5. ポリシーベースの設計

より柔軟な設計として、料金ポリシーを関数として表現できます。

```scala
case class PricingPolicy(
  name: String,
  calculateAmount: Days => Money,
  calculatePoints: Days => Points
)

object PricingPolicies:
  val regular: PricingPolicy = PricingPolicy(
    "Regular",
    days => if days > 2 then BigDecimal(2.0 + (days - 2) * 1.5) else BigDecimal(2.0),
    _ => 1
  )

  val premium: PricingPolicy = PricingPolicy(
    "Premium",
    days => BigDecimal(days * 5.0),
    days => days  // 日数分のポイント
  )
```

## 6. レンタルショップ

```scala
class RentalShop:
  private var movies: Map[String, Movie] = Map.empty
  private var customers: Map[String, Customer] = Map.empty

  def addMovie(movie: Movie): Unit = ...
  def registerCustomer(name: String): Customer = ...
  def rentMovie(customerName: String, movieTitle: String, days: Days): Option[Rental] = ...
  def generateCustomerStatement(customerName: String, format: StatementFormat): Option[String] = ...
```

## 7. 割引計算

```scala
sealed trait Discount:
  def apply(amount: Money): Money

case class PercentageDiscount(percent: Double) extends Discount:
  def apply(amount: Money): Money = amount * BigDecimal(1 - percent)

case class FixedDiscount(value: Money) extends Discount:
  def apply(amount: Money): Money = (amount - value) max BigDecimal(0)

def applyDiscounts(amount: Money, discounts: Seq[Discount]): Money =
  discounts.foldLeft(amount)((acc, d) => d.apply(acc))
```

## 8. 売上レポート

```scala
case class SalesReport(
  period: String,
  totalRentals: Int,
  totalRevenue: Money,
  rentalsByCategory: Map[MovieCategory, Int],
  revenueByCategory: Map[MovieCategory, Money]
)

def generateSalesReport(rentals: Seq[Rental], period: String): SalesReport =
  val rentalsByCategory = rentals.groupBy(_.movie.category).view.mapValues(_.size).toMap
  val revenueByCategory = rentals.groupBy(_.movie.category).view.mapValues(rs => totalAmount(rs)).toMap
  SalesReport(period, rentals.size, totalAmount(rentals), rentalsByCategory, revenueByCategory)
```

## 9. DSL

```scala
object RentalDSL:
  def customer(name: String): CustomerBuilder = CustomerBuilder(name)

  case class CustomerBuilder(name: String, rentals: List[Rental] = Nil):
    def rents(movie: Movie, days: Days): CustomerBuilder =
      copy(rentals = rentals :+ Rental(movie, days))
    def build: Customer = Customer(name, rentals)
    def statement: String = generateStatement(build)
    def statementAs(format: StatementFormat): String = generateStatement(build, format)

  def regular(title: String): Movie = Movie.regular(title)
  def newRelease(title: String): Movie = Movie.newRelease(title)
  def childrens(title: String): Movie = Movie.childrens(title)

// 使用例
import RentalDSL.*
val statement = customer("John")
  .rents(regular("Inception"), 3)
  .rents(newRelease("New Movie"), 2)
  .statement
```

## 10. Clojure との比較

| 概念 | Clojure | Scala |
|------|---------|-------|
| 映画カテゴリ | キーワード (`:regular`) | enum |
| 料金計算 | マルチメソッド | パターンマッチ / object |
| 明細データ | マップ | case class |
| フォーマッター | マルチメソッド | trait + object |

## 11. リファクタリングのポイント

### OOP vs FP

**OOP アプローチ:**
- `Movie` クラスに `getCharge()` メソッド
- サブクラスでオーバーライド

**FP アプローチ:**
- 映画はデータのみ（case class）
- 料金計算は別の関数/object
- カテゴリに応じたディスパッチ

### 利点

1. **データと処理の分離**: テストが容易
2. **拡張容易性**: 新しいカテゴリ追加が容易
3. **オープン・クローズド原則**: 既存コード変更不要

## まとめ

本章では、レンタルビデオシステムを通じて以下を学びました：

1. **パターンマッチ**: カテゴリによる料金計算のディスパッチ
2. **データと処理の分離**: 映画データと計算ロジックの分離
3. **フォーマッターパターン**: 複数の出力形式への対応
4. **拡張性**: 新しいカテゴリやフォーマットの追加が容易
5. **DSL**: 流暢な API による読みやすいコード

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part6/src/main/scala/VideoRentalSystem.scala`
- テストコード: `apps/scala/part6/src/test/scala/VideoRentalSystemSpec.scala`
