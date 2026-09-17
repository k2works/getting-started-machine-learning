---
type: Article
title: "第5章: プロパティベーステスト"
description: "従来の単体テストでは、特定の入力に対する期待される出力を検証します。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第5章: プロパティベーステスト

## はじめに

従来の単体テストでは、特定の入力に対する期待される出力を検証します。一方、**プロパティベーステスト**では、すべての入力に対して成り立つべき「性質（プロパティ）」を定義し、ランダムに生成された多数のテストケースで検証します。

本章では、F# の **FsCheck** ライブラリを使ったプロパティベーステストの手法を学びます。

## 1. プロパティベーステストとは

### 従来のテストとの違い

```fsharp
// 従来のテスト：特定の入力に対する出力を検証
[<Fact>]
let ``reverseString should reverse a specific string`` () =
    StringOperations.reverseString "hello" |> should equal "olleh"
    StringOperations.reverseString "" |> should equal ""
    StringOperations.reverseString "a" |> should equal "a"

// プロパティベーステスト：性質を検証
[<Property>]
let ``文字列反転は対合（involutory）: 2回反転すると元に戻る`` (s: string) =
    let s = if isNull s then "" else s
    reverseString (reverseString s) = s
```

### プロパティベーステストの利点

1. **網羅性**: 手動では思いつかないエッジケースを発見
2. **ドキュメント性**: コードの性質を明確に表現
3. **回帰防止**: リファクタリング時の安全網
4. **シュリンキング**: 失敗時に最小の反例を提示

## 2. FsCheck の基本

### セットアップ

```bash
# プロジェクトに FsCheck を追加
dotnet add package FsCheck.Xunit
```

### 基本的な使い方

```fsharp
open FsCheck
open FsCheck.Xunit

[<Property>]
let ``プロパティの例`` (s: string) =
    let s = if isNull s then "" else s
    s.Reverse().Reverse() = s.ToCharArray() |> Array.rev |> System.String
```

## 3. 基本的なジェネレータ

### プリミティブジェネレータ

```fsharp
// FsCheck は型に基づいて自動的にジェネレータを選択
[<Property>]
let ``整数のプロパティ`` (n: int) = ...

[<Property>]
let ``文字列のプロパティ`` (s: string) = ...

[<Property>]
let ``リストのプロパティ`` (list: int list) = ...

// PositiveInt など特殊な型も使用可能
[<Property>]
let ``正の整数のプロパティ`` (n: PositiveInt) =
    n.Get > 0
```

### カスタムジェネレータ

```fsharp
open FsCheck

// メールアドレスを生成
let validEmailGen =
    gen {
        let! local = Gen.elements ["user"; "test"; "admin"; "info"]
        let! domain = Gen.elements ["example"; "test"; "company"]
        let! tld = Gen.elements ["com"; "org"; "net"; "io"; "jp"]
        return sprintf "%s@%s.%s" local domain tld
    }

// 電話番号を生成
let validPhoneGen =
    gen {
        let! length = Gen.choose(10, 15)
        let! digits = Gen.listOfLength length (Gen.elements ['0'..'9'])
        return System.String(digits |> List.toArray)
    }
```

## 4. プロパティの定義パターン

### 冪等性（Idempotency）

同じ操作を複数回適用しても結果が変わらない性質。

```fsharp
[<Property>]
let ``ソートは冪等: 2回ソートしても結果は同じ`` (nums: int list) =
    sortNumbers (sortNumbers nums) = sortNumbers nums

[<Property>]
let ``大文字変換は冪等`` (s: string) =
    let s = if isNull s then "" else s
    toUpperCase (toUpperCase s) = toUpperCase s
```

### 対合性（Involution）

2回適用すると元に戻る性質。

```fsharp
[<Property>]
let ``文字列反転は対合: 2回反転すると元に戻る`` (s: string) =
    let s = if isNull s then "" else s
    reverseString (reverseString s) = s

[<Property>]
let ``リスト反転は対合`` (list: int list) =
    reverse (reverse list) = list
```

### 不変量（Invariant）

