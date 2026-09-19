module MachineLearning.Tests.Chapter09.DataTest

open System.IO
open Xunit
open MachineLearning.Dataset

let files =
    [ "Boston.csv"; "bike.tsv"; "weather.csv" ]
    |> List.map (fun name -> Path.Combine(dataDir (), name))

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(
        files |> List.forall File.Exists,
        "学習データ Boston.csv・bike.tsv・weather.csv が配置されていない（gulp data:setup）"
    )

[<Fact>]
let ``実行すると特徴量エンジニアリングと表の結合の結果を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter09.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "訓練データ: 70 件, テストデータ: 30 件"
            "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low"
            "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00"
            "ML.NET で正規化した訓練データの RM: 平均 0.00, 標準偏差 1.00"
            "決定係数:"
            "  元の特徴量（3 列）: 訓練 0.5723, テスト 0.6970"
            "  2 乗の項を追加（6 列）: 訓練 0.7561, テスト 0.8351"
            "  交互作用の項も追加（9 列）: 訓練 0.7779, テスト 0.8078"
            "訓練データの PRICE の外れ値: 6 件"
            "  外れ値を除いて 2 乗の項を追加: 訓練 0.6351, テスト 0.7110"
            "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3"
        ],
        lines
    )
