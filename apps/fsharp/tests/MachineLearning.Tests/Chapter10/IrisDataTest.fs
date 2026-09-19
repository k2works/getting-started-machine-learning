module MachineLearning.Tests.Chapter10.IrisDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter10
open MachineLearning.Chapter10.Classifier
open MachineLearning.Chapter10.MlNetClassifier

let csvFile = Path.Combine(dataDir (), "iris.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ iris.csv が配置されていない（gulp data:setup）")

let irisSplit () = prepareIris csvFile 0.3 0

let logisticRegression =
    ofModel (LogisticRegression.fit LogisticRegression.defaults) LogisticRegression.predict

[<Fact>]
let ``ロジスティック回帰はテストデータの 45 件中 39 件を正しく分類する`` () =
    requireData ()

    let score = evaluate logisticRegression (irisSplit ())

    Assert.Equal(39.0 / 45.0, score.Test, 12)

[<Fact>]
let ``ランダムフォレストは訓練データを分け切りテストデータの 45 件中 40 件を正しく分類する`` () =
    requireData ()

    let forest =
        ofModel
            (RandomForest.fit
                { RandomForest.defaults with
                    NEstimators = 100
                })
            RandomForest.predict

    let score = evaluate forest (irisSplit ())

    Assert.Equal(1.0, score.Train)
    Assert.Equal(40.0 / 45.0, score.Test, 12)

[<Fact>]
let ``ML.NET のロジスティック回帰は正則化を 0 にすると自作と同じ正解率になる`` () =
    requireData ()

    Assert.Equal(evaluate logisticRegression (irisSplit ()), evaluate (lbfgsMaximumEntropy 0.0f 0.0f) (irisSplit ()))

[<Fact>]
let ``ML.NET の FastForest は葉の最小件数を 1 にすると訓練データを分け切る`` () =
    requireData ()

    let score = evaluate (fastForest 100 1) (irisSplit ())

    Assert.Equal(1.0, score.Train)
    Assert.Equal(41.0 / 45.0, score.Test, 12)

[<Fact>]
let ``実行するとモデルごとの正解率とランダムフォレストの重要度を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "モデル\t訓練データ\tテストデータ"
            "決定木（深さ 2）\t0.9619\t0.8889"
            "ロジスティック回帰\t0.9524\t0.8667"
            "ランダムフォレスト（100 本）\t1.0000\t0.8889"
            "ランダムフォレスト（100 本・深さ 2）\t0.9619\t0.8889"
            "ML.NET LbfgsMaximumEntropy\t0.9238\t0.8444"
            "ML.NET FastForest（100 本）\t0.9714\t0.9111"
            ""
            "ランダムフォレスト（100 本）の特徴量の重要度:"
            "がく片幅\t0.0982"
            "がく片長さ\t0.2055"
            "花弁幅\t0.4929"
            "花弁長さ\t0.2034"
        ],
        lines
    )
