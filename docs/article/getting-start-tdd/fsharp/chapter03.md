---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成とプリント）を実装し、**match 式** と **パイプライン演算子** を活用してリファクタリングします。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 match 式によるリファクタリング

テスト駆動開発の流れを確認しておきましょう。

> 1. レッド：動作しない、おそらく最初のうちはコンパイルも通らないテストを 1 つ書く。
> 2. グリーン：そのテストを迅速に動作させる。このステップでは罪を犯してもよい。
> 3. リファクタリング：テストを通すために発生した重複をすべて除去する。
>
> レッド・グリーン・リファクタリング。それが TDD のマントラだ。
>
> — テスト駆動開発

現在の `if/elif/else` による実装を、F# らしい **match 式** でリファクタリングします。

### Before: if/elif/else

```fsharp
let generate (number: int) : string =
    if number % 3 = 0 && number % 5 = 0 then "FizzBuzz"
    elif number % 3 = 0 then "Fizz"
    elif number % 5 = 0 then "Buzz"
    else string number
```

### After: match 式（タプルパターン）

```fsharp
let generate (number: int) : string =
    match (number % 3, number % 5) with
    | (0, 0) -> "FizzBuzz"
    | (0, _) -> "Fizz"
    | (_, 0) -> "Buzz"
    | _ -> string number
```

テストを実行して、リファクタリング後もすべて通ることを確認します。

```bash
$ dotnet test
  合計: 6、成功: 6、失敗: 0、スキップ: 0
```

F# の `match` 式は Rust の `match` 式と非常によく似ています。タプル `(number % 3, number % 5)` に対してパターンを照合し、`_` はワイルドカード（任意の値にマッチ）です。Rust との違いは、矢印記号が `=>` ではなく `->` である点です。

## 3.3 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から 100 までの FizzBuzz の結果をリストとして返す関数をテストします。

```fsharp
[<Fact>]
let ``一から百までのリストを生成する`` () =
    let list = generateList 100
    Assert.Equal(100, list.Length)
    Assert.Equal("1", list.[0])
    Assert.Equal("Fizz", list.[2])
    Assert.Equal("Buzz", list.[4])
    Assert.Equal("FizzBuzz", list.[14])
```

```bash
$ dotnet test
error FS0039: 値またはコンストラクター 'generateList' が定義されていません。
```

### Green: 明白な実装

F# のリスト内包表記と `List.map` を使って実装します。

```fsharp
let generateList (count: int) : string list =
    [ 1..count ] |> List.map generate
```

```bash
$ dotnet test
  合計: 7、成功: 7、失敗: 0、スキップ: 0
```

> 明白な実装
>
> シンプルな操作を実現するにはどうすればいいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

`[ 1..count ]` は F# の **リスト内包表記** で、1 から count までのリストを生成します。Rust の `(1..=count)` や Python の `range(1, count + 1)` に相当します。`|>` パイプライン演算子で `List.map generate` に渡し、各要素を `generate` 関数で変換します。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [ ] プリントする

## 3.4 プリント機能

### 学習用テスト

プリント機能は、生成したリストの各要素を出力するものです。学習用テストとして、F# のリスト反復処理を確認します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

F# では `List.iter` で各要素に対して副作用のある処理を実行できます。

```fsharp
[<Fact>]
let ``リストの各要素を処理できる`` () =
    let mutable result = []
    [ "1"; "2"; "Fizz" ] |> List.iter (fun s -> result <- s :: result)
    Assert.Equal(3, result.Length)
```

### Print 関数の実装

```fsharp
let printFizzBuzz (count: int) : unit =
    generateList count |> List.iter (printfn "%s")
```

`printfn "%s"` は F# の書式付き出力関数です。`%s` は文字列のフォーマット指定子で、`printf` の F# 版です。戻り値の `unit` は Rust の `()` や Java の `void` に相当します。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

## 3.5 テストコードのリファクタリング

テストを論理的なグループに構造化します。F# ではモジュールを使ってテストをグルーピングできます。

```fsharp
module Tests

open Xunit
open FizzBuzzFSharp.FizzBuzz

module FizzBuzzRunnerTests =

    [<Fact>]
    let ``数を文字列にして返す_1を渡したら文字列1を返す`` () =
        Assert.Equal("1", generate 1)

    [<Fact>]
    let ``数を文字列にして返す_2を渡したら文字列2を返す`` () =
        Assert.Equal("2", generate 2)

    [<Fact>]
    let ``三の倍数のときはFizzを返す`` () =
        Assert.Equal("Fizz", generate 3)

    [<Fact>]
    let ``五の倍数のときはBuzzを返す`` () =
        Assert.Equal("Buzz", generate 5)

    [<Fact>]
    let ``三と五の倍数のときはFizzBuzzを返す`` () =
        Assert.Equal("FizzBuzz", generate 15)

    [<Fact>]
    let ``一から百までのリストを生成する`` () =
        let list = generateList 100
        Assert.Equal(100, list.Length)
        Assert.Equal("1", list.[0])
        Assert.Equal("Fizz", list.[2])
        Assert.Equal("Buzz", list.[4])
        Assert.Equal("FizzBuzz", list.[14])
```

## 3.6 他言語との比較

| 概念 | Java | Python | Rust | F# |
|------|------|--------|------|-----|
| テストフレームワーク | JUnit 5 | pytest | cargo test | xUnit |
| テスト実行 | `./gradlew test` | `pytest` | `cargo test` | `dotnet test` |
| 文字列変換 | `String.valueOf(n)` | `str(n)` | `n.to_string()` | `string n` |
| 剰余判定 | `n % 3 == 0` | `n % 3 == 0` | `n % 3 == 0` | `n % 3 = 0` |
| リスト生成 | `IntStream.rangeClosed` | `[f(n) for n in range]` | `(1..=n).map(f).collect()` | `[1..n] \|> List.map f` |
| パターンマッチ | `switch`(Java 21+) | `match`(3.10+) | `match` | `match...with` |

## 3.7 まとめ

この章では以下のことを学びました。

- **match 式** によるタプルパターンマッチング
- **パイプライン演算子** `|>` によるデータ変換の連鎖
- **リスト内包表記** `[1..count]` によるリスト生成
- `List.map` と `List.iter` によるリスト操作
- **明白な実装** でシンプルな操作をそのまま実装する手法
- **リファクタリング** でテストモジュールを構造化する考え方
- Red-Green-Refactor サイクルの完了

第 1 部の 3 章を通じて、TDD の基本サイクル（仮実装 → 三角測量 → 明白な実装 → リファクタリング）を一通り体験しました。次の第 2 部では、開発環境の自動化（バージョン管理、パッケージ管理、CI/CD）に進みます。
