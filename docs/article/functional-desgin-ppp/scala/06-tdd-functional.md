---
type: Article
title: "第6章: テスト駆動開発と関数型プログラミング"
description: "テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第6章: テスト駆動開発と関数型プログラミング

## はじめに

テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。関数型プログラミングと TDD は相性が良く、純粋関数はテストが容易で、不変データ構造は予測可能な動作を保証します。

本章では、Red-Green-Refactor サイクルを関数型スタイルで実践する方法を学びます。

## 1. TDD の基本サイクル

### Red-Green-Refactor

```plantuml
@startuml
title TDD サイクル

state "Red" as red #pink
state "Green" as green #lightgreen
state "Refactor" as refactor #lightyellow

red --> green : テストを通す
green --> refactor : コードを改善
refactor --> red : 次のテスト

note right of red
  失敗するテストを書く
end note

note right of green
  最小限のコードを実装
end note

note right of refactor
  テストを維持しながら改善
end note
@enduml
```

1. **Red（赤）**: 失敗するテストを書く
2. **Green（緑）**: テストを通す最小限のコードを書く
3. **Refactor（リファクタリング）**: コードを改善する（テストは通ったまま）

## 2. FizzBuzz - TDD の典型例

### Step 1: Red（最初のテスト）

```scala
test("FizzBuzz: 1は\"1\"を返す") {
  FizzBuzz.fizzbuzz(1) shouldBe "1"
}
```

### Step 2: Green（最小限の実装）

```scala
def fizzbuzz(n: Int): String = "1"
```

### Step 3: 次のテストを追加して段階的に実装を発展

```scala
// テストを追加
test("FizzBuzz: 2は\"2\"を返す") {
  FizzBuzz.fizzbuzz(2) shouldBe "2"
}

test("FizzBuzz: 3は\"Fizz\"を返す") {
  FizzBuzz.fizzbuzz(3) shouldBe "Fizz"
}

test("FizzBuzz: 5は\"Buzz\"を返す") {
  FizzBuzz.fizzbuzz(5) shouldBe "Buzz"
}

test("FizzBuzz: 15は\"FizzBuzz\"を返す") {
  FizzBuzz.fizzbuzz(15) shouldBe "FizzBuzz"
}
```

### 最終実装（小さなヘルパー関数に分割）

```scala
object FizzBuzz:
  /** 3で割り切れるかどうか */
  def isFizz(n: Int): Boolean = n % 3 == 0
  
  /** 5で割り切れるかどうか */
  def isBuzz(n: Int): Boolean = n % 5 == 0
  
  /** 15で割り切れるかどうか（FizzBuzz） */
  def isFizzBuzz(n: Int): Boolean = isFizz(n) && isBuzz(n)
  
  /** FizzBuzz変換 */
  def fizzbuzz(n: Int): String =
    if isFizzBuzz(n) then "FizzBuzz"
    else if isFizz(n) then "Fizz"
    else if isBuzz(n) then "Buzz"
    else n.toString
  
  /** 1からnまでのFizzBuzz列を生成 */
  def fizzbuzzSequence(n: Int): List[String] =
    (1 to n).map(fizzbuzz).toList
```

## 3. ローマ数字変換

### テストから始める

```scala
test("ローマ数字: 1はIを返す") {
  RomanNumerals.toRoman(1) shouldBe "I"
}

test("ローマ数字: 4はIVを返す") {
  RomanNumerals.toRoman(4) shouldBe "IV"
}

test("ローマ数字: 1994はMCMXCIVを返す") {
  RomanNumerals.toRoman(1994) shouldBe "MCMXCIV"
}

test("ローマ数字: toRomanとfromRomanは逆関数") {
  for n <- 1 to 3999 do
    RomanNumerals.fromRoman(RomanNumerals.toRoman(n)) shouldBe n
}
```

### データ駆動の実装

```scala
object RomanNumerals:
  /** ローマ数字の対応表（大きい順） */
  private val romanNumerals: List[(Int, String)] = List(
    1000 -> "M",  900 -> "CM", 500 -> "D", 400 -> "CD",
    100 -> "C",   90 -> "XC",  50 -> "L",  40 -> "XL",
    10 -> "X",    9 -> "IX",   5 -> "V",   4 -> "IV",
    1 -> "I"
  )
  
  /** 整数をローマ数字に変換 */
  def toRoman(n: Int): String =
    require(n > 0 && n <= 3999, s"n must be between 1 and 3999")
    
    @annotation.tailrec
    def loop(remaining: Int, result: StringBuilder): String =
      if remaining == 0 then result.toString
      else
        val (value, numeral) = romanNumerals.find(_._1 <= remaining).get
        loop(remaining - value, result.append(numeral))
    
    loop(n, new StringBuilder)
```

