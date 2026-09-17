---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, csharp]
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

```csharp
[Fact]
public void 数を文字列にして返す_2を渡したら文字列2を返す()
{
    Assert.Equal("2", FizzBuzzRunner.Generate(2));
}
```

テストを実行します。

```bash
$ dotnet test
失敗!   Expected: "2", Actual: "1"
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。C# では `int` 型の `ToString()` メソッドで整数を文字列に変換できます。

```csharp
public static string Generate(int number)
{
    return number.ToString();
}
```

テストを実行します。

```bash
$ dotnet test
成功!   -失敗:     0、合格:     2、スキップ:     0、合計:     2
```

テストが通りました。2 つ目のテストによって `Generate` メソッドの一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

Rust では `number.to_string()` と書くところを、C# でも同じく `number.ToString()` を使います。C# の `ToString()` は `object` 型で定義された仮想メソッドであり、すべての型で利用可能です。

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

```csharp
[Fact]
public void 三の倍数のときはFizzを返す_3を渡したらFizzを返す()
{
    Assert.Equal("Fizz", FizzBuzzRunner.Generate(3));
}
```

```bash
$ dotnet test
失敗!   Expected: "Fizz", Actual: "3"
```

### Green: 明白な実装

3 の倍数のときは "Fizz" を返すように実装します。C# では `%` 演算子で剰余を求め、`== 0` でゼロかどうかを判定します。

```csharp
public static string Generate(int number)
{
    if (number % 3 == 0)
    {
        return "Fizz";
    }
    return number.ToString();
}
```

```bash
$ dotnet test
成功!   -失敗:     0、合格:     3、スキップ:     0、合計:     3
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

```csharp
[Fact]
public void 五の倍数のときはBuzzを返す_5を渡したらBuzzを返す()
{
    Assert.Equal("Buzz", FizzBuzzRunner.Generate(5));
}
```

```bash
$ dotnet test
失敗!   Expected: "Buzz", Actual: "5"
```

### Green: Buzz の実装

```csharp
public static string Generate(int number)
{
    if (number % 3 == 0)
    {
        return "Fizz";
    }
    if (number % 5 == 0)
    {
        return "Buzz";
    }
    return number.ToString();
}
```

```bash
$ dotnet test
成功!   -失敗:     0、合格:     4、スキップ:     0、合計:     4
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 15 の倍数 — FizzBuzz

### Red: 15 の倍数のテスト

```csharp
[Fact]
public void 三と五の倍数のときはFizzBuzzを返す_15を渡したらFizzBuzzを返す()
{
    Assert.Equal("FizzBuzz", FizzBuzzRunner.Generate(15));
}
```

```bash
$ dotnet test
失敗!   Expected: "FizzBuzz", Actual: "Fizz"
```

15 は 3 の倍数でもあるため、"Fizz" が返されてしまいました。3 と 5 の両方の倍数の判定を先に行う必要があります。

### Green: FizzBuzz の実装

`if` 文の順序を整理して、3 と 5 の両方の倍数を最初に判定します。

```csharp
public static string Generate(int number)
{
    if (number % 3 == 0 && number % 5 == 0)
    {
        return "FizzBuzz";
    }
    if (number % 3 == 0)
    {
        return "Fizz";
    }
    if (number % 5 == 0)
    {
        return "Buzz";
    }
    return number.ToString();
}
```

```bash
$ dotnet test
成功!   -失敗:     0、合格:     5、スキップ:     0、合計:     5
```

Rust では `match` 式のタプルパターンで条件分岐を行いましたが、C# では `if-else if` チェーンを使います。C# にも `switch` 式がありますが、この段階ではシンプルな `if` 文で十分です。

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
- C# の `ToString()` メソッドによる整数から文字列への変換
- C# の `%` 演算子による剰余判定
- `if-else if` チェーンによる条件分岐
- Red-Green-Refactor サイクルを繰り返してコアロジックを段階的に構築する方法

次章では、残りの TODO（リスト生成とプリント）を実装し、リファクタリングで「動作するきれいなコード」を目指します。
