module MachineLearning.Tests.Chapter02.IrisDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing

let csvFile = Path.Combine(dataDir (), "iris.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ iris.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``実データから 150 件を読み込む`` () =
    requireData ()

    Assert.Equal(150, (loadIris csvFile).Length)

[<Fact>]
let ``実行するとデータ件数・欠損値の数・分割の件数を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter02.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "データ件数: 150"
            "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0"
            "訓練データ: 105 件, テストデータ: 45 件"
            "補完後の欠損値の数: 訓練データ 0, テストデータ 0"
        ],
        lines
    )
