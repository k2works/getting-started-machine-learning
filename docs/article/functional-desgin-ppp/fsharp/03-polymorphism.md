---
type: Article
title: "第3章: 多態性とディスパッチ"
description: "多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第3章: 多態性とディスパッチ

## はじめに

多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。F# では、判別共用体、パターンマッチング、インターフェース、アクティブパターンという複数のメカニズムで多態性を実現します。

本章では、これらのメカニズムを使い分けて、柔軟で拡張性の高いコードを書く方法を学びます。

## 1. 判別共用体による多態性（代数的データ型）

F# の判別共用体（Discriminated Union）は、有限の型のバリエーションを定義するのに最適です。コンパイラがパターンマッチングの網羅性をチェックしてくれます。

### 基本的な使い方

```fsharp
/// 図形を表す判別共用体
type Shape =
    | Rectangle of width: float * height: float
    | Circle of radius: float
    | Triangle of baseLength: float * height: float

/// 図形の面積を計算する
let calculateArea shape =
    match shape with
    | Rectangle(w, h) -> w * h
    | Circle r -> System.Math.PI * r * r
    | Triangle(b, h) -> b * h / 2.0

// 使用例
calculateArea (Rectangle(4.0, 5.0))  // => 20.0
calculateArea (Circle(3.0))          // => 28.27...
calculateArea (Triangle(6.0, 5.0))   // => 15.0
```

### 判別共用体の利点

1. **網羅性チェック**: コンパイラがすべてのケースがカバーされているか確認
2. **型安全性**: 不正な型の値を渡せない
3. **パターンマッチング**: 分岐を簡潔に記述可能

## 2. 複合ディスパッチ

タプルを使ったパターンマッチングで、複数の値に基づくディスパッチを実現できます。

```fsharp
/// 支払い方法
type PaymentMethod =
    | CreditCard
    | BankTransfer
    | Cash

/// 通貨
type Currency =
    | JPY
    | USD
    | EUR

/// 支払い情報
type Payment =
    { Method: PaymentMethod
      Currency: Currency
      Amount: int }

/// 支払い結果
type PaymentResult =
    { Status: string
      Message: string
      Amount: int
      Converted: int option }

/// 支払いを処理する（複合ディスパッチ）
let processPayment payment =
    match payment.Method, payment.Currency with
    | CreditCard, JPY ->
        { Status = "processed"
          Message = "クレジットカード（円）で処理しました"
          Amount = payment.Amount
          Converted = None }
    | CreditCard, USD ->
        { Status = "processed"
          Message = "Credit card (USD) processed"
          Amount = payment.Amount
          Converted = Some(payment.Amount * 150) }
    | BankTransfer, JPY ->
        { Status = "pending"
          Message = "銀行振込を受け付けました"
          Amount = payment.Amount
          Converted = None }
    | _, _ ->
        { Status = "error"
          Message = "サポートされていない支払い方法です"
          Amount = payment.Amount
          Converted = None }

// 使用例
processPayment { Method = CreditCard; Currency = JPY; Amount = 1000 }
// => { Status = "processed"; Message = "クレジットカード（円）で処理しました"; ... }
```

## 3. 階層的ディスパッチ（型階層の模倣）

判別共用体を使って型の階層を定義し、各型に固有の振る舞いを持たせることができます。

```fsharp
/// 口座タイプ
type AccountType =
    | Savings
    | PremiumSavings
    | Checking

/// 口座
type Account =
    { AccountType: AccountType
      Balance: int }

/// 利率を取得
let getInterestRate accountType =
    match accountType with
    | Savings -> 0.02
    | PremiumSavings -> 0.05
    | Checking -> 0.001

/// 利息を計算
let calculateInterest account =
    float account.Balance * getInterestRate account.AccountType

// 使用例
calculateInterest { AccountType = Savings; Balance = 10000 }        // => 200.0
calculateInterest { AccountType = PremiumSavings; Balance = 10000 } // => 500.0
calculateInterest { AccountType = Checking; Balance = 10000 }       // => 10.0
```

## 4. インターフェース（F# のオブジェクト指向機能）

F# のインターフェースは、特定の操作セットを定義する契約です。複数のインターフェースを実装できます。

### インターフェースの定義

