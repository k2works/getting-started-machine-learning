module MachineLearning.Chapter10.MlNetClassifier

open Microsoft.ML
open Microsoft.ML.Data
open MachineLearning.Chapter03.MlNetAdapter
open MachineLearning.Chapter10.Classifier

/// 特徴量の Map を、列名の順（Map のキーの順）に並べた float32 の配列にする
let private toVector (row: Map<string, float>) : float32[] =
    row |> Map.values |> Seq.map float32 |> Seq.toArray

/// Features 列のベクトルの長さを、実行時に SchemaDefinition で指定する
let private schemaFor (featureCount: int) : SchemaDefinition =
    let schema = SchemaDefinition.Create(typeof<MlRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, featureCount)
    schema

/// 多クラス分類の学習器を受け取り、文字列のラベルを番号にして学習し、予測を文字列に戻す分類器にする
let private multiclass (trainer: MLContext -> IEstimator<'T>) : Classifier<string> =
    fun x t ->
        let context = MLContext(seed = 0)
        let schema = schemaFor x.Head.Count

        let toRows (features: Map<string, float> list) (labels: string list) =
            context.Data.LoadFromEnumerable(
                List.map2
                    (fun row label ->
                        {
                            Features = toVector row
                            Label = label
                        })
                    features
                    labels,
                schema
            )

        let pipeline =
            EstimatorChain()
                .Append(context.Transforms.Conversion.MapValueToKey("Label"))
                .Append(trainer context)
                .Append(context.Transforms.Conversion.MapKeyToValue("PredictedLabel"))

        let model = pipeline.Fit(toRows x t)

        fun newX ->
            model.Transform(toRows newX (newX |> List.map (fun _ -> "")))
            |> fun predictions -> context.Data.CreateEnumerable<MlPrediction>(predictions, reuseRowObject = false)
            |> Seq.map (fun prediction -> prediction.PredictedLabel)
            |> Seq.toList

/// ML.NET のソフトマックスのロジスティック回帰（L-BFGS で最適化する）。L1・L2 正則化の強さを指定する
let lbfgsMaximumEntropy (l1Regularization: float32) (l2Regularization: float32) : Classifier<string> =
    multiclass (fun context ->
        context.MulticlassClassification.Trainers.LbfgsMaximumEntropy(
            l1Regularization = l1Regularization,
            l2Regularization = l2Regularization
        )
        :> IEstimator<_>)

/// ML.NET のランダムフォレスト（FastForest）。2 クラス用なので、OneVersusAll で多クラスにする
let fastForest (numberOfTrees: int) (minimumExampleCountPerLeaf: int) : Classifier<string> =
    multiclass (fun context ->
        context.MulticlassClassification.Trainers.OneVersusAll(
            context.BinaryClassification.Trainers.FastForest(
                numberOfTrees = numberOfTrees,
                minimumExampleCountPerLeaf = minimumExampleCountPerLeaf
            )
        )
        :> IEstimator<_>)
