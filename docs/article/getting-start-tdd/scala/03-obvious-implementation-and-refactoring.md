---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "この章では、残っている仕様を満たして実装を完成させます。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

この章では、残っている仕様を満たして実装を完成させます。

- Obvious Implementation: 仕様が明確なら、素直で読みやすい実装を選ぶ
- Refactoring: 振る舞いを変えずに構造を整理する

## 3.2 FizzBuzz のテスト

まず 15 の倍数ケースを追加します。

```scala
test("15 の倍数を渡すと FizzBuzz を返す") {
  assert(FizzBuzz.generate(15) === "FizzBuzz")
}
```

ここで重要なのは判定順序です。
`3` と `5` を先に判定すると `15` が `Fizz` または `Buzz` になってしまうため、`15` を最初に判定する必要があります。

## 3.3 明白な実装

仕様が揃った段階で、`match` を使って読みやすく整理します。

```scala
object FizzBuzz:
  def generate(number: Int): String =
    number match
      case n if n % 15 == 0 => "FizzBuzz"
      case n if n % 3 == 0  => "Fizz"
      case n if n % 5 == 0  => "Buzz"
      case n                => n.toString
```

この実装は仕様との対応が 1 対 1 で追いやすく、将来の拡張時にも安全です。

## 3.4 リストの生成

続いて `generateList` を追加します。

```scala
def generateList(count: Int): List[String] =
  (1 to count).map(generate).toList
```

対応するテストを追加します。

```scala
test("1 から 100 までのリストを生成する") {
  val result = FizzBuzz.generateList(100)
  assert(result.length === 100)
  assert(result.head === "1")
  assert(result(2) === "Fizz")
  assert(result(4) === "Buzz")
  assert(result(14) === "FizzBuzz")
}
```

`length` と代表値を確認することで、リスト全体の生成と変換ロジックを同時に検証できます。

## 3.5 リファクタリング

この実装では Scala の特性を活かせます。

- Pattern matching により条件分岐の意図が明確になります。
- `Range` と `map` により副作用のない関数型スタイルで記述できます。

振る舞いを変えない範囲で、命名や重複の整理を行います。

## 3.6 プログラムの実行

手元で確認するために `main` メソッドを追加します。

```scala
@main def runFizzBuzz(): Unit =
  FizzBuzz.generateList(100).foreach(println)
```

実行は `sbt run` です。
`1` から `100` までの結果が順に出力されます。

## 3.7 まとめ

3 章を通じて、FizzBuzz を題材に TDD の基本サイクルを完走しました。

- Red: 仕様ごとに失敗するテストを追加
- Green: 最小実装で段階的に通過
- Refactor: 明白で読みやすい形に整理

TODO リストの最終状態です。

- [x] `FizzBuzz.generate(1)` が `"1"` を返す
- [x] `FizzBuzz.generate(2)` が `"2"` を返す
- [x] `FizzBuzz.generate(3)` が `"Fizz"` を返す
- [x] `FizzBuzz.generate(5)` が `"Buzz"` を返す
- [x] `FizzBuzz.generate(15)` が `"FizzBuzz"` を返す
- [x] `FizzBuzz.generateList(100)` が 100 件の結果を返す