## 4. ボウリングスコア計算

### 複雑なビジネスロジックの TDD

```scala
test("ボウリング: ガタースコアは0") {
  Bowling.score(List.fill(20)(0)) shouldBe 0
}

test("ボウリング: すべて1ピンは20点") {
  Bowling.score(List.fill(20)(1)) shouldBe 20
}

test("ボウリング: スペアの後の投球はボーナス") {
  Bowling.score(List(5, 5, 3, 0) ++ List.fill(16)(0)) shouldBe 16
}

test("ボウリング: ストライクの後の2投はボーナス") {
  Bowling.score(List(10, 3, 4) ++ List.fill(16)(0)) shouldBe 24
}

test("ボウリング: パーフェクトゲームは300点") {
  Bowling.score(List.fill(12)(10)) shouldBe 300
}
```

### 小さな関数に分割

```scala
object Bowling:
  def isStrike(rolls: List[Int]): Boolean = 
    rolls.headOption.contains(10)
  
  def isSpare(rolls: List[Int]): Boolean = 
    rolls.length >= 2 && rolls.take(2).sum == 10 && !isStrike(rolls)
  
  def strikeBonus(remaining: List[Int]): Int = 
    remaining.take(2).sum
  
  def spareBonus(remaining: List[Int]): Int = 
    remaining.headOption.getOrElse(0)
  
  def score(rolls: List[Int]): Int =
    @annotation.tailrec
    def loop(remainingRolls: List[Int], frame: Int, total: Int): Int =
      if frame > 10 || remainingRolls.isEmpty then total
      else if isStrike(remainingRolls) then
        loop(remainingRolls.tail, frame + 1, total + 10 + strikeBonus(remainingRolls.tail))
      else if isSpare(remainingRolls) then
        loop(remainingRolls.drop(2), frame + 1, total + 10 + spareBonus(remainingRolls.drop(2)))
      else
        loop(remainingRolls.drop(2), frame + 1, total + remainingRolls.take(2).sum)
    
    loop(rolls, 1, 0)
```

## 5. 素数 - シンプルな関数の TDD

### テストから設計を導く

```scala
test("素数: isPrimeは素数を正しく判定する") {
  Primes.isPrime(0) shouldBe false
  Primes.isPrime(1) shouldBe false
  Primes.isPrime(2) shouldBe true
  Primes.isPrime(3) shouldBe true
  Primes.isPrime(4) shouldBe false
  Primes.isPrime(97) shouldBe true
}

test("素数: primesUpToは正しい素数リストを返す") {
  Primes.primesUpTo(20) shouldBe List(2, 3, 5, 7, 11, 13, 17, 19)
}

test("素数: primeFactorsは素因数分解を返す") {
  Primes.primeFactors(24) shouldBe List(2, 2, 2, 3)
}

test("素数: primeFactorsの積は元の数に等しい") {
  for n <- 2 to 100 do
    Primes.primeFactors(n).product shouldBe n
}
```

### 実装

```scala
object Primes:
  def isPrime(n: Int): Boolean =
    if n < 2 then false
    else if n == 2 then true
    else if n % 2 == 0 then false
    else
      val sqrtN = math.sqrt(n.toDouble).toInt
      !(3 to sqrtN by 2).exists(n % _ == 0)
  
  def primesUpTo(n: Int): List[Int] =
    (2 to n).filter(isPrime).toList
  
  def primeFactors(n: Int): List[Int] =
    @annotation.tailrec
    def loop(remaining: Int, factor: Int, factors: List[Int]): List[Int] =
      if remaining == 1 then factors.reverse
      else if remaining % factor == 0 then loop(remaining / factor, factor, factor :: factors)
      else loop(remaining, factor + 1, factors)
    
    loop(n, 2, Nil)
```

## 6. 不変データ構造 - スタックとキュー

### 不変スタック

```scala
case class Stack[A] private (items: List[A]):
  def isEmpty: Boolean = items.isEmpty
  def size: Int = items.length
  def push(item: A): Stack[A] = Stack(item :: items)
  
  def pop: Option[(A, Stack[A])] =
    items match
      case head :: tail => Some((head, Stack(tail)))
      case Nil => None
  
  def peek: Option[A] = items.headOption

object Stack:
  def empty[A]: Stack[A] = Stack(Nil)
```