```fsharp
/// バウンディングボックス
type BoundingBox =
    { X: float; Y: float; Width: float; Height: float }

/// 描画可能インターフェース
type IDrawable =
    abstract member Draw: unit -> string
    abstract member GetBoundingBox: unit -> BoundingBox

/// 変換可能インターフェース
type ITransformable<'T> =
    abstract member Translate: float * float -> 'T
    abstract member Scale: float -> 'T
    abstract member Rotate: float -> 'T
```

### インターフェースの利点

1. **パフォーマンス**: パターンマッチングより高速な型ベースのディスパッチ
2. **明確なコントラクト**: 実装すべきメソッドが明示的
3. **複数実装**: 複数のインターフェースを組み合わせ可能

## 5. インターフェースを実装するレコード

```fsharp
/// 描画可能な長方形
type DrawableRectangle =
    { X: float; Y: float; Width: float; Height: float }

    interface IDrawable with
        member this.Draw() =
            sprintf "Rectangle at (%.1f,%.1f) with size %.1fx%.1f"
                this.X this.Y this.Width this.Height
        member this.GetBoundingBox() =
            { X = this.X; Y = this.Y; Width = this.Width; Height = this.Height }

    interface ITransformable<DrawableRectangle> with
        member this.Translate(dx, dy) =
            { this with X = this.X + dx; Y = this.Y + dy }
        member this.Scale(factor) =
            { this with Width = this.Width * factor; Height = this.Height * factor }
        member this.Rotate(_) = this

/// 描画可能な円
type DrawableCircle =
    { X: float; Y: float; Radius: float }

    interface IDrawable with
        member this.Draw() =
            sprintf "Circle at (%.1f,%.1f) with radius %.1f" this.X this.Y this.Radius
        member this.GetBoundingBox() =
            { X = this.X - this.Radius
              Y = this.Y - this.Radius
              Width = this.Radius * 2.0
              Height = this.Radius * 2.0 }

    interface ITransformable<DrawableCircle> with
        member this.Translate(dx, dy) =
            { this with X = this.X + dx; Y = this.Y + dy }
        member this.Scale(factor) =
            { this with Radius = this.Radius * factor }
        member this.Rotate(_) = this

// ヘルパー関数（インターフェースメソッドを呼び出しやすくする）
let draw (drawable: IDrawable) = drawable.Draw()
let getBoundingBox (drawable: IDrawable) = drawable.GetBoundingBox()

// 使用例
let rect = { DrawableRectangle.X = 10.0; Y = 20.0; Width = 100.0; Height = 50.0 }
draw rect  // => "Rectangle at (10.0,20.0) with size 100.0x50.0"

let circle = { DrawableCircle.X = 50.0; Y = 50.0; Radius = 25.0 }
getBoundingBox circle  // => { X = 25.0; Y = 25.0; Width = 50.0; Height = 50.0 }
```

## 6. アクティブパターン（既存型への拡張）

アクティブパターンは、パターンマッチングを拡張して、任意の変換ロジックを適用できます。

```fsharp
/// 文字列化アクティブパターン（Map用）
let (|MapToString|) (m: Map<string, obj>) =
    let pairs = m |> Map.toSeq |> Seq.map (fun (k, v) -> sprintf "%s: %O" k v)
    "{" + System.String.Join(", ", pairs) + "}"

/// 文字列化アクティブパターン（リスト用）
let (|ListToString|) (l: 'a list) =
    "[" + System.String.Join(", ", l |> List.map string) + "]"

/// 文字列化関数
let stringify value =
    match box value with
    | :? Map<string, obj> as m ->
        let (MapToString s) = m
        s
    | :? (int list) as l ->
        let (ListToString s) = l
        s
    | :? string as s -> s
    | :? int as i -> string i
    | null -> "nil"
    | _ -> sprintf "%O" value

// 使用例
stringify (Map.ofList ["name", box "田中"; "age", box 30])
// => "{name: 田中, age: 30}"

stringify [1; 2; 3]
// => "[1, 2, 3]"
```

## 7. コンポーネントパターン（ライフサイクル管理）

インターフェースを使って、コンポーネントのライフサイクル管理を実現します。

