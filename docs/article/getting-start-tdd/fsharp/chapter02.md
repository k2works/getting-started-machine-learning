---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。この章では、**三角測量** によってプログラムを一般化し、さらに FizzBuzz のコアロジックを実装していきます。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [ ] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.2 三角測量

1 を渡したら文字列 "1" を返すようにできました。では、2 を渡したらどうなるでしょうか？

### Red: 2 つ目のテストを書く

```fsharp
[<Fact>]
let ``数を文字列にして返す_2を渡したら文字列2を返す`` () =
    Assert.Equal("2", generate 2)
```

テストを実行します。

```bash
$ dotnet test
  Assert.Equal() Failure
  Expected: "2"
  Actual:   "1"
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。F# では `string` 関数で整数を文字列に変換できます。

```fsharp
let generate (number: int) : string =
    string number
```

テストを実行します。

```bash
$ dotnet test
  合計: 2、成功: 2、失敗: 0、スキップ: 0
```

テストが通りました。2 つ目のテストによって `generate` 関数の一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

Rust では `number.to_string()` と書くところを、F# では `string number` という組み込み関数を使います。F# の `string` 関数は Rust の `Display` トレイトに相当する機能で、多くの型を文字列に変換できます。

**TODO リスト**:

- [x] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [x] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.3 3 の倍数 — Fizz

次は「3 の倍数のときは数の代わりに Fizz と返す」に取り掛かります。

### Red: 3 の倍数のテスト

```fsharp
[<Fact>]
let ``三の倍数のときはFizzを返す`` () =
    Assert.Equal("Fizz", generate 3)
```

```bash
$ dotnet test
  Assert.Equal() Failure
  Expected: "Fizz"
  Actual:   "3"
```

### Green: 明白な実装

3 の倍数のときは "Fizz" を返すように実装します。F# では `%` 演算子で剰余を求め、`= 0` でゼロかどうかを判定します。

```fsharp
let generate (number: int) : string =
    if number % 3 = 0 then "Fizz"
    else string number
```

```bash
$ dotnet test
  合計: 3、成功: 3、失敗: 0、スキップ: 0
```

F# の `if/else` は式であり、常に値を返します。Rust の `if` 式や Kotlin の `when` 式と同様に、結果を変数にバインドしたりそのまま返したりできます。

三角測量として 6 のテストも追加して確認します。

```fsharp
[<Fact>]
let ``三の倍数のときはFizzを返す_6`` () =
    Assert.Equal("Fizz", generate 6)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.4 5 の倍数 — Buzz

### Red: 5 の倍数のテスト

```fsharp
[<Fact>]
let ``五の倍数のときはBuzzを返す`` () =
    Assert.Equal("Buzz", generate 5)
```

```bash
$ dotnet test
  Assert.Equal() Failure
  Expected: "Buzz"
  Actual:   "5"
```

### Green: Buzz の実装

F# の `elif` キーワードで条件を追加します。

```fsharp
let generate (number: int) : string =
    if number % 3 = 0 then "Fizz"
    elif number % 5 = 0 then "Buzz"
    else string number
```

```bash
$ dotnet test
  合計: 5、成功: 5、失敗: 0、スキップ: 0
```

F# では `else if` の代わりに `elif` を使います。Python の `elif` と同様の書き方です。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 15 の倍数 — FizzBuzz

### Red: 15 の倍数のテスト

```fsharp
[<Fact>]
let ``三と五の倍数のときはFizzBuzzを返す`` () =
    Assert.Equal("FizzBuzz", generate 15)
```

```bash
$ dotnet test
  Assert.Equal() Failure
  Expected: "FizzBuzz"
  Actual:   "Fizz"
```

15 は 3 の倍数でもあるため、"Fizz" が返されてしまいました。3 と 5 の両方の倍数の判定を先に行う必要があります。

### Green: FizzBuzz の実装

```fsharp
let generate (number: int) : string =
    if number % 3 = 0 && number % 5 = 0 then "FizzBuzz"
    elif number % 3 = 0 then "Fizz"
    elif number % 5 = 0 then "Buzz"
    else string number
```

```bash
$ dotnet test
  合計: 6、成功: 6、失敗: 0、スキップ: 0
```

F# の `&&` は論理 AND 演算子です。Rust や Java と同じ記号を使いますが、F# では等値比較に `=`（Rust は `==`）を使う点に注意してください。

### パイプライン演算子の紹介

ここで F# の特徴的な機能である **パイプライン演算子** `|>` を紹介します。パイプライン演算子は値を関数に渡すための構文で、Unix のパイプ `|` と同様にデータの流れを左から右に読めるようにします。

```fsharp
// 通常の関数呼び出し
string 42

// パイプライン演算子を使った呼び出し
42 |> string
```

この段階ではまだ活用しませんが、次章以降で FizzBuzz のリファクタリングに活用します。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.6 まとめ

この章では以下のことを学びました。

- **三角測量** で 2 つ以上の例を使ってプログラムを一般化する手法
- F# の `string` 関数による整数から文字列への変換
- F# の `%` 演算子による剰余判定
- F# の `if/elif/else` 式による条件分岐
- F# の `&&` 演算子と `=` 演算子
- **パイプライン演算子** `|>` の基本概念
- Red-Green-Refactor サイクルを繰り返してコアロジックを段階的に構築する方法

次章では、残りの TODO（リスト生成とプリント）を実装し、リファクタリングで「動作するきれいなコード」を目指します。
