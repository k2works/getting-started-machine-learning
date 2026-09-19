module MachineLearning.Chapter15.Main

open Microsoft.AspNetCore.Builder
open MachineLearning.Chapter15.FileModelStore
open MachineLearning.Chapter15.PredictionApi
open MachineLearning.Chapter15.Training

/// 学習済みモデルの保存先（apps/fsharp/model/ は .gitignore の対象）
[<Literal>]
let ModelDirectory = "model"

/// 置き場のモデルで予測する API を、指定したアドレスで起動する。止めるまで戻らない
let startServer (modelDirectory: string) (url: string) : unit =
    let app =
        createWebApplication (WebApplication.CreateBuilder()) (fileModelStore modelDirectory)

    app.Run url

/// 学習して保存してから、API を起動する
let run (print: string -> unit) : unit =
    trainAndReport ModelDirectory print
    startServer ModelDirectory $"http://{Host}:{Port}"
