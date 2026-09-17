---
type: Article
title: "第 7 章: レコード型とモジュールによるカプセル化"
description: "第 1 部では手続き型の FizzBuzz プログラムを TDD で構築しました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: レコード型とモジュールによるカプセル化

## 7.1 はじめに

第 1 部では手続き型の FizzBuzz プログラムを TDD で構築しました。この章からは **F# の関数型アプローチ** による設計を導入していきます。まず **レコード型** と **モジュール** によるカプセル化を実現します。

## 7.2 手続き型コードの課題

第 1 部で作成した `generate` 関数は手続き型プログラミングの典型例です。

```fsharp
let generate (number: int) : string =
    match (number % 3, number % 5) with
    | (0, 0) -> "FizzBuzz"
    | (0, _) -> "Fizz"
    | (_, 0) -> "Buzz"
    | _ -> string number
```

この設計の課題は、数値と変換結果の関係がプリミティブ型（`int` と `string`）で表現されており、ドメインの意味が失われていることです。

## 7.3 レコード型による値オブジェクト

### FizzBuzzValue レコード型

数値と FizzBuzz の結果をまとめたレコード型を作成します。

```fsharp
type FizzBuzzValue =
    { Number: int
      Value: string }

    override this.ToString() = sprintf "%d:%s" this.Number this.Value
```

### テストを書く

```fsharp
[<Fact>]
let ``値を保持する`` () =
    let value = createValue 1 "1"
    Assert.Equal(1, value.Number)
    Assert.Equal("1", value.Value)

[<Fact>]
let ``同じ値のレコードは等しい`` () =
    let value1 = createValue 1 "1"
    let value2 = createValue 1 "1"
    Assert.Equal(value1, value2)

[<Fact>]
let ``ToStringはNumber_Colon_Value形式`` () =
    let value = createValue 3 "Fizz"
    Assert.Equal("3:Fizz", value.ToString())
```

### createValue 関数の実装

```fsharp
let createValue number value = { Number = number; Value = value }
```

```bash
$ dotnet test
  合計: 10、成功: 10、失敗: 0、スキップ: 0
```

F# のレコード型は **構造的等価性** を持ちます。つまり、フィールドの値が同じであれば 2 つのレコードは等しいと判定されます。Rust では `#[derive(PartialEq)]` が必要ですが、F# のレコード型ではデフォルトで構造的等価性が提供されます。

### レコード型の特徴

| 特徴 | F# レコード型 | Rust struct | Java class |
|------|-------------|-------------|------------|
| 不変性 | デフォルト不変 | デフォルト不変 | 明示的に `final` |
| 等価性 | 構造的（自動） | `#[derive(PartialEq)]` | `equals()` 手動実装 |
| コピー | `{ record with Field = newValue }` | `.clone()` | コンストラクタで新規作成 |
| パターンマッチ | 対応 | 対応 | Java 21+ で対応 |

## 7.4 モジュールによるアクセス制御

### Domain モジュールの作成

F# では `module` でコードをグルーピングし、`private` でアクセスを制御します。

```fsharp
namespace FizzBuzzFSharp

module Domain =

    type FizzBuzzValue =
        { Number: int
          Value: string }

        override this.ToString() = sprintf "%d:%s" this.Number this.Value

    let createValue number value = { Number = number; Value = value }

    let private isFizz number = number % 3 = 0
    let private isBuzz number = number % 5 = 0
    let private isFizzBuzz number = isFizz number && isBuzz number
```

`private` キーワードで修飾された関数はモジュール外からアクセスできません。Rust の `pub`/非公開 や Java の `private` に相当します。

### generate 関数の更新

`generate` 関数を Domain モジュール内に移動し、`FizzBuzzValue` を返すように変更します。

```fsharp
let generate (number: int) : FizzBuzzValue =
    if isFizzBuzz number then createValue number "FizzBuzz"
    elif isFizz number then createValue number "Fizz"
    elif isBuzz number then createValue number "Buzz"
    else createValue number (string number)
```

### テストの更新

```fsharp
[<Fact>]
let ``Standard_数を文字列にして返す`` () =
    let result = Domain.generate 1
    Assert.Equal("1", result.Value)

[<Fact>]
let ``Standard_三の倍数のときはFizzを返す`` () =
    let result = Domain.generate 3
    Assert.Equal("Fizz", result.Value)
```

## 7.5 F# のモジュールシステム

F# のモジュールシステムは `namespace` と `module` の 2 つの構造で構成されます。

```fsharp
namespace FizzBuzzFSharp    // 名前空間

module Domain =              // モジュール（値と関数を含む）
    let createValue ...      // 公開関数
    let private isFizz ...   // 非公開関数
```

| 構造 | 用途 | Rust の対応 |
|------|------|-----------|
| `namespace` | 型のグルーピング | `mod`（ファイルレベル） |
| `module` | 関数と値のグルーピング | `mod`（内部モジュール） |
| `private` | アクセス制限 | デフォルト非公開 |
| `internal` | アセンブリ内公開 | `pub(crate)` |

## 7.6 まとめ

この章では以下を学びました。

| 概念 | F# の実現方法 | 他言語の対応 |
|------|---------------|-------------|
| 値オブジェクト | レコード型 | Rust: struct + derive, Java: record |
| 構造的等価性 | レコード型のデフォルト | Rust: PartialEq, Java: equals |
| カプセル化 | module + private | Rust: mod + pub, Java: class + private |
| ファクトリ関数 | let createValue | Rust: fn new(), Java: static factory |

次章では、**判別共用体** と **パターンマッチ** を導入して、FizzBuzz のタイプを型安全に表現します。
