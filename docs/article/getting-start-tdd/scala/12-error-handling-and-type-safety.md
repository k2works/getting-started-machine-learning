---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "Scala では、例外を多用するよりも型でエラー可能性を表現する設計が推奨されます。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

Scala では、例外を多用するよりも型でエラー可能性を表現する設計が推奨されます。コンパイル時に考慮漏れを減らせるためです。

この章では `Option`、`Either`、`Try`、`require` を使い、型安全なエラーハンドリングを整理します。

## 12.2 Option 型

`Option` は「値があるかもしれないし、ないかもしれない」を表現します。

```scala
def safeGenerate(number: Int): Option[String] =
  if number > 0 then Some(generate(number))
  else None
```

- `Some(value)`: 値あり
- `None`: 値なし

`Option` は `map`、`flatMap`、`getOrElse` と相性がよく、null チェックを置き換えられます。

```scala
val message = FizzBuzz.safeGenerate(3)
  .map(v => s"結果: $v")
  .getOrElse("入力が不正です")
```

## 12.3 Either 型

`Either` は成功と失敗のどちらかを保持します。

```scala
def generateEither(number: Int): Either[String, String] =
  if number > 0 then Right(generate(number))
  else Left(s"正の整数が必要です: $number")
```

- `Right`: 成功値
- `Left`: エラー情報

`Either` を使うと、エラーを文字列やドメイン型で返せるため、例外より制御しやすくなります。複数処理の連鎖では for 内包表記も利用できます。

```scala
val result = for
  a <- FizzBuzz.generateEither(3)
  b <- FizzBuzz.generateEither(5)
yield s"$a, $b"
```

## 12.4 Try 型

`Try` は例外を値として扱う型です。例外が起きうる処理を安全に包めます。

```scala
import scala.util.Try

val parsed: Try[Int] = Try("42".toInt)
```

`Try` は `Success` / `Failure` の 2 形です。`map` や `recover` と組み合わせると、例外処理を宣言的に記述できます。

## 12.5 require による事前条件

ドメインモデルの入力検証には `require` が使えます。

```scala
case class FizzBuzzValue(number: Int, value: String):
  require(number > 0, s"数値は正の整数でなければなりません: $number")
```

不正な値でオブジェクトが作られることを防ぎ、ドメイン不変条件をコンストラクタ境界で守れます。

## 12.6 型安全な設計のまとめ

- `Option`: 値の有無を型で表現する
- `Either`: 成功 / 失敗の分岐を型で表現する
- `Try`: 例外を値として安全に扱う
- `require`: 事前条件を明示し、不正状態を早期に排除する

## 12.7 シリーズのまとめ

全 12 章を通して、FizzBuzz を題材に TDD と Scala の設計原則を段階的に学びました。

- TDD の基本サイクル（Red-Green-Refactor）
- 開発環境の自動化
- オブジェクト指向設計（ケースクラス、トレイト、パターンマッチ）
- 関数型プログラミング（高階関数、コレクション、型安全なエラーハンドリング）
- Scala の OOP と FP の統合

小さな題材でも、設計・テスト・リファクタリングを繰り返すことで、実務で使える設計力を着実に伸ばせます。
