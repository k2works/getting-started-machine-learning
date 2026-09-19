module MachineLearning.Tests.Chapter07.CinemaDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter07.Cinema
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter07.StatsRegression

let csvFile = Path.Combine(dataDir (), "cinema.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ cinema.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``実データから外れ値を 1 件取り除く`` () =
    requireData ()
    let rows = loadCinema csvFile

    Assert.Equal((100, 99), (rows.Length, (removeOutliers rows).Length))

[<Fact>]
let ``実データで自作のモデルと FSharp.Stats の決定係数が一致する`` () =
    requireData ()
    let split = prepareCinema csvFile 0.2 0

    let mine =
        predictLinearRegression (fitLinearRegression split.XTrain split.TTrain) split.XTest

    let library =
        predictLinearRegression (fitWithFSharpStats split.XTrain split.TTrain) split.XTest

    Assert.Equal(r2Score split.TTest mine, r2WithFSharpStats split.TTest library, 9)

[<Fact>]
let ``実行すると学習した係数と評価指標を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter07.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "データ件数: 100"
            "外れ値を除いた件数: 99"
            "訓練データ: 79 件, テストデータ: 20 件"
            "切片: 6330.97"
            "係数: SNS1=1.1481, SNS2=0.5122, actor=0.2748, original=242.5101"
            "テストデータの評価: R2=0.7740, MAE=320.18, RMSE=396.73"
            "FSharp.Stats: 切片=6330.97, 係数: SNS1=1.1481, SNS2=0.5122, actor=0.2748, original=242.5101"
            "FSharp.Stats の R2: 0.7740"
        ],
        lines
    )