### テスト

```scala
test("スタック: LIFO順序で動作する") {
  val stack = Stack.empty[String]
    .push("a")
    .push("b")
    .push("c")
  
  val Some((v1, s1)) = stack.pop: @unchecked
  val Some((v2, s2)) = s1.pop: @unchecked
  val Some((v3, s3)) = s2.pop: @unchecked
  
  v1 shouldBe "c"
  v2 shouldBe "b"
  v3 shouldBe "a"
  s3.isEmpty shouldBe true
}
```

### 不変キュー（2つのリストで実装）

```scala
case class Queue[A] private (front: List[A], back: List[A]):
  def isEmpty: Boolean = front.isEmpty && back.isEmpty
  def enqueue(item: A): Queue[A] = Queue(front, item :: back)
  
  def dequeue: Option[(A, Queue[A])] =
    front match
      case head :: tail => Some((head, Queue(tail, back)))
      case Nil =>
        back.reverse match
          case head :: tail => Some((head, Queue(tail, Nil)))
          case Nil => None

object Queue:
  def empty[A]: Queue[A] = Queue(Nil, Nil)
```

## 7. 文字列電卓 - 段階的な要件追加

### テスト

```scala
test("文字列電卓: 空文字列は0を返す") {
  StringCalculator.add("") shouldBe 0
}

test("文字列電卓: 単一の数値はその値を返す") {
  StringCalculator.add("5") shouldBe 5
}

test("文字列電卓: カンマ区切りの数値を合計する") {
  StringCalculator.add("1,2,3") shouldBe 6
}

test("文字列電卓: 改行区切りも処理する") {
  StringCalculator.add("1\n2,3") shouldBe 6
}

test("文字列電卓: カスタム区切り文字を使用できる") {
  StringCalculator.add("//;\n1;2") shouldBe 3
}

test("文字列電卓: 負の数は例外をスローする") {
  val exception = intercept[IllegalArgumentException] {
    StringCalculator.add("1,-2,3")
  }
  exception.getMessage should include("-2")
}

test("文字列電卓: 1000より大きい数は無視する") {
  StringCalculator.add("2,1001") shouldBe 2
}
```

### 実装

```scala
object StringCalculator:
  def add(input: String): Int =
    if input.isEmpty then 0
    else
      val (delimiter, numbers) = parseInput(input)
      val nums = parseNumbers(numbers, delimiter)
      validateNumbers(nums)
      nums.filter(_ <= 1000).sum
  
  private def parseInput(input: String): (String, String) =
    if input.startsWith("//") then
      val delimiterEnd = input.indexOf("\n")
      (input.substring(2, delimiterEnd), input.substring(delimiterEnd + 1))
    else
      (",|\n", input)
  
  private def parseNumbers(numbers: String, delimiter: String): List[Int] =
    numbers.split(delimiter).filter(_.nonEmpty).map(_.toInt).toList
  
  private def validateNumbers(nums: List[Int]): Unit =
    val negatives = nums.filter(_ < 0)
    if negatives.nonEmpty then
      throw new IllegalArgumentException(s"negatives not allowed: ${negatives.mkString(", ")}")
```

## 8. 純粋関数とテスト容易性

### 純粋関数の利点

```scala
case class Item(name: String, price: BigDecimal)
case class TaxCalculation(subtotal: BigDecimal, tax: BigDecimal, total: BigDecimal)

object TaxCalculator:
  /** 純粋関数：テストが容易 */
  def calculateTax(amount: BigDecimal, rate: BigDecimal): BigDecimal =
    amount * rate
  
  def calculateTotalWithTax(items: List[Item], taxRate: BigDecimal): TaxCalculation =
    val subtotal = items.map(_.price).sum
    val tax = calculateTax(subtotal, taxRate)
    TaxCalculation(subtotal, tax, subtotal + tax)
```

### テスト

```scala
test("税計算: calculateTotalWithTaxは税込み総額を計算する") {
  val items = List(
    Item("商品A", BigDecimal(1000)),
    Item("商品B", BigDecimal(2000))
  )
  val result = TaxCalculator.calculateTotalWithTax(items, BigDecimal(0.1))
  
  result.subtotal shouldBe BigDecimal(3000)
  result.tax shouldBe BigDecimal(300)
  result.total shouldBe BigDecimal(3300)
}
```

