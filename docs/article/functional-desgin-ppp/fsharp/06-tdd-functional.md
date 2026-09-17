---
type: Article
title: "第6章: テスト駆動開発と関数型プログラミング"
description: "テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第6章: テスト駆動開発と関数型プログラミング

## はじめに

テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。関数型プログラミングと TDD は相性が良く、純粋関数はテストが容易で、不変データ構造は予測可能な動作を保証します。

本章では、Red-Green-Refactor サイクルを関数型スタイルで実践する方法を学びます。

## 1. TDD の基本サイクル

### Red-Green-Refactor

```
┌─────────────────────────────────────────────────┐
│                                                 │
│    ┌───────┐     ┌───────┐     ┌───────────┐   │
│    │  Red  │ ──► │ Green │ ──► │ Refactor  │   │
│    └───────┘     └───────┘     └───────────┘   │
│        ▲                              │         │
│        └──────────────────────────────┘         │
│                                                 │
└─────────────────────────────────────────────────┘
```

1. **Red（赤）**: 失敗するテストを書く
2. **Green（緑）**: テストを通す最小限のコードを書く
3. **Refactor（リファクタリング）**: コードを改善する（テストは通ったまま）

## 2. FizzBuzz - TDD の典型例

### Step 1: Red（最初のテスト）

```fsharp
[<Fact>]
let ``fizzbuzz 1は"1"を返す`` () =
    Assert.Equal("1", fizzbuzz 1)
```

### Step 2: Green（最小限の実装）

```fsharp
let fizzbuzz n = "1"
```

### Step 3: 次のテストを追加して段階的に実装を発展

```fsharp
// テストを追加
[<Fact>]
let ``fizzbuzz 2は"2"を返す`` () =
    Assert.Equal("2", fizzbuzz 2)

[<Fact>]
let ``fizzbuzz 3は"Fizz"を返す`` () =
    Assert.Equal("Fizz", fizzbuzz 3)

[<Fact>]
let ``fizzbuzz 5は"Buzz"を返す`` () =
    Assert.Equal("Buzz", fizzbuzz 5)

[<Fact>]
let ``fizzbuzz 15は"FizzBuzz"を返す`` () =
    Assert.Equal("FizzBuzz", fizzbuzz 15)
```

### 最終実装（小さなヘルパー関数に分割）

```fsharp
/// 3で割り切れるかどうか
let isFizz n = n % 3 = 0

/// 5で割り切れるかどうか
let isBuzz n = n % 5 = 0

/// 15で割り切れるかどうか（FizzBuzz）
let isFizzBuzz n = isFizz n && isBuzz n

/// FizzBuzz変換
let fizzbuzz n =
    if isFizzBuzz n then "FizzBuzz"
    elif isFizz n then "Fizz"
    elif isBuzz n then "Buzz"
    else string n

/// 1からnまでのFizzBuzz列を生成
let fizzbuzzSequence n =
    [ 1 .. n ] |> List.map fizzbuzz
```

### プロパティベーステストと組み合わせ

```fsharp
[<Property>]
let ``3の倍数は常にFizzを含む`` (n: FsCheck.PositiveInt) =
    let num = n.Get * 3
    let result = fizzbuzz num
    result.Contains("Fizz")

[<Property>]
let ``5の倍数は常にBuzzを含む`` (n: FsCheck.PositiveInt) =
    let num = n.Get * 5
    let result = fizzbuzz num
    result.Contains("Buzz")
```

## 3. ローマ数字変換

### テストから始める

```fsharp
[<Fact>]
let ``toRoman 1はIを返す`` () =
    Assert.Equal("I", toRoman 1)

[<Fact>]
let ``toRoman 4はIVを返す`` () =
    Assert.Equal("IV", toRoman 4)

[<Fact>]
let ``toRoman 1994はMCMXCIVを返す`` () =
    Assert.Equal("MCMXCIV", toRoman 1994)

[<Fact>]
let ``toRomanとfromRomanは逆関数`` () =
    for n in 1..100 do
        Assert.Equal(n, fromRoman (toRoman n))
```

### データ駆動の実装

