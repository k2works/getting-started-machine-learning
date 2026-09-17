---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "F# は関数型プログラミングの機能を豊富にサポートしています。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

F# は関数型プログラミングの機能を豊富にサポートしています。この章では **パイプライン演算子**、**部分適用**、**カリー化**、**関数合成** を中心に、高階関数の考え方を学びます。

## 10.2 パイプライン演算子

F# の **パイプライン演算子** `|>` は、値を関数に渡すための構文です。Unix のパイプ `|` と同様に、データの流れを左から右に読めるようにします。

```fsharp
// 通常の関数呼び出し（内側から外側へ読む）
List.map (fun v -> v.Value) (List.filter (fun v -> v.Number > 5) values)

// パイプライン演算子（左から右へ読む）
values
|> List.filter (fun v -> v.Number > 5)
|> List.map (fun v -> v.Value)
```

パイプライン演算子により、データの変換処理が自然な流れで読めるようになります。

### FizzBuzz での活用例

```fsharp
// 1 から 100 までの FizzBuzz リストを生成
let fizzBuzzList =
    [ 1..100 ]
    |> List.map (generate Standard)
    |> createList

// リストから Fizz の値だけを抽出してカウント
let fizzCount =
    fizzBuzzList.Values
    |> List.filter (fun v -> v.Value = "Fizz")
    |> List.length
```

## 10.3 カリー化と部分適用

### カリー化

F# の関数はデフォルトで **カリー化** されています。複数の引数を取る関数は、1 つの引数を取り「残りの引数を取る関数」を返す関数として解釈されます。

```fsharp
// generate は FizzBuzzType -> int -> FizzBuzzValue
// つまり、FizzBuzzType を受け取り、(int -> FizzBuzzValue) を返す関数
let generate (fizzBuzzType: FizzBuzzType) (number: int) : FizzBuzzValue = ...
```

### 部分適用

カリー化された関数に一部の引数だけを渡すことで、新しい関数を作成できます。

```fsharp
// generate に Standard だけを適用して新しい関数を作成
let generateStandard = generate Standard
// generateStandard は int -> FizzBuzzValue 型

// 使用例
let result = generateStandard 15
Assert.Equal("FizzBuzz", result.Value)

// List.map と組み合わせる
let values = [ 1..10 ] |> List.map generateStandard
```

Rust ではクロージャ `|n| generate(n, Standard)` で同様のことを実現しますが、F# ではカリー化のおかげで自然に部分適用が行えます。

### テストで確認

```fsharp
[<Fact>]
let ``部分適用でStandard専用の関数を作成できる`` () =
    let generateStandard = generate Standard
    Assert.Equal("FizzBuzz", (generateStandard 15).Value)
    Assert.Equal("Fizz", (generateStandard 3).Value)
    Assert.Equal("Buzz", (generateStandard 5).Value)

[<Fact>]
let ``部分適用でNumberOnly専用の関数を作成できる`` () =
    let generateNumberOnly = generate NumberOnly
    Assert.Equal("3", (generateNumberOnly 3).Value)
    Assert.Equal("15", (generateNumberOnly 15).Value)
```

## 10.4 関数合成演算子

### >> 演算子

F# の **関数合成演算子** `>>` は、2 つの関数を連結して新しい関数を作成します。

```fsharp
// f >> g は「まず f を適用し、その結果に g を適用する」関数
let classify = generate Standard
let toString (value: FizzBuzzValue) = value.Value

// 関数合成で新しい関数を作成
let fizzBuzz = classify >> toString
// fizzBuzz は int -> string 型

// 使用例
Assert.Equal("FizzBuzz", fizzBuzz 15)
Assert.Equal("1", fizzBuzz 1)
```

### パイプライン演算子との違い

| 演算子 | 用途 | 型 |
|--------|------|-----|
| `\|>` | 値を関数に渡す | `'a -> ('a -> 'b) -> 'b` |
| `>>` | 関数を合成する | `('a -> 'b) -> ('b -> 'c) -> ('a -> 'c)` |

```fsharp
// パイプライン: 値に対して関数を適用
15 |> classify |> toString  // "FizzBuzz"

// 関数合成: 関数同士を結合して新しい関数を作成
let fizzBuzz = classify >> toString
fizzBuzz 15  // "FizzBuzz"
```

パイプライン演算子は「値」を流すのに対し、関数合成演算子は「関数」を結合します。

## 10.5 高階関数

### 関数を引数として受け取る

```fsharp
// applyToRange: 指定した関数を範囲に適用する高階関数
let applyToRange (f: int -> 'a) (start: int) (end': int) : 'a list =
    [ start..end' ] |> List.map f

// 使用例
let fizzBuzzRange = applyToRange (generate Standard >> (fun v -> v.Value)) 1 20
// ["1"; "2"; "Fizz"; "4"; "Buzz"; ...]
```

### FizzBuzzList への関数型メソッド追加

```fsharp
member this.CountByValue() =
    this.Values
    |> List.groupBy (fun v -> v.Value)
    |> List.map (fun (key, group) -> (key, List.length group))
    |> Map.ofList
```

### テストで確認

```fsharp
[<Fact>]
let ``値ごとにカウントできる`` () =
    let list =
        createList
            [ createValue 1 "1"
              createValue 2 "2"
              createValue 3 "Fizz"
              createValue 6 "Fizz"
              createValue 5 "Buzz" ]
    let counts = list.CountByValue()
    Assert.Equal(1, counts.["1"])
    Assert.Equal(2, counts.["Fizz"])
    Assert.Equal(1, counts.["Buzz"])
```

## 10.6 他言語との比較

| 概念 | F# | Rust | Java |
|------|-----|------|------|
| パイプライン | `x \|> f` | — (メソッドチェーン) | — (Stream API) |
| 部分適用 | `generate Standard` | クロージャで代替 | — |
| 関数合成 | `f >> g` | — | `f.andThen(g)` |
| 高階関数 | `List.map f` | `.iter().map(f)` | `.stream().map(f)` |
| カリー化 | デフォルト | — | — |

## 10.7 まとめ

この章では以下を学びました。

| 概念 | F# の記法 | 用途 |
|------|----------|------|
| パイプライン演算子 | `\|>` | 値を関数に渡す |
| 関数合成演算子 | `>>` | 関数を結合して新しい関数を作成 |
| カリー化 | デフォルト | 複数引数関数の段階的適用 |
| 部分適用 | `generate Standard` | 引数の一部を固定した新しい関数 |
| 高階関数 | `List.map`, `List.filter` | 関数を引数に取る関数 |

次章では、**不変データ** と **コレクション処理** を深掘りし、F# のイミュータブル設計を学びます。
