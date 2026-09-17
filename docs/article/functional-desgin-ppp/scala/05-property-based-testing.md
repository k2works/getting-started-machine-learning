---
type: Article
title: "第5章: プロパティベーステスト"
description: "従来の単体テストでは、特定の入力に対する期待される出力を検証します。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第5章: プロパティベーステスト

## はじめに

従来の単体テストでは、特定の入力に対する期待される出力を検証します。一方、**プロパティベーステスト**では、すべての入力に対して成り立つべき「性質（プロパティ）」を定義し、ランダムに生成された多数のテストケースで検証します。

本章では、Scala の **ScalaCheck** ライブラリを使ったプロパティベーステストの手法を学びます。

## 1. プロパティベーステストとは

### 従来のテストとの違い

```scala
// 従来のテスト：特定の入力に対する出力を検証
test("reverseString should reverse a specific string") {
  StringOperations.reverseString("hello") shouldBe "olleh"
  StringOperations.reverseString("") shouldBe ""
  StringOperations.reverseString("a") shouldBe "a"
}

// プロパティベーステスト：性質を検証
test("文字列反転は対合（involutory）: 2回反転すると元に戻る") {
  forAll { (s: String) =>
    StringOperations.reverseString(StringOperations.reverseString(s)) shouldBe s
  }
}
```

### プロパティベーステストの利点

1. **網羅性**: 手動では思いつかないエッジケースを発見
2. **ドキュメント性**: コードの性質を明確に表現
3. **回帰防止**: リファクタリング時の安全網
4. **シュリンキング**: 失敗時に最小の反例を提示

## 2. ScalaCheck の基本

### セットアップ

```scala
// build.sbt に追加
libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test,
  "org.scalacheck" %% "scalacheck" % "1.17.0" % Test
)
```

### 基本的な使い方

```scala
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

class MySpec extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks:
  
  test("プロパティの例") {
    forAll { (s: String) =>
      s.reverse.reverse shouldBe s
    }
  }
```

## 3. 基本的なジェネレータ

### プリミティブジェネレータ

```scala
import org.scalacheck.Gen

// 整数
Gen.posNum[Int]       // 正の整数
Gen.negNum[Int]       // 負の整数
Gen.chooseNum(0, 100) // 範囲指定

// 文字列
Gen.alphaStr          // 英字のみ
Gen.alphaNumStr       // 英数字のみ
Gen.numStr            // 数字のみ

// その他
Gen.oneOf(true, false)              // 列挙
Gen.oneOf("a", "b", "c")            // 選択
Gen.option(Gen.posNum[Int])         // Option型
Gen.uuid                            // UUID
```

### コレクションジェネレータ

```scala
// リスト
Gen.listOf(Gen.posNum[Int])           // 任意長
Gen.listOfN(5, Gen.posNum[Int])       // 固定長
Gen.nonEmptyListOf(Gen.posNum[Int])   // 非空

// マップ
Gen.mapOf(Gen.alphaStr -> Gen.posNum[Int])

// セット
Gen.containerOf[Set, Int](Gen.posNum[Int])
```

## 4. カスタムジェネレータ

### ドメインモデルのジェネレータ

```scala
// 会員ランク
enum Membership:
  case Bronze, Silver, Gold, Platinum

val membershipGen: Gen[Membership] = Gen.oneOf(
  Membership.Bronze, Membership.Silver, 
  Membership.Gold, Membership.Platinum
)

// 人物データ
case class Person(name: String, age: Int, membership: Membership)

val personGen: Gen[Person] = for
  name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  age <- Gen.chooseNum(0, 150)
  membership <- membershipGen
yield Person(name, age, membership)

// 商品データ
case class Product(productId: String, name: String, price: BigDecimal, quantity: Int)

val productGen: Gen[Product] = for
  id <- Gen.posNum[Int].map(n => f"PROD-${n % 100000}%05d")
  name <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  price <- Gen.posNum[Int].map(n => BigDecimal(1 + n % 10000))
  quantity <- Gen.posNum[Int].map(n => 1 + n % 100)
yield Product(id, name, price, quantity)
```