```fsharp
/// ローマ数字の対応表（大きい順）
let private romanNumerals =
    [ (1000, "M"); (900, "CM"); (500, "D"); (400, "CD")
      (100, "C");  (90, "XC");  (50, "L");  (40, "XL")
      (10, "X");   (9, "IX");   (5, "V");   (4, "IV")
      (1, "I") ]

/// 整数をローマ数字に変換
let toRoman n =
    if n <= 0 || n > 3999 then
        invalidArg "n" "n must be between 1 and 3999"

    let rec loop remaining result =
        if remaining = 0 then
            result
        else
            let (value, numeral) = romanNumerals |> List.find (fun (v, _) -> v <= remaining)
            loop (remaining - value) (result + numeral)

    loop n ""

/// ローマ数字から整数へ変換（逆変換）
let private romanValues =
    Map.ofList [ ('I', 1); ('V', 5); ('X', 10); ('L', 50)
                 ('C', 100); ('D', 500); ('M', 1000) ]

let fromRoman (roman: string) =
    let values = roman |> Seq.map (fun c -> Map.find c romanValues) |> Seq.toList

    let rec loop vals acc =
        match vals with
        | [] -> acc
        | [ x ] -> acc + x
        | x :: y :: rest when x < y -> loop (y :: rest) (acc - x)
        | x :: rest -> loop rest (acc + x)

    loop values 0
```

## 4. ボウリングスコア計算

### 複雑なビジネスロジックの TDD

```fsharp
[<Fact>]
let ``ガタースコアは0`` () =
    Assert.Equal(0, bowlingScore (List.replicate 20 0))

[<Fact>]
let ``すべて1ピンは20点`` () =
    Assert.Equal(20, bowlingScore (List.replicate 20 1))

[<Fact>]
let ``スペアの後の投球はボーナス`` () =
    let rolls = [ 5; 5; 3; 0 ] @ List.replicate 16 0
    Assert.Equal(16, bowlingScore rolls)

[<Fact>]
let ``ストライクの後の2投はボーナス`` () =
    let rolls = [ 10; 3; 4 ] @ List.replicate 16 0
    Assert.Equal(24, bowlingScore rolls)

[<Fact>]
let ``パーフェクトゲームは300点`` () =
    Assert.Equal(300, bowlingScore (List.replicate 12 10))
```

### 小さな関数に分割

```fsharp
/// ストライクかどうか
let isStrike rolls =
    match rolls with
    | x :: _ -> x = 10
    | [] -> false

/// スペアかどうか
let isSpare rolls =
    match rolls with
    | x :: y :: _ -> x + y = 10 && x <> 10
    | _ -> false

/// ストライクボーナス
let strikeBonus remaining =
    remaining |> List.truncate 2 |> List.sum

/// スペアボーナス
let spareBonus remaining =
    remaining |> List.tryHead |> Option.defaultValue 0

/// ボウリングスコアを計算
let bowlingScore rolls =
    let rec loop remainingRolls frame total =
        if frame > 10 || List.isEmpty remainingRolls then
            total
        elif isStrike remainingRolls then
            loop (List.tail remainingRolls) (frame + 1) (total + 10 + strikeBonus (List.tail remainingRolls))
        elif isSpare remainingRolls then
            loop (remainingRolls |> List.skip 2) (frame + 1) (total + 10 + spareBonus (remainingRolls |> List.skip 2))
        else
            let frameScore = remainingRolls |> List.truncate 2 |> List.sum
            loop (remainingRolls |> List.skip 2) (frame + 1) (total + frameScore)

    loop rolls 1 0
```

## 5. 素数 - シンプルな関数の TDD

### テストから設計を導く

```fsharp
[<Fact>]
let ``0は素数ではない`` () =
    Assert.False(isPrime 0)

[<Fact>]
let ``2は素数`` () =
    Assert.True(isPrime 2)

[<Fact>]
let ``primesUpTo 20は正しい素数リストを返す`` () =
    let expected = [ 2; 3; 5; 7; 11; 13; 17; 19 ]
    Assert.Equal<int list>(expected, primesUpTo 20)

[<Fact>]
let ``primeFactors 24は2,2,2,3を返す`` () =
    let expected = [ 2; 2; 2; 3 ]
    Assert.Equal<int list>(expected, primeFactors 24)

[<Fact>]
let ``primeFactorsの積は元の数に等しい`` () =
    for n in 2..100 do
        let factors = primeFactors n
        let product = factors |> List.fold (*) 1
        Assert.Equal(n, product)
```