```fsharp
/// ライフサイクルインターフェース
type ILifecycle<'T> =
    abstract member Start: unit -> 'T
    abstract member Stop: unit -> 'T

/// データベース接続
type DatabaseConnection =
    { Host: string; Port: int; Connected: bool }

    static member Create(host, port) =
        { Host = host; Port = port; Connected = false }

    interface ILifecycle<DatabaseConnection> with
        member this.Start() =
            printfn "データベースに接続中: %s : %d" this.Host this.Port
            { this with Connected = true }
        member this.Stop() =
            printfn "データベース接続を切断中"
            { this with Connected = false }

/// Webサーバー
type WebServer =
    { Port: int; Db: DatabaseConnection; Running: bool }

    static member Create(port, db) =
        { Port = port; Db = db; Running = false }

    interface ILifecycle<WebServer> with
        member this.Start() =
            printfn "Webサーバーを起動中 ポート: %d" this.Port
            let startedDb = (this.Db :> ILifecycle<DatabaseConnection>).Start()
            { this with Db = startedDb; Running = true }
        member this.Stop() =
            printfn "Webサーバーを停止中"
            let stoppedDb = (this.Db :> ILifecycle<DatabaseConnection>).Stop()
            { this with Db = stoppedDb; Running = false }

// 使用例
let db = DatabaseConnection.Create("localhost", 5432)
let server = WebServer.Create(8080, db)

let startedServer = (server :> ILifecycle<WebServer>).Start()
// データベースに接続中: localhost : 5432
// Webサーバーを起動中 ポート: 8080

let stoppedServer = (startedServer :> ILifecycle<WebServer>).Stop()
// Webサーバーを停止中
// データベース接続を切断中
```

## 8. 条件分岐の置き換え（Strategy パターン）

多態性を使って、switch/case 文による型判定を排除できます。

### Before（条件分岐）

```fsharp
// 悪い例：型による条件分岐
let sendNotificationBad notificationType message (opts: Map<string, string>) =
    match notificationType with
    | "email" ->
        {| Type = "email"; To = opts.["to"]; Body = message |}
    | "sms" ->
        {| Type = "sms"; To = opts.["phone"]; Body = message.[..min 159 (message.Length - 1)] |}
    | "push" ->
        {| Type = "push"; Device = opts.["device"]; Body = message |}
    | _ -> failwith "未知の通知タイプ"
```

### After（多態性）

```fsharp
/// 通知結果
type NotificationResult =
    { NotificationType: string
      To: string
      Body: string
      Status: string
      Subject: string option }

/// 通知送信インターフェース
type INotificationSender =
    abstract member SendNotification: string -> NotificationResult
    abstract member DeliveryTime: string

/// メール通知
type EmailNotification =
    { To: string; Subject: string }
    interface INotificationSender with
        member this.SendNotification(message) =
            { NotificationType = "email"
              To = this.To
              Body = message
              Status = "sent"
              Subject = Some this.Subject }
        member this.DeliveryTime = "1-2分"

/// SMS通知
type SMSNotification =
    { PhoneNumber: string }
    interface INotificationSender with
        member this.SendNotification(message) =
            let truncated = if message.Length > 160 then message.[..156] else message
            { NotificationType = "sms"
              To = this.PhoneNumber
              Body = truncated
              Status = "sent"
              Subject = None }
        member this.DeliveryTime = "数秒"

/// プッシュ通知
type PushNotification =
    { DeviceToken: string }
    interface INotificationSender with
        member this.SendNotification(message) =
            { NotificationType = "push"
              To = this.DeviceToken
              Body = message
              Status = "sent"
              Subject = None }
        member this.DeliveryTime = "即時"

/// 通知を作成するファクトリ関数
let createNotification notificationType (opts: Map<string, string>) : INotificationSender =
    match notificationType with
    | "email" ->
        { To = opts |> Map.tryFind "to" |> Option.defaultValue ""
          Subject = opts |> Map.tryFind "subject" |> Option.defaultValue "通知" }
    | "sms" ->
        { PhoneNumber = opts |> Map.tryFind "phone" |> Option.defaultValue "" }
    | "push" ->
        { DeviceToken = opts |> Map.tryFind "device" |> Option.defaultValue "" }
    | _ -> failwithf "未知の通知タイプ: %s" notificationType

// 使用例
let email = createNotification "email" (Map.ofList ["to", "user@example.com"])
email.SendNotification("重要なお知らせ")
// => { NotificationType = "email"; To = "user@example.com"; Body = "重要なお知らせ"; ... }
```

