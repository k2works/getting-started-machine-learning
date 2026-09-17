---
type: Article
title: "第 9 章: パッケージとモジュール設計"
description: "クラスが増えると、実装の正しさだけでなく「どこに何を書くか」が品質に直結します。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: パッケージとモジュール設計

## 9.1 はじめに

クラスが増えると、実装の正しさだけでなく「どこに何を書くか」が品質に直結します。この章では、FizzBuzz のコードを題材に、責務ごとに整理するモジュール設計を確認します。

## 9.2 パッケージ構成

現在の Scala 実装は次の構成です。

```text
fizzbuzz/
├── FizzBuzz.scala          (公開 API)
├── domain/
│   ├── Type.scala          (ドメインタイプ)
│   └── Model.scala         (値オブジェクト)
└── application/
    └── Command.scala       (コマンドパターン)
```

`domain` と `application` を分離することで、ビジネスルールとユースケース実行を明確に切り分けています。

## 9.3 ドメイン層

ドメイン層は「FizzBuzz として何が正しいか」を表現する層です。

- `domain.Type`: 変換ルール（Type01/02/03）とファクトリメソッド
- `domain.Model`: 値オブジェクト `FizzBuzzValue` とファーストクラスコレクション `FizzBuzzList`

`require` による事前条件や `sealed trait` による型制約をドメイン側に置くことで、不正な状態の侵入を早期に防げます。

## 9.4 アプリケーション層

アプリケーション層はユースケースを実行する層です。

- `application.Command`: `FizzBuzzCommand` と具象コマンド
- `FizzBuzzValueCommand`: 単一値を返すユースケース
- `FizzBuzzListCommand`: 複数値を改行区切り文字列として返すユースケース

アプリケーション層は `domain` を利用しますが、`domain` は `application` に依存しません。この依存方向により、ビジネスルールを独立して保守できます。

## 9.5 Command パターン

`Command.scala` の中心となる実装です。

```scala
trait FizzBuzzCommand:
  def execute(): String

class FizzBuzzValueCommand(number: Int, typeNumber: Int) extends FizzBuzzCommand:
  def execute(): String =
    val fizzBuzzType = FizzBuzzType.create(typeNumber)
    val value = FizzBuzzValue(number, fizzBuzzType.generate(number))
    value.toString
```

同じインターフェース（`execute`）で操作を抽象化し、呼び出し側はコマンドの差し替えだけで振る舞いを変更できます。

## 9.6 SOLID 原則との対応

この構成は SOLID 原則に次のように対応します。

- 単一責任: `Type.scala` は変換ルール、`Model.scala` は値表現、`Command.scala` は実行手順に責務分離
- 開放閉鎖: 新しいタイプは `FizzBuzzType` の実装追加で拡張しやすい
- 依存性逆転: `trait FizzBuzzCommand` と `trait FizzBuzzType` による抽象化で依存を安定化

## 9.7 まとめ

この章では、FizzBuzz 実装をパッケージ単位で整理し、モジュール設計の基本を確認しました。

- `domain` にビジネスルールを集約
- `application` にユースケース実行を集約
- `trait` を軸に依存関係をシンプルに保つ

第 3 部でオブジェクト指向設計の基盤が整ったので、次の第 4 部では関数型プログラミングの要素を取り入れて設計をさらに発展させます。
