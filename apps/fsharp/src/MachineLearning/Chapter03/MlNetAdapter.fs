module MachineLearning.Chapter03.MlNetAdapter

open Microsoft.ML
open Microsoft.ML.Data

/// ML.NET に渡す 1 行。ML.NET は引数なしのコンストラクターと書き換えられるプロパティを持つクラスを求めるので、
/// [<CLIMutable>] を付けたレコードにする
[<CLIMutable>]
type MlRow = { Features: float32[]; Label: string }

/// ML.NET が返す予測。列の名前（PredictedLabel）でプロパティに対応づけられる
[<CLIMutable>]
type MlPrediction = { PredictedLabel: string }

/// 特徴量の Map を、列名の順（Map のキーの順）に並べた float32 の配列にする
let private toVector (row: Map<string, float>) : float32[] =
    row |> Map.values |> Seq.map float32 |> Seq.toArray

/// 特徴量の数は実行時に決まるので、Features 列のベクトルの長さを SchemaDefinition で指定する
let private schemaFor (featureCount: int) : SchemaDefinition =
    let schema = SchemaDefinition.Create(typeof<MlRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, featureCount)
    schema

/// 木を 1 本だけ作る FastTree を、クラスごとの 2 値分類（OneVersusAll）で多クラスにして学習し、予測する関数を返す。
/// ML.NET には単一の決定木（CART）の学習器が無いので、勾配ブースティングの 1 本目の木で代わりにする。
/// 葉の数の上限 numberOfLeaves は、深さ d の決定木なら 2 の d 乗に当たる。
let trainFastTree
    (numberOfLeaves: int)
    (x: Map<string, float> list)
    (t: string list)
    : Map<string, float> list -> string list =
    let context = MLContext(seed = 0)
    let featureCount = x.Head.Count
    let schema = schemaFor featureCount

    let rows =
        List.map2
            (fun features label ->
                {
                    Features = toVector features
                    Label = label
                })
            x
            t

    let data = context.Data.LoadFromEnumerable(rows, schema)

    let fastTree =
        context.BinaryClassification.Trainers.FastTree(
            numberOfLeaves = numberOfLeaves,
            numberOfTrees = 1,
            minimumExampleCountPerLeaf = 1
        )

    let pipeline =
        EstimatorChain()
            .Append(context.Transforms.Conversion.MapValueToKey("Label"))
            .Append(context.MulticlassClassification.Trainers.OneVersusAll(fastTree))
            .Append(context.Transforms.Conversion.MapKeyToValue("PredictedLabel"))

    let model = pipeline.Fit(data)

    fun newX ->
        let newRows =
            newX
            |> List.map (fun features ->
                {
                    Features = toVector features
                    Label = ""
                })

        model.Transform(context.Data.LoadFromEnumerable(newRows, schema))
        |> fun predictions -> context.Data.CreateEnumerable<MlPrediction>(predictions, reuseRowObject = false)
        |> Seq.map (fun prediction -> prediction.PredictedLabel)
        |> Seq.toList