### 実装

```fsharp
/// 素数判定
let isPrime n =
    if n < 2 then false
    elif n = 2 then true
    elif n % 2 = 0 then false
    else
        let sqrtN = int (sqrt (float n))
        seq { 3 .. 2 .. sqrtN }
        |> Seq.exists (fun i -> n % i = 0)
        |> not

/// n以下の素数をすべて返す
let primesUpTo n =
    [ 2 .. n ] |> List.filter isPrime

/// 素因数分解
let primeFactors n =
    let rec loop remaining factor factors =
        if remaining = 1 then
            List.rev factors
        elif remaining % factor = 0 then
            loop (remaining / factor) factor (factor :: factors)
        else
            loop remaining (factor + 1) factors

    loop n 2 []
```

## 6. 不変データ構造 - スタックとキュー

### 不変スタック

```fsharp
type Stack<'T> =
    private { Items: 'T list }

    member this.IsEmpty = List.isEmpty this.Items
    member this.Size = List.length this.Items

    member this.Push(item: 'T) = { Items = item :: this.Items }

    member this.Pop() =
        match this.Items with
        | head :: tail -> Some(head, { Items = tail })
        | [] -> None

    member this.Peek() = List.tryHead this.Items

module Stack =
    let empty<'T> : Stack<'T> = { Items = [] }
    let push item (stack: Stack<'T>) = stack.Push(item)
    let pop (stack: Stack<'T>) = stack.Pop()
    let peek (stack: Stack<'T>) = stack.Peek()
    let isEmpty (stack: Stack<'T>) = stack.IsEmpty
    let size (stack: Stack<'T>) = stack.Size
```

### テスト

```fsharp
[<Fact>]
let ``LIFO順序で動作する`` () =
    let stack =
        Stack.empty
        |> Stack.push "a"
        |> Stack.push "b"
        |> Stack.push "c"

    match Stack.pop stack with
    | Some (v1, s1) ->
        Assert.Equal("c", v1)
        match Stack.pop s1 with
        | Some (v2, s2) ->
            Assert.Equal("b", v2)
            match Stack.pop s2 with
            | Some (v3, s3) ->
                Assert.Equal("a", v3)
                Assert.True(Stack.isEmpty s3)
            | None -> Assert.Fail("Expected value")
        | None -> Assert.Fail("Expected value")
    | None -> Assert.Fail("Expected value")
```

### 不変キュー（2つのリストで実装）

```fsharp
type Queue<'T> =
    private { Front: 'T list; Back: 'T list }

    member this.IsEmpty = List.isEmpty this.Front && List.isEmpty this.Back

    member this.Enqueue(item: 'T) = { Front = this.Front; Back = item :: this.Back }

    member this.Dequeue() =
        match this.Front with
        | head :: tail -> Some(head, { Front = tail; Back = this.Back })
        | [] ->
            match List.rev this.Back with
            | head :: tail -> Some(head, { Front = tail; Back = [] })
            | [] -> None

module Queue =
    let empty<'T> : Queue<'T> = { Front = []; Back = [] }
    let enqueue item (queue: Queue<'T>) = queue.Enqueue(item)
    let dequeue (queue: Queue<'T>) = queue.Dequeue()
    let isEmpty (queue: Queue<'T>) = queue.IsEmpty
```

## 7. 文字列電卓 - 段階的な要件追加

### テスト

```fsharp
[<Fact>]
let ``空文字列は0を返す`` () =
    Assert.Equal(0, StringCalculator.add "")

[<Fact>]
let ``単一の数値はその値を返す`` () =
    Assert.Equal(5, StringCalculator.add "5")

[<Fact>]
let ``カンマ区切りの数値を合計する`` () =
    Assert.Equal(6, StringCalculator.add "1,2,3")

[<Fact>]
let ``改行区切りも処理する`` () =
    Assert.Equal(6, StringCalculator.add "1\n2,3")

[<Fact>]
let ``カスタム区切り文字を使用できる`` () =
    Assert.Equal(3, StringCalculator.add "//;\n1;2")

[<Fact>]
let ``負の数は例外をスローする`` () =
    let ex = Assert.Throws<System.ArgumentException>(fun () ->
        StringCalculator.add "1,-2,3" |> ignore)
    Assert.Contains("-2", ex.Message)

[<Fact>]
let ``1000より大きい数は無視する`` () =
    Assert.Equal(2, StringCalculator.add "2,1001")
```

