module MachineLearning.Tests.Chapter12.BostonDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter12.Boston

let csvFile = Path.Combine(dataDir (), "Boston.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ Boston.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``外れ値を除いて訓練データと検証データとテストデータに分ける`` () =
    requireData ()

    let dataset = prepareBoston csvFile 0.3 0.3 0

    Assert.Equal<int list>([ 47; 21; 30 ], [ dataset.TTrain.Length; dataset.TValid.Length; dataset.TTest.Length ])

[<Fact>]
let ``実行すると正則化の実験結果を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter12.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "データ件数: 98（外れ値 2 件を除外）"
            "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件"
            "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2"
            "alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計"
            "0\t0.8914\t-0.4803\t12.509"
            "0.1\t0.8914\t-0.4445\t12.400"
            "1\t0.8908\t-0.2447\t11.718"
            "10\t0.8819\t-0.0167\t9.991"
            "100\t0.7769\t-0.1583\t6.406"
            "検証データで選んだ alpha: 10"
            "テストデータの決定係数: 線形回帰 0.9040, リッジ回帰 0.8827"
            "ML.NET（SDCA）のリッジ回帰のテストデータの決定係数: 0.8828"
            "ラッソ回帰（alpha=1）で係数が 0 になった特徴量: RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2"
            ""
            "シード\t選んだ alpha\t線形回帰\tリッジ回帰"
            "0\t10\t0.9040\t0.8827"
            "1\t10\t0.5551\t0.4040"
            "2\t1\t0.7012\t0.7040"
            "3\t0\t0.7460\t0.7460"
            "4\t100\t0.2219\t0.4841"
        ],
        lines
    )
