module MachineLearning.Chapter08.MlNetAdapter

open System.IO
open Microsoft.ML
open Microsoft.ML.Data

/// ML.NET に渡す 1 行。生存したか（Label）と、行の重み（Weight）を持つ
[<CLIMutable>]
type WeightedRow =
    {
        Features: float32[]
        Label: bool
        Weight: float32
    }

/// ML.NET が返す 2 値分類の予測
[<CLIMutable>]
type BinaryPrediction = { PredictedLabel: bool }

let private toVector (row: Map<string, float>) : float32[] =
    row |> Map.values |> Seq.map float32 |> Seq.toArray

let private schemaFor (featureCount: int) : SchemaDefinition =
    let schema = SchemaDefinition.Create(typeof<WeightedRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, featureCount)
    schema

/// 特徴量・正解ラベル・重みを、ML.NET のデータ（IDataView）にする
let private toDataView
    (context: MLContext)
    (x: Map<string, float> list)
    (t: int list)
    (weights: float list)
    : IDataView =
    let rows =
        List.zip3 x t weights
        |> List.map (fun (features, label, weight) ->
            {
                Features = toVector features
                Label = (label = 1)
                Weight = float32 weight
            })

    context.Data.LoadFromEnumerable(rows, schemaFor x.Head.Count)

/// 木を 1 本だけ作る FastTree を、行の重み（Weight 列）を付けて学習する
let trainFastTree
    (numberOfLeaves: int)
    (x: Map<string, float> list)
    (t: int list)
    (weights: float list)
    : ITransformer =
    let context = MLContext(seed = 0)

    let fastTree =
        context.BinaryClassification.Trainers.FastTree(
            exampleWeightColumnName = "Weight",
            numberOfLeaves = numberOfLeaves,
            numberOfTrees = 1,
            minimumExampleCountPerLeaf = 1
        )

    fastTree.Fit(toDataView context x t weights) :> ITransformer

/// 学習した FastTree で、生存（1）か死亡（0）かを予測する
let predictFastTree (model: ITransformer) (x: Map<string, float> list) : int list =
    let context = MLContext(seed = 0)

    let data =
        toDataView context x (x |> List.map (fun _ -> 0)) (x |> List.map (fun _ -> 1.0))

    context.Data.CreateEnumerable<BinaryPrediction>(model.Transform data, reuseRowObject = false)
    |> Seq.map (fun prediction -> if prediction.PredictedLabel then 1 else 0)
    |> Seq.toList

/// 学習した FastTree を zip で保存する。保存先のディレクトリが無ければ作る
let saveFastTree (modelFile: string) (model: ITransformer) : unit =
    Directory.CreateDirectory(Path.GetDirectoryName modelFile) |> ignore
    MLContext(seed = 0).Model.Save(model, null, modelFile)

/// zip に保存した FastTree を読み込む
let loadFastTree (modelFile: string) : ITransformer =
    let model, _ = MLContext(seed = 0).Model.Load(modelFile)
    model
