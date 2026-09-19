[<Xunit.Collection("標準出力を書き換えるテスト")>]
module MachineLearning.Tests.Chapter13.BostonPcaDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter13.BostonStandardized
open MachineLearning.Chapter13.Pca

let csvFile = Path.Combine(dataDir (), "Boston.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ Boston.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``CRIME をダミー変数にして 15 列の標準化済みデータにする`` () =
    requireData ()

    let table = loadStandardizedBoston csvFile

    Assert.Equal((100, 15), (table.X.Length, table.Columns.Length))

[<Fact>]
let ``実データの主成分も分散共分散行列の固有ベクトルになる`` () =
    requireData ()
    let x = (loadStandardizedBoston csvFile).X

    let model = fitPca 15 x

    let covariance = covarianceMatrix x

    Array.iter2
        (fun (pc: float[]) variance ->
            Array.iter2
                (fun (v: float) (av: float) -> Assert.Equal(v * variance, av, 1e-9))
                pc
                (covariance |> Array.map (dot pc)))
        model.Components
        model.ExplainedVariance

    Assert.Equal(1.0, Array.sum model.ExplainedVarianceRatio, 1e-9)

[<Fact>]
let ``実行すると寄与率と主成分の解釈を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter13.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "データ件数: 100, 列数: 15"
            "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581"
            "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）"
            "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328"
            "第 2 主成分で影響の大きい列: PRICE 0.444, low 0.423, RM 0.405"
            "FSharp.Stats の PCA と一致した数（許容誤差 1E-09）: 寄与率 15/15, 主成分 15/15"
        ],
        lines
    )
