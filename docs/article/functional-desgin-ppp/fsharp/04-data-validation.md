---
type: Article
title: "第4章: データバリデーション"
description: "F# では、判別共用体、スマートコンストラクタ、Result 型を組み合わせることで、コンパイル時と実行時の両方でデータの整合性を保証できます。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第4章: データバリデーション

## はじめに

F# では、判別共用体、スマートコンストラクタ、`Result` 型を組み合わせることで、コンパイル時と実行時の両方でデータの整合性を保証できます。本章では、`Result`、`Validated` パターン、ドメインプリミティブを使ったデータバリデーションの実現方法を学びます。

## 1. 基本的なバリデーション（Result を使用）

### Result によるバリデーション

`Result<'T, 'E>` は、成功時に `Ok`、失敗時に `Error` を返す型です。バリデーションに最適です。

```fsharp
/// バリデーション結果の型エイリアス
type ValidationResult<'T> = Result<'T, string list>

/// 名前のバリデーション
let validateName (name: string) : ValidationResult<string> =
    if System.String.IsNullOrEmpty(name) then
        Error ["名前は空にできません"]
    elif name.Length > 100 then
        Error ["名前は100文字以内である必要があります"]
    else
        Ok name

/// 年齢のバリデーション
let validateAge (age: int) : ValidationResult<int> =
    if age < 0 then
        Error ["年齢は0以上である必要があります"]
    elif age > 150 then
        Error ["年齢は150以下である必要があります"]
    else
        Ok age

/// メールアドレスのバリデーション
let validateEmail (email: string) : ValidationResult<string> =
    let emailRegex = System.Text.RegularExpressions.Regex(@".+@.+\..+")
    if emailRegex.IsMatch(email) then Ok email
    else Error ["無効なメールアドレス形式です"]

// 使用例
validateName "田中太郎"  // => Ok "田中太郎"
validateName ""          // => Error ["名前は空にできません"]
validateAge 25           // => Ok 25
validateAge -1           // => Error ["年齢は0以上である必要があります"]
```

## 2. 列挙型とスマートコンストラクタ

### 判別共用体による列挙型

```fsharp
/// 会員種別
type Membership =
    | Bronze
    | Silver
    | Gold
    | Platinum

module Membership =
    let fromString (s: string) : ValidationResult<Membership> =
        match s.ToLower() with
        | "bronze" -> Ok Bronze
        | "silver" -> Ok Silver
        | "gold" -> Ok Gold
        | "platinum" -> Ok Platinum
        | _ -> Error [sprintf "無効な会員種別: %s" s]

    let toString = function
        | Bronze -> "bronze"
        | Silver -> "silver"
        | Gold -> "gold"
        | Platinum -> "platinum"

/// ステータス
type Status =
    | Active
    | Inactive
    | Suspended

// 使用例
Membership.fromString "gold"    // => Ok Gold
Membership.fromString "GOLD"    // => Ok Gold（大文字小文字を区別しない）
Membership.fromString "invalid" // => Error ["無効な会員種別: invalid"]
```

## 3. Validated パターン（エラー蓄積）

`Result` は最初のエラーで停止しますが、すべてのエラーを収集したい場合は `Validated` パターンを使用します。

```fsharp
/// Validated 型（エラー蓄積対応）
type Validated<'E, 'A> =
    | Valid of 'A
    | Invalid of 'E list

module Validated =
    let valid a : Validated<'E, 'A> = Valid a
    let invalid errors : Validated<'E, 'A> = Invalid errors

    let map (f: 'A -> 'B) (va: Validated<'E, 'A>) : Validated<'E, 'B> =
        match va with
        | Valid a -> Valid (f a)
        | Invalid errs -> Invalid errs

    let isValid = function
        | Valid _ -> true
        | Invalid _ -> false

    let toResult = function
        | Valid a -> Ok a
        | Invalid errs -> Error errs

    /// 2つの Validated を結合（エラー蓄積）
    let combine (f: 'A -> 'B -> 'C) (va: Validated<'E, 'A>) (vb: Validated<'E, 'B>) : Validated<'E, 'C> =
        match va, vb with
        | Valid a, Valid b -> Valid (f a b)
        | Invalid e1, Invalid e2 -> Invalid (e1 @ e2)  // エラーを蓄積！
        | Invalid e, _ -> Invalid e
        | _, Invalid e -> Invalid e
```

### 使用例

```fsharp
let result =
    Validated.combine (+)
        (Validated.invalid ["error1"])
        (Validated.invalid ["error2"])
// => Invalid ["error1"; "error2"]  // 両方のエラーが収集される
```

## 4. ドメインプリミティブ（バリデーション付き型）

プライベートなコンストラクタを持つ型で、不正な値の生成を防ぎます。