### ジェネレータの変換

```scala
// map: 値の変換
val upperCaseGen = Gen.alphaStr.map(_.toUpperCase)

// flatMap / for-comprehension: 依存関係のある生成
val subsetGen = for
  list <- Gen.nonEmptyListOf(Gen.posNum[Int])
  size <- Gen.chooseNum(1, list.length)
yield list.take(size)

// suchThat: フィルタリング（注意：効率が悪くなる可能性）
val positiveEvenGen = Gen.posNum[Int].suchThat(_ % 2 == 0)

// filter: suchThatのエイリアス
val nonEmptyStringGen = Gen.alphaStr.filter(_.nonEmpty)
```

## 5. プロパティの定義パターン

### 冪等性（Idempotency）

同じ操作を複数回適用しても結果が変わらない性質。

```scala
test("ソートは冪等: 2回ソートしても結果は同じ") {
  forAll { (nums: List[Int]) =>
    NumberOperations.sortNumbers(NumberOperations.sortNumbers(nums)) shouldBe 
      NumberOperations.sortNumbers(nums)
  }
}

test("大文字変換は冪等") {
  forAll { (s: String) =>
    StringOperations.toUpperCase(StringOperations.toUpperCase(s)) shouldBe 
      StringOperations.toUpperCase(s)
  }
}
```

### 対合性（Involution）

2回適用すると元に戻る性質。

```scala
test("文字列反転は対合: 2回反転すると元に戻る") {
  forAll { (s: String) =>
    StringOperations.reverseString(StringOperations.reverseString(s)) shouldBe s
  }
}

test("リスト反転は対合") {
  forAll { (list: List[Int]) =>
    CollectionOps.reverse(CollectionOps.reverse(list)) shouldBe list
  }
}
```

### 不変量（Invariant）

操作の前後で保存される性質。

```scala
test("ソートは要素を保存する") {
  forAll { (nums: List[Int]) =>
    NumberOperations.sortNumbers(nums).groupBy(identity).view.mapValues(_.size).toMap shouldBe
      nums.groupBy(identity).view.mapValues(_.size).toMap
  }
}

test("ソートは長さを保存する") {
  forAll { (nums: List[Int]) =>
    NumberOperations.sortNumbers(nums).length shouldBe nums.length
  }
}
```

### 境界条件（Boundary Conditions）

```scala
test("割引後の価格は0以上、元の価格以下") {
  forAll(
    Gen.chooseNum(0, 10000).map(BigDecimal(_)),
    Gen.chooseNum(0.0, 1.0)
  ) { (price, rate) =>
    val discounted = PricingLogic.calculateDiscount(price, rate)
    discounted should be >= BigDecimal(0)
    discounted should be <= price
  }
}
```

## 6. ラウンドトリッププロパティ

エンコード/デコードの可逆性は典型的なプロパティです。

```scala
object RunLengthEncoding:
  /** ランレングス符号化 */
  def encode(s: String): List[(Char, Int)] =
    if s.isEmpty then List.empty
    else
      s.foldLeft(List.empty[(Char, Int)]) { (acc, char) =>
        acc match
          case Nil => List((char, 1))
          case (lastChar, count) :: rest if lastChar == char =>
            (lastChar, count + 1) :: rest
          case _ => (char, 1) :: acc
      }.reverse
  
  /** ランレングス復号化 */
  def decode(encoded: List[(Char, Int)]): String =
    encoded.map { case (char, count) => char.toString * count }.mkString

test("ランレングス符号化は可逆") {
  forAll(Gen.alphaStr) { s =>
    RunLengthEncoding.decode(RunLengthEncoding.encode(s)) shouldBe s
  }
}

test("Base64エンコード/デコードは可逆") {
  forAll { (s: String) =>
    Base64Codec.decode(Base64Codec.encode(s)) shouldBe s
  }
}
```

