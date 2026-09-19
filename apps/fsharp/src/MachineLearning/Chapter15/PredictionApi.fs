/// プレゼンテーション層。Giraffe のエンドポイントと、HTTP の状態コード。ドメイン層とアプリケーション層を知り、インフラ層は知らない
module MachineLearning.Chapter15.PredictionApi

open System.Text.Encodings.Web
open System.Text.Json
open System.Text.Unicode
open Microsoft.AspNetCore.Builder
open Microsoft.AspNetCore.Http
open Microsoft.Extensions.DependencyInjection
open Microsoft.Extensions.Logging
open Giraffe
open MachineLearning.Chapter15.Domain
open MachineLearning.Chapter15.PredictionService
open MachineLearning.Chapter15.RequestValidation

/// 予測の結果を応答にする。モデルが無ければ 503 とその説明を返す
let private respond (result: Result<'T, PredictionError>) : HttpHandler =
    match result with
    | Ok prediction -> json prediction
    | Error error ->
        setStatusCode StatusCodes.Status503ServiceUnavailable
        >=> json {| detail = describe error |}

/// 本文を検証し、正しければ予測する。不正なら 422 と理由の一覧を返す
let private predictWith (parse: string -> Validation<'I>) (predict: 'I -> Result<'O, PredictionError>) : HttpHandler =
    fun next ctx ->
        task {
            let! body = ctx.ReadBodyFromRequestAsync()

            let handler =
                match parse body with
                | Error errors ->
                    setStatusCode StatusCodes.Status422UnprocessableEntity
                    >=> json {| detail = errors |}
                | Ok input -> respond (predict input)

            return! handler next ctx
        }

/// ヘルスチェックの応答。匿名レコードはフィールドを名前の順に並べるので、項目の順を決めるために名前を付ける
type HealthResponse = { Status: string; Models: Health }

let private healthHandler (store: ModelStore) : HttpHandler =
    fun next ctx ->
        let models = health store

        let status =
            if models.Cinema && models.Survived then
                "ok"
            else
                "degraded"

        json { Status = status; Models = models } next ctx

/// 経路と HTTP のメソッドから、処理を選ぶ
let webApp (store: ModelStore) : HttpHandler =
    choose
        [
            POST >=> route "/cinema/sales" >=> predictWith parseMovie (predictSales store)
            POST
            >=> route "/survived"
            >=> predictWith parsePassenger (predictSurvival store)
            GET >=> route "/health" >=> healthHandler store
            setStatusCode StatusCodes.Status404NotFound >=> json {| detail = "見つかりません" |}
        ]

/// 予期しない例外は記録し、内部の情報を出さずに 500 を返す
let private errorHandler (error: exn) (logger: ILogger) : HttpHandler =
    logger.LogError(error, "予測中に例外が発生しました")

    clearResponse
    >=> setStatusCode StatusCodes.Status500InternalServerError
    >=> json {| detail = "予測中にエラーが発生しました" |}

/// JSON の項目名を camelCase にし、日本語をエスケープせずにそのまま書く
let private jsonOptions =
    JsonSerializerOptions(JsonSerializerDefaults.Web, Encoder = JavaScriptEncoder.Create(UnicodeRanges.All))

/// 置き場を使う API のアプリケーションを組み立てる。起動方法（本物のサーバーかテスト用か）は builder に任せる
let createWebApplication (builder: WebApplicationBuilder) (store: ModelStore) : WebApplication =
    builder.Services.AddGiraffe() |> ignore

    builder.Services.AddSingleton<Json.ISerializer>(Json.Serializer(jsonOptions))
    |> ignore

    let app = builder.Build()
    app.UseGiraffeErrorHandler(errorHandler).UseGiraffe(webApp store)
    app
