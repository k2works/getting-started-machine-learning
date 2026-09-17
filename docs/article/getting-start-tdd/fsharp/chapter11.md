---
type: Article
title: "第 11 章: 不変データとコレクション処理"
description: "前章ではパイプライン演算子と関数合成を学びました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとコレクション処理

## 11.1 はじめに

前章ではパイプライン演算子と関数合成を学びました。この章では F# の **不変データ** の考え方と、**List モジュール** / **Seq モジュール** によるコレクション処理を深掘りします。

## 11.2 F# の不変性

F# では変数はデフォルトで **不変（immutable）** です。

```fsharp
let x = 5        // 不変（デフォルト）
let mutable y = 10  // 可変（明示的）
```

Rust と同様に、不変がデフォルトの設計です。Rust では `let` が不変、`let mut` が可変ですが、F# では `let` が不変、`let mutable` が可変です。

### レコード型の不変性

FizzBuzzValue レコード型はデフォルトで不変です。

```fsharp
let value = createValue 3 "Fizz"
// value.Value <- "Buzz"  // コンパイルエラー: レコードフィールドは変更できない

// 新しい値を作成する場合は with 式を使う
let newValue = { value with Value = "Buzz" }
Assert.Equal("Fizz", value.Value)    // 元の値は変わらない
Assert.Equal("Buzz", newValue.Value) // 新しい値が作成される
```

### FizzBuzzList の不変設計

`Add` メソッドは元のリストを変更せず、新しいリストを返します。

```fsharp
[<Fact>]
let ``値を追加できる`` () =
    let list = emptyList
    let newList = list.Add(createValue 1 "1")
    Assert.Equal(1, newList.Count)
    Assert.Equal(0, list.Count)  // 元のリストは不変
```

## 11.3 List モジュール

F# の `List` モジュールはイミュータブルなリンクドリストを操作する関数群を提供します。

### List.map — 要素の変換

```fsharp
// 各 FizzBuzzValue の Value フィールドを取得
let stringValues =
    fizzBuzzList.Values
    |> List.map (fun v -> v.Value)
// ["1"; "2"; "Fizz"; "4"; "Buzz"; ...]
```

### List.filter — 要素の選別

```fsharp
// Fizz の値だけを抽出
let fizzValues =
    fizzBuzzList.Values
    |> List.filter (fun v -> v.Value = "Fizz")
```

### List.fold — 集約

```fsharp
// すべての Value を改行区切りで連結
let joined =
    fizzBuzzList.Values
    |> List.map (fun v -> v.Value)
    |> List.fold (fun acc v ->
        if acc = "" then v
        else acc + "\n" + v) ""
```

`List.fold` は Rust の `.fold()` や Java の `Stream.reduce()` に相当します。初期値と累積関数を受け取り、リストの各要素を順に処理します。

### List.groupBy — グルーピング

```fsharp
// Value でグルーピングしてカウント
let counts =
    fizzBuzzList.Values
    |> List.groupBy (fun v -> v.Value)
    |> List.map (fun (key, group) -> (key, List.length group))
    |> Map.ofList
```

### List.tryFind — 最初の一致要素

```fsharp
let firstFizz =
    fizzBuzzList.Values
    |> List.tryFind (fun v -> v.Value = "Fizz")

match firstFizz with
| Some value -> printfn "Found: %s" (value.ToString())
| None -> printfn "Not found"
```

`List.tryFind` は `Option<'T>` を返します。Rust の `.find()` が `Option<T>` を返すのと同様です。

## 11.4 Seq モジュール

`Seq` モジュールは **遅延評価** されるシーケンスを操作します。

```fsharp
// Seq は遅延評価（必要な分だけ計算）
let firstFiveFizzBuzz =
    Seq.initInfinite (fun i -> generate Standard (i + 1))
    |> Seq.map (fun v -> v.Value)
    |> Seq.take 5
    |> Seq.toList
// ["1"; "2"; "Fizz"; "4"; "Buzz"]
```

### List と Seq の使い分け

| 特徴 | List | Seq |
|------|------|-----|
| 評価方式 | 即時評価 | 遅延評価 |
| メモリ使用 | 全要素をメモリに保持 | 必要な分だけ計算 |
| 無限列 | 不可 | 可能（Seq.initInfinite） |
| パフォーマンス | 小〜中サイズに最適 | 大量データに最適 |
| 用途 | 一般的なコレクション操作 | ストリーム処理、大量データ |

Rust のイテレータも遅延評価ですが、F# では `List`（即時）と `Seq`（遅延）を明示的に使い分けます。

## 11.5 FizzBuzzList のイミュータブル設計

### パイプラインによるコレクション操作

FizzBuzzList にコレクション操作メソッドを追加します。

```fsharp
member this.Filter(predicate: FizzBuzzValue -> bool) =
    { Values = this.Values |> List.filter predicate }

member this.FindFirst(predicate: FizzBuzzValue -> bool) =
    this.Values |> List.tryFind predicate

member this.ToStringValues() =
    this.Values |> List.map (fun v -> v.Value)

member this.Add(value: FizzBuzzValue) =
    { Values = this.Values @ [ value ] }

member this.AddRange(values: FizzBuzzValue list) =
    { Values = this.Values @ values }
```

すべてのメソッドが **新しい値を返す** 設計です。元のリストは変更されません。

### テストで確認

```fsharp
[<Fact>]
let ``文字列リストに変換できる`` () =
    let list =
        createList
            [ createValue 1 "1"
              createValue 3 "Fizz"
              createValue 5 "Buzz" ]
    let strings = list.ToStringValues()
    Assert.Equal<string list>([ "1"; "Fizz"; "Buzz" ], strings)

[<Fact>]
let ``最初の一致する値を取得できる`` () =
    let list =
        createList
            [ createValue 1 "1"
              createValue 3 "Fizz"
              createValue 6 "Fizz" ]
    let found = list.FindFirst(fun v -> v.Value = "Fizz")
    Assert.True(found.IsSome)
    Assert.Equal(3, found.Value.Number)

[<Fact>]
let ``一致する値がない場合はNoneを返す`` () =
    let list =
        createList [ createValue 1 "1"; createValue 2 "2" ]
    let found = list.FindFirst(fun v -> v.Value = "Fizz")
    Assert.True(found.IsNone)
```

## 11.6 他言語との比較

| 概念 | F# | Rust | Java |
|------|-----|------|------|
| 不変デフォルト | `let x = 5` | `let x = 5` | `final` で明示 |
| パイプライン | `\|> List.filter f \|> List.map g` | `.iter().filter(f).map(g)` | `.stream().filter(f).map(g)` |
| fold | `List.fold f init list` | `.fold(init, f)` | `.reduce(init, f)` |
| 遅延評価 | `Seq` モジュール | イテレータ（デフォルト遅延） | `Stream`（デフォルト遅延） |
| Option | `Some v / None` | `Some(v) / None` | `Optional.of(v) / empty()` |

## 11.7 まとめ

この章では以下を学びました。

| 概念 | F# の実現方法 |
|------|---------------|
| 不変性 | `let` バインディング、レコード型 |
| コピー | `with` 式によるレコードの部分更新 |
| List モジュール | map, filter, fold, groupBy, tryFind |
| Seq モジュール | 遅延評価によるストリーム処理 |
| イミュータブル設計 | 操作は常に新しい値を返す |

次章では、**Option 型** と **Result 型** を使ったエラーハンドリングと型安全性を学びます。