## 7. 代数的性質

### モノイドの法則

```scala
trait Monoid[A]:
  def empty: A
  def combine(x: A, y: A): A

object Monoid:
  given intAddition: Monoid[Int] with
    def empty: Int = 0
    def combine(x: Int, y: Int): Int = x + y
  
  given stringConcat: Monoid[String] with
    def empty: String = ""
    def combine(x: String, y: String): String = x + y
  
  given listConcat[A]: Monoid[List[A]] with
    def empty: List[A] = List.empty
    def combine(x: List[A], y: List[A]): List[A] = x ++ y

// 結合律のテスト
test("Int加算モノイドの結合律") {
  import Monoid.given
  forAll { (a: Int, b: Int, c: Int) =>
    val m = summon[Monoid[Int]]
    m.combine(m.combine(a, b), c) shouldBe m.combine(a, m.combine(b, c))
  }
}

// 単位元のテスト
test("Int加算モノイドの単位元") {
  import Monoid.given
  forAll { (a: Int) =>
    val m = summon[Monoid[Int]]
    m.combine(a, m.empty) shouldBe a
    m.combine(m.empty, a) shouldBe a
  }
}
```

### 算術演算の性質

```scala
test("加算の結合律") {
  forAll(Gen.chooseNum(-1000, 1000), Gen.chooseNum(-1000, 1000), Gen.chooseNum(-1000, 1000)) { 
    (a, b, c) =>
    ((a + b) + c) shouldBe (a + (b + c))
  }
}

test("加算の交換律") {
  forAll { (a: Int, b: Int) =>
    (a + b) shouldBe (b + a)
  }
}

test("加算の単位元") {
  forAll { (a: Int) =>
    (a + 0) shouldBe a
  }
}
```

## 8. オラクルテスト

既知の正しい実装（標準ライブラリなど）と比較します。

```scala
test("sortNumbersは標準ライブラリのsortと同じ結果") {
  forAll { (nums: List[Int]) =>
    NumberOperations.sortNumbers(nums) shouldBe nums.sorted
  }
}

test("distinctは標準ライブラリのdistinctと同じ結果") {
  forAll { (list: List[Int]) =>
    CollectionOps.distinct(list) shouldBe list.distinct
  }
}

test("reverseは標準ライブラリのreverseと同じ結果") {
  forAll { (list: List[Int]) =>
    CollectionOps.reverse(list) shouldBe list.reverse
  }
}
```

## 9. コレクション操作のプロパティ

### filter の性質

```scala
test("filterは長さを減らすか維持する") {
  forAll { (list: List[Int]) =>
    CollectionOps.filter(list)(_ > 0).length should be <= list.length
  }
}

test("filter(常にtrue)は元のリストと同じ") {
  forAll { (list: List[Int]) =>
    CollectionOps.filter(list)(_ => true) shouldBe list
  }
}

test("filter(常にfalse)は空リスト") {
  forAll { (list: List[Int]) =>
    CollectionOps.filter(list)(_ => false) shouldBe List.empty
  }
}
```

### map の性質

```scala
test("mapは長さを保存する") {
  forAll { (list: List[Int]) =>
    CollectionOps.map(list)(_ * 2).length shouldBe list.length
  }
}

test("map(identity)は元のリストと同じ") {
  forAll { (list: List[Int]) =>
    CollectionOps.map(list)(identity) shouldBe list
  }
}

// ファンクター則：map(f).map(g) == map(f andThen g)
test("map(f).map(g) == map(f andThen g)") {
  val f: Int => Int = _ + 1
  val g: Int => Int = _ * 2
  forAll { (list: List[Int]) =>
    CollectionOps.map(CollectionOps.map(list)(f))(g) shouldBe 
      CollectionOps.map(list)(f andThen g)
  }
}
```

### concat の性質

