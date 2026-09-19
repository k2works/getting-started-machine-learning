module MachineLearning.Tests.Chapter11.DataTest

open System.IO
open Xunit
open MachineLearning.Dataset

let survivedCsv = Path.Combine(dataDir (), "Survived.csv")
let cinemaCsv = Path.Combine(dataDir (), "cinema.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(
        File.Exists survivedCsv && File.Exists cinemaCsv,
        "学習データ Survived.csv・cinema.csv が配置されていない（gulp data:setup）"
    )

[<Fact>]
let ``実行すると Survived と cinema の交差検証の平均を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter11.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "Survived（決定木、5 分割交差検証の平均）"
            "  正解率: 0.7800"
            "  適合率: 0.8114"
            "  再現率: 0.5736"
            "  F値: 0.6587"
            "cinema（線形回帰、5 分割交差検証の平均）"
            "  RMSE: 392.75"
            "  MAE: 314.71"
        ],
        lines
    )
