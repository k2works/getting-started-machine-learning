module MachineLearning.Chapter15.Training

open System.IO
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter07.Cinema
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter08.Pipeline
open MachineLearning.Chapter08.SurvivedData
open MachineLearning.Chapter08.WeightedTree
open MachineLearning.Chapter15.FileModelStore

[<Literal>]
let TestSize = 0.2

[<Literal>]
let Seed = 0

[<Literal>]
let MaxDepth = 5

[<Literal>]
let Host = "127.0.0.1"

[<Literal>]
let Port = 8015

/// 第 7・8 章と同じ条件でモデルを学習し、ディレクトリに保存する
let trainAndSaveModels (dataDirectory: string) (modelDirectory: string) : unit =
    let cinema = prepareCinema (Path.Combine(dataDirectory, "cinema.csv")) TestSize Seed
    saveSalesModel modelDirectory (fitLinearRegression cinema.XTrain cinema.TTrain)

    let x, t =
        loadSurvived (Path.Combine(dataDirectory, "Survived.csv"))
        |> splitFeaturesAndTarget

    let survived = splitTrainTest TestSize Seed x t

    fitPipeline
        {
            MaxDepth = Some MaxDepth
            ClassWeight = Balanced
        }
        survived.XTrain
        survived.TTrain
    |> saveSurvivalModel modelDirectory

/// 学習して保存し、保存したファイルと API の場所を表示する
let trainAndReport (modelDirectory: string) (print: string -> unit) : unit =
    trainAndSaveModels (MachineLearning.Dataset.dataDir ()) modelDirectory
    print $"学習済みモデルを保存しました: {SalesModelFile}, {SurvivalModelFile}"
    print $"API を起動します: http://{Host}:{Port}"
