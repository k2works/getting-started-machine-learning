---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "第 1 章では、最小の 1 ケースを Green にしました。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

第 1 章では、最小の 1 ケースを Green にしました。
この章では、次の 2 つのテクニックで実装を育てます。

- Fake It: まず動く最小のコードを書く
- Triangulation: 複数ケースから一般ルールを導く

## 2.2 仮実装（Fake It）

まず `2` のケースを追加して、仮実装の限界を明確にします。

```scala
test("2 を渡すと文字列 2 を返す") {
  assert(FizzBuzz.generate(2) === "2")
}
```

このテストは失敗します。
現在の実装は常に `"1"` を返すためです。

ここで実装を一段だけ一般化します。

```scala
object FizzBuzz:
  def generate(number: Int): String = number.toString
```

`1` と `2` のテストがともに通れば Green です。

## 2.3 三角測量（Triangulation）

Triangulation は、1 つの具体例だけで一般化せず、2 つ以上の点で法則を確定する手法です。

実践手順は次の通りです。

1. 具体的な新テストを 1 つ追加して失敗を作る
2. 最小変更でテストを通す
3. 既存テストも含めて一貫するルールか確認する

この繰り返しにより、偶然通る実装ではなく、仕様に沿う実装へ近づけます。

## 2.4 Fizz のテスト

次に 3 の倍数ケースを追加します。

```scala
test("3 の倍数を渡すと Fizz を返す") {
  assert(FizzBuzz.generate(3) === "Fizz")
}
```

テストを通す最小実装の例です。

```scala
object FizzBuzz:
  def generate(number: Int): String =
    if number % 3 == 0 then "Fizz"
    else number.toString
```

`if` でも `match` でも構いません。
この時点では、可読性よりも小さなステップを優先します。

## 2.5 Buzz のテスト

続いて 5 の倍数ケースを追加します。

```scala
test("5 の倍数を渡すと Buzz を返す") {
  assert(FizzBuzz.generate(5) === "Buzz")
}
```

最小実装の例です。

```scala
object FizzBuzz:
  def generate(number: Int): String =
    if number % 3 == 0 then "Fizz"
    else if number % 5 == 0 then "Buzz"
    else number.toString
```

まだ 15 のケースは未対応なので、次章で仕上げます。

## 2.6 TODO リストの更新

ここまでの完了状態を反映します。

- [x] `FizzBuzz.generate(1)` が `"1"` を返す
- [x] `FizzBuzz.generate(2)` が `"2"` を返す
- [x] `FizzBuzz.generate(3)` が `"Fizz"` を返す
- [x] `FizzBuzz.generate(5)` が `"Buzz"` を返す
- [ ] `FizzBuzz.generate(15)` が `"FizzBuzz"` を返す
- [ ] `FizzBuzz.generateList(100)` が 100 件の結果を返す

## 2.7 まとめ

この章のポイントは次の 2 点です。

- Fake It で、まず動く最小実装を作る
- Triangulation で、複数のテストケースから実装を一般化する

次章では、`15` と `generateList` を追加して実装を完成させます。
