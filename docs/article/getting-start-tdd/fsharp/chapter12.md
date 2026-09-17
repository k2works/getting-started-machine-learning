---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章では不変データとコレクション処理を学びました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章では不変データとコレクション処理を学びました。この最終章では、F# の **Option 型** と **Result 型** を使ったエラーハンドリング、そして **計算式（Computation Expressions）** による型安全な設計を学びます。

## 12.2 Option 型

F# には `null` を直接扱う代わりに **Option 型** を使います。値が存在しない可能性を型で明示します。

```fsharp
// Option 型の定義（組み込み）
// type Option<'T> = Some of 'T | None
```

### FizzBuzzList での活用

`FindFirst` メソッドは `Option<FizzBuzzValue>` を返します。

```fsharp
member this.FindFirst(predicate: FizzBuzzValue -> bool) =
    this.Values |> List.tryFind predicate
```

### パターンマッチによる処理

```fsharp
let list =
    createList
        [ createValue 1 "1"
          createValue 3 "Fizz"
          createValue 5 "Buzz" ]

match list.FindFirst(fun v -> v.Value = "Fizz") with
| Some value -> printfn "Found: %d:%s" value.Number value.Value
| None -> printfn "Not found"
```

### Option モジュールの活用

```fsharp
// Option.map: Some の場合のみ変換
let fizzNumber =
    list.FindFirst(fun v -> v.Value = "Fizz")
    |> Option.map (fun v -> v.Number)
// Some 3

// Option.defaultValue: None の場合のデフォルト値
let numberOrDefault =
    list.FindFirst(fun v -> v.Value = "Missing")
    |> Option.map (fun v -> v.Number)
    |> Option.defaultValue 0
// 0

// Option.bind: Some の場合のみ次の Option を返す関数を適用
let findAndFilter =
    list.FindFirst(fun v -> v.Value = "Fizz")
    |> Option.bind (fun v ->
        if v.Number > 2 then Some v
        else None)
```

## 12.3 Result 型

**Result 型** は操作の成功/失敗を型で明示します。例外をスローする代わりに、`Ok` または `Error` を返します。

```fsharp
// Result 型の定義（組み込み）
// type Result<'T, 'TError> = Ok of 'T | Error of 'TError
```

### FizzBuzz でのエラーハンドリング

不正な入力値に対するバリデーションを Result 型で表現します。

```fsharp
let validateNumber (number: int) : Result<int, string> =
    if number <= 0 then Error "数値は正の整数でなければなりません"
    elif number > 1000 then Error "数値は1000以下でなければなりません"
    else Ok number

let safeGenerate (fizzBuzzType: FizzBuzzType) (number: int) : Result<FizzBuzzValue, string> =
    number
    |> validateNumber
    |> Result.map (generate fizzBuzzType)
```

### テストで確認

```fsharp
[<Fact>]
let ``正の整数でバリデーション成功`` () =
    let result = validateNumber 5
    match result with
    | Ok n -> Assert.Equal(5, n)
    | Error _ -> Assert.Fail("Expected Ok")

[<Fact>]
let ``ゼロ以下でバリデーション失敗`` () =
    let result = validateNumber 0
    match result with
    | Ok _ -> Assert.Fail("Expected Error")
    | Error msg -> Assert.Equal("数値は正の整数でなければなりません", msg)

[<Fact>]
let ``safeGenerateで安全にFizzBuzzを生成`` () =
    let result = safeGenerate Standard 15
    match result with
    | Ok value -> Assert.Equal("FizzBuzz", value.Value)
    | Error _ -> Assert.Fail("Expected Ok")
```

### Result モジュールの活用

```fsharp
// Result.map: Ok の場合のみ変換
let stringResult =
    safeGenerate Standard 15
    |> Result.map (fun v -> v.Value)
// Ok "FizzBuzz"

// Result.bind: Ok の場合のみ次の Result を返す関数を適用
let chainedResult =
    validateNumber 15
    |> Result.bind (fun n ->
        if n % 3 = 0 || n % 5 = 0 then Ok n
        else Error "FizzBuzz に該当しない数値です")
    |> Result.map (generate Standard)
```

## 12.4 計算式（Computation Expressions）

F# の **計算式** は、モナド的な処理を直感的に記述するための構文です。`let!` を使って Result や Option のチェーンを読みやすく書けます。

### result 計算式ビルダー

```fsharp
type ResultBuilder() =
    member _.Bind(result, f) =
        match result with
        | Ok value -> f value
        | Error e -> Error e
    member _.Return(value) = Ok value
    member _.ReturnFrom(result) = result

let result = ResultBuilder()
```

### 使用例

```fsharp
let processNumber (fizzBuzzType: FizzBuzzType) (input: int) =
    result {
        let! validNumber = validateNumber input
        let fizzBuzzValue = generate fizzBuzzType validNumber
        return fizzBuzzValue.Value
    }

// テスト
[<Fact>]
let ``計算式で安全に処理できる`` () =
    match processNumber Standard 15 with
    | Ok value -> Assert.Equal("FizzBuzz", value)
    | Error _ -> Assert.Fail("Expected Ok")

[<Fact>]
let ``計算式でエラーが伝播する`` () =
    match processNumber Standard 0 with
    | Ok _ -> Assert.Fail("Expected Error")
    | Error msg -> Assert.Equal("数値は正の整数でなければなりません", msg)
```

計算式の `let!` は Rust の `?` 演算子に相当します。`Error` が発生した時点で処理を中断し、エラーを伝播します。

## 12.5 Option と Result の使い分け

| 型 | 用途 | 例 |
|-----|------|-----|
| `Option<'T>` | 値の有無を表現 | 検索結果が見つからない場合 |
| `Result<'T, 'E>` | 成功/失敗とエラー情報を表現 | バリデーション失敗時にエラーメッセージを返す |

```fsharp
// Option: 見つからないだけ（エラー情報不要）
let found = list.FindFirst(fun v -> v.Value = "Fizz")

// Result: なぜ失敗したかの情報が必要
let validated = validateNumber -1
```

## 12.6 他言語との比較

| 概念 | F# | Rust | Java |
|------|-----|------|------|
| エラーハンドリング | `Result<'T, 'E>` | `Result<T, E>` | `try-catch` |
| null 安全 | `Option<'T>` | `Option<T>` | `Optional<T>` |
| エラー伝播 | 計算式 `let!` | `?` 演算子 | `throws` |
| パターンマッチ | `match...with` | `match` | `switch`(Java 21+) |
| モナド構文 | 計算式 | — | — |

F# の計算式は Haskell の `do` 記法に相当する機能で、Rust にはない F# 独自の強力な機能です。

## 12.7 まとめ

この章では以下を学びました。

| 概念 | F# の実現方法 |
|------|---------------|
| Option 型 | `Some v / None` + パターンマッチ |
| Result 型 | `Ok v / Error e` + パターンマッチ |
| Option モジュール | map, bind, defaultValue |
| Result モジュール | map, bind |
| 計算式 | `result { let! ... return ... }` |
| エラー伝播 | `let!` による自動伝播 |

全 12 章を通じて、F# の TDD 基本サイクル、開発環境の自動化、関数型アプローチによる設計、高度な関数型プログラミングを一通り学びました。F# のパイプライン演算子、判別共用体、パターンマッチ、不変データ、Result/Option は、安全で堅牢なソフトウェアを構築するための強力な基盤です。
