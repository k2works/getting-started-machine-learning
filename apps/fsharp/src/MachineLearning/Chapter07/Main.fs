module MachineLearning.Chapter07.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter07.Cinema
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter07.StatsRegression

[<Literal>]
let TestSize = 0.2

[<Literal>]
let Seed = 0

let private formatCoefficients (coefficients: Map<string, float>) : string =
    coefficients
    |> Map.toList
    |> List.map (fun (name, value) -> $"{name}={value:F4}")
    |> String.concat ", "

/// cinema.csv で線形回帰を学習し、係数とテストデータの評価、FSharp.Stats との比較を表示する
let run (print: string -> unit) : unit =
    let csvFile = Path.Combine(dataDir (), "cinema.csv")
    let rows = loadCinema csvFile
    let split = prepareCinema csvFile TestSize Seed
    let model = fitLinearRegression split.XTrain split.TTrain
    let y = predictLinearRegression model split.XTest
    let library = fitWithFSharpStats split.XTrain split.TTrain
    let libraryY = predictLinearRegression library split.XTest

    print $"データ件数: {rows.Length}"
    print $"外れ値を除いた件数: {(removeOutliers rows).Length}"
    print $"訓練データ: {split.XTrain.Length} 件, テストデータ: {split.XTest.Length} 件"
    print $"切片: {model.Intercept:F2}"
    print $"係数: {formatCoefficients model.Coefficients}"

    print
        $"テストデータの評価: R2={r2Score split.TTest y:F4}, MAE={meanAbsoluteError split.TTest y:F2}, RMSE={rootMeanSquaredError split.TTest y:F2}"

    print $"FSharp.Stats: 切片={library.Intercept:F2}, 係数: {formatCoefficients library.Coefficients}"
    print $"FSharp.Stats の R2: {r2WithFSharpStats split.TTest libraryY:F4}"
