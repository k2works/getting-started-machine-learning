module MachineLearning.Tests.Chapter03.IrisDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03.DecisionTree

let csvFile = Path.Combine(dataDir (), "iris.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ iris.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``深さ 2 の決定木はテストデータの 45 件中 40 件を正しく分類する`` () =
    requireData ()
    let split = prepareIris csvFile 0.3 0

    let tree = fit (Some 2) split.XTrain split.TTrain

    Assert.Equal(40.0 / 45.0, accuracy (predict tree split.XTest) split.TTest, 12)

[<Fact>]
let ``実行すると深さごとの正解率・ML.NET との一致数・深さ 2 の決定木を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter03.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "深さ\t訓練データ\tテストデータ\tML.NET と一致"
            "1\t0.6952\t0.6000\t32/45"
            "2\t0.9619\t0.8889\t45/45"
            "3\t0.9714\t0.8889\t43/45"
            "4\t0.9810\t0.9111\t44/45"
            "5\t1.0000\t0.9111\t44/45"
            "制限なし\t1.0000\t0.9111\t44/45"
            ""
            "深さ 2 の決定木:"
            "花弁幅 <= 0.2750"
            "  Iris-setosa"
            "花弁幅 > 0.2750"
            "  花弁幅 <= 0.6900"
            "    Iris-versicolor"
            "  花弁幅 > 0.6900"
            "    Iris-virginica"
        ],
        lines
    )
