module MachineLearning.Chapter08.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter08.SurvivedData
open MachineLearning.Chapter08.WeightedTree
open MachineLearning.Chapter08.Pipeline
open MachineLearning.Chapter08.ModelFile
open MachineLearning.Chapter08.MlNetAdapter

[<Literal>]
let TestSize = 0.2

[<Literal>]
let Seed = 0

[<Literal>]
let MaxDepth = 5

/// 学習済みのパイプラインとモデルの保存先（apps/dotnet/model/ は .gitignore の対象）
[<Literal>]
let ModelDirectory = "model"

[<Literal>]
let PipelineFileName = "survived.json"

[<Literal>]
let MlNetFileName = "survived-mlnet.zip"

let ClassWeights = [ Unweighted; Balanced ]

/// 年齢が欠けた架空の乗客（1 等客室の女性、3 等客室の男性）
let NewPassengers: Passenger list =
    [
        {
            Pclass = 1
            Sex = "female"
            Age = None
            SibSp = 0
            Parch = 0
            Fare = 50.0
            Embarked = Some "C"
        }
        {
            Pclass = 3
            Sex = "male"
            Age = None
            SibSp = 0
            Parch = 0
            Fare = 8.0
            Embarked = Some "S"
        }
    ]

let private formatList (values: int list) : string =
    values |> List.map string |> String.concat ", " |> sprintf "[%s]"

/// Survived.csv でクラスの重みの有無を比べ、ML.NET と突き合わせ、パイプラインを保存して読み込む
let runWith (modelDirectory: string) (print: string -> unit) : unit =
    let rows = loadSurvived (Path.Combine(dataDir (), "Survived.csv"))
    let x, t = splitFeaturesAndTarget rows
    let split = splitTrainTest TestSize Seed x t
    let survivors = t |> List.filter ((=) 1) |> List.length
    print $"データ件数: {rows.Length}（生存 {survivors}, 死亡 {rows.Length - survivors}）"
    print $"訓練データ: {split.XTrain.Length} 件, テストデータ: {split.XTest.Length} 件"

    let pipelines =
        ClassWeights
        |> List.map (fun classWeight ->
            classWeight,
            fitPipeline
                {
                    MaxDepth = Some MaxDepth
                    ClassWeight = classWeight
                }
                split.XTrain
                split.TTrain)
        |> Map.ofList

    for classWeight in ClassWeights do
        let pipeline = pipelines[classWeight]
        let result = evaluate pipeline split

        let fastTree =
            trainFastTree
                (pown 2 MaxDepth)
                (split.XTrain |> List.map (transform pipeline))
                split.TTrain
                (classWeights classWeight split.TTrain)

        let mlNet = predictFastTree fastTree (split.XTest |> List.map (transform pipeline))

        let agreed =
            List.zip (predict pipeline split.XTest) mlNet
            |> List.filter (fun (a, b) -> a = b)
            |> List.length

        print (
            $"classWeight={classWeight}: 訓練 {result.TrainAccuracy:F3}, テスト {result.TestAccuracy:F3}, "
            + $"生存者 {result.Survivors} 人中 {result.FoundSurvivors} 人を発見, ML.NET と一致 {agreed}/{split.XTest.Length}"
        )

    let balanced = pipelines[Balanced]
    let pipelineFile = Path.Combine(modelDirectory, PipelineFileName)
    saveModel pipelineFile balanced

    match loadModel pipelineFile with
    | Ok loaded -> print $"架空の乗客の予測（{PipelineFileName}）: {formatList (predict loaded NewPassengers)}"
    | Error message -> print $"{PipelineFileName} を読み込めません: {message}"

    let mlNetFile = Path.Combine(modelDirectory, MlNetFileName)

    trainFastTree
        (pown 2 MaxDepth)
        (split.XTrain |> List.map (transform balanced))
        split.TTrain
        (classWeights Balanced split.TTrain)
    |> saveFastTree mlNetFile

    let mlNetPredictions =
        predictFastTree (loadFastTree mlNetFile) (NewPassengers |> List.map (transform balanced))

    print $"架空の乗客の予測（{MlNetFileName}）: {formatList mlNetPredictions}"

let run (print: string -> unit) : unit = runWith ModelDirectory print
