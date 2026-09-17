---
type: Article
title: "第 8 章: 判別共用体とパターンマッチ"
description: "前章ではレコード型とモジュールによるカプセル化を実現しました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: 判別共用体とパターンマッチ

## 8.1 はじめに

前章ではレコード型とモジュールによるカプセル化を実現しました。この章では F# の最も強力な機能の一つである **判別共用体（Discriminated Union）** と **パターンマッチ** を導入して、FizzBuzz のタイプを型安全に表現します。

## 8.2 判別共用体の導入

### FizzBuzzType 判別共用体

FizzBuzz の動作モードを表現する判別共用体を定義します。

```fsharp
type FizzBuzzType =
    | Standard
    | NumberOnly
    | FizzBuzzOnly
```

この定義により、FizzBuzz の動作モードを 3 つの明確な型で表現できます。

- `Standard`: 通常の FizzBuzz（3 の倍数 → "Fizz"、5 の倍数 → "Buzz"、15 の倍数 → "FizzBuzz"）
- `NumberOnly`: 常に数値を文字列にして返す
- `FizzBuzzOnly`: 15 の倍数のときだけ "FizzBuzz"、それ以外は数値を返す

### 判別共用体の特徴

F# の判別共用体は Rust の `enum` や Java の sealed interface に相当します。

| 特徴 | F# 判別共用体 | Rust enum | Java |
|------|-------------|-----------|------|
| 定義 | `type T = A \| B` | `enum T { A, B }` | `sealed interface` |
| データ保持 | `A of int` | `A(i32)` | レコードクラス |
| 網羅性チェック | コンパイラが検出 | コンパイラが検出 | Java 21+ で対応 |
| パターンマッチ | `match...with` | `match` | `switch`(Java 21+) |

## 8.3 パターンマッチによる generate 関数

### テストを書く

まず各タイプに対応するテストを追加します。

```fsharp
module FizzBuzzTypeTests =

    [<Fact>]
    let ``Standard_数を文字列にして返す`` () =
        let result = Domain.generate Standard 1
        Assert.Equal("1", result.Value)

    [<Fact>]
    let ``Standard_三の倍数のときはFizzを返す`` () =
        let result = Domain.generate Standard 3
        Assert.Equal("Fizz", result.Value)

    [<Fact>]
    let ``Standard_五の倍数のときはBuzzを返す`` () =
        let result = Domain.generate Standard 5
        Assert.Equal("Buzz", result.Value)

    [<Fact>]
    let ``Standard_三と五の倍数のときはFizzBuzzを返す`` () =
        let result = Domain.generate Standard 15
        Assert.Equal("FizzBuzz", result.Value)

    [<Fact>]
    let ``NumberOnly_常に数値を文字列にして返す`` () =
        Assert.Equal("1", (Domain.generate NumberOnly 1).Value)
        Assert.Equal("3", (Domain.generate NumberOnly 3).Value)
        Assert.Equal("5", (Domain.generate NumberOnly 5).Value)

    [<Fact>]
    let ``FizzBuzzOnly_十五の倍数のときはFizzBuzzを返す`` () =
        Assert.Equal("FizzBuzz", (Domain.generate FizzBuzzOnly 15).Value)
        Assert.Equal("FizzBuzz", (Domain.generate FizzBuzzOnly 30).Value)

    [<Fact>]
    let ``FizzBuzzOnly_十五の倍数以外は数値を文字列にして返す`` () =
        Assert.Equal("1", (Domain.generate FizzBuzzOnly 1).Value)
        Assert.Equal("3", (Domain.generate FizzBuzzOnly 3).Value)
```

### generate 関数の実装

判別共用体をパターンマッチで分岐させます。

```fsharp
let generate (fizzBuzzType: FizzBuzzType) (number: int) : FizzBuzzValue =
    match fizzBuzzType with
    | Standard ->
        if isFizzBuzz number then createValue number "FizzBuzz"
        elif isFizz number then createValue number "Fizz"
        elif isBuzz number then createValue number "Buzz"
        else createValue number (string number)
    | NumberOnly ->
        createValue number (string number)
    | FizzBuzzOnly ->
        if number % 15 = 0 then createValue number "FizzBuzz"
        else createValue number (string number)
```

```bash
$ dotnet test
  合計: 17、成功: 17、失敗: 0、スキップ: 0
```

## 8.4 網羅的パターンマッチ

F# のパターンマッチの最大の利点は **網羅性チェック** です。もし新しいタイプを追加して、対応するパターンを書き忘れると、コンパイラが警告を出します。

```fsharp
// 新しいタイプを追加
type FizzBuzzType =
    | Standard
    | NumberOnly
    | FizzBuzzOnly
    | CustomFizz    // 新しいタイプ

// generate 関数で CustomFizz のパターンを書き忘れると...
// 警告 FS0025: 不完全なパターンマッチ。この式が取り得る値の一部が処理されません。
```

この網羅性チェックにより、新しい判別共用体のケースを追加した際に、対応漏れをコンパイル時に検出できます。Rust の `match` と同様に、F# はすべてのパターンを処理することを要求します。

## 8.5 判別共用体とオブジェクト指向の比較

### Before: オブジェクト指向アプローチ

Rust や Java では、タイプごとにクラス/構造体を作成し、トレイト/インターフェースで共通化します。

```fsharp
// OOP アプローチ（F# でも可能だが非推奨）
[<AbstractClass>]
type FizzBuzzType() =
    abstract member Generate: int -> string

type StandardType() =
    inherit FizzBuzzType()
    override this.Generate(number) = ...

type NumberOnlyType() =
    inherit FizzBuzzType()
    override this.Generate(number) = ...
```

### After: 関数型アプローチ

```fsharp
// FP アプローチ（F# らしい）
type FizzBuzzType =
    | Standard
    | NumberOnly
    | FizzBuzzOnly

let generate fizzBuzzType number =
    match fizzBuzzType with
    | Standard -> ...
    | NumberOnly -> ...
    | FizzBuzzOnly -> ...
```

関数型アプローチでは、データ（判別共用体）と振る舞い（関数）が分離されています。新しいタイプの追加は判別共用体にケースを追加し、関数のパターンマッチに対応する分岐を追加するだけです。

## 8.6 まとめ

この章では以下を学びました。

| 概念 | F# の実現方法 | 利点 |
|------|---------------|------|
| 判別共用体 | `type T = A \| B \| C` | タイプの明確な表現 |
| パターンマッチ | `match...with` | 網羅的な条件分岐 |
| 網羅性チェック | コンパイラ警告 | パターン漏れの防止 |
| カリー化 | `generate type number` | 部分適用が可能 |

次章では、**モジュール設計** と **型による設計** を通じて、FizzBuzz プログラム全体のアーキテクチャを整理します。
