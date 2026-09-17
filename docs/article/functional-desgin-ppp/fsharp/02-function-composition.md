---
type: Article
title: "第2章: 関数合成と高階関数"
description: "関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第2章: 関数合成と高階関数

## はじめに

関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。本章では、F# における関数合成のテクニックと高階関数の活用方法を学びます。

## 1. 関数合成の基本 (`>>` と `<<`)

### `>>` と `<<` による関数の連結

F# では `>>` と `<<` 演算子を使用して関数を合成できます。

- **`>>`**: 左から右へ順番に適用（直感的、パイプラインと同じ方向）
- **`<<`**: 右から左へ順番に適用（数学的な関数合成）

```fsharp
/// 税金を追加する
let addTax rate amount = amount * (1.0 + rate)

/// 割引を適用する
let applyDiscountRate rate amount = amount * (1.0 - rate)

/// 円単位に丸める
let roundToYen (amount: float) = int64 (System.Math.Round(amount))

/// >> による関数合成（左から右）
let calculateFinalPrice: float -> int64 =
    applyDiscountRate 0.2 >> addTax 0.1 >> roundToYen

/// << による関数合成（右から左、数学的）
let calculateFinalPriceCompose: float -> int64 =
    roundToYen << addTax 0.1 << applyDiscountRate 0.2

// 使用例
calculateFinalPrice 1000.0
// => 880L
// 処理順序: 1000 → 20%割引(800) → 10%税込(880) → 丸め(880)
```

### パイプライン演算子との関係

```fsharp
// パイプライン演算子 |> を使った同等の処理
let result =
    1000.0
    |> applyDiscountRate 0.2
    |> addTax 0.1
    |> roundToYen
// => 880L
```

### 関数合成の利点

1. **宣言的な記述**: 処理の流れを関数のチェーンとして表現
2. **再利用性**: 合成した関数を別の場所で再利用可能
3. **テスト容易性**: 各関数を個別にテスト可能

## 2. カリー化と部分適用

### F# のカリー化

F# では全ての関数がデフォルトでカリー化されています。複数の引数を持つ関数は、実際には「1つの引数を取り、次の関数を返す関数」のチェーンです。

```fsharp
/// カリー化された挨拶関数
let greet greeting name = sprintf "%s, %s!" greeting name

/// 部分適用で特化した関数を作成
let sayHello = greet "Hello"
let sayGoodbye = greet "Goodbye"

sayHello "田中"    // => "Hello, 田中!"
sayGoodbye "鈴木"  // => "Goodbye, 鈴木!"
```

### 複数引数の部分適用

```fsharp
/// Emailレコード
type Email =
    { From: string
      To: string
      Subject: string
      Body: string }

/// カリー化されたメール送信関数
let sendEmail from' to' subject body =
    { From = from'
      To = to'
      Subject = subject
      Body = body }

/// 部分適用でシステムメール送信関数を作成
let sendFromSystem = sendEmail "system@example.com"
let sendNotification = sendFromSystem "user@example.com" "通知"

sendNotification "メッセージ本文"
// => { From = "system@example.com"
//      To = "user@example.com"
//      Subject = "通知"
//      Body = "メッセージ本文" }
```

## 3. 複数の関数を並列適用 (juxt 相当)

Clojure の `juxt` に相当する機能は、F# ではタプルを使って表現します。

```fsharp
/// 数値リストの統計情報を取得する
let getStats (numbers: int list) =
    (List.head numbers,
     List.last numbers,
     List.length numbers,
     List.min numbers,
     List.max numbers)

getStats [3; 1; 4; 1; 5; 9; 2; 6]
// => (3, 6, 8, 1, 9)
// (最初の値, 最後の値, 要素数, 最小値, 最大値)
```

### juxt 的な高階関数

```fsharp
/// juxt的な関数を作成する高階関数
let juxt2 f1 f2 x = (f1 x, f2 x)
let juxt3 f1 f2 f3 x = (f1 x, f2 x, f3 x)

// 使用例
juxt2 (fun x -> x * 2) (fun x -> x + 10) 5
// => (10, 15)

juxt3 List.head List.last List.length [1; 2; 3]
// => (1, 3, 3)
```

### 実用例：データ分析

```fsharp
/// 人物分析結果
type PersonAnalysis =
    { Name: string
      Age: int
      Category: string }

/// 人物情報を分析する
let analyzePerson (person: Map<string, obj>) =
    let name = person.["name"] :?> string
    let age = person.["age"] :?> int
    let category = if age >= 18 then "adult" else "minor"
    { Name = name; Age = age; Category = category }

analyzePerson (Map.ofList ["name", box "田中"; "age", box 25])
// => { Name = "田中"; Age = 25; Category = "adult" }
```

