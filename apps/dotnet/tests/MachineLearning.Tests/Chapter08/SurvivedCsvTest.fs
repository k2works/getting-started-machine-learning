module MachineLearning.Tests.Chapter08.SurvivedCsvTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03
open MachineLearning.Chapter08.SurvivedData
open MachineLearning.Chapter08.WeightedTree
open MachineLearning.Chapter08.Pipeline

let csvFile = Path.Combine(dataDir (), "Survived.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ Survived.csv が配置されていない（gulp data:setup）")

let loadSplit () : TrainTestSplit<Passenger, int> =
    let x, t = loadSurvived csvFile |> splitFeaturesAndTarget
    splitTrainTest 0.2 0 x t

let evaluateWith (split: TrainTestSplit<Passenger, int>) (maxDepth: int) (classWeight: ClassWeight) : Evaluation =
    let options =
        {
            MaxDepth = Some maxDepth
            ClassWeight = classWeight
        }

    evaluate (fitPipeline options split.XTrain split.TTrain) split

[<Fact>]
let ``実データの件数と欠損値の数を確認する`` () =
    requireData ()
    let rows = loadSurvived csvFile

    let missing =
        [
            rows |> List.filter (fun row -> row.Passenger.Age.IsNone) |> List.length
            rows |> List.filter (fun row -> row.Cabin.IsNone) |> List.length
            rows |> List.filter (fun row -> row.Passenger.Embarked.IsNone) |> List.length
        ]

    Assert.Equal(891, rows.Length)
    Assert.Equal<int list>([ 177; 687; 2 ], missing)

[<Fact>]
let ``深さ 5 では balanced にすると見つけられる生存者が増える`` () =
    requireData ()
    let split = loadSplit ()

    Assert.Equal(54, (evaluateWith split 5 Unweighted).FoundSurvivors)
    Assert.Equal(59, (evaluateWith split 5 Balanced).FoundSurvivors)

[<Fact>]
let ``深さ 1 では balanced にしても評価が変わらない`` () =
    requireData ()
    let split = loadSplit ()

    Assert.Equal(evaluateWith split 1 Unweighted, evaluateWith split 1 Balanced)

[<Fact>]
let ``重み付けなしなら深さ 10 まで第 3 章の決定木と同じ木を作る`` () =
    requireData ()
    let split = loadSplit ()

    for maxDepth in 1..10 do
        let options =
            {
                MaxDepth = Some maxDepth
                ClassWeight = Unweighted
            }

        let pipeline = fitPipeline options split.XTrain split.TTrain
        let features = split.XTrain |> List.map (transform pipeline)

        Assert.Equal(DecisionTree.fit (Some maxDepth) features split.TTrain, pipeline.Tree)

[<Fact>]
let ``実行すると評価結果を表示し、保存したモデルで架空の乗客を予測する`` () =
    requireData ()
    let modelDirectory = Directory.CreateTempSubdirectory("model-").FullName
    let lines = ResizeArray<string>()

    MachineLearning.Chapter08.Main.runWith modelDirectory lines.Add

    Assert.True(File.Exists(Path.Combine(modelDirectory, "survived.json")))
    Assert.True(File.Exists(Path.Combine(modelDirectory, "survived-mlnet.zip")))

    Assert.Equal<string seq>(
        [
            "データ件数: 891（生存 342, 死亡 549）"
            "訓練データ: 712 件, テストデータ: 179 件"
            "classWeight=Unweighted: 訓練 0.858, テスト 0.821, 生存者 72 人中 54 人を発見, ML.NET と一致 175/179"
            "classWeight=Balanced: 訓練 0.840, テスト 0.788, 生存者 72 人中 59 人を発見, ML.NET と一致 170/179"
            "架空の乗客の予測（survived.json）: [1, 0]"
            "架空の乗客の予測（survived-mlnet.zip）: [1, 0]"
        ],
        lines
    )