## 9. リファクタリングパターン - データ駆動の実装

### Before: 複雑な条件分岐

```scala
def calculateShippingBefore(total: BigDecimal, weight: Double, region: Region): Int =
  if total >= 10000 then 0
  else region match
    case Region.Local if weight < 5 => 300
    case Region.Local => 500
    case Region.Domestic if weight < 5 => 500
    case Region.Domestic => 800
    case Region.International if weight < 5 => 2000
    case Region.International => 3000
```

### After: データ駆動の実装

```scala
object ShippingCalculator:
  def isFreeShipping(total: BigDecimal): Boolean = total >= 10000
  
  private val shippingRates: Map[Region, Map[Boolean, Int]] = Map(
    Region.Local -> Map(true -> 300, false -> 500),
    Region.Domestic -> Map(true -> 500, false -> 800),
    Region.International -> Map(true -> 2000, false -> 3000)
  )
  
  def calculateShipping(order: ShippingOrder): Int =
    if isFreeShipping(order.total) then 0
    else
      val isLight = order.weight < 5.0
      shippingRates.get(order.region)
        .flatMap(_.get(isLight))
        .getOrElse(500)
```

## 10. パスワードバリデーター - ルールの合成

```scala
object PasswordValidator:
  type Rule = String => Option[String]
  
  def minLength(min: Int): Rule = password =>
    if password.length >= min then None
    else Some(s"Password must be at least $min characters")
  
  val hasUppercase: Rule = password =>
    if password.exists(_.isUpper) then None
    else Some("Password must contain at least one uppercase letter")
  
  val hasLowercase: Rule = password =>
    if password.exists(_.isLower) then None
    else Some("Password must contain at least one lowercase letter")
  
  val hasDigit: Rule = password =>
    if password.exists(_.isDigit) then None
    else Some("Password must contain at least one digit")
  
  val defaultRules: List[Rule] = List(
    minLength(8), hasUppercase, hasLowercase, hasDigit
  )
  
  def validate(password: String, rules: List[Rule] = defaultRules): Either[List[String], String] =
    val errors = rules.flatMap(_(password))
    if errors.isEmpty then Right(password)
    else Left(errors)
```

## TDD のベストプラクティス

### 1. 小さなステップで進む

- 一度に1つのテストだけを追加
- テストが通ったら次のテストへ

### 2. テスト名は仕様として読める

```scala
test("10000円以上は送料無料")
test("負の数は例外をスローする")
test("パーフェクトゲームは300点")
```

### 3. 純粋関数を優先

- 副作用を持つ関数は最小限に
- 副作用は境界に追い出す

### 4. エッジケースをテスト

```scala
test("空文字列は0を返す")
test("空のリストは空のリストを返す")
test("範囲外の値は例外をスローする")
```

## Clojure との比較

| 概念 | Clojure | Scala |
|------|---------|-------|
| テストフレームワーク | speclj, clojure.test | ScalaTest |
| テスト構文 | `(it "..." (should= ...))` | `test("...") { ... shouldBe ... }` |
| 例外テスト | `(should-throw ...)` | `intercept[...] { ... }` |
| データ構造 | 永続化データ構造（デフォルト） | `case class` + `copy` |
| ループ | `loop/recur` | `@annotation.tailrec` |
| パターンマッチ | `cond`, `case` | `match` |

## まとめ

本章では、TDD と関数型プログラミングについて学びました：

1. **Red-Green-Refactor**: 基本サイクル
2. **FizzBuzz**: 典型的な TDD 例
3. **ローマ数字**: データ駆動の実装
4. **ボウリング**: 複雑なビジネスロジック
5. **素数**: シンプルな関数設計
6. **スタック/キュー**: 不変データ構造
7. **文字列電卓**: 段階的な要件追加
8. **純粋関数**: テスト容易性
9. **リファクタリング**: 条件分岐の整理
10. **パスワードバリデーター**: ルールの合成

関数型プログラミングと TDD の組み合わせにより、信頼性の高いコードを効率的に開発できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part2/src/main/scala/TddFunctional.scala`
- テストコード: `apps/scala/part2/src/test/scala/TddFunctionalSpec.scala`

## 次章予告

次章から第3部「デザインパターン - 構造パターン」に入ります。Composite パターンを関数型スタイルで実装する方法を学びます。