### 実装

```fsharp
module StringCalculator =
    /// 区切り文字と数値文字列をパース
    let private parseInput (input: string) =
        if input.StartsWith("//") then
            let delimiterEnd = input.IndexOf('\n')
            let delimiter = input.Substring(2, delimiterEnd - 2)
            let numbers = input.Substring(delimiterEnd + 1)
            (delimiter, numbers)
        else
            (",|\n", input)

    /// 数値をパース
    let private parseNumbers (numbers: string) (delimiter: string) =
        let regex = System.Text.RegularExpressions.Regex(delimiter)
        regex.Split(numbers)
        |> Array.filter (fun s -> not (System.String.IsNullOrEmpty(s)))
        |> Array.map int
        |> Array.toList

    /// 負の数をバリデーション
    let private validateNumbers (nums: int list) =
        let negatives = nums |> List.filter (fun n -> n < 0)
        if not (List.isEmpty negatives) then
            let negStr = negatives |> List.map string |> String.concat ", "
            invalidArg "input" (sprintf "negatives not allowed: %s" negStr)

    /// 文字列電卓
    let add (input: string) =
        if System.String.IsNullOrEmpty(input) then
            0
        else
            let (delimiter, numbers) = parseInput input
            let nums = parseNumbers numbers delimiter
            validateNumbers nums
            nums |> List.filter (fun n -> n <= 1000) |> List.sum
```

## 8. 純粋関数とテスト容易性

### 純粋関数の利点

```fsharp
type Item = { Name: string; Price: decimal }

type TaxCalculation =
    { Subtotal: decimal
      Tax: decimal
      Total: decimal }

module TaxCalculator =
    /// 税額を計算
    let calculateTax (amount: decimal) (rate: decimal) = amount * rate

    /// 税込み総額を計算
    let calculateTotalWithTax (items: Item list) (taxRate: decimal) =
        let subtotal = items |> List.sumBy (fun i -> i.Price)
        let tax = calculateTax subtotal taxRate
        { Subtotal = subtotal
          Tax = tax
          Total = subtotal + tax }
```

### テスト

```fsharp
[<Fact>]
let ``calculateTotalWithTaxは税込み総額を計算する`` () =
    let items =
        [ { Name = "商品A"; Price = 1000m }
          { Name = "商品B"; Price = 2000m } ]
    let result = TaxCalculator.calculateTotalWithTax items 0.1m

    Assert.Equal(3000m, result.Subtotal)
    Assert.Equal(300m, result.Tax)
    Assert.Equal(3300m, result.Total)
```

## 9. リファクタリングパターン - データ駆動の実装

### Before: 複雑な条件分岐

```fsharp
let calculateShippingBefore total weight region =
    if total >= 10000m then 0
    else
        match region with
        | Local when weight < 5.0 -> 300
        | Local -> 500
        | Domestic when weight < 5.0 -> 500
        | Domestic -> 800
        | International when weight < 5.0 -> 2000
        | International -> 3000
```

### After: データ駆動の実装

```fsharp
type Region =
    | Local
    | Domestic
    | International

type ShippingOrder =
    { Total: decimal
      Weight: float
      Region: Region }

module ShippingCalculator =
    let isFreeShipping (total: decimal) = total >= 10000m

    let private shippingRates =
        Map.ofList
            [ (Local, Map.ofList [ (true, 300); (false, 500) ])
              (Domestic, Map.ofList [ (true, 500); (false, 800) ])
              (International, Map.ofList [ (true, 2000); (false, 3000) ]) ]

    let calculateShipping (order: ShippingOrder) =
        if isFreeShipping order.Total then
            0
        else
            let isLight = order.Weight < 5.0
            shippingRates
            |> Map.tryFind order.Region
            |> Option.bind (Map.tryFind isLight)
            |> Option.defaultValue 500
```

## 10. パスワードバリデーター - ルールの合成