```scala
test("concatの結合律") {
  forAll { (a: List[Int], b: List[Int], c: List[Int]) =>
    CollectionOps.concat(CollectionOps.concat(a, b), c) shouldBe 
      CollectionOps.concat(a, CollectionOps.concat(b, c))
  }
}

test("concatの長さは入力の長さの合計") {
  forAll { (a: List[Int], b: List[Int]) =>
    CollectionOps.concat(a, b).length shouldBe (a.length + b.length)
  }
}
```

## 10. ビジネスロジックのプロパティ

```scala
test("注文合計は常に非負") {
  forAll(orderGen) { order =>
    PricingLogic.calculateOrderTotal(order) should be >= BigDecimal(0)
  }
}

test("最終価格は注文合計以下（会員割引適用後）") {
  forAll(orderGen, membershipGen) { (order, membership) =>
    val total = PricingLogic.calculateOrderTotal(order)
    val finalPrice = PricingLogic.calculateFinalPrice(order, membership)
    finalPrice should be <= total
  }
}

test("Platinumは最大の割引を受ける") {
  forAll(orderGen) { order =>
    val platinumPrice = PricingLogic.calculateFinalPrice(order, Membership.Platinum)
    val bronzePrice = PricingLogic.calculateFinalPrice(order, Membership.Bronze)
    platinumPrice should be <= bronzePrice
  }
}
```

## 11. バリデーションのプロパティ

ジェネレータで有効な入力を生成し、バリデーションを通過することを確認します。

```scala
val validEmailGen: Gen[String] = for
  local <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  domain <- Gen.alphaNumStr.suchThat(_.nonEmpty)
  tld <- Gen.oneOf("com", "org", "net", "io", "jp")
yield s"$local@$domain.$tld"

test("生成された有効なメールアドレスはバリデーションを通過する") {
  forAll(validEmailGen) { email =>
    Validation.isValidEmail(email) shouldBe true
  }
}

val validPhoneGen: Gen[String] = for
  length <- Gen.chooseNum(10, 15)
  digits <- Gen.listOfN(length, Gen.numChar)
yield digits.mkString

test("生成された有効な電話番号はバリデーションを通過する") {
  forAll(validPhoneGen) { phone =>
    Validation.isValidPhoneNumber(phone) shouldBe true
  }
}
```

## Clojure との比較

| 概念 | Clojure (test.check) | Scala (ScalaCheck) |
|------|---------------------|-------------------|
| ジェネレータ | `gen/string`, `gen/int` | `Gen.alphaStr`, `Gen.posNum[Int]` |
| 範囲指定 | `gen/choose` | `Gen.chooseNum` |
| 列挙 | `gen/elements` | `Gen.oneOf` |
| コレクション | `gen/vector`, `gen/list` | `Gen.listOf`, `Gen.containerOf` |
| 変換 | `gen/fmap` | `Gen.map` / for-comprehension |
| 依存関係 | `gen/bind` | `Gen.flatMap` / for-comprehension |
| フィルタ | `gen/such-that` | `Gen.suchThat` |
| プロパティ定義 | `prop/for-all` | `forAll` |
| 実行 | `tc/quick-check` | ScalaTest統合 |

## まとめ

本章では、プロパティベーステストについて学びました：

1. **ジェネレータ**: テストデータの自動生成
2. **プリミティブ**: 整数、文字列、ブール値など
3. **コレクション**: リスト、マップ、セット
4. **変換**: map, flatMap, suchThat による加工
5. **プロパティ**: すべての入力で成り立つべき性質
6. **パターン**: 冪等性、対合性、不変量、ラウンドトリップ
7. **代数的性質**: モノイド則、ファンクター則

プロパティベーステストは、従来のテストを補完し、より堅牢なソフトウェアを実現します。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part2/src/main/scala/PropertyBasedTesting.scala`
- テストコード: `apps/scala/part2/src/test/scala/PropertyBasedTestingSpec.scala`

## 次章予告

次章では、**テスト駆動開発と関数型プログラミング**について学びます。Red-Green-Refactor サイクルを関数型スタイルで実践する方法を探ります。
