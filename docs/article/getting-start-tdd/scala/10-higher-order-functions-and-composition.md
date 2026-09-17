---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "第 3 部ではケースクラスやトレイトを使って OOP の基礎を整理しました。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

第 3 部ではケースクラスやトレイトを使って OOP の基礎を整理しました。第 4 部では Scala の関数型プログラミング（FP）に焦点を当てます。

Scala では関数は第一級オブジェクトです。関数を変数に代入でき、引数として渡せて、戻り値として返せます。この性質により、柔軟で再利用しやすい実装が可能になります。

## 10.2 高階関数

高階関数は「関数を引数に取る、または関数を返す関数」です。`generateWith` は最小構成の例です。

```scala
def generateWith(rule: Int => String)(number: Int): String =
  rule(number)
```

このメソッドは 2 つのパラメータリストを持つカリー化された形です。

- 第 1 パラメータリスト: `rule`（`Int => String`）
- 第 2 パラメータリスト: `number`（`Int`）

使い方の例です。

```scala
val customRule: Int => String = n => if n % 2 == 0 then "Even" else "Odd"
val generator = FizzBuzz.generateWith(customRule)

assert(generator(2) == "Even")
assert(generator(3) == "Odd")
```

`val generator = FizzBuzz.generateWith(customRule)` は部分適用です。ルールだけ先に固定し、後から数値だけ渡せるようになります。

## 10.3 コレクション操作

Scala のコレクションは `map`、`filter`、`reduce` のような高階関数を標準で備えています。

- `map`: 各要素を変換する
- `filter`: 条件に一致する要素だけ残す
- `reduce`: 要素を 1 つの値に畳み込む

FizzBuzz の実装では、`transform` と `filter` でこの考え方をそのまま表現しています。

```scala
def transform(values: List[String], f: String => String): List[String] =
  values.map(f)

def filter(values: List[String], predicate: String => Boolean): List[String] =
  values.filter(predicate)
```

`transform` は変換ロジックを引数で受け取るため、`toUpperCase` 以外にも自由に差し替えできます。

## 10.4 関数合成

複数の小さな関数を組み合わせて新しい関数を作るのが関数合成です。実装は次のとおりです。

```scala
def compose(f: String => String, g: String => String): String => String =
  f.compose(g)
```

`f.compose(g)` は `f(g(x))` を意味します。つまり右から左へ適用されます。対して `andThen` は `g(f(x))` で、左から右へ適用されます。

```scala
val addBrackets: String => String = s => s"[$s]"
val toUpper: String => String = _.toUpperCase

val composed = addBrackets.compose(toUpper) // addBrackets(toUpper(x))
val piped = addBrackets.andThen(toUpper)    // toUpper(addBrackets(x))
```

この違いを理解すると、パイプライン処理を意図通りに組み立てられます。

## 10.5 テスト

実装した関数型メソッドは、次のようにテストできます。

```scala
test("generateWith: カスタムルールで生成する") {
  val customRule: Int => String = n => if n % 2 == 0 then "Even" else "Odd"
  assert(FizzBuzz.generateWith(customRule)(2) === "Even")
}

test("compose: 2 つの関数を合成する") {
  val addBrackets: String => String = s => s"[$s]"
  val toUpper: String => String = _.toUpperCase
  val combined = FizzBuzz.compose(addBrackets, toUpper)
  assert(combined("Fizz") === "[FIZZ]")
}
```

高階関数や関数合成は抽象度が上がるため、入力と出力を明示したテストが特に重要です。

## 10.6 まとめ

この章では、Scala の関数型プログラミングの入口として次を確認しました。

- 高階関数 `generateWith` とカリー化
- `map`、`filter` を使うコレクション操作
- `compose` と `andThen` の違い

次章では、コレクション処理をさらに発展させ、`LazyList` による遅延評価を扱います。