```fsharp
/// 商品ID
type ProductId = private ProductId of string

module ProductId =
    let private pattern = System.Text.RegularExpressions.Regex(@"^PROD-\d{5}$")

    let create (id: string) : Validated<string, ProductId> =
        if pattern.IsMatch(id) then Validated.valid (ProductId id)
        else Validated.invalid [sprintf "無効な商品ID形式: %s (PROD-XXXXXの形式が必要)" id]

    let value (ProductId id) = id
    let unsafe id = ProductId id  // テスト用

/// 価格（正の数）
type Price = private Price of decimal

module Price =
    let create (amount: decimal) : Validated<string, Price> =
        if amount <= 0m then Validated.invalid ["価格は正の数である必要があります"]
        else Validated.valid (Price amount)

    let value (Price p) = p
    let unsafe amount = Price amount

/// 数量（正の整数）
type Quantity = private Quantity of int

module Quantity =
    let create (qty: int) : Validated<string, Quantity> =
        if qty <= 0 then Validated.invalid ["数量は正の整数である必要があります"]
        else Validated.valid (Quantity qty)

    let value (Quantity q) = q
    let unsafe qty = Quantity qty

// 使用例
ProductId.create "PROD-00001"  // => Valid (ProductId "PROD-00001")
ProductId.create "INVALID"     // => Invalid ["無効な商品ID形式: INVALID..."]

Price.create 1000m  // => Valid (Price 1000m)
Price.create 0m     // => Invalid ["価格は正の数である必要があります"]
```

## 5. 商品モデル

### ドメインプリミティブを使用した商品型

```fsharp
/// 商品
type Product =
    { Id: ProductId
      Name: ProductName
      Price: Price
      Description: string option
      Category: string option }

module Product =
    let create
        (id: string)
        (name: string)
        (price: decimal)
        (description: string option)
        (category: string option)
        : Validated<string, Product> =
        Validated.combine3
            (fun pid pname pprice ->
                { Id = pid
                  Name = pname
                  Price = pprice
                  Description = description
                  Category = category })
            (ProductId.create id)
            (ProductName.create name)
            (Price.create price)

// 使用例
Product.create "PROD-00001" "テスト商品" 1000m None None
// => Valid { Id = ProductId "PROD-00001"; Name = ProductName "テスト商品"; Price = Price 1000m; ... }

Product.create "INVALID" "" -100m None None
// => Invalid ["無効な商品ID形式...", "商品名は空にできません", "価格は正の数である必要があります"]
// すべてのエラーが蓄積される！
```

## 6. 注文ドメインモデル

```fsharp
/// 注文ID
type OrderId = private OrderId of string

module OrderId =
    let private pattern = System.Text.RegularExpressions.Regex(@"^ORD-\d{8}$")
    let create (id: string) : Validated<string, OrderId> =
        if pattern.IsMatch(id) then Validated.valid (OrderId id)
        else Validated.invalid [sprintf "無効な注文ID形式: %s" id]

/// 注文アイテム
type OrderItem =
    { ProductId: ProductId
      Quantity: Quantity
      Price: Price }

module OrderItem =
    let create (productId: string) (quantity: int) (price: decimal) : Validated<string, OrderItem> =
        Validated.combine3
            (fun pid qty pr -> { ProductId = pid; Quantity = qty; Price = pr })
            (ProductId.create productId)
            (Quantity.create quantity)
            (Price.create price)

    let total (item: OrderItem) =
        Price.value item.Price * decimal (Quantity.value item.Quantity)

/// 注文
type Order =
    { OrderId: OrderId
      CustomerId: CustomerId
      Items: OrderItem list
      OrderDate: System.DateTime
      Total: decimal option
      Status: Status option }

module Order =
    let calculateTotal (order: Order) =
        order.Items |> List.sumBy OrderItem.total
```

## 7. 条件付きバリデーション（ADT）

データの種類に応じて異なるバリデーションルールを適用する場合は、判別共用体を使用します。

