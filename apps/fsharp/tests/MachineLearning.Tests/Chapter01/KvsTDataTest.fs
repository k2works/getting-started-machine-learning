module MachineLearning.Tests.Chapter01.KvsTDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter01.KinokoTakenoko

let csvFile = Path.Combine(dataDir (), "KvsT.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ KvsT.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``実データから 19 人分を読み込む`` () =
    requireData ()

    Assert.Equal(19, (loadPeople csvFile).Length)

[<Fact>]
let ``ルールによる判定の正解率を実データで計算する`` () =
    requireData ()
    let features, labels = splitFeaturesAndLabels (loadPeople csvFile)

    let predictions = features |> List.map predictByRule

    Assert.Equal(14.0 / 19.0, accuracy predictions labels, 12)

[<Fact>]
let ``実行するとデータ件数と正解率を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter01.Main.run lines.Add

    Assert.Equal<string seq>([ "データ件数: 19"; "ルールによる判定の正解率: 0.7368" ], lines)