## 4. 高階関数によるデータ処理

高階関数とは、関数を引数として受け取るか、関数を返す関数のことです。

### 処理の記録

```fsharp
/// 処理結果を記録する
type ProcessLog<'a, 'b> = { Input: 'a; Output: 'b }

let processWithLog (f: 'a -> 'b) (input: 'a) : ProcessLog<'a, 'b> =
    let output = f input
    { Input = input; Output = output }

let log = processWithLog (fun x -> x * 2) 5
// => { Input = 5; Output = 10 }
```

### リトライ機能の追加

```fsharp
/// リトライ機能を追加する高階関数
let retry maxRetries (f: 'a -> 'b) (input: 'a) : 'b =
    let rec attempt attempts =
        try
            f input
        with
        | ex ->
            if attempts < maxRetries then
                attempt (attempts + 1)
            else
                reraise ()
    attempt 0

// 不安定なAPI呼び出しをリトライ付きでラップ
let fetchWithRetry = retry 3 fetchData
```

### メモ化

```fsharp
/// 基本的なメモ化
let memoize (f: 'a -> 'b) : 'a -> 'b =
    let cache = System.Collections.Generic.Dictionary<'a, 'b>()
    fun input ->
        match cache.TryGetValue(input) with
        | true, value -> value
        | false, _ ->
            let result = f input
            cache.[input] <- result
            result

// 使用例
let mutable callCount = 0
let expensiveFunction x =
    callCount <- callCount + 1
    x * 2

let memoized = memoize expensiveFunction
memoized 5  // 計算実行
memoized 5  // キャッシュから取得
memoized 5  // キャッシュから取得
// callCount = 1 (1回だけ計算)
```

## 5. パイプライン処理

複数の関数を順次適用するパイプラインを構築します。

```fsharp
/// 関数のリストを順次適用するパイプラインを作成する
let pipeline (fns: ('a -> 'a) list) (input: 'a) : 'a =
    List.fold (fun acc f -> f acc) input fns
```

### 注文処理パイプラインの実装

```fsharp
/// 注文アイテム
type OrderItem = { Price: int; Quantity: int }

/// 顧客
type PipelineCustomer = { Membership: string }

/// 注文
type PipelineOrder =
    { Items: OrderItem list
      Customer: PipelineCustomer
      Total: float
      Shipping: int }

/// 注文を検証する
let validateOrder (order: PipelineOrder) =
    if List.isEmpty order.Items then
        failwith "注文にアイテムがありません"
    else
        order

/// 注文合計を計算する
let calculateOrderTotal (order: PipelineOrder) =
    let total =
        order.Items
        |> List.map (fun item -> item.Price * item.Quantity)
        |> List.sum
        |> float
    { order with Total = total }

/// 注文割引を適用する
let applyOrderDiscount (order: PipelineOrder) =
    let discountRates = Map.ofList ["gold", 0.1; "silver", 0.05; "bronze", 0.02]
    let discountRate =
        discountRates
        |> Map.tryFind order.Customer.Membership
        |> Option.defaultValue 0.0
    { order with Total = order.Total * (1.0 - discountRate) }

/// 送料を追加する
let addShipping (order: PipelineOrder) =
    let shipping = if order.Total >= 5000.0 then 0 else 500
    { order with
        Shipping = shipping
        Total = order.Total + float shipping }

/// 注文処理パイプライン
let processOrderPipeline =
    pipeline [validateOrder; calculateOrderTotal; applyOrderDiscount; addShipping]

// 使用例
processOrderPipeline
    { Items = [{Price = 1000; Quantity = 2}; {Price = 500; Quantity = 3}]
      Customer = { Membership = "gold" }
      Total = 0.0
      Shipping = 0 }
// => { Items = [...]; Customer = ...; Total = 3650.0; Shipping = 500 }
```

## 6. 関数合成によるバリデーション

バリデーションロジックを関数合成で表現します。