操作の前後で保存される性質。

```fsharp
[<Property>]
let ``ソートは要素を保存する`` (nums: int list) =
    (sortNumbers nums |> List.sort) = (nums |> List.sort)

[<Property>]
let ``ソートは長さを保存する`` (nums: int list) =
    (sortNumbers nums).Length = nums.Length
```

### 境界条件（Boundary Conditions）

```fsharp
[<Property>]
let ``割引後の価格は0以上、元の価格以下`` (price: PositiveInt) =
    let p = decimal price.Get
    let r = 0.5 // 割引率
    let discounted = calculateDiscount p r
    discounted >= 0m && discounted <= p
```

## 5. ラウンドトリッププロパティ

エンコード/デコードの可逆性は典型的なプロパティです。

```fsharp
/// ランレングス符号化
let encode (s: string) : (char * int) list =
    if System.String.IsNullOrEmpty(s) then []
    else
        s |> Seq.fold (fun acc c ->
            match acc with
            | [] -> [(c, 1)]
            | (lastChar, count) :: rest when lastChar = c ->
                (lastChar, count + 1) :: rest
            | _ -> (c, 1) :: acc
        ) []
        |> List.rev

/// ランレングス復号化
let decode (encoded: (char * int) list) : string =
    encoded
    |> List.map (fun (c, count) -> System.String(c, count))
    |> System.String.Concat

[<Property>]
let ``ランレングス符号化は可逆`` (s: string) =
    let s = if isNull s then "" else s
    let alphaOnly = s |> String.filter System.Char.IsLetter
    if alphaOnly.Length > 0 then
        decode (encode alphaOnly) = alphaOnly
    else
        true

[<Property>]
let ``Base64エンコード/デコードは可逆`` (s: string) =
    let s = if isNull s then "" else s
    try
        base64Decode (base64Encode s) = s
    with _ -> true
```

## 6. 代数的性質

### モノイドの法則

```fsharp
/// モノイドインターフェース
type IMonoid<'T> =
    abstract member Empty: 'T
    abstract member Combine: 'T -> 'T -> 'T

/// 整数加算モノイド
let intAdditionMonoid =
    { new IMonoid<int> with
        member _.Empty = 0
        member _.Combine x y = x + y }

// 結合律のテスト
[<Property>]
let ``整数加算モノイドの結合律`` (a: int) (b: int) (c: int) =
    let m = intAdditionMonoid
    m.Combine (m.Combine a b) c = m.Combine a (m.Combine b c)

// 単位元のテスト
[<Property>]
let ``整数加算モノイドの単位元`` (a: int) =
    let m = intAdditionMonoid
    m.Combine a m.Empty = a && m.Combine m.Empty a = a
```

### 算術演算の性質

```fsharp
[<Property>]
let ``加算の結合律`` (a: int) (b: int) (c: int) =
    let a, b, c = a % 1000, b % 1000, c % 1000
    (a + b) + c = a + (b + c)

[<Property>]
let ``加算の交換律`` (a: int) (b: int) =
    a + b = b + a

[<Property>]
let ``加算の単位元`` (a: int) =
    a + 0 = a && 0 + a = a
```

## 7. コレクション操作のプロパティ

### filter の性質

```fsharp
[<Property>]
let ``filterは長さを減らすか維持する`` (list: int list) =
    (filter (fun x -> x > 0) list).Length <= list.Length

[<Property>]
let ``filter(常にtrue)は元のリストと同じ`` (list: int list) =
    filter (fun _ -> true) list = list

[<Property>]
let ``filter(常にfalse)は空リスト`` (list: int list) =
    filter (fun _ -> false) list = []
```

### map の性質

```fsharp
[<Property>]
let ``mapは長さを保存する`` (list: int list) =
    (map (fun x -> x * 2) list).Length = list.Length

[<Property>]
let ``map(identity)は元のリストと同じ`` (list: int list) =
    map id list = list
```

### concat の性質