## 9. 式ツリーパターン（Expression Problem の解決）

判別共用体とパターンマッチングを使って、式の評価や変換を行います。

```fsharp
/// 式を表す判別共用体
type Expr =
    | Num of int
    | Add of Expr * Expr
    | Mul of Expr * Expr
    | Neg of Expr

/// 式を評価する
let rec eval expr =
    match expr with
    | Num n -> n
    | Add(e1, e2) -> eval e1 + eval e2
    | Mul(e1, e2) -> eval e1 * eval e2
    | Neg e -> -(eval e)

/// 式を文字列化する
let rec exprToString expr =
    match expr with
    | Num n -> string n
    | Add(e1, e2) -> sprintf "(%s + %s)" (exprToString e1) (exprToString e2)
    | Mul(e1, e2) -> sprintf "(%s * %s)" (exprToString e1) (exprToString e2)
    | Neg e -> sprintf "(-%s)" (exprToString e)

/// 式を簡約する（定数畳み込み）
let rec simplify expr =
    match expr with
    | Add(Num 0, e) | Add(e, Num 0) -> simplify e
    | Mul(Num 0, _) | Mul(_, Num 0) -> Num 0
    | Mul(Num 1, e) | Mul(e, Num 1) -> simplify e
    | Neg(Neg e) -> simplify e
    | Add(e1, e2) -> Add(simplify e1, simplify e2)
    | Mul(e1, e2) -> Mul(simplify e1, simplify e2)
    | Neg e -> Neg(simplify e)
    | e -> e

// 使用例
let expr = Mul(Add(Num 2, Num 3), Num 4)
eval expr          // => 20
exprToString expr  // => "((2 + 3) * 4)"

simplify (Add(Num 0, Num 5))  // => Num 5
simplify (Mul(Num 1, Num 5))  // => Num 5
```

## Clojure / Scala / F# 比較

| 概念 | Clojure | Scala | F# |
|------|---------|-------|-----|
| 代数的データ型 | マルチメソッド | sealed trait | 判別共用体 |
| 複合ディスパッチ | ベクターキー | タプルマッチ | タプルマッチ |
| インターフェース | プロトコル | trait | interface |
| 型階層 | derive | 継承 | 判別共用体 |
| 既存型拡張 | extend-protocol | 型クラス | アクティブパターン |
| ファクトリ | 関数 | コンパニオンオブジェクト | 関数/静的メソッド |

## パターンマッチングとインターフェースの使い分け

| 特徴 | パターンマッチング | インターフェース |
|------|------------------|----------------|
| ディスパッチ | 値に基づく | 型に基づく |
| 拡張性 | クローズド | オープン |
| パフォーマンス | 良い | 最良 |
| 用途 | ADT、有限の型 | 共通のインターフェース |

### 使い分けの指針

- **判別共用体 + パターンマッチング**: 有限で固定された型のバリエーション
- **インターフェース**: 新しい型が追加される可能性がある場合
- **アクティブパターン**: 既存の型にパターンマッチングを拡張したい場合

## まとめ

本章では、F# における多態性について学びました：

1. **判別共用体**: 代数的データ型による有限の型バリエーション
2. **複合ディスパッチ**: タプルによる複数の値に基づくパターンマッチング
3. **階層的ディスパッチ**: 判別共用体を使った型階層
4. **インターフェース**: 共通インターフェースの定義と複数実装
5. **アクティブパターン**: 既存型へのパターンマッチング拡張
6. **コンポーネントパターン**: ライフサイクル管理
7. **Strategy パターン**: 条件分岐の置き換え
8. **式ツリー**: 評価・変換・簡約の実装

これらのメカニズムを適切に使い分けることで、拡張性が高く保守しやすいコードを実現できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part1/src/Library.fs`（Polymorphism モジュール）
- テストコード: `apps/fsharp/part1/tests/Tests.fs`

## 次章予告

次章から第2部「仕様とテスト」に入ります。F# のデータバリデーションと仕様定義について学びます。