```fsharp
/// バリデーション結果
type ValidationResult<'a> =
    { Valid: bool
      Value: 'a
      Error: string option }

/// バリデータを作成する高階関数
let validator (pred: 'a -> bool) (errorMsg: string) (value: 'a) : ValidationResult<'a> =
    if pred value then
        { Valid = true; Value = value; Error = None }
    else
        { Valid = false; Value = value; Error = Some errorMsg }

/// 複数のバリデータを合成する
let combineValidators (validators: ('a -> ValidationResult<'a>) list) (value: 'a) =
    let initial = { Valid = true; Value = value; Error = None }
    validators
    |> List.fold (fun result v ->
        if result.Valid then v result.Value
        else result) initial

// 個別のバリデータ
let validatePositive = validator (fun x -> x > 0) "値は正の数である必要があります"
let validateUnder100 = validator (fun x -> x < 100) "値は100未満である必要があります"

// バリデータの合成
let validateQuantity = combineValidators [validatePositive; validateUnder100]

// 使用例
validateQuantity 50   // => { Valid = true; Value = 50; Error = None }
validateQuantity -1   // => { Valid = false; Value = -1; Error = Some "値は正の数である必要があります" }
validateQuantity 100  // => { Valid = false; Value = 100; Error = Some "値は100未満である必要があります" }
```

## 7. 関数の変換

関数自体を変換するユーティリティ関数を作成します。

### 引数の順序を反転

```fsharp
/// 引数の順序を反転する
let flip f a b = f b a

let subtract a b = a - b
flip subtract 3 5  // => 2  (5 - 3)
```

### カリー化と非カリー化

```fsharp
/// 2引数関数をカリー化する
let curry f a b = f (a, b)

/// カリー化された関数を元に戻す
let uncurry f (a, b) = f a b

let addTuple (a, b) = a + b
let curriedAdd = curry addTuple
curriedAdd 5 3  // => 8

let addCurried a b = a + b
let uncurriedAdd = uncurry addCurried
uncurriedAdd (5, 3)  // => 8
```

### 補関数（complement）

```fsharp
/// 補関数（述語を反転）
let complement pred x = not (pred x)

let isEven x = x % 2 = 0
let isOdd = complement isEven
isOdd 3  // => true
isOdd 4  // => false
```

## 8. 関数合成のパターン

### 述語の合成

```fsharp
/// 複数の述語を AND で合成する
let composePredicates (preds: ('a -> bool) list) (x: 'a) : bool =
    preds |> List.forall (fun pred -> pred x)

/// 複数の述語を OR で合成する
let composePredicatesOr (preds: ('a -> bool) list) (x: 'a) : bool =
    preds |> List.exists (fun pred -> pred x)

// 有効な年齢チェック
let validAge =
    composePredicates [(fun x -> x > 0); (fun x -> x <= 150)]

validAge 25   // => true
validAge -1   // => false
validAge 200  // => false

// プレミアム顧客チェック
type CustomerInfo =
    { Membership: string
      PurchaseCount: int
      TotalSpent: int }

let premiumCustomer =
    composePredicatesOr [
        (fun c -> c.Membership = "gold")
        (fun c -> c.PurchaseCount >= 100)
        (fun c -> c.TotalSpent >= 100000)
    ]

premiumCustomer { Membership = "gold"; PurchaseCount = 0; TotalSpent = 0 }
// => true
premiumCustomer { Membership = "bronze"; PurchaseCount = 100; TotalSpent = 0 }
// => true
premiumCustomer { Membership = "bronze"; PurchaseCount = 10; TotalSpent = 1000 }
// => false
```

## Clojure / Scala / F# 比較

| 概念 | Clojure | Scala | F# |
|------|---------|-------|-----|
| 関数合成（左から右） | `->>`（スレッディング） | `andThen` | `>>` |
| 関数合成（右から左） | `comp` | `compose` | `<<` |
| 部分適用 | `partial` | カリー化構文 | 自動カリー化 |
| 並列適用 | `juxt` | タプル | タプル |
| 述語合成 AND | `every-pred` | `forall` | `List.forall` |
| 述語合成 OR | `some-fn` | `exists` | `List.exists` |

## まとめ

本章では、関数合成と高階関数について学びました：

1. **`>>` / `<<`**: 関数を合成して新しい関数を作成
2. **カリー化**: F# では全関数が自動的にカリー化
3. **タプル**: 複数の関数を並列適用して結果を取得
4. **高階関数**: ログ、リトライ、メモ化などの横断的関心事を抽象化
5. **パイプライン**: 処理の流れを関数のチェーンとして表現
6. **バリデーション**: 関数合成による柔軟な検証ロジック
7. **述語合成**: AND/OR で複数の条件を組み合わせ

これらのテクニックにより、小さく再利用可能な関数から複雑なビジネスロジックを構築できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part1/src/Library.fs`（Composition モジュール）
- テストコード: `apps/fsharp/part1/tests/Tests.fs`

## 次章予告

次章では、**多態性とディスパッチ**について学びます。判別共用体、アクティブパターン、インターフェースを活用した柔軟な設計パターンを探ります。
