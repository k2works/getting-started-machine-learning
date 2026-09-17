---
type: Article
title: "第 7 章: ケースクラスとトレイトによるポリモーフィズム"
description: "第 1 部〜第 2 部で TDD の基本サイクルと開発環境を整えました。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: ケースクラスとトレイトによるポリモーフィズム

## 7.1 はじめに

第 1 部〜第 2 部で TDD の基本サイクルと開発環境を整えました。ここからは FizzBuzz に追加仕様を導入し、オブジェクト指向設計を進めます。

今回の追加仕様は、FizzBuzz のタイプバリエーションです。

- Type01: 通常の FizzBuzz（3 の倍数で Fizz、5 の倍数で Buzz、15 の倍数で FizzBuzz）
- Type02: 数値のみ（常に数値文字列を返す）
- Type03: FizzBuzz のみ（15 の倍数のみ FizzBuzz、それ以外は数値文字列）

Scala では `case class` と `trait` を組み合わせることで、型安全なポリモーフィズムをシンプルに表現できます。

## 7.2 TODO リスト

既存 TODO に加えて、タイプバリエーションに関する TODO を整理します。

- [x] ~~1 を渡したら "1" を返す~~
- [x] ~~3 の倍数のときは "Fizz" を返す~~
- [x] ~~5 の倍数のときは "Buzz" を返す~~
- [x] ~~15 の倍数のときは "FizzBuzz" を返す~~
- [x] ~~1 から 100 までのリストを返す~~
- [ ] Type01 のルールを実装する
- [ ] Type02 のルールを実装する
- [ ] Type03 のルールを実装する
- [ ] 未定義タイプで例外を送出する

## 7.3 値オブジェクト（case class）

単一値の表現には `case class FizzBuzzValue` を使います。

```scala
case class FizzBuzzValue(number: Int, value: String):
  require(number > 0, s"数値は正の整数でなければなりません: $number")
  override def toString: String = value
```

`case class` の主な特徴は次のとおりです。

- 不変データを簡潔に定義できる
- `equals` / `hashCode` / `toString` が自動生成される
- パターンマッチに自然に対応できる

また、`require` により「正の整数であること」という事前条件をコンストラクタ境界で保証しています。

## 7.4 ファーストクラスコレクション

複数値の表現には `FizzBuzzList` を使います。

```scala
case class FizzBuzzList(values: List[FizzBuzzValue]):
  require(values.nonEmpty, "リストは空であってはなりません")
  def toStringList: List[String] = values.map(_.value)
  def count: Int = values.length
```

`List[FizzBuzzValue]` を直接扱わず、`FizzBuzzList` にラップすることで、コレクション操作をドメインの語彙で表現できます。

実装ではコンパニオンオブジェクトに `create` を持たせ、生成ロジックを集約しています。

```scala
object FizzBuzzList:
  def create(count: Int, fizzBuzzType: FizzBuzzType): FizzBuzzList =
    val values = (1 to count).map { n =>
      FizzBuzzValue(n, fizzBuzzType.generate(n))
    }.toList
    FizzBuzzList(values)
```

## 7.5 トレイトによるポリモーフィズム

FizzBuzz のタイプごとの差し替えポイントは `FizzBuzzType` で抽象化します。

```scala
sealed trait FizzBuzzType:
  def generate(number: Int): String
```

実装は 3 つの `case object` です。

```scala
object FizzBuzzType:
  case object Type01 extends FizzBuzzType:
    def generate(number: Int): String =
      number match
        case n if n % 15 == 0 => "FizzBuzz"
        case n if n % 3 == 0  => "Fizz"
        case n if n % 5 == 0  => "Buzz"
        case n                => n.toString

  case object Type02 extends FizzBuzzType:
    def generate(number: Int): String = number.toString

  case object Type03 extends FizzBuzzType:
    def generate(number: Int): String =
      number match
        case n if n % 15 == 0 => "FizzBuzz"
        case n                => n.toString
```

`sealed` を付けることで実装候補を同一ファイルに閉じ込められ、コンパイラの網羅性チェックが効くようになります。

## 7.6 テスト

`TypeSpec` では各タイプとファクトリメソッドを検証しています。

```scala
class TypeSpec extends AnyFunSuite:
  test("Type01: 3 の倍数で Fizz を返す") {
    assert(FizzBuzzType.Type01.generate(3) === "Fizz")
  }

  test("Type02: 15 を渡すと 15 を返す") {
    assert(FizzBuzzType.Type02.generate(15) === "15")
  }

  test("Type03: 15 の倍数で FizzBuzz を返す") {
    assert(FizzBuzzType.Type03.generate(15) === "FizzBuzz")
  }

  test("create: 未定義のタイプで例外が発生する") {
    assertThrows[IllegalArgumentException] {
      FizzBuzzType.create(4)
    }
  }
```

タイプごとの振る舞いを個別に固定することで、追加仕様時の回帰を防げます。

## 7.7 まとめ

この章では、`case class` と `trait` を使って FizzBuzz の型安全な設計を行いました。

- `FizzBuzzValue` で値を明示的に表現
- `FizzBuzzList` でコレクション操作をカプセル化
- `FizzBuzzType` のポリモーフィズムでタイプ差分を分離

次章では、`match` と `sealed trait` をさらに掘り下げ、Scala らしい分岐設計を整理します。