```fsharp
/// 通知（判別共用体による条件付きバリデーション）
type Notification =
    | EmailNotification of to': string * subject: string * body: string
    | SMSNotification of phoneNumber: string * body: string
    | PushNotification of deviceToken: string * body: string

module Notification =
    let private emailPattern = System.Text.RegularExpressions.Regex(@".+@.+\..+")
    let private phonePattern = System.Text.RegularExpressions.Regex(@"\d{2,4}-\d{2,4}-\d{4}")

    let createEmail (to': string) (subject: string) (body: string) : Validated<string, Notification> =
        let errors = [
            if not (emailPattern.IsMatch(to')) then "無効なメールアドレス形式です"
            if System.String.IsNullOrEmpty(subject) then "件名は空にできません"
            if System.String.IsNullOrEmpty(body) then "本文は空にできません"
        ]
        if errors.IsEmpty then Validated.valid (EmailNotification(to', subject, body))
        else Validated.invalid errors

    let createSMS (phoneNumber: string) (body: string) : Validated<string, Notification> =
        let errors = [
            if not (phonePattern.IsMatch(phoneNumber)) then "無効な電話番号形式です"
            if System.String.IsNullOrEmpty(body) then "本文は空にできません"
        ]
        if errors.IsEmpty then Validated.valid (SMSNotification(phoneNumber, body))
        else Validated.invalid errors

    let createPush (deviceToken: string) (body: string) : Validated<string, Notification> =
        let errors = [
            if System.String.IsNullOrEmpty(deviceToken) then "デバイストークンは空にできません"
            if System.String.IsNullOrEmpty(body) then "本文は空にできません"
        ]
        if errors.IsEmpty then Validated.valid (PushNotification(deviceToken, body))
        else Validated.invalid errors

// 使用例
Notification.createEmail "test@example.com" "テスト" "本文"
// => Valid (EmailNotification ("test@example.com", "テスト", "本文"))

Notification.createSMS "090-1234-5678" "本文"
// => Valid (SMSNotification ("090-1234-5678", "本文"))
```

## 8. バリデーションユーティリティ

### バリデーション結果の構造化

```fsharp
/// バリデーションレスポンス
type ValidationResponse<'T> =
    { Valid: bool
      Data: 'T option
      Errors: string list }

/// Person のバリデーション
let validatePerson (name: string) (age: int) (email: string option) : ValidationResponse<Person> =
    let nameV =
        if System.String.IsNullOrEmpty(name) then Validated.invalid ["名前は空にできません"]
        else Validated.valid name

    let ageV =
        if age < 0 then Validated.invalid ["年齢は0以上である必要があります"]
        else Validated.valid age

    let validated =
        Validated.combine
            (fun n a -> { Name = n; Age = a; Email = email })
            nameV
            ageV

    match validated with
    | Valid p -> { Valid = true; Data = Some p; Errors = [] }
    | Invalid e -> { Valid = false; Data = None; Errors = e }

// 使用例
validatePerson "田中" 30 None
// => { Valid = true; Data = Some { Name = "田中"; Age = 30; Email = None }; Errors = [] }

validatePerson "" -1 None
// => { Valid = false; Data = None; Errors = ["名前は空にできません"; "年齢は0以上である必要があります"] }
```

### 例外をスローするバリデーション

```fsharp
let conformOrThrow (validated: Validated<string, 'T>) : 'T =
    match validated with
    | Valid a -> a
    | Invalid errs -> failwithf "Validation failed: %s" (System.String.Join(", ", errs))
```

## 9. 計算関数

```fsharp
/// アイテム合計を計算
let calculateItemTotal (item: OrderItem) =
    Price.value item.Price * decimal (Quantity.value item.Quantity)

/// 注文合計を計算
let calculateOrderTotal (order: Order) =
    order.Items |> List.sumBy calculateItemTotal

/// 割引を適用
let applyDiscount (total: decimal) (discountRate: float) : Result<decimal, string> =
    if discountRate < 0.0 || discountRate > 1.0 then
        Error "割引率は0から1の間である必要があります"
    else
        Ok (total * (1m - decimal discountRate))

/// 複数の価格を合計
let sumPrices (prices: decimal list) : decimal =
    prices |> List.sum
```

## Clojure Spec / Scala との比較

| 特徴 | Clojure Spec | Scala | F# |
|------|-------------|-------|-----|
| 検証タイミング | 実行時 | コンパイル時 + 実行時 | コンパイル時 + 実行時 |
| エラー蓄積 | explain-data | Validated パターン | Validated パターン |
| 型安全性 | 動的 | 静的（Opaque Type） | 静的（プライベートコンストラクタ） |
| ジェネレータ | 組み込み | ScalaCheck | FsCheck |
| 関数仕様 | fdef | 型シグネチャ | 型シグネチャ |

## まとめ

本章では、F# におけるデータバリデーションについて学びました：

1. **Result**: 基本的な成功/失敗の表現
2. **Validated パターン**: エラーの蓄積
3. **ドメインプリミティブ**: プライベートコンストラクタによる型安全なドメイン型
4. **スマートコンストラクタ**: バリデーション付きのインスタンス生成
5. **判別共用体**: 条件付きバリデーション
6. **ValidationResponse**: 構造化されたバリデーション結果

F# の型システムを活用することで、コンパイル時に多くのエラーを検出し、実行時には Validated パターンで詳細なエラー情報を提供できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part2/src/Library.fs`
- テストコード: `apps/fsharp/part2/tests/Tests.fs`

## 次章予告

次章では、**プロパティベーステスト**について学びます。FsCheck を使った生成的テストの手法を探ります。