```fsharp
[<Property>]
let ``concatの結合律`` (a: int list) (b: int list) (c: int list) =
    concat (concat a b) c = concat a (concat b c)

[<Property>]
let ``concatの長さは入力の長さの合計`` (a: int list) (b: int list) =
    (concat a b).Length = a.Length + b.Length
```

## 8. ビジネスロジックのプロパティ

```fsharp
[<Property>]
let ``最終価格は元の価格以下`` (price: PositiveInt) (membership: Membership) =
    let total = decimal price.Get
    let finalPrice = calculateFinalPrice total membership
    finalPrice <= total

[<Property>]
let ``Platinumは最大の割引を受ける`` (price: PositiveInt) =
    let total = decimal price.Get
    let platinumPrice = calculateFinalPrice total Platinum
    let bronzePrice = calculateFinalPrice total Bronze
    platinumPrice <= bronzePrice

[<Property>]
let ``割引率の順序: Platinum < Gold < Silver < Bronze`` (price: PositiveInt) =
    let total = decimal price.Get
    let prices = [Platinum; Gold; Silver; Bronze] |> List.map (calculateFinalPrice total)
    prices |> List.pairwise |> List.forall (fun (a, b) -> a <= b)
```

## 9. バリデーションのプロパティ

ジェネレータで有効な入力を生成し、バリデーションを通過することを確認します。

```fsharp
let validEmailGen =
    gen {
        let! local = Gen.elements ["user"; "test"; "admin"; "info"]
        let! domain = Gen.elements ["example"; "test"; "company"]
        let! tld = Gen.elements ["com"; "org"; "net"; "io"; "jp"]
        return sprintf "%s@%s.%s" local domain tld
    }

[<Property>]
let ``生成された有効なメールアドレスはバリデーションを通過する`` () =
    Prop.forAll (Arb.fromGen validEmailGen) (fun email ->
        isValidEmail email)
```

## 10. オラクルテスト

既知の正しい実装（標準ライブラリなど）と比較します。

```fsharp
[<Property>]
let ``sortNumbersは標準ライブラリのsortと同じ結果`` (nums: int list) =
    sortNumbers nums = List.sort nums

[<Property>]
let ``reverseは標準ライブラリのrevと同じ結果`` (list: int list) =
    reverse list = List.rev list

[<Property>]
let ``filterは標準ライブラリのfilterと同じ結果`` (list: int list) =
    let pred x = x > 0
    filter pred list = List.filter pred list
```

## Clojure / Scala / F# 比較

| 概念 | Clojure (test.check) | Scala (ScalaCheck) | F# (FsCheck) |
|------|---------------------|-------------------|--------------|
| ジェネレータ | `gen/string`, `gen/int` | `Gen.alphaStr`, `Gen.posNum[Int]` | 型推論 / `Gen` |
| 範囲指定 | `gen/choose` | `Gen.chooseNum` | `Gen.choose` |
| 列挙 | `gen/elements` | `Gen.oneOf` | `Gen.elements` |
| コレクション | `gen/vector`, `gen/list` | `Gen.listOf` | 型推論 |
| 変換 | `gen/fmap` | `Gen.map` | Computation expression |
| フィルタ | `gen/such-that` | `Gen.suchThat` | `Gen.filter` |
| プロパティ定義 | `prop/for-all` | `forAll` | `[<Property>]` |

## まとめ

本章では、プロパティベーステストについて学びました：

1. **ジェネレータ**: テストデータの自動生成
2. **プリミティブ**: 整数、文字列、ブール値など
3. **コレクション**: リスト、マップ、セット
4. **変換**: computation expression による加工
5. **プロパティ**: すべての入力で成り立つべき性質
6. **パターン**: 冪等性、対合性、不変量、ラウンドトリップ
7. **代数的性質**: モノイド則

プロパティベーステストは、従来のテストを補完し、より堅牢なソフトウェアを実現します。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part2/src/Library.fs`（PropertyBasedTesting モジュール）
- テストコード: `apps/fsharp/part2/tests/Tests.fs`

## 次章予告

次章では、**テスト駆動開発と関数型プログラミング**について学びます。Red-Green-Refactor サイクルを関数型スタイルで実践する方法を探ります。
