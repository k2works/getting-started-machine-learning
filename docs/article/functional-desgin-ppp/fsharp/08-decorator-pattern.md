---
type: Article
title: "第8章: Decorator パターン"
description: "Decorator パターンは、既存のオブジェクトに新しい機能を動的に追加するパターンです。"
tags: [article, functional-desgin-ppp, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第8章: Decorator パターン

## はじめに

Decorator パターンは、既存のオブジェクトに新しい機能を動的に追加するパターンです。このパターンを使用すると、サブクラス化による継承よりも柔軟な方法で機能を拡張できます。

関数型プログラミングでは、高階関数を使って関数をラップし、横断的関心事（ログ、キャッシュ、認証など）を追加します。本章では、形状のジャーナリングと関数デコレータについて学びます。

## 1. パターンの構造

```plantuml
@startuml
skinparam classAttributeIconSize 0

abstract class Component {
  +operation(): Result
}

class ConcreteComponent {
  +operation()
}

class Decorator {
  -wrapped: Component
  +operation()
}

Component <|-- ConcreteComponent
Component <|-- Decorator
Decorator o-- Component : wrapped
@enduml
```

## 2. JournaledShape - 形状デコレータ

### 基本形状の定義

```fsharp
/// ジャーナルエントリ（操作の記録）
type JournalEntry =
    | Translate of dx: float * dy: float
    | Scale of factor: float
    | Rotate of angle: float

/// 基本形状（デコレート対象）
[<RequireQualifiedAccess>]
type Shape =
    | Circle of centerX: float * centerY: float * radius: float
    | Square of topLeftX: float * topLeftY: float * side: float
    | Rectangle of topLeftX: float * topLeftY: float * width: float * height: float

module Shape =
    let translate dx dy shape =
        match shape with
        | Shape.Circle(cx, cy, r) -> Shape.Circle(cx + dx, cy + dy, r)
        | Shape.Square(x, y, s) -> Shape.Square(x + dx, y + dy, s)
        | Shape.Rectangle(x, y, w, h) -> Shape.Rectangle(x + dx, y + dy, w, h)

    let scale factor shape =
        match shape with
        | Shape.Circle(cx, cy, r) -> Shape.Circle(cx, cy, r * factor)
        | Shape.Square(x, y, s) -> Shape.Square(x, y, s * factor)
        | Shape.Rectangle(x, y, w, h) -> Shape.Rectangle(x, y, w * factor, h * factor)
```

### ジャーナル付き形状（デコレータ）

```fsharp
/// ジャーナル付き形状（デコレータ）
type JournaledShape =
    { Shape: Shape
      Journal: JournalEntry list }

module JournaledShape =
    let create shape =
        { Shape = shape; Journal = [] }

    let translate dx dy js =
        { Shape = Shape.translate dx dy js.Shape
          Journal = js.Journal @ [ Translate(dx, dy) ] }

    let scale factor js =
        { Shape = Shape.scale factor js.Shape
          Journal = js.Journal @ [ Scale factor ] }

    let clearJournal js = { js with Journal = [] }

    /// ジャーナルエントリを再生
    let replay entries js =
        entries
        |> List.fold
            (fun acc entry ->
                match entry with
                | Translate(dx, dy) -> translate dx dy acc
                | Scale factor -> scale factor acc
                | Rotate _ -> acc)
            js
```

### 使用例

```fsharp
// 形状をデコレート
let circle = Shape.Circle(0.0, 0.0, 5.0)
let journaled =
    JournaledShape.create circle
    |> JournaledShape.translate 2.0 3.0
    |> JournaledShape.scale 5.0

// ジャーナルを確認
JournaledShape.getJournal journaled
// => [Translate(2.0, 3.0); Scale(5.0)]

// 実際の形状を確認
JournaledShape.getShape journaled
// => Circle(2.0, 3.0, 25.0)
```

## 3. 関数デコレータ

関数型プログラミングでは、高階関数を使って関数をデコレートします。

### ログ付きデコレータ

```fsharp
/// ログコレクター（副作用を追跡）
type LogCollector() =
    let mutable logs: string list = []
    member _.Add(msg: string) = logs <- logs @ [ msg ]
    member _.GetLogs() = logs

/// ログ付きデコレータ
let withLogging (name: string) (collector: LogCollector) (f: 'a -> 'b) : 'a -> 'b =
    fun a ->
        collector.Add(sprintf "[%s] called with: %A" name a)
        let result = f a
        collector.Add(sprintf "[%s] returned: %A" name result)
        result

// 使用例
let collector = LogCollector()
let add10 = fun x -> x + 10
let logged = withLogging "add10" collector add10
logged 5  // 15 (ログも記録される)
```

### リトライデコレータ

```fsharp
/// リトライデコレータ
let withRetry (maxRetries: int) (f: 'a -> 'b) : 'a -> 'b =
    fun a ->
        let rec attempt remaining =
            try
                f a
            with ex ->
                if remaining > 0 then
                    attempt (remaining - 1)
                else
                    raise ex

        attempt maxRetries

// 使用例
let unreliableFn = withRetry 3 callExternalService
```

### キャッシュデコレータ

```fsharp
/// キャッシュデコレータ
let withCache () : ('a -> 'b) -> ('a -> 'b) =
    fun f ->
        let cache = System.Collections.Generic.Dictionary<'a, 'b>()

        fun a ->
            if cache.ContainsKey(a) then
                cache.[a]
            else
                let result = f a
                cache.[a] <- result
                result

// 使用例
let expensiveFn = withCache () (fun x -> x * x)
```

### バリデーションデコレータ

```fsharp
/// バリデーションデコレータ
let withValidation (validator: 'a -> bool) (errorMsg: string) (f: 'a -> 'b) : 'a -> 'b =
    fun a ->
        if validator a then
            f a
        else
            invalidArg "input" (sprintf "%s: %A" errorMsg a)

// 使用例
let positiveOnly = withValidation (fun x -> x > 0) "Must be positive" (fun x -> x * 2)
positiveOnly 5   // 10
positiveOnly -5  // Exception!
```

### エラーハンドリングデコレータ

```fsharp
/// Option結果デコレータ（例外をOptionに変換）
let withOptionResult (f: 'a -> 'b) : 'a -> 'b option =
    fun a ->
        try
            Some(f a)
        with _ ->
            None

/// Either結果デコレータ（例外をResultに変換）
let withEitherResult (f: 'a -> 'b) : 'a -> Result<'b, exn> =
    fun a ->
        try
            Ok(f a)
        with ex ->
            Error ex

/// デフォルト値デコレータ
let withDefault (defaultValue: 'b) (f: 'a -> 'b) : 'a -> 'b =
    fun a ->
        try
            f a
        with _ ->
            defaultValue
```

## 4. デコレータの合成

```fsharp
/// 複数のデコレータを合成
let composeDecorators (decorators: (('a -> 'b) -> ('a -> 'b)) list) (f: 'a -> 'b) : 'a -> 'b =
    decorators |> List.fold (fun decorated decorator -> decorator decorated) f

// 使用例
let collector = LogCollector()
let double' = fun x -> x * 2
let decorators = [
    withLogging "double" collector
    withValidation (fun x -> x > 0) "Must be positive"
]
let composed = composeDecorators decorators double'
composed 5  // 10 (ログも記録される)
```

## 5. AuditedList - コレクションデコレータ

```fsharp
/// 操作履歴付きリスト
type AuditedList<'T> =
    { Items: 'T list
      Operations: string list }

module AuditedList =
    let empty<'T> : AuditedList<'T> = { Items = []; Operations = [] }

    let add (item: 'T) (list: AuditedList<'T>) =
        { Items = list.Items @ [ item ]
          Operations = list.Operations @ [ sprintf "add(%A)" item ] }

    let remove (item: 'T) (list: AuditedList<'T>) =
        { Items = list.Items |> List.filter ((<>) item)
          Operations = list.Operations @ [ sprintf "remove(%A)" item ] }

    let map (f: 'T -> 'U) (list: AuditedList<'T>) : AuditedList<'U> =
        { Items = List.map f list.Items
          Operations = list.Operations @ [ "map" ] }

    let filter (predicate: 'T -> bool) (list: AuditedList<'T>) =
        { Items = List.filter predicate list.Items
          Operations = list.Operations @ [ "filter" ] }

// 使用例
let list =
    AuditedList.empty
    |> AuditedList.add 1
    |> AuditedList.add 2
    |> AuditedList.add 3
    |> AuditedList.filter (fun x -> x > 1)

list.Operations // ["add(1)"; "add(2)"; "add(3)"; "filter"]
```

## 6. HTTPクライアントデコレータ

```fsharp
/// HTTPレスポンス
type HttpResponse = { StatusCode: int; Body: string }

/// HTTPクライアントインターフェース
type IHttpClient =
    abstract member Get: string -> HttpResponse

/// シンプルなHTTPクライアント
type SimpleHttpClient() =
    interface IHttpClient with
        member _.Get(url: string) =
            { StatusCode = 200
              Body = sprintf "Response from %s" url }

/// ログ付きHTTPクライアント（デコレータ）
type LoggingHttpClient(client: IHttpClient, collector: LogCollector) =
    interface IHttpClient with
        member _.Get(url: string) =
            collector.Add(sprintf "[HTTP] GET %s" url)
            let response = client.Get(url)
            collector.Add(sprintf "[HTTP] Response: %d" response.StatusCode)
            response

/// キャッシュ付きHTTPクライアント（デコレータ）
type CachingHttpClient(client: IHttpClient) =
    let cache = System.Collections.Generic.Dictionary<string, HttpResponse>()

    interface IHttpClient with
        member _.Get(url: string) =
            if cache.ContainsKey(url) then
                cache.[url]
            else
                let response = client.Get(url)
                cache.[url] <- response
                response

// デコレータの組み合わせ
let collector = LogCollector()
let client =
    SimpleHttpClient() :> IHttpClient
    |> fun c -> CachingHttpClient(c) :> IHttpClient
    |> fun c -> LoggingHttpClient(c, collector) :> IHttpClient
```

## 7. FunctionBuilder - ビルダースタイル

```fsharp
/// 関数ビルダー
type FunctionBuilder<'a, 'b>(f: 'a -> 'b, collector: LogCollector) =
    let mutable current = f

    member _.WithLogging(name: string) =
        current <- withLogging name collector current
        FunctionBuilder(current, collector)

    member _.WithValidation(validator: 'a -> bool, errorMsg: string) =
        current <- withValidation validator errorMsg current
        FunctionBuilder(current, collector)

    member _.WithRetry(maxRetries: int) =
        current <- withRetry maxRetries current
        FunctionBuilder(current, collector)

    member _.Build() = current

// 使用例
let collector = LogCollector()
let fn =
    FunctionBuilder.create collector (fun x -> x * 2)
    |> fun b -> b.WithLogging("double")
    |> fun b -> b.WithValidation((fun x -> x > 0), "Must be positive")
    |> fun b -> b.Build()
```

## 8. TransactionalOperation - トランザクションデコレータ

```fsharp
/// トランザクション状態
type TransactionState =
    | NotStarted
    | InProgress
    | Committed
    | RolledBack

/// トランザクション付き操作
type TransactionalOperation<'T> =
    { Operations: ('T -> 'T) list
      State: TransactionState
      OriginalValue: 'T option
      CurrentValue: 'T option }

module TransactionalOperation =
    let create () =
        { Operations = []; State = NotStarted; OriginalValue = None; CurrentValue = None }

    let begin' (value: 'T) (tx: TransactionalOperation<'T>) =
        { tx with State = InProgress; OriginalValue = Some value; CurrentValue = Some value }

    let addOperation (op: 'T -> 'T) (tx: TransactionalOperation<'T>) =
        match tx.State with
        | InProgress ->
            let newValue = tx.CurrentValue |> Option.map op
            { tx with Operations = tx.Operations @ [ op ]; CurrentValue = newValue }
        | _ -> tx

    let commit (tx: TransactionalOperation<'T>) =
        match tx.State with
        | InProgress -> { tx with State = Committed }
        | _ -> tx

    let rollback (tx: TransactionalOperation<'T>) =
        match tx.State with
        | InProgress ->
            { tx with State = RolledBack; CurrentValue = tx.OriginalValue; Operations = [] }
        | _ -> tx
```

## パターンの利点

1. **単一責任の原則**: 各デコレータは一つの機能のみを追加
2. **開放/閉鎖の原則**: 既存コードを変更せずに機能を追加
3. **柔軟な組み合わせ**: 必要な機能だけを選択して組み合わせ可能
4. **実行時の決定**: どのデコレータを適用するか実行時に決定可能

## F# での特徴

### 高階関数によるデコレータ

```fsharp
// 関数を受け取り、関数を返す
let withLogging name collector f =
    fun a ->
        collector.Add(sprintf "called with: %A" a)
        f a
```

### パイプラインでデコレータを適用

```fsharp
let decorated =
    originalFn
    |> withCache ()
    |> withLogging "fn" collector
    |> withRetry 3
```

### 不変性を維持したデコレータ

```fsharp
// 元のデータは変更されない
let original = JournaledShape.create circle
let decorated = original |> JournaledShape.translate 2.0 3.0
// original は変更されていない
```

## Clojure/Scala との比較

| 概念 | Clojure | Scala | F# |
|------|---------|-------|-----|
| 高階関数 | `(defn with-logging [f] ...)` | `def withLogging[A,B](f: A => B)` | `let withLogging f = fun a -> ...` |
| クロージャ | `(let [cache (atom {})] ...)` | `val cache = mutable.Map.empty` | `let cache = Dictionary()` |
| 合成 | `(comp decorator1 decorator2)` | `compose(f, decorators)` | `composeDecorators decorators f` |
| 状態 | `atom` | `var` or mutable | `mutable` (explicit) |
| ビルダー | なし（関数合成） | class with fluent API | F# class with methods |

## まとめ

本章では、Decorator パターンについて学びました：

1. **JournaledShape**: 形状操作の履歴を記録するデコレータ
2. **関数デコレータ**: ログ、リトライ、キャッシュなどの横断的関心事
3. **AuditedList**: 操作履歴付きコレクション
4. **HTTPクライアント**: インターフェースベースのデコレータ
5. **FunctionBuilder**: ビルダースタイルのデコレータ適用
6. **TransactionalOperation**: トランザクション機能

Decorator パターンは、既存のコードを変更せずに機能を拡張する強力なパターンです。F# では高階関数を使って関数レベルでデコレータを実装でき、型安全で柔軟な設計が可能です。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/fsharp/part3/src/Library.fs`
- テストコード: `apps/fsharp/part3/tests/Tests.fs`

## 次章予告

次章では、**Adapter パターン**について学びます。異なるインターフェース間の変換とレガシーシステムとの統合を探ります。
