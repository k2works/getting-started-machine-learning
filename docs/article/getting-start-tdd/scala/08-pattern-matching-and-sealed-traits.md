---
type: Article
title: "第 8 章: パターンマッチとシールドトレイト"
description: "Scala の match は単なる switch の置き換えではなく、型と条件を統合して表現できる強力な構文です。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: パターンマッチとシールドトレイト

## 8.1 はじめに

Scala の `match` は単なる `switch` の置き換えではなく、型と条件を統合して表現できる強力な構文です。`sealed trait` と組み合わせると、代数的データ型（ADT）として安全に分岐を記述できます。

この章では、FizzBuzz のタイプ生成と変換ロジックを題材に、パターンマッチの基本と網羅性チェックを確認します。

## 8.2 パターンマッチの基本

`match/case` の基本構文は次のとおりです。

```scala
def generate(number: Int): String =
  number match
    case n if n % 15 == 0 => "FizzBuzz"
    case n if n % 3 == 0  => "Fizz"
    case n if n % 5 == 0  => "Buzz"
    case n                => n.toString
```

重要な要素は 3 つです。

- `match/case` で分岐を宣言的に記述できる
- `case n if ...` のガード条件で複合条件を自然に表現できる
- `case _` や `case n` でワイルドカード／フォールバックを定義できる

## 8.3 シールドトレイトと網羅性

`sealed trait` は「この抽象型の実装は同一ファイル内に限定する」という制約を与えます。

```scala
sealed trait FizzBuzzType:
  def generate(number: Int): String
```

この制約により、コンパイラは次を支援できます。

- 実装候補を把握し、網羅性を検証できる
- `match` で未処理ケースがあると警告できる
- 将来の型追加時に、分岐漏れを早期に検知できる

`sealed trait` は ADT を安全に扱うための土台です。

## 8.4 ファクトリメソッド

タイプ番号から `FizzBuzzType` を生成する実装です。

```scala
def create(typeNumber: Int): FizzBuzzType =
  typeNumber match
    case 1 => Type01
    case 2 => Type02
    case 3 => Type03
    case _ => throw IllegalArgumentException(s"未定義のタイプ: $typeNumber")
```

生成責務を `FizzBuzzType.create` に集約することで、呼び出し側は型番号の解釈を意識せずに済みます。

## 8.5 Strategy パターンとの対応

OOP の Strategy パターンでは、アルゴリズムをインターフェースで抽象化し、実行時に具象実装を差し替えます。

FizzBuzz では次の対応になります。

- Strategy の抽象: `FizzBuzzType`（`sealed trait`）
- 具体 Strategy: `Type01` / `Type02` / `Type03`
- Strategy 選択: `FizzBuzzType.create(typeNumber)`

この構成により、呼び出し側の条件分岐を排除し、ポリモーフィズムで振る舞いを切り替えられます。

## 8.6 テスト

ファクトリメソッドは `TypeSpec` で次のように検証しています。

```scala
test("create: タイプ 1 を生成できる") {
  assert(FizzBuzzType.create(1) === FizzBuzzType.Type01)
}

test("create: タイプ 2 を生成できる") {
  assert(FizzBuzzType.create(2) === FizzBuzzType.Type02)
}

test("create: タイプ 3 を生成できる") {
  assert(FizzBuzzType.create(3) === FizzBuzzType.Type03)
}

test("create: 未定義のタイプで例外が発生する") {
  assertThrows[IllegalArgumentException] {
    FizzBuzzType.create(4)
  }
}
```

正常系と異常系を両方固定することで、生成ルールの変更に対して安全にリファクタリングできます。

## 8.7 まとめ

この章では、Scala のパターンマッチと `sealed trait` の組み合わせを確認しました。

- `match/case` とガード条件で分岐を明確化
- `sealed trait` で実装範囲を制約し、網羅性チェックを有効化
- `create` ファクトリで Strategy の切り替えを一元化

次章では、ここまで作成したドメインモデルをパッケージ単位で整理し、モジュール設計としてまとめます。
