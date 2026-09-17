---
type: Article
title: "第 11 章: コレクション処理と遅延評価"
description: "Scala の強みの 1 つは、表現力の高いコレクションライブラリです。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: コレクション処理と遅延評価

## 11.1 はじめに

Scala の強みの 1 つは、表現力の高いコレクションライブラリです。`List` や `Vector` を使った宣言的な処理により、ループ中心の実装より意図を明確に書けます。

ここで重要なのが評価戦略です。

- 正格評価（eager evaluation）: すぐに計算する
- 遅延評価（lazy evaluation）: 必要になるまで計算しない

## 11.2 イミュータブルコレクション

Scala の標準コレクションは、デフォルトでイミュータブルを前提に設計されています。

- `List`: 連結リスト。先頭追加や再帰的処理と相性がよい
- `Vector`: ランダムアクセスに強い
- `Map`: キーと値の対応
- `Set`: 重複しない集合

イミュータブルな設計により、副作用を抑えやすくなり、テストしやすいコードになります。

## 11.3 コレクション操作の連鎖

`map`、`flatMap`、`filter`、`foldLeft` を連鎖させると、データ変換の流れを自然に表現できます。

```scala
val numbers = (1 to 20).toList
val result = numbers
  .map(FizzBuzz.generate)
  .filter(_ != "FizzBuzz")
  .foldLeft(List.empty[String])((acc, v) => acc :+ v)
```

for 内包表記も同じ考え方で使えます。

```scala
val result = for
  n <- 1 to 100
  value = FizzBuzz.generate(n)
  if value != n.toString
yield value
```

for 内包表記は、`map`、`flatMap`、`filter` の組み合わせを読みやすく書くための構文糖衣です。

## 11.4 LazyList による遅延評価

FizzBuzz では無限シーケンスを `LazyList` で表現できます。

```scala
def lazyList: LazyList[String] =
  LazyList.from(1).map(generate)
```

`LazyList.from(1)` は 1 から始まる無限列です。`map(generate)` を付けても、実際の計算は要素を取り出すまで実行されません。

```scala
val firstFive = FizzBuzz.lazyList.take(5).toList
// List("1", "2", "Fizz", "4", "Buzz")
```

遅延評価の利点は次のとおりです。

- 必要な分だけ計算するため無駄が少ない
- 無限リストを安全に表現できる
- `take`、`drop`、`filter` を組み合わせた逐次処理を書きやすい

## 11.5 テスト

`LazyList` は「必要なときだけ評価される」ことを確認するテストが重要です。

```scala
test("lazyList: 遅延リストから最初の 5 要素を取得する") {
  val result = FizzBuzz.lazyList.take(5).toList
  assert(result === List("1", "2", "Fizz", "4", "Buzz"))
}

test("lazyList: 遅延リストから 15 番目の要素を取得する") {
  assert(FizzBuzz.lazyList(14) === "FizzBuzz")
}
```

先頭数件だけを取り出すテストと、任意の位置の要素を直接参照するテストを組み合わせると、遅延シーケンスの振る舞いを網羅できます。

## 11.6 まとめ

この章では、Scala のコレクション処理と遅延評価を確認しました。

- イミュータブルコレクションを前提に実装する
- `map`、`flatMap`、`filter`、`foldLeft` を連鎖して処理を記述する
- `LazyList` で無限シーケンスと必要時評価を実現する

次章では、`Option` や `Either` を使った型安全なエラーハンドリングを扱います。