```fsharp
module PasswordValidator =
    type Rule = string -> string option

    let minLength (min: int) : Rule =
        fun password ->
            if password.Length >= min then None
            else Some (sprintf "Password must be at least %d characters" min)

    let hasUppercase: Rule =
        fun password ->
            if password |> Seq.exists System.Char.IsUpper then None
            else Some "Password must contain at least one uppercase letter"

    let hasLowercase: Rule =
        fun password ->
            if password |> Seq.exists System.Char.IsLower then None
            else Some "Password must contain at least one lowercase letter"

    let hasDigit: Rule =
        fun password ->
            if password |> Seq.exists System.Char.IsDigit then None
            else Some "Password must contain at least one digit"

    let defaultRules: Rule list =
        [ minLength 8; hasUppercase; hasLowercase; hasDigit ]

    let validate (password: string) (rules: Rule list) : Result<string, string list> =
        let errors = rules |> List.choose (fun rule -> rule password)
        if List.isEmpty errors then Ok password
        else Error errors

    let validateWithDefaults (password: string) : Result<string, string list> =
        validate password defaultRules
```

## TDD のベストプラクティス

### 1. 小さなステップで進む

- 一度に1つのテストだけを追加
- テストが通ったら次のテストへ

### 2. テスト名は仕様として読める

```fsharp
[<Fact>]
let ``10000円以上は送料無料`` () = ...

[<Fact>]
let ``負の数は例外をスローする`` () = ...

[<Fact>]
let ``パーフェクトゲームは300点`` () = ...
```

### 3. 純粋関数を優先

- 副作用を持つ関数は最小限に
- 副作用は境界に追い出す

### 4. エッジケースをテスト

```fsharp
[<Fact>]
let ``空文字列は0を返す`` () = ...

[<Fact>]
let ``空のリストは空のリストを返す`` () = ...

[<Fact>]
let ``境界値で正しく動作する`` () = ...
```

## Clojure/Scala との比較

| 概念 | Clojure | Scala | F# |
|------|---------|-------|-----|
| テストフレームワーク | speclj, clojure.test | ScalaTest | xUnit, FsCheck |
| テスト構文 | `(it "..." (should= ...))` | `test("...") { ... shouldBe ... }` | `[<Fact>] let ``...`` () = Assert.Equal(...)` |
| 例外テスト | `(should-throw ...)` | `intercept[...] { ... }` | `Assert.Throws<...>(...)` |
| データ構造 | 永続化データ構造（デフォルト） | `case class` + `copy` | レコード型 + `with` |
| ループ | `loop/recur` | `@annotation.tailrec` | `let rec` |
| パターンマッチ | `cond`, `case` | `match` | `match` |
| 型システム | 動的型付け | 静的型付け | 静的型付け + 型推論 |

## F# 特有のパターン

### バッククォートテスト名

F# では日本語やスペースを含むテスト名が書ける：

```fsharp
[<Fact>]
let ``fizzbuzz 15は"FizzBuzz"を返す`` () = ...
```

### Option 型による安全なアクセス

```fsharp
member this.Pop() =
    match this.Items with
    | head :: tail -> Some(head, { Items = tail })
    | [] -> None
```

### パイプライン演算子との親和性

```fsharp
let fizzbuzzSequence n =
    [ 1 .. n ] |> List.map fizzbuzz
```

## まとめ

本章では、TDD と関数型プログラミングについて学びました：

1. **Red-Green-Refactor**: 基本サイクル
2. **FizzBuzz**: 典型的な TDD 例
3. **ローマ数字**: データ駆動の実装
4. **ボウリング**: 複雑なビジネスロジック
5. **素数**: シンプルな関数設計
6. **スタック/キュー**: 不変データ構造
7. **文字列電卓**: 段階的な要件追加
8. **純粋関数**: テスト容易性
9. **リファクタリング**: 条件分岐の整理
10. **パスワードバリデーター**: ルールの合成

関数型プログラミングと TDD の組み合わせにより、信頼性の高いコードを効率的に開発できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part2/src/Library.fs`
- テストコード: `apps/fsharp/part2/tests/Tests.fs`

## 次章予告

次章から第3部「デザインパターン - 構造パターン」に入ります。Composite パターンを関数型スタイルで実装する方法を学びます。
